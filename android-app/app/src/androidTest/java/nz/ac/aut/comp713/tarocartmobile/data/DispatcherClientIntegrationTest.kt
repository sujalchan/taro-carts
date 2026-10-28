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
import java.util.concurrent.atomic.AtomicReference

/** Exercises the Android client's real HTTP and JSON path against a local API stand-in. */
@RunWith(AndroidJUnit4::class)
class DispatcherClientIntegrationTest {
    private lateinit var server: DispatcherApiStandIn

    @Before fun setUp() {
        server = DispatcherApiStandIn()
        DispatcherEndpoint.baseUrl = "http://127.0.0.1:${server.port}"
        AllocationApi.setSessionToken("test-session")
    }

    @After fun tearDown() {
        AllocationApi.setSessionToken(null)
        DispatcherEndpoint.baseUrl = "http://10.0.2.2:8084"
        server.close()
    }

    @Test fun deliveryStatusFlowsThroughTheAndroidApiClient() = runBlocking {
        val profile = AllocationApi.getDispatchers().single()
        assertEquals(7L, profile.id)
        val before = AllocationApi.getAssignedDeliveries(profile.id).single().allocation
        assertEquals("IN_TRANSIT", before.deliveryStatus)
        assertEquals("69 Greenmeadows Avenue", before.deliveryAddress)

        AllocationApi.advanceDelivery(before.id, "DELIVERED")
        val after = AllocationApi.getAssignedDeliveries(profile.id).single().allocation
        assertEquals("DELIVERED", after.deliveryStatus)

        assertTrue(server.requests.any { it.path == "/api/v1/mobile/portal/deliveries" &&
            it.cookie.contains("SESSION=test-session") })
        assertTrue(server.requests.any { it.path == "/api/v1/mobile/portal/deliveries/11/status" &&
            it.method == "PUT" && it.body.contains("DELIVERED") &&
            it.cookie.contains("DISPATCHER_CSRF=test-csrf") && it.csrf == "test-csrf" })
    }
}

private data class RecordedRequest(val method: String, val path: String, val cookie: String,
                                   val csrf: String, val body: String)

private class DispatcherApiStandIn : AutoCloseable {
    private val listener = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = listener.localPort
    val requests: MutableList<RecordedRequest> = Collections.synchronizedList(mutableListOf())
    private val status = AtomicReference("IN_TRANSIT")
    private val worker = Thread {
        while (!listener.isClosed) {
            val socket = try { listener.accept() } catch (_: Exception) { break }
            socket.use { respond(it) }
        }
    }.apply { isDaemon = true; start() }

    private fun respond(socket: Socket) {
        socket.soTimeout = 5_000
        val reader = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8)
        val requestLine = reader.readLine() ?: return
        val parts = requestLine.split(' ')
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        val body = CharArray(length).also { chars ->
            var read = 0
            while (read < chars.size) {
                val count = reader.read(chars, read, chars.size - read)
                if (count < 0) break
                read += count
            }
        }.concatToString()
        val method = parts[0]
        val path = parts[1]
        requests.add(RecordedRequest(method, path, headers["cookie"].orEmpty(),
            headers["x-csrf-token"].orEmpty(), body))
        val response = when (path) {
            "/api/v1/mobile/portal/profile" -> """{"id":7,"name":"Driver","active":true}"""
            "/api/v1/mobile/portal/deliveries" -> """
                [{"id":11,"customerId":3,"customerName":"Customer","weekStart":"2026-11-16",
                "dispatcherId":7,"deliveryAddress":"69 Greenmeadows Avenue",
                "deliveryStatus":"${status.get()}","paymentStatus":"UNPAID",
                "invoiceAvailable":false,"allocationItems":[]}]
                """.trimIndent()
            "/api/v1/mobile/portal/deliveries/11/status" -> {
                if (method == "PUT" && body.contains("DELIVERED")) status.set("DELIVERED")
                ""
            }
            else -> "{}"
        }
        val bytes = response.toByteArray(StandardCharsets.UTF_8)
        val code = if (method == "PUT") "204 No Content" else "200 OK"
        val header = "HTTP/1.1 $code\r\nContent-Type: application/json\r\n" +
            "Content-Length: ${bytes.size}\r\nSet-Cookie: DISPATCHER_CSRF=test-csrf; Path=/\r\n" +
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
