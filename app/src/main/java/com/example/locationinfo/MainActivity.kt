package com.example.locationinfo
import android.annotation.SuppressLint
import android.content.Context
import android.Manifest
import android.location.Location
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LocationApp()
        }
    }
}
suspend fun getLocation(context: Context): Location? {
    val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    return try {
        fusedLocationClient.lastLocation.await()
    } catch (e: SecurityException) {
        null
    }
}

suspend fun getAddress(context: Context, location: Location?): String {
    if (location == null) return "Unknown Location"
    val geocoder = Geocoder(context, Locale.getDefault())

    return try {
        val addresses: List<Address> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(
                    location.latitude,
                    location.longitude,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            cont.resume(addresses)
                        }

                        override fun onError(errorMessage: String?) {
                            cont.resume(emptyList())
                        }
                    }
                )
            }
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(location.latitude, location.longitude, 1) ?: emptyList()
        }

        addresses.firstOrNull()?.getAddressLine(0) ?: "Address Not Found"
    } catch (e: Exception) {
        "Error Fetching Address"
    }
}

@Composable
fun LocationApp() {
    var hasPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            hasPermission = true
        }
    }
    if (hasPermission) {
        MapScreen()
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Location permission is required to use this app.",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) {
                Text("Grant Permission")
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun MapScreen() {
    val context = LocalContext.current
    var userLocation by remember { mutableStateOf<Location?>(null) }
    var userAddress by remember { mutableStateOf("Fetching Address...") }
    val coroutineScope = rememberCoroutineScope()
    val cameraPositionState = rememberCameraPositionState()

    val uiSettings = remember { MapUiSettings(zoomControlsEnabled = false) }
    val properties = remember { MapProperties(isMyLocationEnabled = true) }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            ) {
                userLocation = getLocation(context)
            }
            userAddress = getAddress(context, userLocation)
            userLocation?.let {
                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15f))
            }
        }
    }

    var customMarkers by remember { mutableStateOf(listOf<LatLng>()) }

    Column(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.weight(1f),
            properties = properties,
            uiSettings = uiSettings,
            cameraPositionState = cameraPositionState,
            onMapClick = { latLng ->
                customMarkers = customMarkers + latLng
            }
        ) {
            userLocation?.let {
                Marker(
                    state = MarkerState(position = LatLng(it.latitude, it.longitude)),
                    title = "You are here",
                    snippet = userAddress
                )
            }
            customMarkers.forEach { latLng ->
                Marker(
                    state = MarkerState(position = latLng),
                    title = "Custom Marker",
                    snippet = "Lat: ${latLng.latitude}, Lng: ${latLng.longitude}"
                )
            }
        }
        Text(
            text = "Your Location: $userAddress",
            modifier = Modifier.padding(16.dp)
        )
    }
}