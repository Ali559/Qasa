package alibarznji.qasa.vault.csv

import alibarznji.qasa.vault.VaultPayload
import alibarznji.qasa.vault.totp.Totp

/** iOS `VaultCSVError`; the UI maps these to `settings.importNothing` / `settings.importBadHeader`. */
sealed class VaultCsvException : Exception() {
    object Empty : VaultCsvException() { private fun readResolve(): Any = Empty }
    data class InvalidHeader(val missing: String) : VaultCsvException()
}

/**
 * Plaintext CSV backup of the vault — port of iOS `Services/Vault/VaultCSV.swift`.
 *
 * Wide human-readable columns for the common fields plus a `fields` column holding the
 * compact JSON of the iOS `*Fields` struct (see [IosFieldsMapper]). Import prefers `fields`
 * so the round trip is exact; the wide columns keep the file usable in a spreadsheet.
 */
object VaultCsv {
    val columns = listOf(
        "type", "title", "username", "password", "url", "email",
        "account_holder", "number", "security_code", "expiry",
        "account_number", "routing_number", "bank_name", "content",
        "notes", "fields",
    )

    // Export

    /** iOS `exportCSV`: header + one row per item, joined with "\n", no trailing newline. */
    fun exportCsv(payloads: List<VaultPayload>): String =
        (listOf(columns.joinToString(",")) + payloads.map(::row)).joinToString("\n")

    private fun row(p: VaultPayload): String {
        val cells = HashMap<String, String>()
        cells["type"] = typeName(p)
        cells["title"] = iosDisplayName(p)
        when (p) {
            is VaultPayload.Login -> {
                cells["username"] = p.username
                cells["password"] = p.password
                cells["url"] = p.website
                cells["email"] = p.email
                cells["notes"] = p.note
            }
            is VaultPayload.SecureNote -> {
                cells["content"] = p.content
                cells["notes"] = p.protectItem.toString()
            }
            is VaultPayload.Card -> {
                cells["account_holder"] = p.accountHolder
                cells["number"] = p.number
                cells["security_code"] = p.securityCode
                cells["expiry"] = p.expiryDate
                cells["notes"] = p.note
            }
            is VaultPayload.BankAccount -> {
                cells["account_holder"] = p.accountHolder
                cells["account_number"] = p.accountNumber
                cells["routing_number"] = p.routingNumber
                cells["bank_name"] = p.bankName
            }
            is VaultPayload.Name -> {
                cells["email"] = p.defaultUsername
                cells["expiry"] = p.birthDate
                cells["content"] = p.birthPlace
            }
            is VaultPayload.PersonalEmail -> cells["email"] = p.email
            is VaultPayload.Phone -> cells["number"] = p.number
            is VaultPayload.Address -> {
                cells["content"] = listOf(p.address, p.city, p.zipCode).filter { it.isNotEmpty() }.joinToString(" ")
                cells["notes"] = listOf(p.building, p.floor, p.apartment, p.doorCode)
                    .filter { it.isNotEmpty() }.joinToString(" ")
                cells["account_holder"] = p.recipient
            }
            is VaultPayload.Company -> cells["account_holder"] = p.jobTitle
            is VaultPayload.Website -> cells["url"] = p.websiteURL
            is VaultPayload.IdCard -> idDocCells(cells, p.name, p.number, p.expiryDate, p.issueDate, p.issuePlace)
            is VaultPayload.Passport -> idDocCells(cells, p.name, p.number, p.expiryDate, p.issueDate, p.issuePlace)
            is VaultPayload.DriverLicense ->
                idDocCells(cells, p.name, p.number, p.expiryDate, p.issueDate, p.issuePlace)
        }
        cells["fields"] = fieldsJson(p)
        return columns.joinToString(",") { CsvPrimitives.escape(cells[it] ?: "") }
    }

    private fun idDocCells(
        cells: MutableMap<String, String>, name: String, number: String,
        expiry: String, issue: String, place: String,
    ) {
        cells["account_holder"] = name
        cells["number"] = number
        cells["expiry"] = expiry
        cells["content"] = issue
        cells["notes"] = place
    }

    /** Compact sorted-key JSON of the iOS struct; deterministic so repeat exports match. */
    fun fieldsJson(p: VaultPayload): String = IosJsonWriter.write(IosFieldsMapper.toIosFields(p))

    // Import

    /** iOS `fingerprint`: used to skip rows already in the vault on a repeated import. */
    fun fingerprint(p: VaultPayload): String = typeName(p) + "|" + fieldsJson(p)

