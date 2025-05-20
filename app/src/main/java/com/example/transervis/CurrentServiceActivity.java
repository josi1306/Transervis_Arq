package com.example.transervis;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
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
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class CurrentServiceActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "CurrentServiceActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final double MAX_SEARCH_RADIUS_KM = 5.0; // Radio máximo de búsqueda en km
    private static final long SEARCH_INTERVAL_MS = 10000; // Intervalo entre búsquedas (10 segundos)

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LatLng currentLocation;
    private LatLng destinationLocation; // Destino del conductor
    private LatLng pickupLocation; // Ubicación de recogida del pasajero
    private LatLng dropoffLocation; // Ubicación de destino del pasajero
    private List<LatLng> routePoints; // Puntos de la ruta del conductor
    private Polyline currentRoute; // Polilínea actual en el mapa

    private TextView serviceStatusText;
    private TextView pickupLocationText;
    private TextView destinationLocationText;
    private TextView requestDistanceText;
    private TextView estimatedPriceText;
    private TextView passengerNameText;
    private TextView activeDistanceText;
    private TextView passengerRatingText; // Añadir para mostrar calificación del pasajero

    private Button acceptButton;
    private Button rejectButton;
    private Button navigateButton;
    private Button completeButton;
    private FloatingActionButton showRouteButton; // Para mostrar/ocultar panel de ruta

    private CardView serviceRequestCard;
    private CardView activeServiceCard;
    private LinearLayout routeSelectionLayout; // Contenedor para selección de ruta
    private ProgressBar loadingProgressBar;

    private BottomSheetBehavior<View> bottomSheetBehavior; // Para panel deslizable
    private View bottomSheetView;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ListenerRegistration serviceListener;

    private String currentServiceId;
    private String currentDriverId;
    private String currentPassengerId;
    private Handler searchHandler; // Para búsqueda periódica
    private Runnable searchRunnable;
    private boolean isSearching = false;
    private boolean hasActiveService = false;
    private MediaPlayer notificationSound;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_current_service);

        try {
            // Inicializar Firebase
            auth = FirebaseAuth.getInstance();
            db = FirebaseFirestore.getInstance();

            // Verificar si hay usuario autenticado
            FirebaseUser currentUser = auth.getCurrentUser();
            if (currentUser == null) {
                Toast.makeText(this, "Sesión expirada, por favor inicia sesión nuevamente", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, LoginConductor.class));
                finish();
                return;
            }

            currentDriverId = currentUser.getUid();

            // Inicializar vistas
            initializeViews();

            // Inicializar mapa
            SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.map);
            if (mapFragment != null) {
                mapFragment.getMapAsync(this);
            }

            // Inicializar servicios de ubicación
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            createLocationCallback();

            // Inicializar handler para búsqueda periódica
            searchHandler = new Handler();
            searchRunnable = new Runnable() {
                @Override
                public void run() {
                    if (isSearching && !hasActiveService) {
                        findNearbyPassengers();
                        searchHandler.postDelayed(this, SEARCH_INTERVAL_MS);
                    }
                }
            };

            // Verificar permisos y empezar a actualizar ubicación
            checkLocationPermissions();

            // Configurar sonido de notificación
            //notificationSound = MediaPlayer.create(this, R.raw.notification_sound);

            // Inicializar lista de puntos de ruta
            routePoints = new ArrayList<>();

            // Verificar si ya hay un servicio activo
            checkActiveService();

        } catch (Exception e) {
            Log.e(TAG, "Error en onCreate", e);
            Toast.makeText(this, "Error al inicializar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void initializeViews() {
        try {
            // Obtener referencias a las vistas
            serviceStatusText = findViewById(R.id.serviceStatusText);
            pickupLocationText = findViewById(R.id.pickupLocationText);
            destinationLocationText = findViewById(R.id.destinationLocationText);
            requestDistanceText = findViewById(R.id.requestDistanceText);
            estimatedPriceText = findViewById(R.id.estimatedPriceText);
            passengerNameText = findViewById(R.id.passengerNameText);
            activeDistanceText = findViewById(R.id.activeDistanceText);
            passengerRatingText = findViewById(R.id.passengerRatingText);

            acceptButton = findViewById(R.id.acceptButton);
            rejectButton = findViewById(R.id.rejectButton);
            navigateButton = findViewById(R.id.navigateButton);
            completeButton = findViewById(R.id.completeButton);
            showRouteButton = findViewById(R.id.showRouteButton);

            serviceRequestCard = findViewById(R.id.serviceRequestCard);
            activeServiceCard = findViewById(R.id.activeServiceCard);
            routeSelectionLayout = findViewById(R.id.routeSelectionLayout);
            loadingProgressBar = findViewById(R.id.loadingProgressBar);

            // Configurar bottom sheet para selección de ruta
            bottomSheetView = findViewById(R.id.bottomSheet);
            bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetView);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

            // Configurar listeners
            acceptButton.setOnClickListener(v -> acceptService());
            rejectButton.setOnClickListener(v -> rejectService());
            navigateButton.setOnClickListener(v -> navigateToPickup());
            completeButton.setOnClickListener(v -> completeService());
            showRouteButton.setOnClickListener(v -> toggleRouteSelectionPanel());

            // Configurar listeners para selección de ruta
            Button setRouteButton = findViewById(R.id.setRouteButton);
            Button cancelRouteButton = findViewById(R.id.cancelRouteButton);

            setRouteButton.setOnClickListener(v -> {
                saveRoute();
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            });

            cancelRouteButton.setOnClickListener(v -> {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            });

            // Ocultar tarjetas hasta que sea necesario
            serviceRequestCard.setVisibility(View.GONE);
            activeServiceCard.setVisibility(View.GONE);

        } catch (Exception e) {
            Log.e(TAG, "Error inicializando vistas", e);
            throw e;
        }
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
                    if (mMap != null) {
                        updateMapWithCurrentLocation();
                    }

                    // Actualizar ubicación en Firestore
                    updateDriverLocation();

                    break; // Solo usar la primera ubicación
                }
            }
        };
    }

    private void checkLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            // Tenemos permisos, iniciar actualizaciones de ubicación
            startLocationUpdates();
        }
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

        // Obtener ubicación inicial
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        currentLocation = new LatLng(location.getLatitude(), location.getLongitude());

                        // Mover cámara a ubicación actual
                        if (mMap != null) {
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15));
                        }

                        // Actualizar ubicación en Firestore
                        updateDriverLocation();
                    }
                });
    }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    private void updateDriverLocation() {
        if (currentLocation == null || auth.getCurrentUser() == null) return;

        String driverId = auth.getCurrentUser().getUid();

        Map<String, Object> locationData = new HashMap<>();
        locationData.put("conductorId", driverId);
        locationData.put("location", new GeoPoint(currentLocation.latitude, currentLocation.longitude));
        locationData.put("heading", 0); // Podría calcularse con ubicaciones previas
        locationData.put("speed", 0); // Podría obtenerse de la API de ubicación
        locationData.put("timestamp", new Date());
        locationData.put("isAvailable", !hasActiveService); // No disponible si tiene servicio activo
        locationData.put("battery", 100); // Podría obtenerse del sistema

        db.collection("driver_locations")
                .document(driverId)
                .set(locationData)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al actualizar ubicación", e);
                });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Habilitar controles del mapa
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        // Habilitar mi ubicación si hay permisos
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }

        // Configurar listener para clic largo en el mapa (selección de destino)
        mMap.setOnMapLongClickListener(latLng -> {
            // Si no hay ruta activa, establecer como destino
            if (routePoints.isEmpty() || routePoints.size() == 1) {
                destinationLocation = latLng;

                // Si solo tenemos ubicación actual, comenzar una nueva ruta
                if (routePoints.isEmpty() && currentLocation != null) {
                    routePoints.add(currentLocation);
                }

                // Añadir destino a la ruta
                if (routePoints.size() == 1) {
                    routePoints.add(destinationLocation);
                    drawRoute();

                    // Mostrar diálogo de confirmación
                    showRouteConfirmationDialog();
                }
            }
            // Si ya hay una ruta con puntos intermedios, añadir punto
            else if (routePoints.size() >= 2) {
                // Insertar punto antes del destino final
                routePoints.add(routePoints.size() - 1, latLng);
                drawRoute();
            }
        });

        // Si hay ubicación disponible, centrar mapa
        if (currentLocation != null) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permiso concedido, iniciar actualizaciones de ubicación
                startLocationUpdates();

                // Habilitar mi ubicación en el mapa
                if (mMap != null) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {
                        mMap.setMyLocationEnabled(true);
                    }
                }
            } else {
                // Permiso denegado, informar al usuario
                Toast.makeText(this, "Se requiere permiso de ubicación para usar esta funcionalidad",
                        Toast.LENGTH_LONG).show();

                // Regresar a la actividad anterior
                finish();
            }
        }
    }

    private void updateMapWithCurrentLocation() {
        if (currentLocation == null || mMap == null) return;

        // Si no hay ninguna ruta activa, centrar en ubicación actual
        if (!hasActiveService && (routePoints == null || routePoints.isEmpty())) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15));
        }
    }

    private void checkActiveService() {
        if (auth.getCurrentUser() == null) return;

        String driverId = auth.getCurrentUser().getUid();

        // Buscar servicios activos para este conductor
        db.collection("services")
                .whereEqualTo("driverId", driverId)
                .whereEqualTo("status", "accepted")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        // Hay un servicio activo
                        DocumentSnapshot serviceDoc = queryDocumentSnapshots.getDocuments().get(0);
                        currentServiceId = serviceDoc.getId();
                        currentPassengerId = serviceDoc.getString("passengerId");
                        hasActiveService = true;

                        // Mostrar servicio activo
                        displayActiveService(serviceDoc);
                    } else {
                        // No hay servicio activo, comenzar búsqueda
                        hasActiveService = false;
                        startPassengerSearch();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error al verificar servicio activo", e);
                    Toast.makeText(this, "Error al verificar servicios activos: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();

                    // Iniciar búsqueda por defecto
                    startPassengerSearch();
                });
    }

    private void startPassengerSearch() {
        if (isSearching) return;

        isSearching = true;
        serviceStatusText.setText("Buscando pasajeros cercanos...");

        // Iniciar búsqueda periódica
        searchHandler.post(searchRunnable);
    }

    private void stopPassengerSearch() {
        isSearching = false;
        searchHandler.removeCallbacks(searchRunnable);
    }

    private void findNearbyPassengers() {
        if (currentLocation == null) return;

        // Mostrar estado de búsqueda
        runOnUiThread(() -> {
            serviceStatusText.setText("Buscando pasajeros cercanos...");

            // Mostrar indicador de carga si es necesario
            if (loadingProgressBar != null) {
                loadingProgressBar.setVisibility(View.VISIBLE);
            }
        });

        // Buscar solicitudes de servicio pendientes
        db.collection("services")
                .whereEqualTo("status", "requested")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<DocumentSnapshot> nearbyRequests = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        try {
                            // Obtener coordenadas de recogida
                            GeoPoint pickupCoordinates = doc.getGeoPoint("pickupCoordinates");

                            if (pickupCoordinates != null) {
                                double pickupLat = pickupCoordinates.getLatitude();
                                double pickupLng = pickupCoordinates.getLongitude();

                                // Calcular distancia Haversine (en línea recta)
                                double distanceKm = calculateHaversineDistance(
                                        currentLocation.latitude, currentLocation.longitude,
                                        pickupLat, pickupLng);

                                // Si está dentro del radio, añadir a la lista
                                if (distanceKm <= MAX_SEARCH_RADIUS_KM) {
                                    nearbyRequests.add(doc);
                                    Log.d(TAG, "Pasajero encontrado a " + distanceKm + " km");
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error procesando documento de servicio", e);
                        }
                    }

                    runOnUiThread(() -> {
                        // Ocultar indicador de carga
                        if (loadingProgressBar != null) {
                            loadingProgressBar.setVisibility(View.GONE);
                        }

                        // Procesar solicitudes cercanas
                        if (!nearbyRequests.isEmpty()) {
                            // Si hay rutas definidas, filtrar por cercanía a la ruta
                            if (!routePoints.isEmpty() && routePoints.size() >= 2) {
                                filterRequestsByRoute(nearbyRequests);
                            } else {
                                // Si no hay ruta, mostrar la solicitud más cercana
                                displayClosestRequest(nearbyRequests);
                            }
                        } else {
                            // No hay solicitudes cercanas
                            serviceStatusText.setText("No se encontraron pasajeros cercanos. Buscando...");
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error buscando solicitudes de servicio", e);

                    runOnUiThread(() -> {
                        // Ocultar indicador de carga
                        if (loadingProgressBar != null) {
                            loadingProgressBar.setVisibility(View.GONE);
                        }

                        serviceStatusText.setText("Error al buscar pasajeros. Reintentando...");
                    });
                });
    }

    private void filterRequestsByRoute(List<DocumentSnapshot> requests) {
        if (routePoints.isEmpty() || routePoints.size() < 2) {
            displayClosestRequest(requests);
            return;
        }

        // Lista para almacenar solicitudes filtradas con su distancia a la ruta
        List<RequestWithDistance> filteredRequests = new ArrayList<>();

        for (DocumentSnapshot doc : requests) {
            try {
                GeoPoint pickupCoordinates = doc.getGeoPoint("pickupCoordinates");
                GeoPoint destinationCoordinates = doc.getGeoPoint("destinationCoordinates");

                if (pickupCoordinates != null && destinationCoordinates != null) {
                    LatLng pickup = new LatLng(pickupCoordinates.getLatitude(), pickupCoordinates.getLongitude());

                    // Calcular distancia mínima del punto de recogida a la ruta
                    double minDistance = Double.MAX_VALUE;

                    // Iterar por los segmentos de la ruta
                    for (int i = 0; i < routePoints.size() - 1; i++) {
                        LatLng routeStart = routePoints.get(i);
                        LatLng routeEnd = routePoints.get(i + 1);

                        // Calcular distancia del punto al segmento
                        double distance = distanceToSegment(pickup, routeStart, routeEnd);

                        if (distance < minDistance) {
                            minDistance = distance;
                        }
                    }

                    // Si la distancia es menor a 2km (ajustar según necesidades)
                    if (minDistance <= 2.0) {
                        filteredRequests.add(new RequestWithDistance(doc, minDistance));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error filtrando solicitud por ruta", e);
            }
        }

        // Ordenar por distancia
        filteredRequests.sort((r1, r2) -> Double.compare(r1.distance, r2.distance));

        if (!filteredRequests.isEmpty()) {
            // Mostrar la solicitud más cercana a la ruta
            displayServiceRequest(filteredRequests.get(0).request);

            // Reproducir sonido
            playNotificationSound();
        } else {
            // No hay solicitudes cercanas a la ruta
            serviceStatusText.setText("No hay pasajeros cercanos a tu ruta. Buscando...");
        }
    }

    private void displayClosestRequest(List<DocumentSnapshot> requests) {
        if (requests.isEmpty()) return;

        DocumentSnapshot closestRequest = null;
        double minDistance = Double.MAX_VALUE;

        for (DocumentSnapshot doc : requests) {
            try {
                GeoPoint pickupCoordinates = doc.getGeoPoint("pickupCoordinates");

                if (pickupCoordinates != null && currentLocation != null) {
                    double distance = calculateHaversineDistance(
                            currentLocation.latitude, currentLocation.longitude,
                            pickupCoordinates.getLatitude(), pickupCoordinates.getLongitude());

                    if (distance < minDistance) {
                        minDistance = distance;
                        closestRequest = doc;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error calculando distancia", e);
            }
        }

        if (closestRequest != null) {
            // Mostrar la solicitud más cercana
            displayServiceRequest(closestRequest);

            // Reproducir sonido
            playNotificationSound();
        }
    }

    private void displayServiceRequest(DocumentSnapshot request) {
        try {
            // Detener búsqueda mientras se muestra la solicitud
            stopPassengerSearch();

            // Obtener datos de la solicitud
            GeoPoint pickupCoordinates = request.getGeoPoint("pickupCoordinates");
            GeoPoint destinationCoordinates = request.getGeoPoint("destinationCoordinates");
            String passengerId = request.getString("passengerId");
            Double distanceKm = request.getDouble("distanceKm");
            Double estimatedPrice = request.getDouble("estimatedPrice");
            String pickupLocation = request.getString("pickupLocation");
            String destination = request.getString("destination");

            // Guardar IDs para referencia
            currentServiceId = request.getId();
            currentPassengerId = passengerId;

            // Actualizar UI con la información
            if (pickupLocationText != null && destinationLocationText != null) {
                if (pickupLocation != null && !pickupLocation.isEmpty()) {
                    pickupLocationText.setText(pickupLocation);
                } else if (pickupCoordinates != null) {
                    getAddressFromLocation(
                            new LatLng(pickupCoordinates.getLatitude(), pickupCoordinates.getLongitude()),
                            pickupLocationText);
                }

                if (destination != null && !destination.isEmpty()) {
                    destinationLocationText.setText(destination);
                } else if (destinationCoordinates != null) {
                    getAddressFromLocation(
                            new LatLng(destinationCoordinates.getLatitude(), destinationCoordinates.getLongitude()),
                            destinationLocationText);
                }
            }

            if (requestDistanceText != null && distanceKm != null) {
                requestDistanceText.setText(String.format(Locale.getDefault(), "%.1f km", distanceKm));
            }

            if (estimatedPriceText != null && estimatedPrice != null) {
                DecimalFormat format = new DecimalFormat("#,###");
                estimatedPriceText.setText("$" + format.format(estimatedPrice));
            }

// Actualizar mapa con la solicitud
            if (pickupCoordinates != null && destinationCoordinates != null) {
                this.pickupLocation = new LatLng(pickupCoordinates.getLatitude(), pickupCoordinates.getLongitude());
                this.dropoffLocation = new LatLng(destinationCoordinates.getLatitude(), destinationCoordinates.getLongitude());

                updateMapWithServiceRequest(this.pickupLocation, this.dropoffLocation);
            }

            // Obtener información del pasajero
            fetchPassengerInfo(passengerId);

            // Mostrar tarjeta de solicitud
            if (serviceRequestCard != null) {
                serviceRequestCard.setVisibility(View.VISIBLE);
            }

            // Actualizar estado
            serviceStatusText.setText("¡Nueva solicitud de servicio!");

        } catch (Exception e) {
            Log.e(TAG, "Error mostrando solicitud de servicio", e);

            // Continuar con la búsqueda
            startPassengerSearch();
        }
    }

    private void fetchPassengerInfo(String passengerId) {
        if (passengerId == null || passengerId.isEmpty()) return;

        db.collection("profiles").document(passengerId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Obtener datos del perfil
                        String nombre = documentSnapshot.getString("nombre");
                        String apellido = documentSnapshot.getString("apellido");

                        // Actualizar nombre del pasajero
                        if (passengerNameText != null) {
                            if (nombre != null && apellido != null) {
                                passengerNameText.setText(nombre + " " + apellido);
                            } else if (nombre != null) {
                                passengerNameText.setText(nombre);
                            } else {
                                passengerNameText.setText("Pasajero");
                            }
                        }

                        // Obtener calificación promedio del pasajero
                        fetchPassengerRating(passengerId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error obteniendo información del pasajero", e);

                    if (passengerNameText != null) {
                        passengerNameText.setText("Pasajero");
                    }
                });
    }

    private void fetchPassengerRating(String passengerId) {
        db.collection("ratings")
                .whereEqualTo("toUserId", passengerId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        // Calcular promedio de calificaciones
                        double totalRating = 0;
                        int count = 0;

                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            Number puntuacion = doc.getLong("puntuacion");
                            if (puntuacion != null) {
                                totalRating += puntuacion.doubleValue();
                                count++;
                            }
                        }

                        if (count > 0) {
                            double averageRating = totalRating / count;

                            // Mostrar calificación
                            if (passengerRatingText != null) {
                                passengerRatingText.setText(String.format(Locale.getDefault(),
                                        "%.1f ★ (%d valoraciones)", averageRating, count));
                            }
                        } else {
                            if (passengerRatingText != null) {
                                passengerRatingText.setText("Sin valoraciones");
                            }
                        }
                    } else {
                        if (passengerRatingText != null) {
                            passengerRatingText.setText("Sin valoraciones");
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error obteniendo calificaciones del pasajero", e);

                    if (passengerRatingText != null) {
                        passengerRatingText.setText("Sin valoraciones");
                    }
                });
    }

    private void updateMapWithServiceRequest(LatLng pickup, LatLng destination) {
        if (mMap == null || pickup == null || destination == null) return;

        // Limpiar mapa
        mMap.clear();

        // Añadir marcadores
        mMap.addMarker(new MarkerOptions()
                .position(pickup)
                .title("Punto de recogida")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        mMap.addMarker(new MarkerOptions()
                .position(destination)
                .title("Destino")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        // Dibujar línea entre origen y destino
        mMap.addPolyline(new PolylineOptions()
                .add(pickup, destination)
                .width(5)
                .color(ContextCompat.getColor(this, R.color.lavender)));

        // Ajustar zoom para mostrar ambos puntos
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(pickup);
        builder.include(destination);

        // Aplicar zoom con padding
        try {
            int padding = 100;
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), padding));
        } catch (Exception e) {
            Log.e(TAG, "Error ajustando cámara", e);

            // Alternativa: centrar en punto medio
            LatLng center = new LatLng(
                    (pickup.latitude + destination.latitude) / 2,
                    (pickup.longitude + destination.longitude) / 2);
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 12));
        }
    }

    private void acceptService() {
        if (currentServiceId == null || currentServiceId.isEmpty()) {
            Toast.makeText(this, "Error: ID de servicio no disponible", Toast.LENGTH_SHORT).show();
            return;
        }

        // Mostrar progreso
        loadingProgressBar.setVisibility(View.VISIBLE);

        // Actualizar estado del servicio en Firestore
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "accepted");
        updates.put("driverId", auth.getCurrentUser().getUid());
        updates.put("acceptedAt", new Date());

        db.collection("services").document(currentServiceId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    // Servicio aceptado con éxito
                    hasActiveService = true;

                    // Actualizar disponibilidad del conductor
                    updateDriverAvailability(false);

                    // Ocultar tarjeta de solicitud
                    serviceRequestCard.setVisibility(View.GONE);

                    // Mostrar tarjeta de servicio activo
                    activeServiceCard.setVisibility(View.VISIBLE);

                    // Actualizar estado
                    serviceStatusText.setText("Servicio aceptado - En camino a recoger al pasajero");

                    // Ocultar progreso
                    loadingProgressBar.setVisibility(View.GONE);

                    // Actualizar información en la tarjeta de servicio activo
                    if (activeDistanceText != null && requestDistanceText != null && estimatedPriceText != null) {
                        activeDistanceText.setText(requestDistanceText.getText() + " - " + estimatedPriceText.getText());
                    }

                    // Enfocar mapa en punto de recogida
                    if (mMap != null && pickupLocation != null) {
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pickupLocation, 15));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error aceptando servicio", e);

                    // Mostrar error
                    Toast.makeText(CurrentServiceActivity.this,
                            "Error al aceptar servicio: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();

                    // Ocultar progreso
                    loadingProgressBar.setVisibility(View.GONE);

                    // Continuar con la búsqueda
                    startPassengerSearch();
                });
    }

    private void rejectService() {
        // Ocultar tarjeta de solicitud
        serviceRequestCard.setVisibility(View.GONE);

        // Limpiar mapa
        if (mMap != null) {
            mMap.clear();

            // Si hay ruta definida, redibujarla
            if (!routePoints.isEmpty() && routePoints.size() >= 2) {
                drawRoute();
            }
        }

        // Actualizar estado
        serviceStatusText.setText("Solicitud rechazada - Buscando nuevos pasajeros...");

        // Continuar con la búsqueda
        startPassengerSearch();
    }

    private void navigateToPickup() {
        if (pickupLocation == null) return;

        // Abrir Google Maps para navegación
        Uri navigationUri = Uri.parse("google.navigation:q=" +
                pickupLocation.latitude + "," + pickupLocation.longitude + "&mode=d");
        Intent navigationIntent = new Intent(Intent.ACTION_VIEW, navigationUri);
        navigationIntent.setPackage("com.google.android.apps.maps");

        if (navigationIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(navigationIntent);
        } else {
            Toast.makeText(this, "No se encontró la aplicación de Google Maps",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void completeService() {
        // Mostrar diálogo de confirmación
        new AlertDialog.Builder(this)
                .setTitle("Completar Servicio")
                .setMessage("¿Estás seguro de que deseas completar este servicio?")
                .setPositiveButton("Sí", (dialog, which) -> {
                    completeServiceConfirmed();
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void completeServiceConfirmed() {
        if (currentServiceId == null || currentServiceId.isEmpty()) {
            Toast.makeText(this, "Error: ID de servicio no disponible", Toast.LENGTH_SHORT).show();
            return;
        }

        // Mostrar progreso
        loadingProgressBar.setVisibility(View.VISIBLE);

        // Actualizar estado del servicio en Firestore
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "completed");
        updates.put("completedAt", new Date());

        db.collection("services").document(currentServiceId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    // Servicio completado con éxito
                    hasActiveService = false;

                    // Actualizar disponibilidad del conductor
                    updateDriverAvailability(true);

                    // Ocultar tarjeta de servicio activo
                    activeServiceCard.setVisibility(View.GONE);

                    // Actualizar estado
                    serviceStatusText.setText("Servicio completado - Buscando nuevos pasajeros...");

                    // Limpiar mapa
                    if (mMap != null) {
                        mMap.clear();

                        // Si hay ruta definida, redibujarla
                        if (!routePoints.isEmpty() && routePoints.size() >= 2) {
                            drawRoute();
                        }
                    }

                    // Ocultar progreso
                    loadingProgressBar.setVisibility(View.GONE);

                    // Solicitar calificación
                    showRatingDialog();

                    // Actualizar ganancias del conductor
                    updateDriverEarnings();

                    // Continuar con la búsqueda
                    startPassengerSearch();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error completando servicio", e);

                    // Mostrar error
                    Toast.makeText(CurrentServiceActivity.this,
                            "Error al completar servicio: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();

                    // Ocultar progreso
                    loadingProgressBar.setVisibility(View.GONE);
                });
    }

    private void showRatingDialog() {
        // En una aplicación real, esto sería un diálogo personalizado con estrellas
        Toast.makeText(this, "Servicio calificado con 5 estrellas", Toast.LENGTH_LONG).show();
    }

    private void updateDriverEarnings() {
        if (auth.getCurrentUser() == null) return;

        String driverId = auth.getCurrentUser().getUid();

        // Obtener datos del servicio actual
        db.collection("services").document(currentServiceId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Obtener precio estimado
                        Double estimatedPrice = documentSnapshot.getDouble("estimatedPrice");
                        Double distanceKm = documentSnapshot.getDouble("distanceKm");

                        if (estimatedPrice != null && distanceKm != null) {
                            // Calcular comisión de la plataforma (15%)
                            double platformFee = estimatedPrice * 0.15;
                            double driverEarnings = estimatedPrice - platformFee;

                            // Actualizar documento de ganancias
                            updateEarningsDocument(driverId, driverEarnings, platformFee, distanceKm, currentServiceId);
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error obteniendo datos del servicio", e));
    }

    private void updateEarningsDocument(String driverId, double earnings, double platformFee,
                                        double distanceKm, String serviceId) {
        // Obtener documento de ganancias actual
        db.collection("driver_earnings").document(driverId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    Map<String, Object> updates = new HashMap<>();

                    if (documentSnapshot.exists()) {
                        // Actualizar documento existente
                        Double totalEarnings = documentSnapshot.getDouble("totalGanancias");
                        Double totalDistance = documentSnapshot.getDouble("distanciaTotal");
                        Long totalTrips = documentSnapshot.getLong("totalViajes");

                        // Calcular nuevos totales
                        double newTotalEarnings = (totalEarnings != null ? totalEarnings : 0) + earnings;
                        double newTotalDistance = (totalDistance != null ? totalDistance : 0) + distanceKm;
                        long newTotalTrips = (totalTrips != null ? totalTrips : 0) + 1;

                        // Preparar actualizaciones
                        updates.put("totalGanancias", newTotalEarnings);
                        updates.put("distanciaTotal", newTotalDistance);
                        updates.put("totalViajes", newTotalTrips);
                        updates.put("comisionPlataforma", platformFee);

                        // Añadir ID del servicio al array
                        updates.put("viajesIds." + (newTotalTrips - 1), serviceId);
                    } else {
                        // Crear nuevo documento
                        updates.put("totalGanancias", earnings);
                        updates.put("distanciaTotal", distanceKm);
                        updates.put("totalViajes", 1L);
                        updates.put("comisionPlataforma", platformFee);
                        updates.put("viajesIds.0", serviceId);
                    }

                    // Actualizar o crear documento
                    db.collection("driver_earnings").document(driverId)
                            .set(updates, com.google.firebase.firestore.SetOptions.merge())
                            .addOnFailureListener(e -> Log.e(TAG, "Error actualizando ganancias", e));
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error obteniendo documento de ganancias", e));
    }

    private void displayActiveService(DocumentSnapshot serviceDoc) {
        try {
            // Obtener datos del servicio
            GeoPoint pickupCoordinates = serviceDoc.getGeoPoint("pickupCoordinates");
            GeoPoint destinationCoordinates = serviceDoc.getGeoPoint("destinationCoordinates");
            String passengerId = serviceDoc.getString("passengerId");
            Double distanceKm = serviceDoc.getDouble("distanceKm");
            Double estimatedPrice = serviceDoc.getDouble("estimatedPrice");
            String pickupLocation = serviceDoc.getString("pickupLocation");
            String destination = serviceDoc.getString("destination");

            // Guardar IDs para referencia
            currentPassengerId = passengerId;

            // Convertir GeoPoints a LatLng
            if (pickupCoordinates != null) {
                this.pickupLocation = new LatLng(pickupCoordinates.getLatitude(), pickupCoordinates.getLongitude());
            }

            if (destinationCoordinates != null) {
                this.dropoffLocation = new LatLng(destinationCoordinates.getLatitude(), destinationCoordinates.getLongitude());
            }

            // Actualizar UI
            // Mostrar tarjeta de servicio activo
            activeServiceCard.setVisibility(View.VISIBLE);

            // Actualizar estado
            serviceStatusText.setText("Servicio activo - En camino a recoger al pasajero");

            // Actualizar información del servicio
            if (activeDistanceText != null && distanceKm != null && estimatedPrice != null) {
                DecimalFormat format = new DecimalFormat("#,###");
                activeDistanceText.setText(String.format(Locale.getDefault(), "%.1f km - $%s",
                        distanceKm, format.format(estimatedPrice)));
            }

            // Obtener información del pasajero
            fetchPassengerInfo(passengerId);

            // Actualizar mapa
            if (this.pickupLocation != null && this.dropoffLocation != null) {
                updateMapWithServiceRequest(this.pickupLocation, this.dropoffLocation);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error mostrando servicio activo", e);
            Toast.makeText(this, "Error al cargar servicio activo", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateDriverAvailability(boolean isAvailable) {
        if (auth.getCurrentUser() == null) return;

        String driverId = auth.getCurrentUser().getUid();

        db.collection("driver_locations").document(driverId)
                .update("isAvailable", isAvailable)
                .addOnFailureListener(e -> Log.e(TAG, "Error actualizando disponibilidad", e));
    }

    private void toggleRouteSelectionPanel() {
        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        } else {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        }
    }

    private void showRouteConfirmationDialog() {
        // Mostrar diálogo de confirmación
        new AlertDialog.Builder(this)
                .setTitle("Confirmar Ruta")
                .setMessage("¿Deseas establecer esta ruta para buscar pasajeros?")
                .setPositiveButton("Sí", (dialog, which) -> {
                    saveRoute();
                })
                .setNegativeButton("No", (dialog, which) -> {
                    // Limpiar ruta
                    routePoints.clear();
                    if (mMap != null) {
                        mMap.clear();
                    }
                })
                .show();
    }

    private void saveRoute() {
        if (routePoints.size() < 2) {
            Toast.makeText(this, "Se requieren al menos dos puntos para definir una ruta",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Guardar ruta
        Toast.makeText(this, "Ruta guardada. Buscando pasajeros en esta ruta...",
                Toast.LENGTH_SHORT).show();

        // Redibujar ruta
        drawRoute();

        // Iniciar búsqueda
        startPassengerSearch();
    }

    private void drawRoute() {
        if (mMap == null || routePoints.size() < 2) return;

        // Limpiar mapa
        mMap.clear();

        // Crear polilínea para la ruta
        PolylineOptions options = new PolylineOptions()
                .width(8)
                .color(Color.BLUE);

        // Añadir todos los puntos
        for (LatLng point : routePoints) {
            options.add(point);
        }

        // Dibujar polilínea
        currentRoute = mMap.addPolyline(options);

        // Añadir marcadores de origen y destino
        mMap.addMarker(new MarkerOptions()
                .position(routePoints.get(0))
                .title("Origen")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        mMap.addMarker(new MarkerOptions()
                .position(routePoints.get(routePoints.size() - 1))
                .title("Destino")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

        // Añadir marcadores para puntos intermedios
        for (int i = 1; i < routePoints.size() - 1; i++) {
            mMap.addMarker(new MarkerOptions()
                    .position(routePoints.get(i))
                    .title("Punto " + i)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        }

        // Ajustar cámara
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLng point : routePoints) {
            builder.include(point);
        }

        try {
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
        } catch (Exception e) {
            Log.e(TAG, "Error ajustando cámara a la ruta", e);

            // Alternativa: centrar en punto medio
            if (routePoints.size() >= 2) {
                LatLng center = new LatLng(
                        (routePoints.get(0).latitude + routePoints.get(routePoints.size() - 1).latitude) / 2,
                        (routePoints.get(0).longitude + routePoints.get(routePoints.size() - 1).longitude) / 2);
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 12));
            }
        }
    }

    // Método para calcular distancia Haversine (distancia en línea recta)
    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        // Radio de la Tierra en kilómetros
        final double R = 6371.0;

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c; // Distancia en kilómetros
    }

    // Método para calcular distancia de un punto a un segmento de línea
    private double distanceToSegment(LatLng p, LatLng v, LatLng w) {
        double l2 = Math.pow(v.latitude - w.latitude, 2) + Math.pow(v.longitude - w.longitude, 2);
        if (l2 == 0) return calculateHaversineDistance(p.latitude, p.longitude, v.latitude, v.longitude);

        double t = ((p.latitude - v.latitude) * (w.latitude - v.latitude) +
                (p.longitude - v.longitude) * (w.longitude - v.longitude)) / l2;

        if (t < 0) return calculateHaversineDistance(p.latitude, p.longitude, v.latitude, v.longitude);
        if (t > 1) return calculateHaversineDistance(p.latitude, p.longitude, w.latitude, w.longitude);

        double projectionLat = v.latitude + t * (w.latitude - v.latitude);
        double projectionLng = v.longitude + t * (w.longitude - v.longitude);

        return calculateHaversineDistance(p.latitude, p.longitude, projectionLat, projectionLng);
    }

    private void getAddressFromLocation(LatLng location, TextView textView) {
        if (location == null || textView == null) return;

        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                StringBuilder sb = new StringBuilder();

                // Obtener dirección formateada
                for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(address.getAddressLine(i));
                }

                textView.setText(sb.toString());
            } else {
                textView.setText("Dirección no disponible");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error obteniendo dirección", e);
            textView.setText("Error obteniendo dirección");
        }
    }

    private void playNotificationSound() {
        try {
            if (notificationSound != null) {
                notificationSound.seekTo(0);
                notificationSound.start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reproduciendo sonido", e);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Iniciar actualizaciones de ubicación
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Detener actualizaciones de ubicación
        stopLocationUpdates();

        // Detener búsqueda
        stopPassengerSearch();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Liberar recursos
        if (notificationSound != null) {
            notificationSound.release();
            notificationSound = null;
        }

        // Detener listener de servicio si existe
        if (serviceListener != null) {
            serviceListener.remove();
        }
    }

    // Clase auxiliar para almacenar solicitudes con su distancia
    private static class RequestWithDistance {
        DocumentSnapshot request;
        double distance;

        RequestWithDistance(DocumentSnapshot request, double distance) {
            this.request = request;
            this.distance = distance;
        }
    }
}