package com.example.transervis;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import androidx.appcompat.app.AlertDialog;

public class ViewAvailabilityActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "ViewAvailability";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final long REFRESH_INTERVAL_MS = 10000; // 10 segundos para actualizar automáticamente

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LatLng currentLocation;

    private TextView availableDriversText;
    private FloatingActionButton refreshButton;
    private Button backButton;
    private FloatingActionButton requestRideButton;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ListenerRegistration driversListener;

    private Map<String, Marker> driverMarkers;
    private int driversCount = 0;
    private Handler refreshHandler;
    private Runnable refreshRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_availability);

        try {
            // Inicializar Firebase
            auth = FirebaseAuth.getInstance();
            db = FirebaseFirestore.getInstance();

            // Verificar si hay usuario autenticado
            if (auth.getCurrentUser() == null) {
                Toast.makeText(this, "Sesión expirada, por favor inicia sesión nuevamente", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
                return;
            }

            // Inicializar UI elements
            initializeViews();

            // Inicializar el mapa
            SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.map);
            if (mapFragment != null) {
                mapFragment.getMapAsync(this);
            }

            // Inicializar servicios de ubicación
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            createLocationCallback();

            // Inicializar mapa de marcadores
            driverMarkers = new HashMap<>();

            // Inicializar handler para refresh automático
            refreshHandler = new Handler();
            refreshRunnable = new Runnable() {
                @Override
                public void run() {
                    fetchAvailableDrivers();
                    refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
                }
            };

        } catch (Exception e) {
            Log.e(TAG, "Error en onCreate", e);
            Toast.makeText(this, "Error al inicializar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void initializeViews() {
        availableDriversText = findViewById(R.id.availableDriversText);
        refreshButton = findViewById(R.id.refreshButton);
        backButton = findViewById(R.id.backButton);
        requestRideButton = findViewById(R.id.requestRideButton);

        // Set listeners
        refreshButton.setOnClickListener(v -> fetchAvailableDrivers());
        backButton.setOnClickListener(v -> finish());
        requestRideButton.setOnClickListener(v -> {
            startActivity(new Intent(ViewAvailabilityActivity.this, RequestServiceActivity.class));
        });
    }

    private void createLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }

                for (Location location : locationResult.getLocations()) {
                    // Actualizar ubicación actual
                    currentLocation = new LatLng(location.getLatitude(), location.getLongitude());

                    // Actualizar mapa
                    if (mMap != null && currentLocation != null) {
                        // Solo mover la cámara la primera vez
                        if (mMap.getCameraPosition().zoom < 10) {
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 14));
                        }
                    }
                    break; // Solo usar la primera ubicación
                }
            }
        };
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Configurar mapa
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        // Habilitar mi ubicación si hay permisos
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            startLocationUpdates();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }

        // Obtener ubicación actual y mostrar conductores disponibles
        getLastLocation();

        // Configurar listener para clics en marcadores
        mMap.setOnMarkerClickListener(marker -> {
            // Mostrar diálogo para solicitar servicio directamente
            if (marker.getTitle() != null && marker.getTitle().equals("Conductor disponible")) {
                showRequestServiceDialog(marker.getPosition());
                return true; // Consumir el evento
            }
            return false; // Dejar que se maneje normalmente
        });
    }

    private void showRequestServiceDialog(LatLng driverLocation) {
        if (currentLocation == null) return;

        // Calcular distancia aproximada
        float[] results = new float[1];
        Location.distanceBetween(
                currentLocation.latitude, currentLocation.longitude,
                driverLocation.latitude, driverLocation.longitude,
                results);

        float distanceInKm = results[0] / 1000f;

        // Mostrar diálogo
        new AlertDialog.Builder(this)
                .setTitle("Solicitar Servicio")
                .setMessage("¿Quieres solicitar este conductor? Está a " +
                        String.format(Locale.getDefault(), "%.1f km", distanceInKm) + " de ti.")
                .setPositiveButton("Solicitar", (dialog, which) -> {
                    // Abrir pantalla de solicitud
                    Intent intent = new Intent(ViewAvailabilityActivity.this, RequestServiceActivity.class);
                    startActivity(intent);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void getLastLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            currentLocation = new LatLng(location.getLatitude(), location.getLongitude());

                            // Mover cámara a ubicación actual
                            if (mMap != null) {
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 14));
                            }

                            // Buscar conductores disponibles
                            fetchAvailableDrivers();
                        }
                    }
                });
    }

    private void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // Crear solicitud de ubicación
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10000) // 10 segundos
                .setFastestInterval(5000); // 5 segundos

        // Solicitar actualizaciones de ubicación
        fusedLocationClient.requestLocationUpdates(locationRequest,
                locationCallback,
                getMainLooper());
    }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (mMap != null) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {
                        mMap.setMyLocationEnabled(true);
                    }
                }
                startLocationUpdates();
                getLastLocation();
            } else {
                Toast.makeText(this, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void fetchAvailableDrivers() {
        if (currentLocation == null) {
            Toast.makeText(this, "Esperando ubicación...", Toast.LENGTH_SHORT).show();
            return;
        }

        // Mostrar carga
        availableDriversText.setText("Buscando conductores disponibles...");

        // Limpiar marcadores anteriores
        for (Marker marker : driverMarkers.values()) {
            marker.remove();
        }
        driverMarkers.clear();

        // Buscar conductores disponibles en Firestore
        db.collection("driver_locations")
                .whereEqualTo("isAvailable", true)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    driversCount = 0;

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        try {
                            GeoPoint driverLocation = doc.getGeoPoint("location");
                            String driverId = doc.getString("conductorId");
                            Date timestamp = doc.getDate("timestamp");

                            if (driverLocation != null && driverId != null && timestamp != null) {
                                // Verificar si la ubicación es reciente (menos de 10 minutos)
                                Date now = new Date();
                                long diffMs = now.getTime() - timestamp.getTime();
                                long diffMinutes = diffMs / (60 * 1000);

                                if (diffMinutes <= 10) { // Mostrar solo conductores con ubicaciones actualizadas
                                    LatLng driverLatLng = new LatLng(driverLocation.getLatitude(), driverLocation.getLongitude());

                                    // Calcular distancia con el usuario
                                    float[] results = new float[1];
                                    Location.distanceBetween(
                                            currentLocation.latitude, currentLocation.longitude,
                                            driverLatLng.latitude, driverLatLng.longitude,
                                            results);

                                    float distanceInKm = results[0] / 1000f;

                                    // Mostrar solo conductores dentro de un radio de 5km
                                    if (distanceInKm <= 5.0f) {
                                        // Formatear tiempo
                                        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                                        String timeString = sdf.format(timestamp);

                                        // Añadir marcador
                                        MarkerOptions markerOptions = new MarkerOptions()
                                                .position(driverLatLng)
                                                .title("Conductor disponible")
                                                .snippet("Última actualización: " + timeString)
                                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));

                                        Marker marker = mMap.addMarker(markerOptions);
                                        if (marker != null) {
                                            driverMarkers.put(driverId, marker);
                                            driversCount++;
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error procesando conductor", e);
                        }
                    }

                    // Actualizar texto con el conteo
                    runOnUiThread(() -> {
                        if (driversCount > 0) {
                            availableDriversText.setText(driversCount + " conductores disponibles cerca de ti");
                        } else {
                            availableDriversText.setText("No hay conductores disponibles en este momento");
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al buscar conductores", e);
                    availableDriversText.setText("Error al buscar conductores");
                    Toast.makeText(ViewAvailabilityActivity.this, "Error al buscar conductores: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void setupRealtimeUpdates() {
        // Escuchar cambios en ubicaciones de conductores en tiempo real
        driversListener = db.collection("driver_locations")
                .whereEqualTo("isAvailable", true)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Error escuchando conductores", e);
                        return;
                    }

                    if (snapshots != null) {
                        // Actualizar marcadores en tiempo real
                        fetchAvailableDrivers();
                    }
                });
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Iniciar actualizaciones en tiempo real
        setupRealtimeUpdates();

        // Iniciar actualizaciones periódicas
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Detener escucha en tiempo real
        if (driversListener != null) {
            driversListener.remove();
        }

        // Detener actualizaciones periódicas
        refreshHandler.removeCallbacks(refreshRunnable);

        // Detener actualizaciones de ubicación
        stopLocationUpdates();
    }
}