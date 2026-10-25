package nz.ac.aut.comp713.tarocartmobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// 10.0.2.2 maps to the development computer from the standard Android emulator.
private const val API_BASE_URL = "http://10.0.2.2:8082"

data class Customer(val id: Long, val name: String)

data class TaroType(val id: Long, val name: String, val standardPrice: Double)

data class RequestedItem(val taroTypeId: Long, val quantity: Int)

data class Allocation(
    val id: Long,
    val customerId: Long,
    val requestedByCustomerId: Long?,
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
    suspend fun downloadInvoice(cacheDir: File, allocationId: Long): File = withContext(Dispatchers.IO) {
        val connection = (URL("$API_BASE_URL/api/v1/allocations/$allocationId/invoice.pdf?inline=true")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/pdf")
        }
        var file: File? = null
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val message = runCatching { JSONObject(body).optString("message") }.getOrNull()
                throw IOException(message?.takeIf { it.isNotBlank() } ?: "Invoice request failed (HTTP $status).")
            }
            if (!connection.contentType.orEmpty().startsWith("application/pdf", ignoreCase = true)) {
                throw IOException("The server did not return a PDF invoice.")
            }
            val invoiceFile = File.createTempFile("invoice-$allocationId-", ".pdf", cacheDir)
            file = invoiceFile
            connection.inputStream.use { input -> invoiceFile.outputStream().use { output -> input.copyTo(output) } }
            if (invoiceFile.length() == 0L) throw IOException("The invoice PDF is empty.")
            invoiceFile
        } catch (exception: Exception) {
            file?.delete()
            throw exception
        } finally {
            connection.disconnect()
        }
    }

    suspend fun getCustomers(): List<Customer> = withContext(Dispatchers.IO) {
        val json = getJson("/api/v1/customer-service-reference/customers") as? JSONArray
            ?: throw IOException("The server returned an unexpected customer response.")
        (0 until json.length()).map { index ->
            val customer = json.getJSONObject(index)
            Customer(customer.getLong("id"), customer.optString("name", "Customer"))
        }
    }

    suspend fun getTaroTypes(): List<TaroType> = withContext(Dispatchers.IO) {
        val json = getJson("/api/v1/taro-service-reference/taro-types") as? JSONArray
            ?: throw IOException("The server returned an unexpected taro type response.")
        (0 until json.length()).map { index ->
            val taroType = json.getJSONObject(index)
            TaroType(
                taroType.getLong("id"),
                taroType.optString("name", "Taro"),
                taroType.getDouble("standardPrice")
            )
        }
    }

    suspend fun getAllocations(customerId: Long): List<Allocation> = withContext(Dispatchers.IO) {
        val json = getJson("/api/v1/allocations?customerId=$customerId") as? JSONArray
            ?: throw IOException("The server returned an unexpected allocations response.")
        (0 until json.length()).map { index -> json.getJSONObject(index).toAllocation() }
    }

    suspend fun requestAllocation(customerId: Long, weekStart: String, items: List<RequestedItem>) =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("customerId", customerId)
                put("weekStart", weekStart)
                put("allocationItems", JSONArray().apply {
                    items.forEach { item ->
                        put(JSONObject().apply {
                            put("taroTypeId", item.taroTypeId)
                            put("quantity", item.quantity)
                        })
                    }
                })
            }
            request("POST", "/api/v1/allocations/customer-request", body)
        }

    suspend fun deleteCustomerRequest(allocationId: Long, customerId: Long) = withContext(Dispatchers.IO) {
        request("DELETE", "/api/v1/allocations/$allocationId/customer-request?customerId=$customerId")
    }

    private fun JSONObject.toAllocation(): Allocation {
        val allocationItems = optJSONArray("allocationItems") ?: JSONArray()
        return Allocation(
            id = getLong("id"),
            customerId = getLong("customerId"),
            requestedByCustomerId = if (isNull("requestedByCustomerId")) null else getLong("requestedByCustomerId"),
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
        val body = request("GET", path)
        val trimmed = body.trimStart()
        return when {
            trimmed.startsWith("[") -> JSONArray(body)
            trimmed.startsWith("{") -> JSONObject(body)
            else -> throw IOException("The server returned an invalid response.")
        }
    }

    private fun request(method: String, path: String, payload: JSONObject? = null): String {
        val connection = (URL(API_BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            if (payload != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        try {
            if (payload != null) {
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (status !in 200..299) {
                val serverMessage = runCatching { JSONObject(body).optString("message") }.getOrNull()
                throw IOException(serverMessage?.takeIf { it.isNotBlank() } ?: "Request failed (HTTP $status).")
            }
            return body
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
