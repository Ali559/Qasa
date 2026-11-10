package alibarznji.qasa.network

import alibarznji.qasa.BuildConfig
import alibarznji.qasa.config.QasaConfig
import alibarznji.qasa.vault.VaultSecureStore
import javax.inject.Inject
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor

sealed class QasaApiException(message: String) : Exception(message) {
    class Unauthorized : QasaApiException("Unauthorized")
    class Forbidden : QasaApiException("Forbidden")
    class DeviceLimitExceeded(val body: DeviceLimitErrorBody) : QasaApiException(body.message)
    class HttpStatus(val code: Int) : QasaApiException("Server error $code")
    class Network(cause: Throwable) : QasaApiException(cause.message ?: "Network error")
    class Decoding(cause: Throwable) : QasaApiException(cause.message ?: "Decoding error")
}

@Singleton
class QasaApiClient @Inject constructor(
    private val store: VaultSecureStore,
) {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    private val client = OkHttpClient.Builder()
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                // BASIC logs request lines and status codes, never bodies, so it is not a
                // secret leak — but it enumerates every endpoint and status on a release
                // build, which is needless disclosure. NONE is a no-op sink.
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                else HttpLoggingInterceptor.Level.NONE
            }
        )
        .build()
    private val media = "application/json".toMediaType()

    fun <T> get(url: String, deserializer: KSerializer<T>, authorized: Boolean = true): T =
        decode(raw(url, "GET", null, authorized), deserializer)

    fun <T, B> post(url: String, body: B, bodySerializer: KSerializer<B>, deserializer: KSerializer<T>, authorized: Boolean = true): T =
        decode(raw(url, "POST", json.encodeToString(bodySerializer, body), authorized), deserializer)

    fun <T, B> patch(url: String, body: B, bodySerializer: KSerializer<B>, deserializer: KSerializer<T>, authorized: Boolean = true): T =
        decode(raw(url, "PATCH", json.encodeToString(bodySerializer, body), authorized), deserializer)

    fun <T, B> deleteWithBody(url: String, body: B, bodySerializer: KSerializer<B>, deserializer: KSerializer<T>, authorized: Boolean = true): T =
        decode(raw(url, "DELETE", json.encodeToString(bodySerializer, body), authorized), deserializer)

    fun deleteEmpty(url: String, authorized: Boolean = true) {
        raw(url, "DELETE", null, authorized)
    }

    fun postRaw(url: String, bodyJson: String, authorized: Boolean = false): String =
        raw(url, "POST", bodyJson, authorized)

    private fun <T> decode(data: String, deserializer: KSerializer<T>): T = try {
        json.decodeFromString(deserializer, data)
    } catch (e: Exception) {
        throw QasaApiException.Decoding(e)
    }

    /**
     * A call that must give up quickly (the restore key PUT before a backup): same pool and
     * interceptors, [timeoutMs] for the whole call.
     */
    fun rawQuick(url: String, method: String, bodyJson: String?, authorized: Boolean, timeoutMs: Long): String =
        raw(url, method, bodyJson, authorized, client.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build())

    fun raw(url: String, method: String, bodyJson: String?, authorized: Boolean): String =
        raw(url, method, bodyJson, authorized, client)

    private fun raw(url: String, method: String, bodyJson: String?, authorized: Boolean, client: OkHttpClient): String {
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        when {
            bodyJson != null -> {
                builder.header("Content-Type", "application/json")
                builder.method(method, bodyJson.toRequestBody(media))
            }
            method == "POST" || method == "PATCH" || method == "PUT" -> builder.method(method, "{}".toRequestBody(media))
            else -> builder.method(method, null)
        }
        if (authorized) {
            store.licenseToken?.let { builder.header("Authorization", "Bearer $it") }
        }
        val response = try {
            client.newCall(builder.build()).execute()
        } catch (e: Exception) {
            throw QasaApiException.Network(e)
        }
        val body = response.body?.string().orEmpty()
        when (response.code) {
            in 200..299 -> return body.ifBlank { "{}" }
            401 -> throw QasaApiException.Unauthorized()
            403 -> {
                val limit = runCatching { json.decodeFromString(DeviceLimitErrorBody.serializer(), body) }.getOrNull()
                if (limit?.error == "device_limit_exceeded") throw QasaApiException.DeviceLimitExceeded(limit)
                throw QasaApiException.Forbidden()
            }
            else -> throw QasaApiException.HttpStatus(response.code)
        }
    }

    fun healthCheck(): Boolean = try {
        raw(QasaConfig.healthUrl, "GET", null, authorized = false)
        true
    } catch (_: Exception) {
        false
    }
}

inline fun <reified T> QasaApiClient.get(url: String, authorized: Boolean = true): T =
    get(url, serializer(), authorized)

inline fun <reified T, reified B> QasaApiClient.post(url: String, body: B, authorized: Boolean = true): T =
    post(url, body, serializer(), serializer(), authorized)

inline fun <reified T, reified B> QasaApiClient.patch(url: String, body: B, authorized: Boolean = true): T =
    patch(url, body, serializer(), serializer(), authorized)

inline fun <reified T, reified B> QasaApiClient.delete(url: String, body: B, authorized: Boolean = true): T =
    deleteWithBody(url, body, serializer(), serializer(), authorized)
