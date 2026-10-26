package nz.ac.aut.comp713.tarocartmobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class MobileAccount(val username: String, val role: String, val profileId: Long?)
data class MobileLoginResult(val account: MobileAccount, val sessionToken: String)

/** Native login-service API; all blocking HTTP work is confined to Dispatchers.IO. */
object MobileAuthApi {
    suspend fun login(username: String, password: String): MobileLoginResult = withContext(Dispatchers.IO) {
        val response = post("/api/v1/mobile/login", JSONObject().put("username", username).put("password", password))
        MobileLoginResult(
            MobileAccount(response.getString("username"), response.getString("role"),
                if (response.isNull("profileId")) null else response.getLong("profileId")),
            response.getString("sessionToken")
        )
    }

    suspend fun register(role: String, username: String, password: String, confirmPassword: String,
                         name: String, contactName: String, phone: String, address: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("username", username); put("password", password); put("confirmPassword", confirmPassword)
            put("name", name); put("contactName", contactName); put("phone", phone); put("address", address)
        }
        post("/api/v1/mobile/register/${role.lowercase()}", body).optString("message")
    }

    suspend fun logout(sessionToken: String) = withContext(Dispatchers.IO) {
        post("/api/v1/mobile/logout", JSONObject().put("sessionToken", sessionToken))
    }

    private fun post(path: String, body: JSONObject): JSONObject {
        val connection = (URL(nz.ac.aut.comp713.tarocartmobile.BuildConfig.LOGIN_SERVICE_BASE_URL.trimEnd('/') + path)
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val responseText = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val error = runCatching {
                    val json = JSONObject(responseText)
                    json.optString("detail").ifBlank { json.optString("message") }
                }.getOrNull()
                throw IOException(error?.takeIf { it.isNotBlank() } ?: "Request failed (HTTP $status).")
            }
            return if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
        } catch (exception: IOException) {
            if (exception.message?.startsWith("CLEARTEXT") == true) {
                throw IOException("HTTP traffic is blocked. Check the app network configuration.", exception)
            }
            throw exception
        } finally {
            connection.disconnect()
        }
    }
}
