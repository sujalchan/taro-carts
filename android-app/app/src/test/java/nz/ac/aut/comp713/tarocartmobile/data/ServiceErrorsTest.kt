package nz.ac.aut.comp713.tarocartmobile.data

import java.net.ConnectException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceErrorsTest {
    @Test
    fun directConnectionFailureNamesTheServiceWithoutExposingItsAddress() {
        BackendService.entries.forEach { service ->
            val failure = ServiceErrors.network(service,
                ConnectException("Failed to connect to /10.0.2.2:8081"))
            assertTrue(failure.message.orEmpty().contains(service.label))
            assertFalse(failure.message.orEmpty().contains("10.0.2.2"))
        }
    }

    @Test
    fun timeoutNamesTheServiceThatWasContacted() {
        assertTrue(ServiceErrors.network(BackendService.DISPATCHER,
            SocketTimeoutException()).message.orEmpty().contains("Dispatcher Service"))
    }

    @Test
    fun proxyFailureNamesTheAffectedDownstreamService() {
        assertEquals("Taro Service is unavailable. Try again shortly.",
            ServiceErrors.http(BackendService.CUSTOMER, 503,
                "TARO_SERVICE_UNAVAILABLE", "Service temporarily unavailable"))
        assertEquals("Allocation Service is unavailable. Try again shortly.",
            ServiceErrors.http(BackendService.DISPATCHER, 503,
                "ALLOCATION_SERVICE_UNAVAILABLE", "Allocation Service is unavailable."))
        assertEquals("Login Service is unavailable. Try again shortly.",
            ServiceErrors.http(BackendService.CUSTOMER, 503,
                "", "Authentication service unavailable"))
    }

    @Test
    fun validationFailuresKeepTheirActionableMessage() {
        assertEquals("Quantities must be whole kilograms.",
            ServiceErrors.http(BackendService.CUSTOMER, 400, "VALIDATION_ERROR",
                "Quantities must be whole kilograms."))
    }

    @Test
    fun gatewayErrorsNameTheServiceContacted() {
        assertEquals("Customer Service is unavailable. Try again shortly.",
            ServiceErrors.http(BackendService.CUSTOMER, 502, "", ""))
        assertEquals("Login Service is unavailable. Try again shortly.",
            ServiceErrors.http(BackendService.LOGIN, 504, "", ""))
    }
}
