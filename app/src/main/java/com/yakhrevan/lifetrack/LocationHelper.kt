package com.yakhrevan.lifetrack

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

sealed class LocationResult {
    data class Success(
        val cityName: String,
        val fullLocation: String,
        val latitude: Double,
        val longitude: Double
    ) : LocationResult()

    object PermissionRequired : LocationResult()
    object ServicesDisabled : LocationResult()
    data class Error(val message: String) : LocationResult()
}

object LocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineGranted || coarseGranted
    }

    @SuppressLint("MissingPermission")
    suspend fun detectLocation(context: Context): LocationResult = withContext(Dispatchers.IO) {
        if (!hasLocationPermission(context)) {
            return@withContext LocationResult.PermissionRequired
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext LocationResult.Error("Location service is unavailable on this device.")

        val isGpsEnabled = runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        val isNetworkEnabled = runCatching { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)

        if (!isGpsEnabled && !isNetworkEnabled) {
            return@withContext LocationResult.ServicesDisabled
        }

        var bestLocation: Location? = null

        val finePermissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarsePermissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (isGpsEnabled && finePermissionGranted) {
            runCatching {
                bestLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }
        }

        if (isNetworkEnabled && (finePermissionGranted || coarsePermissionGranted)) {
            runCatching {
                val networkLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (networkLoc != null && (bestLocation == null || networkLoc.time > bestLocation!!.time)) {
                    bestLocation = networkLoc
                }
            }
        }

        val location = bestLocation
            ?: return@withContext LocationResult.Error("Could not retrieve current GPS coordinates. Please ensure you are in an open area.")

        // Reverse geocode to find city and region
        return@withContext runCatching {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(location.latitude, location.longitude, 1)
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(location.latitude, location.longitude, 1)
            }

            val address = addresses?.firstOrNull()
            if (address != null) {
                val city = address.locality
                    ?: address.subAdminArea
                    ?: address.adminArea
                    ?: "Detected Location"
                val state = address.adminArea ?: ""
                val full = if (state.isNotBlank() && !city.contains(state)) "$city, $state" else city
                LocationResult.Success(
                    cityName = city,
                    fullLocation = full,
                    latitude = location.latitude,
                    longitude = location.longitude
                )
            } else {
                LocationResult.Success(
                    cityName = "Current Location",
                    fullLocation = String.format(Locale.US, "%.3f, %.3f", location.latitude, location.longitude),
                    latitude = location.latitude,
                    longitude = location.longitude
                )
            }
        }.getOrElse { err ->
            LocationResult.Error(err.message ?: "Network error resolving location name.")
        }
    }
}
