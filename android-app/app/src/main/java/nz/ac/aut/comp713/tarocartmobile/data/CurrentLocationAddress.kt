package nz.ac.aut.comp713.tarocartmobile.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Resolves a one-time precise fix through login-service or the keyless Android geocoder. */
object CurrentLocationAddress {
    suspend fun coordinates(context: Context): Pair<Double, Double> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            throw IOException("Allow precise location to find your current address.")
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            Build.VERSION.SDK_INT >= 31 && manager.isProviderEnabled(LocationManager.FUSED_PROVIDER) ->
                LocationManager.FUSED_PROVIDER
            else -> throw IOException("Turn on device location, or search for your address instead.")
        }
        val location = withTimeoutOrNull(20_000) { currentLocation(context, manager, provider) }
            ?: throw IOException("Could not get your location. Try again outdoors or search for your address.")
        if (!location.hasAccuracy() || location.accuracy > 100f) {
            throw IOException("Your location is too approximate. Try again outdoors.")
        }
        return location.latitude to location.longitude
    }

    suspend fun lookup(context: Context): String {
        val (latitude, longitude) = coordinates(context)
        val location = Location(LocationManager.GPS_PROVIDER).apply {
            this.latitude = latitude
            this.longitude = longitude
        }
        return try {
            MobileAuthApi.reverseGeocode(location.latitude, location.longitude)
        } catch (exception: MobileApiException) {
            if (exception.statusCode != 404) throw exception
            // Older login-service instances have no reverse lookup endpoint.
            reverseGeocodeOnDevice(context, location)
        }
    }

    private suspend fun currentLocation(context: Context, manager: LocationManager,
                                        provider: String): Location? = suspendCancellableCoroutine { continuation ->
        val cancellation = CancellationSignal()
        continuation.invokeOnCancellation { cancellation.cancel() }
        try {
            LocationManagerCompat.getCurrentLocation(manager, provider, cancellation,
                ContextCompat.getMainExecutor(context)) { location ->
                if (continuation.isActive) continuation.resume(location)
            }
        } catch (exception: Exception) {
            if (continuation.isActive) continuation.resumeWithException(exception)
        }
    }

    private suspend fun reverseGeocodeOnDevice(context: Context, location: Location): String {
        if (!Geocoder.isPresent()) {
            throw IOException("Address lookup is unavailable. Search for your address instead.")
        }
        val geocoder = Geocoder(context, Locale("en", "NZ"))
        val addresses = withTimeoutOrNull(15_000) {
            if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine<List<Address>> { continuation ->
                    geocoder.getFromLocation(location.latitude, location.longitude, 5,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(results: MutableList<Address>) {
                                if (continuation.isActive) continuation.resume(results)
                            }

                            override fun onError(errorMessage: String?) {
                                if (continuation.isActive) continuation.resumeWithException(
                                    IOException(errorMessage ?: "Address lookup failed."))
                            }
                        })
                }
            } else {
                @Suppress("DEPRECATION")
                withContext(Dispatchers.IO) {
                    geocoder.getFromLocation(location.latitude, location.longitude, 5).orEmpty()
                }
            }
        } ?: throw IOException("Address lookup timed out. Search for your address instead.")
        val street = addresses.firstOrNull { address ->
            address.countryCode.equals("NZ", ignoreCase = true) &&
                !address.subThoroughfare.isNullOrBlank() &&
                !address.thoroughfare.isNullOrBlank() &&
                (!address.locality.isNullOrBlank() || !address.postalCode.isNullOrBlank())
        } ?: throw IOException("Could not find a complete street address here. Search for your address instead.")
        return street.getAddressLine(0)?.trim()?.takeIf { it.isNotBlank() && it.length <= 500 }
            ?: throw IOException("Could not read this street address. Search for your address instead.")
    }
}
