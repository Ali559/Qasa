package alibarznji.qasa.vault.db

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Runs the vault's SQL against a real SQLite (sqlite-jdbc) on the JVM: the 1 → 2
 * migration against Room's exported schemas, and the DAO's tombstone filtering
 * ([VaultSql], the same constants the `@Query` annotations use).
 */
class VaultSchemaTest {
    private lateinit var db: Connection

    @Before fun open() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
    }

    @After fun close() = db.close()

    private fun schema(version: Int): JsonObject {
        val file = File("schemas/alibarznji.qasa.vault.db.VaultDatabase/$version.json")
        return Json.parseToJsonElement(file.readText()).jsonObject["database"]!!.jsonObject
    }

    private fun create(version: Int) {
        for (entity in schema(version)["entities"]!!.jsonArray) {
            val e = entity.jsonObject
            val sql = e["createSql"]!!.jsonPrimitive.content
                .replace("\${TABLE_NAME}", e["tableName"]!!.jsonPrimitive.content)
            db.createStatement().use { it.execute(sql) }
        }
    }

    private fun exec(sql: String) = db.createStatement().use { it.execute(sql) }

    /** name → (type, notNull, default) as SQLite reports them. */
    private fun columns(table: String): Map<String, Triple<String, Boolean, String?>> =
        db.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info(`$table`)").use { rs ->
                buildMap {
                    while (rs.next()) {
                        put(rs.getString("name"), Triple(rs.getString("type"), rs.getInt("notnull") == 1, rs.getString("dflt_value")))
                    }
                }
            }
        }

    private fun ids(sql: String, vararg args: Any): List<String> =
        db.prepareStatement(sql.replace(Regex(":[a-zA-Z]+"), "?")).use { ps ->
            args.forEachIndexed { i, a -> ps.setObject(i + 1, a) }
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString("id")) } }
        }

    private fun update(sql: String, vararg args: Any) {
        // SQLite numbers repeated named parameters once, so bind by first appearance.
        val names = Regex(":([a-zA-Z]+)").findAll(sql).map { it.value }.distinct().toList()
        db.prepareStatement(sql).use { ps ->
            names.forEachIndexed { i, _ -> ps.setObject(i + 1, args[i]) }
            ps.executeUpdate()
        }
    }

    private fun insertItem(id: String, updatedAt: Long, deletedAt: Long? = null) = exec(
        "INSERT INTO vault_items (id, kindRaw, encryptedEnvelope, encryptedPayload, schemaVersion, updatedAt, createdAt, deletedAt) " +
            "VALUES ('$id', 'login', X'0102', X'0304', 1, $updatedAt, 1, ${deletedAt ?: "NULL"})",
    )

    @Test fun migration1To2MatchesRoomsVersion2Schema() {
        create(1)
        exec("INSERT INTO vault_collections (id, name, createdAt) VALUES ('c1', 'Work', 1234)")
        exec(
            "INSERT INTO vault_items (id, kindRaw, encryptedEnvelope, encryptedPayload, schemaVersion, updatedAt, createdAt) " +
                "VALUES ('a', 'login', X'01', X'02', 1, 5, 1)",
        )

        VaultMigrations.MIGRATION_1_2_SQL.forEach(::exec)

        // Every table matches what Room expects at v2 (Room validates this on open).
        val expected = DriverManager.getConnection("jdbc:sqlite::memory:")
        expected.use { other ->
            for (entity in schema(2)["entities"]!!.jsonArray) {
                val e = entity.jsonObject
                val table = e["tableName"]!!.jsonPrimitive.content
                other.createStatement().use {
                    it.execute(e["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
                val want = other.createStatement().use { st ->
                    st.executeQuery("PRAGMA table_info(`$table`)").use { rs ->
                        buildMap {
                            while (rs.next()) {
                                put(rs.getString("name"), Triple(rs.getString("type"), rs.getInt("notnull") == 1, rs.getString("dflt_value")))
                            }
                        }
                    }
                }
                assertEquals(table, want, columns(table))
            }
        }

        // Existing rows survive; a collection's updatedAt starts at its createdAt.
        db.createStatement().use { st ->
            st.executeQuery("SELECT updatedAt, deletedAt FROM vault_collections WHERE id = 'c1'").use { rs ->
                rs.next()
                assertEquals(1234L, rs.getLong(1))
                assertNull(rs.getObject(2))
            }
        }
        assertEquals(listOf("a"), ids(VaultSql.LIVE_ITEMS))
    }

    @Test fun liveQueriesSkipTombstones() {
        create(2)
        insertItem("live-old", updatedAt = 10)
        insertItem("live-new", updatedAt = 20)
        insertItem("gone", updatedAt = 30, deletedAt = 30)

        assertEquals(listOf("live-new", "live-old"), ids(VaultSql.LIVE_ITEMS))
        assertEquals(listOf("live-old"), ids(VaultSql.LIVE_ITEM, "live-old"))
        assertEquals(emptyList<String>(), ids(VaultSql.LIVE_ITEM, "gone"))
    }

    @Test fun deleteLeavesAnEmptyTombstoneTheSyncStillSees() {
        create(2)
        insertItem("a", updatedAt = 10)

        update(VaultSql.TOMBSTONE_ITEM, 99L, "a")

        assertEquals(emptyList<String>(), ids(VaultSql.LIVE_ITEMS))
        db.createStatement().use { st ->
            st.executeQuery("SELECT length(encryptedEnvelope), length(encryptedPayload), updatedAt, deletedAt FROM vault_items").use { rs ->
                rs.next()
                assertEquals(0, rs.getInt(1))
                assertEquals(0, rs.getInt(2))
                assertEquals(99L, rs.getLong(3))
                assertEquals(99L, rs.getLong(4))
            }
        }
    }

    @Test fun collectionTombstonesAreHidden() {
        create(2)
        exec("INSERT INTO vault_collections (id, name, createdAt, updatedAt) VALUES ('b', 'Bank', 1, 1)")
        exec("INSERT INTO vault_collections (id, name, createdAt, updatedAt) VALUES ('a', 'Apps', 1, 1)")
        assertEquals(listOf("a", "b"), ids(VaultSql.LIVE_COLLECTIONS))

        update(VaultSql.TOMBSTONE_COLLECTION, 50L, "a")

        assertEquals(listOf("b"), ids(VaultSql.LIVE_COLLECTIONS))
    }
}
