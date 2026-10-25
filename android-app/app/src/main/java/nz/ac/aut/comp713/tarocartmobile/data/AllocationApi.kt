package nz.ac.aut.comp713.tarocartmobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// 10.0.2.2 maps to the development computer from the standard Android emulator.
private const val API_BASE_URL = "http://10.0.2.2:8082"

data class Customer(val id: Long, val name: String)

data class Allocation(
    val id: Long,
    val customerName: String,
    val weekStart: String,
    val deliveryStatus: String,
    val paymentStatus: String,
    val invoiceAvailable: Boolean,
    val items: List<AllocationItem>
)

data class AllocationItem(
    val taroTypeName: String,
    val quantity: Int,
    val pricePerKg: Double
)

object AllocationApi {
    suspend fun getCustomers(): List<Customer> = withContext(Dispatchers.IO) {
        val json = getJson("/api/v1/customer-service-reference/customers?includeInactive=true") as? JSONArray
            ?: throw IOException("The server returned an unexpected customer response.")
        (0 until json.length()).map { index ->
            val customer = json.getJSONObject(index)
            Customer(customer.getLong("id"), customer.optString("name", "Customer"))
        }
    }

    suspend fun getAllocations(customerId: Long?): List<Allocation> = withContext(Dispatchers.IO) {
        val filter = customerId?.let { "?customerId=$it" } ?: ""
        val json = getJson("/api/v1/allocations$filter") as? JSONArray
            ?: throw IOException("The server returned an unexpected allocations response.")
        (0 until json.length()).map { index -> json.getJSONObject(index).toAllocation() }
    }

    private fun JSONObject.toAllocation(): Allocation {
        val allocationItems = optJSONArray("allocationItems") ?: JSONArray()
        return Allocation(
            id = getLong("id"),
            customerName = optString("customerName", "Unknown customer"),
            weekStart = optString("weekStart", ""),
            deliveryStatus = optString("deliveryStatus", "UNKNOWN"),
            paymentStatus = optString("paymentStatus", "UNKNOWN"),
            invoiceAvailable = optBoolean("invoiceAvailable", false),
            items = (0 until allocationItems.length()).map { index ->
                val item = allocationItems.getJSONObject(index)
                AllocationItem(
                    taroTypeName = item.optString("taroTypeName", "Taro"),
                    quantity = item.optInt("quantity", 0),
                    pricePerKg = item.optDouble("pricePerKg", 0.0)
                )
            }
        )
    }

    private fun getJson(path: String): Any {
        val connection = (URL(API_BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }

        try {
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (status !in 200..299) {
                val serverMessage = runCatching { JSONObject(body).optString("message") }.getOrNull()
                throw IOException(serverMessage?.takeIf { it.isNotBlank() } ?: "Request failed (HTTP $status).")
            }
            val trimmed = body.trimStart()
            return when {
                trimmed.startsWith("[") -> JSONArray(body)
                trimmed.startsWith("{") -> JSONObject(body)
                else -> throw IOException("The server returned an invalid response.")
            }
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