    /** iOS `parseImport`. Throws [VaultCsvException]. */
    fun parseImport(csv: String): List<VaultPayload> {
        val records = CsvPrimitives.parseRecords(csv)
            .map { it.trim('\r') }
            .filter { it.trim(' ', '\t').isNotEmpty() }
        val header = records.firstOrNull() ?: throw VaultCsvException.Empty

        val headerCells = CsvPrimitives.parseLine(header).map { it.trim(' ', '\t').lowercase() }
        if ("type" !in headerCells || "title" !in headerCells) {
            throw VaultCsvException.InvalidHeader("'type', 'title'")
        }
        fun index(name: String) = headerCells.indexOf(name).takeIf { it >= 0 }
        val titleIdx = index("title")
        val fieldsIdx = index("fields")

        val out = mutableListOf<VaultPayload>()
        for (record in records.drop(1)) {
            val cells = CsvPrimitives.parseLine(record)
            fun cell(i: Int?) = if (i != null && i < cells.size) cells[i] else ""
            fun value(name: String) = cell(index(name))

            val title = cell(titleIdx)
            if (title.isEmpty()) continue

            // Prefer the exact JSON round-trip when present.
            val fieldsRaw = cell(fieldsIdx)
            val type = value("type")
            val exact = if (fieldsRaw.isNotEmpty()) IosFieldsMapper.fromIosFields(type, fieldsRaw) else null
            if (exact != null) {
                out.add(exact)
                continue
            }

            // Fall back to the wide columns. Only the kinds those columns fully describe
            // are rebuilt; anything else is skipped rather than silently losing secrets.
            when (type) {
                "login" -> out.add(
                    VaultPayload.Login(
                        itemName = title, email = value("email"), username = value("username"),
                        password = value("password"), website = value("url"), note = value("notes"),
                        // Other managers' flat exports name the 2FA column either way; a
                        // value that isn't a valid TOTP secret is dropped, not stored.
                        totp = Totp.normalize(value("totp").ifEmpty { value("login_totp") }).orEmpty(),
                    ),
                )
                "secureNote" -> out.add(VaultPayload.SecureNote(title = title, content = value("content")))
                "card" -> out.add(
                    VaultPayload.Card(
                        itemName = title, accountHolder = value("account_holder"), number = value("number"),
                        securityCode = value("security_code"), expiryDate = value("expiry"),
                        note = value("notes"),
                    ),
                )
                "bankAccount" -> out.add(
                    VaultPayload.BankAccount(
                        itemName = title, accountHolder = value("account_holder"),
                        accountNumber = value("account_number"), routingNumber = value("routing_number"),
                        bankName = value("bank_name"),
                    ),
                )
                else -> Unit
            }
        }
        return out
    }

    /** iOS `VaultPayload.displayName` — no "Untitled" fallback, so blank titles stay blank. */
    fun iosDisplayName(p: VaultPayload): String = when (p) {
        is VaultPayload.Login -> p.itemName
        is VaultPayload.SecureNote -> p.title
        is VaultPayload.Card -> p.itemName
        is VaultPayload.BankAccount -> p.itemName
        is VaultPayload.Name -> listOf(p.firstName, p.lastName).filter { it.isNotEmpty() }.joinToString(" ")
        is VaultPayload.PersonalEmail -> p.itemName.ifEmpty { p.email }
        is VaultPayload.Phone -> p.itemName.ifEmpty { p.number }
        is VaultPayload.Address -> p.itemName
        is VaultPayload.Company -> p.companyName
        is VaultPayload.Website -> p.itemName
        is VaultPayload.IdCard -> p.name
        is VaultPayload.Passport -> p.name
        is VaultPayload.DriverLicense -> p.name
    }

    fun typeName(p: VaultPayload): String = when (p) {
        is VaultPayload.Login -> "login"
        is VaultPayload.SecureNote -> "secureNote"
        is VaultPayload.Card -> "card"
        is VaultPayload.BankAccount -> "bankAccount"
        is VaultPayload.Name -> "name"
        is VaultPayload.PersonalEmail -> "personalEmail"
        is VaultPayload.Phone -> "phone"
        is VaultPayload.Address -> "address"
        is VaultPayload.Company -> "company"
        is VaultPayload.Website -> "website"
        is VaultPayload.IdCard -> "idCard"
        is VaultPayload.Passport -> "passport"
        is VaultPayload.DriverLicense -> "driverLicense"
    }
}
