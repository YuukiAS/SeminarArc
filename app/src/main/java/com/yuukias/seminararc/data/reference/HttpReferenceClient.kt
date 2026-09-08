package com.yuukias.seminararc.data.reference

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HttpReferenceClient @Inject constructor() {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpReferenceResponse {
        return withContext(Dispatchers.IO) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "SeminarArc/0.3.x (Android local-first reference lookup)")
                headers.forEach { (key, value) -> setRequestProperty(key, value) }
            }
            try {
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                HttpReferenceResponse(code, body, connection.getHeaderField("Retry-After"))
            } catch (throwable: IOException) {
                HttpReferenceResponse(null, "", null, throwable.message ?: "Network request failed.")
            } finally {
                connection.disconnect()
            }
        }
    }

    fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}

data class HttpReferenceResponse(
    val statusCode: Int?,
    val body: String,
    val retryAfter: String?,
    val networkError: String? = null,
)
