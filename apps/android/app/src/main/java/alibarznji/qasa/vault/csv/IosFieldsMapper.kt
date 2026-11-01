package alibarznji.qasa.vault.csv

import alibarznji.qasa.screens.vault.editor.EmailKind
import alibarznji.qasa.screens.vault.editor.Honorific
import alibarznji.qasa.screens.vault.editor.NoteColor
import alibarznji.qasa.screens.vault.editor.PhoneKind
import alibarznji.qasa.vault.VaultPayload
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Explicit mapping between Android [VaultPayload] and the iOS `*Fields` structs
 * (`Models/VaultEnums.swift:249-340`) as they appear in the CSV `fields` column.
 *
 * The two payload encodings differ (parity plan P3 "Format note"): Android has a `type`
 * discriminator and stores the Name/Email/Phone pickers as `honorific`/`emailType`/
 * `phoneType`, while iOS encodes the bare struct with the picker under `type`. Swift's
 * synthesized decoder needs every non-optional key and a valid enum raw, so export writes
 * every key and normalises picker/colour values to the iOS raws.
 */
internal object IosFieldsMapper {

    /** iOS struct property names → values, for [IosJsonWriter]. */
    fun toIosFields(p: VaultPayload): Map<String, Any?> = when (p) {
        is VaultPayload.Login -> mapOf(
            "itemName" to p.itemName, "email" to p.email, "username" to p.username,
            "password" to p.password, "website" to p.website, "note" to p.note,
            "collectionId" to uuidOut(p.collectionId),
        )
        is VaultPayload.SecureNote -> mapOf(
            "title" to p.title, "content" to p.content,
            "color" to NoteColor.fromRaw(p.color, NoteColor.Mint).raw,
            "collectionId" to uuidOut(p.collectionId), "protectItem" to p.protectItem,
        )
        is VaultPayload.Card -> mapOf(
            "itemName" to p.itemName, "accountHolder" to p.accountHolder, "number" to p.number,
            "securityCode" to p.securityCode, "expiryDate" to p.expiryDate, "note" to p.note,
            "color" to NoteColor.fromRaw(p.color, NoteColor.Blue).raw,
        )
        is VaultPayload.BankAccount -> mapOf(
            "itemName" to p.itemName, "accountHolder" to p.accountHolder,
            "accountNumber" to p.accountNumber, "routingNumber" to p.routingNumber,
            "countryCode" to p.countryCode, "bankName" to p.bankName,
        )
        is VaultPayload.Name -> mapOf(
            "type" to Honorific.fromRaw(p.type).iosRaw, "firstName" to p.firstName,
            "lastName" to p.lastName, "middleName" to p.middleName,
            "defaultUsername" to p.defaultUsername, "birthDate" to p.birthDate,
            "birthPlace" to p.birthPlace,
        )
        is VaultPayload.PersonalEmail -> mapOf(
            "itemName" to p.itemName, "type" to EmailKind.fromRaw(p.type).iosRaw, "email" to p.email,
        )
        is VaultPayload.Phone -> mapOf(
            "itemName" to p.itemName, "type" to PhoneKind.fromRaw(p.type).iosRaw,
            "countryCode" to p.countryCode, "number" to p.number,
        )
        is VaultPayload.Address -> mapOf(
            "itemName" to p.itemName, "address" to p.address, "city" to p.city,
            "zipCode" to p.zipCode, "countryCode" to p.countryCode, "recipient" to p.recipient,
            "building" to p.building, "floor" to p.floor, "apartment" to p.apartment,
            "doorCode" to p.doorCode, "phoneItemId" to uuidOut(p.phoneItemId),
        )
        is VaultPayload.Company -> mapOf("companyName" to p.companyName, "jobTitle" to p.jobTitle)
        is VaultPayload.Website -> mapOf("itemName" to p.itemName, "websiteURL" to p.websiteURL)
        is VaultPayload.IdCard -> idDoc(p.name, p.countryCode, p.number, p.issueDate, p.expiryDate, p.issuePlace)
        is VaultPayload.Passport -> idDoc(p.name, p.countryCode, p.number, p.issueDate, p.expiryDate, p.issuePlace)
        is VaultPayload.DriverLicense ->
            idDoc(p.name, p.countryCode, p.number, p.issueDate, p.expiryDate, p.issuePlace)
    }

    private fun idDoc(name: String, cc: String, number: String, issue: String, expiry: String, place: String) =
        mapOf(
            "name" to name, "countryCode" to cc, "number" to number,
            "issueDate" to issue, "expiryDate" to expiry, "issuePlace" to place,
        )

