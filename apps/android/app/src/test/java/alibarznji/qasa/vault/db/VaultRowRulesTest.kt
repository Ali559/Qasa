package alibarznji.qasa.vault.db

import alibarznji.qasa.sync.VaultSyncStore
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import uniffi.qasa_ffi.FfiItem

/**
 * The structural item-row rules ([VaultRowRules]): the SQLite triggers (fresh install and
 * the 3 → 4 migration) run against a real SQLite, and the guard Wi‑Fi sync and backup
 * restore apply before writing ciphertext as received.
 */
class VaultRowRulesTest {
    private lateinit var db: Connection

    @Before fun open() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
    }

    @After fun close() = db.close()

    private fun exec(sql: String) = db.createStatement().use { it.execute(sql) }

    private fun create(version: Int) {
        val file = File("schemas/alibarznji.qasa.vault.db.VaultDatabase/$version.json")
        val schema = Json.parseToJsonElement(file.readText()).jsonObject["database"]!!.jsonObject
        for (entity in schema["entities"]!!.jsonArray) {
            val e = entity.jsonObject
            exec(e["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", e["tableName"]!!.jsonPrimitive.content))
        }
    }

    /** What `VaultMigrations.CreateTriggersCallback.onCreate` runs on a fresh install. */
    private fun freshInstall() {
        create(4)
        VaultRowRules.TRIGGERS_SQL.forEach(::exec)
    }

    private fun insert(
        id: String,
        kind: String = "login",
        envelope: String = "X'0102'",
        payload: String = "X'0304'",
        schemaVersion: Int = 1,
        deletedAt: Long? = null,
    ) = exec(
        "INSERT OR REPLACE INTO vault_items (id, kindRaw, encryptedEnvelope, encryptedPayload, schemaVersion, updatedAt, createdAt, deletedAt) " +
            "VALUES ('$id', '$kind', $envelope, $payload, $schemaVersion, 1, 1, ${deletedAt ?: "NULL"})",
    )

    private fun assertRejected(what: String, block: () -> Unit) {
        try {
            block()
            fail("$what must be rejected")
        } catch (e: SQLException) {
            assertTrue(e.message, e.message.orEmpty().contains("invalid row"))
        }
    }

    private fun count(): Int = db.createStatement().use { st ->
        st.executeQuery("SELECT COUNT(*) FROM vault_items").use { it.next(); it.getInt(1) }
    }

    @Test fun validRowsAndTombstonesAreAccepted() {
        freshInstall()
        for (kind in VaultRowRules.KINDS) insert("live-$kind", kind = kind)
        insert("gone", envelope = "X''", payload = "X''", deletedAt = 5)
        assertEquals(VaultRowRules.KINDS.size + 1, count())
    }

    @Test fun emptyLivePayloadIsRejected() {
        freshInstall()
        assertRejected("empty payload") { insert("a", payload = "X''") }
        assertRejected("empty envelope") { insert("b", envelope = "X''") }
        assertEquals(0, count())
    }

    @Test fun unknownKindAndSchemaVersionZeroAreRejected() {
        freshInstall()
        assertRejected("unknown kind") { insert("a", kind = "card") }
        assertRejected("schema version 0") { insert("b", schemaVersion = 0) }
    }

    @Test fun updatesAreChecked() {
        freshInstall()
        insert("a")
        assertRejected("emptying a live row") { exec("UPDATE vault_items SET encryptedPayload = X'' WHERE id = 'a'") }
        // The DAO's tombstone (empties the contents and sets deletedAt) is fine.
        exec(VaultSql.TOMBSTONE_ITEM.replace(":at", "9").replace(":id", "'a'"))
    }

    @Test fun migrationKeepsLegacyRowsUsable() {
        create(3)
        // Rows the triggers would refuse today, written before they existed.
        insert("old-version", schemaVersion = 0)
        insert("odd-kind", kind = "legacyKind")
        insert("gone", envelope = "X''", payload = "X''", deletedAt = 5)
        VaultMigrations.MIGRATION_3_4_SQL.forEach(::exec)

        val versions = db.createStatement().use { st ->
            st.executeQuery("SELECT schemaVersion FROM vault_items WHERE id = 'old-version'").use { it.next(); it.getInt(1) }
        }
        assertEquals(1, versions)
        // markUsed and a tombstone still work on the legacy-kind row.
        exec("UPDATE vault_items SET lastUsedAt = 3, updatedAt = 3 WHERE id = 'odd-kind'")
        exec(VaultSql.TOMBSTONE_ITEM.replace(":at", "9").replace(":id", "'odd-kind'"))
        // New writes are checked.
        assertRejected("empty payload after migration") { insert("new", payload = "X''") }
    }

    @Test fun entityRuleMatchesTheTriggers() {
        val live = VaultItemEntity("a", "login", byteArrayOf(1), byteArrayOf(2), 1, 1, 1)
        assertTrue(VaultRowRules.isValid(live))
        assertFalse(VaultRowRules.isValid(live.copy(encryptedPayload = ByteArray(0))))
        assertFalse(VaultRowRules.isValid(live.copy(kindRaw = "card")))
        assertFalse(VaultRowRules.isValid(live.copy(schemaVersion = 0)))
        assertTrue(VaultRowRules.isValid(live.copy(encryptedEnvelope = ByteArray(0), encryptedPayload = ByteArray(0), deletedAt = 2)))
    }

    @Test fun syncApplySkipsStructurallyInvalidRows() {
        fun item(id: String, kind: String = "login", payload: ByteArray = byteArrayOf(1), deletedAt: Long? = null) = FfiItem(
            id = id,
            kind = kind,
            envelope = byteArrayOf(1),
            payload = payload,
            schemaVersion = 1,
            createdAtMs = 1,
            updatedAtMs = 2,
            lastUsedAtMs = null,
            collectionId = null,
            deletedAtMs = deletedAt,
        )
        val (rows, rejected) = VaultSyncStore.guardRows(
            listOf(
                item("OK"),
                item("EMPTY", payload = ByteArray(0)),
                item("ODD", kind = "unknown"),
                // A tombstone carries no contents.
                item("GONE", payload = ByteArray(0), deletedAt = 3),
            ),
        )
        assertEquals(listOf("ok", "gone"), rows.map { it.id })
        assertEquals(2, rejected)
    }
}
