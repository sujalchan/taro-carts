package nz.ac.aut.comp713.tarocartmobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONException
import java.net.ConnectException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL

data class MobileAccount(val username: String, val role: String, val profileId: Long?)
data class MobileLoginResult(val account: MobileAccount, val sessionToken: String)

class MobileApiException(val statusCode: Int, val errorCode: String, message: String) : IOException(message)

/** Native login-service API; all blocking HTTP work is confined to Dispatchers.IO. */
object MobileAuthApi {
    suspend fun login(username: String, password: String): MobileLoginResult = withContext(Dispatchers.IO) {
        val response = post("/api/v1/mobile/login", JSONObject().put("username", username).put("password", password))
        try {
            MobileLoginResult(
                MobileAccount(response.getString("username"), response.getString("role"),
                    if (response.isNull("profileId")) null else response.getLong("profileId")),
                response.getString("sessionToken")
            )
        } catch (exception: JSONException) {
            throw IOException("Taro Carts returned an unexpected sign-in response. Please try again.", exception)
        }
    }

    suspend fun register(role: String, username: String, password: String, confirmPassword: String,
                         name: String, contactName: String, phone: String, address: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("username", username); put("password", password); put("confirmPassword", confirmPassword)
            put("name", name); put("contactName", contactName); put("phone", phone); put("address", address)
        }
        post("/api/v1/mobile/register/${role.lowercase()}", body).optString("message")
            .ifBlank { "Your request was received. An administrator must approve it before you can sign in." }
    }

    suspend fun logout(sessionToken: String) = withContext(Dispatchers.IO) {
        post("/api/v1/mobile/logout", JSONObject().put("sessionToken", sessionToken))
    }

    suspend fun checkSession(sessionToken: String) = withContext(Dispatchers.IO) {
        post("/api/v1/mobile/session", JSONObject().put("sessionToken", sessionToken))
    }

    suspend fun changePassword(sessionToken: String, currentPassword: String,
                               newPassword: String, confirmPassword: String) = withContext(Dispatchers.IO) {
        post("/api/v1/account/password", JSONObject()
            .put("sessionToken", sessionToken)
            .put("currentPassword", currentPassword)
            .put("newPassword", newPassword)
            .put("confirmPassword", confirmPassword))
    }

    fun userFacingMessage(exception: Exception, fallback: String): String = when (exception) {
        is MobileApiException -> exception.message ?: fallback
        is SocketTimeoutException -> "Login Service is not responding. Try again shortly."
        is UnknownHostException, is ConnectException -> "Cannot connect to Login Service. Check your connection and try again."
        is IOException -> exception.message?.takeIf { it.isNotBlank() } ?: fallback
        else -> fallback
    }

    suspend fun reverseGeocode(latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        post("/api/v1/mobile/address/reverse", JSONObject()
            .put("latitude", latitude).put("longitude", longitude)).getString("address")
    }

    private fun post(path: String, body: JSONObject): JSONObject {
        val url = nz.ac.aut.comp713.tarocartmobile.BuildConfig.LOGIN_SERVICE_BASE_URL.trimEnd('/') + path
        val connection = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        } catch (exception: IOException) {
            throw friendlyNetworkFailure(exception)
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val responseText = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val apiError = runCatching {
                    val json = JSONObject(responseText)
                    json.optString("code") to json.optString("message")
                }.getOrNull()
                val code = apiError?.first.orEmpty()
                val serverMessage = apiError?.second.orEmpty()
                val message = if (status in setOf(502, 503, 504)) {
                    ServiceErrors.http(
                        if (path.endsWith("/address/reverse")) BackendService.ADDRESS_LOOKUP
                        else BackendService.LOGIN, status, code, serverMessage)
                } else {
                    serverMessage.takeIf { it.isNotBlank() } ?: statusMessage(path, status)
                }
                throw MobileApiException(status, code, message)
            }
            return if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
        } catch (exception: MobileApiException) {
            throw exception
        } catch (exception: SocketTimeoutException) {
            throw friendlyNetworkFailure(exception)
        } catch (exception: UnknownHostException) {
            throw friendlyNetworkFailure(exception)
        } catch (exception: JSONException) {
            throw IOException("Taro Carts returned an unexpected response. Please try again.", exception)
        } catch (exception: IOException) {
            throw friendlyNetworkFailure(exception)
        } finally {
            connection.disconnect()
        }
    }

    private fun friendlyNetworkFailure(exception: IOException): IOException =
        ServiceErrors.network(BackendService.LOGIN, exception)

    private fun statusMessage(path: String, status: Int): String = when {
        status == 400 -> "Some information is missing or invalid. Check it and try again."
        status == 401 && path.endsWith("/login") -> "Your username or password may be incorrect, or your account may not be available. Check your details; if your account is disabled, contact your administrator."
        status == 401 -> "Your session has expired or was signed out. Please sign in again."
        status == 403 -> "Your account cannot use this service right now. Contact your administrator for help."
        status == 404 -> "This sign-in service is unavailable. Please try again later."
        status == 409 -> "That username is already in use. Choose another username."
        status == 429 -> "There have been too many sign-in attempts. Wait a few minutes and try again."
        status in 500..599 -> "Taro Carts couldn't complete the request. Please try again shortly."
        else -> "Taro Carts couldn't complete the request. Please try again."
    }
}