    /**
     * iOS `decodePayload(type:json:)`. Returns null for an unknown type or malformed JSON so
     * the caller falls back to the wide columns, as iOS does. Unlike Swift's synthesized
     * decoder, missing keys take the struct defaults instead of failing the whole row.
     */
    fun fromIosFields(type: String, json: String): VaultPayload? {
        val o = try {
            Json.parseToJsonElement(json).jsonObject
        } catch (_: Exception) {
            return null
        }
        fun s(key: String, default: String = "") =
            (o[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: default
        fun uuid(key: String) = uuidIn((o[key] as? JsonPrimitive)?.contentOrNull)
        // iOS writes the picker under "type"; also accept the Android payload keys.
        fun picker(androidKey: String) = s("type").ifEmpty { s(androidKey) }

        return when (type) {
            "login" -> VaultPayload.Login(
                itemName = s("itemName"), email = s("email"), username = s("username"),
                password = s("password"), website = s("website"), note = s("note"),
                collectionId = uuid("collectionId"),
            )
            "secureNote" -> VaultPayload.SecureNote(
                title = s("title"), content = s("content"),
                color = NoteColor.fromRaw(s("color"), NoteColor.Mint).raw,
                collectionId = uuid("collectionId"),
                protectItem = (o["protectItem"] as? JsonPrimitive)?.booleanOrNull ?: false,
            )
            "card" -> VaultPayload.Card(
                itemName = s("itemName"), accountHolder = s("accountHolder"), number = s("number"),
                securityCode = s("securityCode"), expiryDate = s("expiryDate"), note = s("note"),
                color = NoteColor.fromRaw(s("color"), NoteColor.Blue).raw,
            )
            "bankAccount" -> VaultPayload.BankAccount(
                itemName = s("itemName"), accountHolder = s("accountHolder"),
                accountNumber = s("accountNumber"), routingNumber = s("routingNumber"),
                countryCode = s("countryCode", "IQ"), bankName = s("bankName"),
            )
            "name" -> VaultPayload.Name(
                type = Honorific.fromRaw(picker("honorific")).iosRaw, firstName = s("firstName"),
                lastName = s("lastName"), middleName = s("middleName"),
                defaultUsername = s("defaultUsername"), birthDate = s("birthDate"),
                birthPlace = s("birthPlace"),
            )
            "personalEmail" -> VaultPayload.PersonalEmail(
                itemName = s("itemName"), type = EmailKind.fromRaw(picker("emailType")).iosRaw,
                email = s("email"),
            )
            "phone" -> VaultPayload.Phone(
                itemName = s("itemName"), type = PhoneKind.fromRaw(picker("phoneType")).iosRaw,
                countryCode = s("countryCode", "IQ"), number = s("number"),
            )
            "address" -> VaultPayload.Address(
                itemName = s("itemName"), address = s("address"), city = s("city"),
                zipCode = s("zipCode"), countryCode = s("countryCode", "IQ"),
                recipient = s("recipient"), building = s("building"), floor = s("floor"),
                apartment = s("apartment"), doorCode = s("doorCode"),
                phoneItemId = uuid("phoneItemId"),
            )
            "company" -> VaultPayload.Company(companyName = s("companyName"), jobTitle = s("jobTitle"))
            "website" -> VaultPayload.Website(itemName = s("itemName"), websiteURL = s("websiteURL"))
            "idCard" -> VaultPayload.IdCard(
                s("name"), s("countryCode", "IQ"), s("number"), s("issueDate"), s("expiryDate"), s("issuePlace"),
            )
            "passport" -> VaultPayload.Passport(
                s("name"), s("countryCode", "IQ"), s("number"), s("issueDate"), s("expiryDate"), s("issuePlace"),
            )
            "driverLicense" -> VaultPayload.DriverLicense(
                s("name"), s("countryCode", "IQ"), s("number"), s("issueDate"), s("expiryDate"), s("issuePlace"),
            )
            else -> null
        }
    }

    /**
     * Swift encodes `UUID?` as its uppercase `uuidString` and rejects anything that isn't a
     * UUID, so a non-UUID id is dropped rather than making iOS discard the whole row.
     */
    private fun uuidOut(id: String?): String? =
        id?.let { runCatching { UUID.fromString(it) }.getOrNull() }?.toString()?.uppercase()

    /** Android ids are lowercase UUIDs (`VaultRepository.save`). */
    private fun uuidIn(raw: String?): String? =
        raw?.let { runCatching { UUID.fromString(it) }.getOrNull() }?.toString()?.lowercase()
}
