package nz.ac.aut.comp713.tarocartmobile.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Collections

/** Exercises the customer app's authenticated request and payment HTTP workflow. */
@RunWith(AndroidJUnit4::class)
class CustomerClientIntegrationTest {
    private lateinit var server: CustomerApiStandIn

    @Before fun setUp() {
        server = CustomerApiStandIn()
        CustomerEndpoint.baseUrl = "http://127.0.0.1:${server.port}"
        AllocationApi.setSessionToken("customer-session")
    }

    @After fun tearDown() {
        AllocationApi.setSessionToken(null)
        CustomerEndpoint.baseUrl = "http://10.0.2.2:8081"
        server.close()
    }

    @Test fun requestAndPaymentRoundTripThroughAndroidClient() = runBlocking {
        assertEquals(3L, AllocationApi.getCustomers().single().id)
        assertEquals(9L, AllocationApi.getTaroTypes().single().id)
        assertTrue(AllocationApi.getAllocations(3).isEmpty())

        AllocationApi.requestAllocation(3, "2026-11-16", listOf(RequestedItem(9, 50)))
        val requested = AllocationApi.getAllocations(3).single()
        assertEquals("REQUESTED", requested.deliveryStatus)
        assertEquals(50, requested.items.single().quantity)

        AllocationApi.recordPayment(requested.id, 3)
        assertEquals("PAID", AllocationApi.getAllocations(3).single().paymentStatus)

        val create = server.requests.single { it.method == "POST" && it.path.endsWith("/allocations") }
        assertTrue(create.body.contains("\"weekStart\":\"2026-11-16\""))
        assertTrue(create.body.contains("\"taroTypeId\":9"))
        assertTrue(create.cookie.contains("SESSION=customer-session"))
        assertTrue(create.cookie.contains("CUSTOMER_CSRF=test-csrf"))
        assertEquals("test-csrf", create.csrf)
        assertTrue(server.requests.any { it.path.endsWith("/allocations/21/pay") &&
            it.method == "POST" && it.csrf == "test-csrf" })
    }
}

private data class CustomerRequest(val method: String, val path: String, val cookie: String,
                                   val csrf: String, val body: String)

private class CustomerApiStandIn : AutoCloseable {
    private val listener = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = listener.localPort
    val requests: MutableList<CustomerRequest> = Collections.synchronizedList(mutableListOf())
    private var created = false
    private var paid = false
    private val worker = Thread {
        while (!listener.isClosed) {
            val socket = try { listener.accept() } catch (_: Exception) { break }
            socket.use { respond(it) }
        }
    }.apply { isDaemon = true; start() }

    private fun respond(socket: Socket) {
        socket.soTimeout = 5_000
        val reader = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8)
        val parts = (reader.readLine() ?: return).split(' ')
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
        }
        val body = CharArray(headers["content-length"]?.toIntOrNull() ?: 0).also { chars ->
            var count = 0
            while (count < chars.size) {
                val read = reader.read(chars, count, chars.size - count)
                if (read < 0) break
                count += read
            }
        }.concatToString()
        val method = parts[0]
        val path = parts[1]
        requests.add(CustomerRequest(method, path, headers["cookie"].orEmpty(),
            headers["x-csrf-token"].orEmpty(), body))
        val response = when (path) {
            "/api/v1/mobile/portal/profile" ->
                """{"id":3,"name":"Customer","address":"Auckland","contactName":"C","phone":"1","active":true}"""
            "/api/v1/mobile/portal/taro-types" ->
                """[{"id":9,"name":"Samoan Taro","standardPrice":5.0}]"""
            "/api/v1/mobile/portal/allocations" -> when (method) {
                "POST" -> { created = true; "" }
                else -> if (created) """[{"id":21,"customerId":3,"customerName":"Customer",
                    "weekStart":"2026-11-16","deliveryStatus":"REQUESTED",
                    "paymentStatus":"${if (paid) "PAID" else "UNPAID"}",
                    "allocationItems":[{"taroTypeName":"Samoan Taro","quantity":50,"pricePerKg":5.0}]}]"""
                    else "[]"
            }
            "/api/v1/mobile/portal/allocations/21/pay" -> { paid = true; "" }
            else -> "{}"
        }
        val bytes = response.toByteArray(StandardCharsets.UTF_8)
        val status = if (method == "POST") "204 No Content" else "200 OK"
        val header = "HTTP/1.1 $status\r\nContent-Type: application/json\r\n" +
            "Content-Length: ${bytes.size}\r\nSet-Cookie: CUSTOMER_CSRF=test-csrf; Path=/\r\n" +
            "Connection: close\r\n\r\n"
        socket.getOutputStream().write(header.toByteArray(StandardCharsets.UTF_8))
        socket.getOutputStream().write(bytes)
        socket.getOutputStream().flush()
    }

    override fun close() {
        listener.close()
        worker.join(1_000)
    }
}
