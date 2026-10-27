package nz.ac.aut.comp713.tarocartmobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

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

data class AssignedDelivery(val allocation: Allocation)

data class RequestedItem(val taroTypeId: Long, val quantity: Int)

data class Allocation(
    val id: Long,
    val customerId: Long,
    val requestedByCustomerId: Long?,
    val customerName: String,
    val weekStart: String,
    val dispatcherId: Long?,
    val deliveryAddress: String?,
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
    @Volatile private var sessionToken: String? = null
    private val csrfTokens = ConcurrentHashMap<String, String>()

    fun setSessionToken(token: String?) {
        sessionToken = token
        csrfTokens.clear()
    }

    // Dispatcher profiles are owned by dispatcher-service, while assignments are
    // stored on allocations. Match by ID so duplicate or changed names are safe.
    suspend fun getDispatchers(): List<Dispatcher> = withContext(Dispatchers.IO) {
        val json = getJson("/api/v1/mobile/portal/profile", DISPATCHER_API_BASE_URL) as? JSONObject
            ?: throw IOException("The server returned an unexpected dispatcher response.")
        listOf(Dispatcher(json.getLong("id"), json.optString("name", "Dispatcher"),
            json.optBoolean("active", true)))
    }

    suspend fun getAssignedDeliveries(dispatcherId: Long): List<AssignedDelivery> = withContext(Dispatchers.IO) {
        val json = getJson("/api/v1/mobile/portal/deliveries", DISPATCHER_API_BASE_URL) as? JSONArray
            ?: throw IOException("The server returned an unexpected allocations response.")
        val assigned = (0 until json.length()).map { index ->
            json.getJSONObject(index).toAllocation()
        }
        assigned.map { allocation -> AssignedDelivery(allocation) }
            .sortedWith(compareBy<AssignedDelivery> { it.allocation.weekStart }.thenBy { it.allocation.id })
    }

    suspend fun advanceDelivery(allocationId: Long, nextStatus: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("nextStatus", nextStatus)
        request("PUT", "/api/v1/mobile/portal/deliveries/$allocationId/status", body,
            DISPATCHER_API_BASE_URL)
    }

    // downloads an invoice into the app cache and removes partial files after failures
    suspend fun downloadInvoice(cacheDir: File, allocationId: Long): File = withContext(Dispatchers.IO) {
        val connection = (URL("$CUSTOMER_API_BASE_URL/api/v1/mobile/portal/allocations/$allocationId/invoice.pdf?inline=true")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/pdf")
            setRequestProperty("Cookie", sessionCookie(CUSTOMER_API_BASE_URL))
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
        val json = getJson("/api/v1/mobile/portal/profile", CUSTOMER_API_BASE_URL) as? JSONObject
            ?: throw IOException("The server returned an unexpected customer response.")
        listOf(json.toCustomer())
    }

    // updates the selected customer's editable information in customer-service
    suspend fun updateCustomer(customer: Customer): Customer = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("name", customer.name)
            put("address", customer.address)
            put("contactName", customer.contactName)
            put("phone", customer.phone)
        }
        val response = request(
            method = "PUT",
            path = "/api/v1/mobile/portal/profile",
            payload = body,
            baseUrl = CUSTOMER_API_BASE_URL
        )
        JSONObject(response).toCustomer()
    }

    // loads taro types used to populate allocation request choices
    suspend fun getTaroTypes(): List<TaroType> = withContext(Dispatchers.IO) {
        val json = getJson("/api/v1/mobile/portal/taro-types", CUSTOMER_API_BASE_URL) as? JSONArray
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
        val json = getJson("/api/v1/mobile/portal/allocations", CUSTOMER_API_BASE_URL) as? JSONArray
            ?: throw IOException("The server returned an unexpected allocations response.")
        (0 until json.length()).map { index -> json.getJSONObject(index).toAllocation() }
    }

    // converts a customer request into the json structure expected by the api
    suspend fun requestAllocation(customerId: Long, weekStart: String, items: List<RequestedItem>) =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
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
            request("POST", "/api/v1/mobile/portal/allocations", body, CUSTOMER_API_BASE_URL)
        }

    // deletes a request while sending the acting customer for ownership validation
    suspend fun deleteCustomerRequest(allocationId: Long, customerId: Long) = withContext(Dispatchers.IO) {
        request("DELETE", "/api/v1/mobile/portal/allocations/$allocationId/request", baseUrl = CUSTOMER_API_BASE_URL)
    }

    // records payment confirmation for an allocation owned by the acting customer
    suspend fun recordPayment(allocationId: Long, customerId: Long) = withContext(Dispatchers.IO) {
        request("POST", "/api/v1/mobile/portal/allocations/$allocationId/pay", baseUrl = CUSTOMER_API_BASE_URL)
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
            deliveryAddress = optString("deliveryAddress", "").takeIf { it.isNotBlank() },
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
        contactName = if (isNull("contactName")) "" else optString("contactName", ""),
        phone = if (isNull("phone")) "" else optString("phone", ""),
        active = optBoolean("active", true)
    )

    // accepts either json object or array responses and rejects malformed content
    private suspend fun getJson(path: String, baseUrl: String = API_BASE_URL): Any {
        val body = request("GET", path, baseUrl = baseUrl)
        val trimmed = body.trimStart()
        return when {
            trimmed.startsWith("[") -> JSONArray(body)
            trimmed.startsWith("{") -> JSONObject(body)
            else -> throw IOException("The server returned an invalid response.")
        }
    }

    // performs a shared http request and converts server errors into readable exceptions
    // Enforces the threading rule at the lowest HTTP boundary so new callers
    // cannot accidentally perform a blocking connection on the main thread.
    private suspend fun request(
        method: String,
        path: String,
        payload: JSONObject? = null,
        baseUrl: String = API_BASE_URL
    ): String = withContext(Dispatchers.IO) {
        val token = sessionToken ?: throw IOException("Sign in to use the customer or dispatcher portal.")
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cookie", sessionCookie(baseUrl))
            if (method != "GET" && method != "HEAD") {
                val csrf = csrfTokens[baseUrl] ?: throw IOException("Refresh the portal and try again.")
                setRequestProperty("X-CSRF-Token", csrf)
            }
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
            rememberCsrf(baseUrl, connection.getHeaderFields()["Set-Cookie"])
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (status !in 200..299) {
                val serverMessage = runCatching { JSONObject(body).optString("message") }.getOrNull()
                throw IOException(serverMessage?.takeIf { it.isNotBlank() } ?: "Request failed (HTTP $status).")
            }
            body
        } catch (exception: IOException) {
            if (exception.message?.startsWith("CLEARTEXT") == true) {
                throw IOException("HTTP traffic is blocked. Check the app network configuration.", exception)
            }
            throw exception
        } finally {
            connection.disconnect()
        }
    }

    private fun sessionCookie(baseUrl: String): String {
        val token = sessionToken ?: throw IOException("Sign in to use the customer or dispatcher portal.")
        val csrfName = when (baseUrl) {
            CUSTOMER_API_BASE_URL -> "CUSTOMER_CSRF"
            DISPATCHER_API_BASE_URL -> "DISPATCHER_CSRF"
            else -> null
        }
        val csrf = csrfName?.let { name -> csrfTokens[baseUrl]?.let { value -> "$name=$value" } }
        return listOfNotNull("SESSION=$token", csrf).joinToString("; ")
    }

    private fun rememberCsrf(baseUrl: String, headers: List<String>?) {
        val csrfName = when (baseUrl) {
            CUSTOMER_API_BASE_URL -> "CUSTOMER_CSRF"
            DISPATCHER_API_BASE_URL -> "DISPATCHER_CSRF"
            else -> return
        }
        headers.orEmpty().forEach { header ->
            val cookie = header.substringBefore(';').trim()
            if (cookie.startsWith("$csrfName=")) {
                csrfTokens[baseUrl] = cookie.substringAfter('=')
            }
        }
    }
}
