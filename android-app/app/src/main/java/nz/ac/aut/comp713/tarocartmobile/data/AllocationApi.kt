package nz.ac.aut.comp713.tarocartmobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// 10.0.2.2 maps to the development computer from the standard android emulator
private const val API_BASE_URL = "http://10.0.2.2:8082"
private const val CUSTOMER_API_BASE_URL = "http://10.0.2.2:8081"
private const val DISPATCHER_API_BASE_URL = "http://10.0.2.2:8084"

// lightweight models used to exchange customer, product, and allocation data with the ui
data class Customer(
    val id: Long,
    val name: String,
    val address: String,
    val contactName: String,
    val phone: String,
    val active: Boolean
)

data class TaroType(val id: Long, val name: String, val standardPrice: Double)

data class Dispatcher(val id: Long, val name: String, val active: Boolean)

data class AssignedDelivery(val allocation: Allocation, val customer: Customer)

data class RequestedItem(val taroTypeId: Long, val quantity: Int)

data class Allocation(
    val id: Long,
    val customerId: Long,
    val requestedByCustomerId: Long?,
    val customerName: String,
    val weekStart: String,
    val dispatcherId: Long?,
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

// centralizes allocation-service requests and keeps network work off the main thread
object AllocationApi {
    // Dispatcher profiles are owned by dispatcher-service, while assignments are
    // stored on allocations. Match by ID so duplicate or changed names are safe.
    suspend fun getDispatchers(): List<Dispatcher> = withContext(Dispatchers.IO) {
        val json = getJson("/api/v1/dispatchers", DISPATCHER_API_BASE_URL) as? JSONArray
            ?: throw IOException("The server returned an unexpected dispatcher response.")
        (0 until json.length()).map { index ->
            json.getJSONObject(index).let { dispatcher ->
                Dispatcher(dispatcher.getLong("id"), dispatcher.optString("name", "Dispatcher"),
                    dispatcher.optBoolean("active", true))
            }
        }.filter { it.active }
    }

    suspend fun getAssignedDeliveries(dispatcherId: Long): List<AssignedDelivery> = withContext(Dispatchers.IO) {
        val json = getJson("/api/v1/allocations/dispatcher/$dispatcherId") as? JSONArray
            ?: throw IOException("The server returned an unexpected allocations response.")
        val assigned = (0 until json.length()).map { index ->
            json.getJSONObject(index).toAllocation()
        }.filter { it.dispatcherId == dispatcherId }
        val customers = assigned.map { it.customerId }.distinct().associateWith { customerId ->
            (getJson("/api/v1/customers/$customerId", CUSTOMER_API_BASE_URL) as? JSONObject)
                ?.toCustomer() ?: throw IOException("The server returned an unexpected customer response.")
        }
        assigned.map { allocation -> AssignedDelivery(allocation, customers.getValue(allocation.customerId)) }
            .sortedWith(compareBy<AssignedDelivery> { it.allocation.weekStart }.thenBy { it.allocation.id })
    }

    // downloads an invoice into the app cache and removes partial files after failures
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

    // loads active customers exposed through the allocation-service reference endpoint
    suspend fun getCustomers(): List<Customer> = withContext(Dispatchers.IO) {
        val json = getJson("/api/v1/customer-service-reference/customers") as? JSONArray
            ?: throw IOException("The server returned an unexpected customer response.")
        (0 until json.length()).map { index ->
            json.getJSONObject(index).toCustomer()
        }
    }

    // updates the selected customer's editable information in customer-service
    suspend fun updateCustomer(customer: Customer): Customer = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("name", customer.name)
            put("address", customer.address)
            put("contactName", customer.contactName)
            put("phone", customer.phone)
            put("active", customer.active)
        }
        val response = request(
            method = "PUT",
            path = "/api/v1/customers/${customer.id}",
            payload = body,
            baseUrl = CUSTOMER_API_BASE_URL
        )
        JSONObject(response).toCustomer()
    }

    // loads taro types used to populate allocation request choices
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

    // retrieves allocations belonging to the selected acting customer
    suspend fun getAllocations(customerId: Long): List<Allocation> = withContext(Dispatchers.IO) {
        val json = getJson("/api/v1/allocations?customerId=$customerId") as? JSONArray
            ?: throw IOException("The server returned an unexpected allocations response.")
        (0 until json.length()).map { index -> json.getJSONObject(index).toAllocation() }
    }

    // converts a customer request into the json structure expected by the api
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

    // deletes a request while sending the acting customer for ownership validation
    suspend fun deleteCustomerRequest(allocationId: Long, customerId: Long) = withContext(Dispatchers.IO) {
        request("DELETE", "/api/v1/allocations/$allocationId/customer-request?customerId=$customerId")
    }

    // records payment confirmation for an allocation owned by the acting customer
    suspend fun recordPayment(allocationId: Long, customerId: Long) = withContext(Dispatchers.IO) {
        request("POST", "/api/v1/allocations/$allocationId/pay?customerId=$customerId")
    }

    // maps a nested allocation response into strongly typed app models
    private fun JSONObject.toAllocation(): Allocation {
        val allocationItems = optJSONArray("allocationItems") ?: JSONArray()
        return Allocation(
            id = getLong("id"),
            customerId = getLong("customerId"),
            requestedByCustomerId = if (isNull("requestedByCustomerId")) null else getLong("requestedByCustomerId"),
            customerName = optString("customerName", "Unknown customer"),
            weekStart = optString("weekStart", ""),
            dispatcherId = if (isNull("dispatcherId")) null else getLong("dispatcherId"),
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

    // maps customer json while treating optional text fields as empty strings
    private fun JSONObject.toCustomer(): Customer = Customer(
        id = getLong("id"),
        name = optString("name", "Customer"),
        address = optString("address", ""),
        contactName = optString("contactName", ""),
        phone = optString("phone", ""),
        active = optBoolean("active", true)
    )

    // accepts either json object or array responses and rejects malformed content
    private fun getJson(path: String, baseUrl: String = API_BASE_URL): Any {
        val body = request("GET", path, baseUrl = baseUrl)
        val trimmed = body.trimStart()
        return when {
            trimmed.startsWith("[") -> JSONArray(body)
            trimmed.startsWith("{") -> JSONObject(body)
            else -> throw IOException("The server returned an invalid response.")
        }
    }

    // performs a shared http request and converts server errors into readable exceptions
    private fun request(
        method: String,
        path: String,
        payload: JSONObject? = null,
        baseUrl: String = API_BASE_URL
    ): String {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
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
