package alibarznji.qasa.vault.db

import alibarznji.qasa.vault.CollectionNames
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Runs the vault's SQL against a real SQLite (sqlite-jdbc) on the JVM: the 1 → 2 and
 * 2 → 3 migrations against Room's exported schemas, the DAO's tombstone filtering, and the
 * encrypted collection names ([VaultSql], the same constants the `@Query` annotations use).
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

        assertMatchesSchema(2)

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

    /** Every table matches what Room expects at [version] (Room validates this on open). */
    private fun assertMatchesSchema(version: Int) {
        val expected = DriverManager.getConnection("jdbc:sqlite::memory:")
        expected.use { other ->
            for (entity in schema(version)["entities"]!!.jsonArray) {
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
    }

    /** Rows of `vault_collections` as the DAO maps them. */
    private fun collections(sql: String = "SELECT * FROM vault_collections ORDER BY id"): List<VaultCollectionEntity> =
        db.createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            VaultCollectionEntity(
                                id = rs.getString("id"),
                                name = rs.getString("name"),
                                createdAt = rs.getLong("createdAt"),
                                updatedAt = rs.getLong("updatedAt"),
                                deletedAt = rs.getObject("deletedAt")?.let { (it as Number).toLong() },
                                nameEnc = rs.getBytes("nameEnc"),
                            ),
                        )
                    }
                }
            }
        }

    private fun collection(id: String) = collections().single { it.id == id }

    private val key = ByteArray(32) { it.toByte() }
    private val otherKey = ByteArray(32) { (it + 100).toByte() }

    @Test fun migration2To3MatchesRoomsVersion3Schema() {
        create(2)
        exec("INSERT INTO vault_collections (id, name, createdAt, updatedAt) VALUES ('c1', 'Work', 1, 5)")
        exec("INSERT INTO vault_collections (id, name, createdAt, updatedAt, deletedAt) VALUES ('c2', 'Old', 1, 9, 9)")

        VaultMigrations.MIGRATION_2_3_SQL.forEach(::exec)

        assertMatchesSchema(3)
        // A live row keeps its plaintext name until the next unlock encrypts it.
        val live = collection("c1")
        assertEquals("Work", live.name)
        assertNull(live.nameEnc)
        assertEquals(5L, live.updatedAt)
        // A tombstone keeps no name at all.
        val tomb = collection("c2")
        assertEquals("", tomb.name)
        assertEquals(0, tomb.nameEnc!!.size)
        assertEquals(9L, tomb.updatedAt)
        // Only the live plaintext row is left for the unlock pass.
        assertEquals(listOf("c1"), collections(VaultSql.PLAINTEXT_COLLECTION_NAMES).map { it.id })
    }

    @Test fun legacyNamesAreEncryptedAtUnlockWithoutAnEdit() {
        create(2)
        exec("INSERT INTO vault_collections (id, name, createdAt, updatedAt) VALUES ('c1', 'Família 🔒', 1, 5)")
        VaultMigrations.MIGRATION_2_3_SQL.forEach(::exec)

        // `VaultRepository.encryptLegacyCollectionNames`, step by step.
        val updates = CollectionNames.legacyUpdates(collections(VaultSql.PLAINTEXT_COLLECTION_NAMES), key)
        updates.forEach { (id, enc) -> update(VaultSql.SET_COLLECTION_NAME_ENC, enc, id) }

        val row = collection("c1")
        assertEquals("", row.name)
        assertEquals("Família 🔒", CollectionNames.decrypt(row.nameEnc!!, key))
        assertEquals(5L, row.updatedAt)
        assertEquals("Família 🔒", CollectionNames.readable(row, key)!!.name)
        // Nothing left for the next unlock.
        assertEquals(emptyList<VaultCollectionEntity>(), collections(VaultSql.PLAINTEXT_COLLECTION_NAMES))
    }

    @Test fun renameStoresOnlyCiphertext() {
        create(3)
        exec("INSERT INTO vault_collections (id, name, createdAt, updatedAt) VALUES ('c1', 'Plain', 1, 1)")

        update(VaultSql.RENAME_COLLECTION, CollectionNames.encrypt("Bank", key), 42L, "c1")

        val row = collection("c1")
        assertEquals("", row.name)
        assertEquals("Bank", CollectionNames.decrypt(row.nameEnc!!, key))
        assertEquals(42L, row.updatedAt)
        assertEquals(1L, row.createdAt)
    }

    @Test fun deletedCollectionKeepsNoName() {
        create(3)
        exec("INSERT INTO vault_collections (id, name, createdAt, updatedAt) VALUES ('c1', 'Plain', 1, 1)")
        db.prepareStatement("INSERT INTO vault_collections (id, name, nameEnc, createdAt, updatedAt) VALUES ('c2', '', ?, 1, 1)").use {
            it.setBytes(1, CollectionNames.encrypt("Bank", key))
            it.executeUpdate()
        }

        update(VaultSql.TOMBSTONE_COLLECTION, 50L, "c1")
        update(VaultSql.TOMBSTONE_COLLECTION, 50L, "c2")

        for (row in collections()) {
            assertEquals("", row.name)
            assertEquals(0, row.nameEnc!!.size)
            assertEquals(50L, row.deletedAt)
            assertNull(CollectionNames.readable(row, key))
        }
        assertEquals(emptyList<VaultCollectionEntity>(), collections(VaultSql.ENCRYPTED_COLLECTION_NAMES))
        assertEquals(emptyList<VaultCollectionEntity>(), collections(VaultSql.PLAINTEXT_COLLECTION_NAMES))
    }

    @Test fun passwordChangeReencryptsNamesUnderTheNewKey() {
        create(3)
        db.prepareStatement("INSERT INTO vault_collections (id, name, nameEnc, createdAt, updatedAt) VALUES ('c1', '', ?, 1, 3)").use {
            it.setBytes(1, CollectionNames.encrypt("Work", key))
            it.executeUpdate()
        }
        exec("INSERT INTO vault_collections (id, name, createdAt, updatedAt) VALUES ('c2', 'Legacy', 1, 4)")

        // `VaultRepository.reencryptAll`, the collection half (one transaction there).
        val names = CollectionNames.rekey(collections(VaultSql.ENCRYPTED_COLLECTION_NAMES), key, otherKey) +
            CollectionNames.legacyUpdates(collections(VaultSql.PLAINTEXT_COLLECTION_NAMES), otherKey)
        names.forEach { (id, enc) -> update(VaultSql.SET_COLLECTION_NAME_ENC, enc, id) }

        val readable = CollectionNames.readableSorted(collections(VaultSql.LIVE_COLLECTIONS), otherKey)
        assertEquals(listOf("Legacy", "Work"), readable.map { it.name })
        assertEquals(listOf(4L, 3L), readable.map { it.updatedAt })
        // The old key no longer reads them: unreadable names are skipped, not shown.
        assertEquals(emptyList<String>(), CollectionNames.readableSorted(collections(VaultSql.LIVE_COLLECTIONS), key).map { it.name })
        assertTrue(collections().all { it.name.isEmpty() })
    }

    @Test fun rekeyRefusesAnUnreadableName() {
        val row = VaultCollectionEntity(id = "c1", createdAt = 1, nameEnc = CollectionNames.encrypt("Work", otherKey))
        val failed = runCatching { CollectionNames.rekey(listOf(row), key, otherKey) }
        assertNotNull(failed.exceptionOrNull())
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
        create(3)
        exec("INSERT INTO vault_collections (id, name, createdAt, updatedAt) VALUES ('b', '', 1, 1)")
        exec("INSERT INTO vault_collections (id, name, createdAt, updatedAt) VALUES ('a', '', 2, 2)")
        // Oldest first: names are ciphertext, sorted after decryption.
        assertEquals(listOf("b", "a"), ids(VaultSql.LIVE_COLLECTIONS))

        update(VaultSql.TOMBSTONE_COLLECTION, 50L, "a")

        assertEquals(listOf("b"), ids(VaultSql.LIVE_COLLECTIONS))
    }
}
