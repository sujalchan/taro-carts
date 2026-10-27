package nz.ac.aut.comp713.tarocartmobile.data

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal enum class BackendService(val label: String) {
    CUSTOMER("Customer Service"),
    DISPATCHER("Dispatcher Service"),
    ALLOCATION("Allocation Service"),
    LOGIN("Login Service"),
    ADDRESS_LOOKUP("Address Lookup Service")
}

internal class BackendResponseException(message: String) : IOException(message)

/** Names the service contacted by the app, or the downstream service identified by a 503 response. */
internal object ServiceErrors {
    fun network(service: BackendService, exception: IOException): IOException {
        val message = when {
            exception.message?.startsWith("CLEARTEXT") == true ->
                "Cannot connect to ${service.label}: HTTP traffic is blocked by the app's network settings."
            exception is SocketTimeoutException ->
                "${service.label} is not responding. Try again shortly."
            exception is UnknownHostException ->
                "Cannot find ${service.label}. Check your connection and try again."
            exception is ConnectException ->
                "Cannot connect to ${service.label}. The service may be down; try again shortly."
            else -> "Cannot reach ${service.label}. Check your connection and try again."
        }
        return IOException(message, exception)
    }

    fun http(service: BackendService, status: Int, code: String, message: String): String {
        if (status in setOf(502, 503, 504)) {
            val affected = when (code) {
                "CUSTOMER_SERVICE_UNAVAILABLE" -> BackendService.CUSTOMER.label
                "DISPATCHER_SERVICE_UNAVAILABLE" -> BackendService.DISPATCHER.label
                "ALLOCATION_SERVICE_UNAVAILABLE" -> BackendService.ALLOCATION.label
                "LOGIN_SERVICE_UNAVAILABLE" -> BackendService.LOGIN.label
                "TARO_SERVICE_UNAVAILABLE" -> "Taro Service"
                else -> when {
                    message.contains("Authentication service unavailable", ignoreCase = true) ->
                        BackendService.LOGIN.label
                    message.contains("Allocation Service is unavailable", ignoreCase = true) ->
                        BackendService.ALLOCATION.label
                    message.contains("Taro Service is unavailable", ignoreCase = true) -> "Taro Service"
                    else -> service.label
                }
            }
            return "$affected is unavailable. Try again shortly."
        }
        return message.takeIf { it.isNotBlank() } ?: "Request to ${service.label} failed (HTTP $status)."
    }
}
