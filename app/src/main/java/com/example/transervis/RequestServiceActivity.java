package com.example.transervis;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AutoCompleteTextView;
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
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.LocationBias;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.RectangularBounds;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.maps.DirectionsApi;
import com.google.maps.DirectionsApiRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.PendingResult;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;
import com.google.maps.model.TravelMode;
import com.google.firebase.firestore.DocumentSnapshot;


import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class RequestServiceActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "RequestServiceActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final double BASE_FARE = 3500.00; // Tarifa base en pesos colombianos
    private static final double PRICE_PER_KM = 850.00; // Precio por kilómetro
    private static final double PRICE_PER_MINUTE = 150.00; // Precio por minuto

    // Firebase
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ListenerRegistration serviceListener;
    private String serviceId;


    // Google Services
    private FusedLocationProviderClient fusedLocationClient;
    private PlacesClient placesClient;
    private GoogleMap mMap;
    private GeoApiContext geoApiContext;
    private boolean placesInitialized = false;

    // UI Elements
    private AutoCompleteTextView pickupLocationEditText, destinationEditText;
    private Button calculateRouteButton, confirmServiceButton, cancelServiceButton;
    private TextView distanceTextView, durationTextView, priceTextView;
    private TextView driverNameTextView, vehicleInfoTextView, arrivalTimeTextView;
    private LinearLayout driverInfoLayout;
    private ProgressBar progressBar, driverLoadingProgressBar;
    private CardView serviceInfoCardView;
    private TextView errorTextView;
    private LocationCallback locationCallback;

    // Data
    private Location currentLocation;
    private LatLng originLatLng, destinationLatLng;
    private String originAddress, destinationAddress;
    private double distanceInKm;
    private int durationInMinutes;
    private double estimatedPrice;
    private DirectionsResult directionsResult;
    private boolean serviceConfirmed = false;
    private PlacesAutoCompleteAdapter pickupAdapter, destinationAdapter;

    private LinearLayout searchPanelContainer;
    private FloatingActionButton toggleSearchPanelButton;
    private boolean isSearchPanelVisible = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_request_service);

            Log.d(TAG, "RequestServiceActivity onCreate - inicio");

            // Inicializar Firebase Auth y verificar autenticación
            auth = FirebaseAuth.getInstance();
            FirebaseUser currentUser = auth.getCurrentUser();
            if (currentUser == null) {
                Toast.makeText(this, "Sesión expirada, por favor inicia sesión nuevamente", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
                return;
            }

            // Inicializar FirebaseFirestore
            db = FirebaseFirestore.getInstance();

            // Inicializar vistas
            if (!initializeViews()) {
                Toast.makeText(this, "Error al inicializar la interfaz", Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            // Inicializar servicios de ubicación antes de Places API para tener la ubicación actual
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            createLocationCallback();

            // Inicializar Places API - verificamos que sea después de tener permisos y ubicación
            initializePlacesAPI();

            // Inicializar mapa
            SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.mapFragment);
            if (mapFragment != null) {
                mapFragment.getMapAsync(this);
            } else {
                Log.e(TAG, "Error: No se encontró el fragmento del mapa");
                Toast.makeText(this, "Error al cargar el mapa", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            // Verificar permisos de ubicación
            checkLocationPermissions();

            // Inicializar GeoApiContext para Directions API
            try {
                geoApiContext = new GeoApiContext.Builder()
                        .apiKey(getString(R.string.google_maps_key))
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .writeTimeout(10, TimeUnit.SECONDS)
                        .build();
            } catch (Exception e) {
                Log.e(TAG, "Error al crear GeoApiContext", e);
                Toast.makeText(this, "Error al inicializar servicios de mapas", Toast.LENGTH_SHORT).show();
            }

            // Configurar listeners
            setupListeners();

            Log.d(TAG, "RequestServiceActivity onCreate completado");
        } catch (Exception e) {
            Log.e(TAG, "Error en onCreate", e);
            Toast.makeText(this, "Error al inicializar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private boolean initializeViews() {
        try {
            Log.d(TAG, "Inicializando vistas");

            // Panel de búsqueda
            searchPanelContainer = findViewById(R.id.searchPanelContainer);
            toggleSearchPanelButton = findViewById(R.id.toggleSearchPanelButton);

            // Campos de texto
            pickupLocationEditText = findViewById(R.id.pickupLocationEditText);
            destinationEditText = findViewById(R.id.destinationEditText);

            // Botones
            calculateRouteButton = findViewById(R.id.calculateRouteButton);
            confirmServiceButton = findViewById(R.id.confirmServiceButton);
            cancelServiceButton = findViewById(R.id.cancelServiceButton);

            // Text views para mostrar información
            distanceTextView = findViewById(R.id.distanceTextView);
            durationTextView = findViewById(R.id.durationTextView);
            priceTextView = findViewById(R.id.priceTextView);
            driverNameTextView = findViewById(R.id.driverNameTextView);
            vehicleInfoTextView = findViewById(R.id.vehicleInfoTextView);
            arrivalTimeTextView = findViewById(R.id.arrivalTimeTextView);

            // Otros elementos de UI
            driverInfoLayout = findViewById(R.id.driverInfoLayout);
            progressBar = findViewById(R.id.progressBar);
            driverLoadingProgressBar = findViewById(R.id.driverLoadingProgressBar);
            serviceInfoCardView = findViewById(R.id.serviceInfoCardView);
            errorTextView = findViewById(R.id.errorTextView);

            // Verificar que todos los elementos críticos se hayan encontrado
            if (pickupLocationEditText == null || destinationEditText == null ||
                    calculateRouteButton == null || confirmServiceButton == null ||
                    serviceInfoCardView == null) {
                Log.e(TAG, "Error: Elementos esenciales de UI no encontrados");
                return false;
            }

            // Ocultar elementos que se mostrarán más tarde
            if (serviceInfoCardView != null) {
                serviceInfoCardView.setVisibility(View.GONE);
            }

            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }

            Log.d(TAG, "Vistas inicializadas correctamente");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error al inicializar vistas", e);
            return false;
        }
    }

    private void initializePlacesAPI() {
        try {
            if (!Places.isInitialized()) {
                Places.initialize(getApplicationContext(), getString(R.string.google_maps_key));
                placesInitialized = true;
            }

            placesClient = Places.createClient(this);

            // Crear adaptadores de autocompletado
            LocationBias colombiaBias = getColombiaLocationBias();
            pickupAdapter = new PlacesAutoCompleteAdapter(this, placesClient, colombiaBias);
            destinationAdapter = new PlacesAutoCompleteAdapter(this, placesClient, colombiaBias);

            if (pickupLocationEditText != null) {
                pickupLocationEditText.setAdapter(pickupAdapter);
                pickupLocationEditText.setThreshold(2);
            }

            if (destinationEditText != null) {
                destinationEditText.setAdapter(destinationAdapter);
                destinationEditText.setThreshold(2);
            }

            Log.d(TAG, "Places API inicializada correctamente");
        } catch (Exception e) {
            placesInitialized = false;
            Log.e(TAG, "Error inicializando Places API", e);
            Toast.makeText(this, "Error al inicializar servicios de ubicación", Toast.LENGTH_SHORT).show();
        }
    }

    private LocationBias getColombiaLocationBias() {
        // Coordenadas aproximadas de Colombia (centro)
        LatLng colombiaCenter = new LatLng(4.5709, -74.2973);

        return RectangularBounds.newInstance(
                new LatLng(colombiaCenter.latitude - 5, colombiaCenter.longitude - 5),
                new LatLng(colombiaCenter.latitude + 5, colombiaCenter.longitude + 5)
        );
    }

    private void setupListeners() {
        try {
            // Botón de calcular ruta
            if (calculateRouteButton != null) {
                calculateRouteButton.setOnClickListener(v -> {
                    calculateRoute();
                    hideSearchPanel();
                });
            }

            // Botón de confirmar servicio
            if (confirmServiceButton != null) {
                confirmServiceButton.setOnClickListener(v -> confirmService());
            }

            // Botón de cancelar servicio
            if (cancelServiceButton != null) {
                cancelServiceButton.setOnClickListener(v -> cancelService());
            }

            // Botón de mostrar/ocultar panel de búsqueda
            if (toggleSearchPanelButton != null) {
                toggleSearchPanelButton.setOnClickListener(v -> toggleSearchPanel());
            }

            // Configurar listeners para autocompletado
            if (pickupLocationEditText != null && pickupAdapter != null) {
                pickupLocationEditText.setOnItemClickListener((parent, view, position, id) -> {
                    AutocompletePrediction prediction = pickupAdapter.getItem(position);
                    if (prediction != null) {
                        fetchPlaceAndUpdateOrigin(prediction);
                    }
                });
            }

            if (destinationEditText != null && destinationAdapter != null) {
                destinationEditText.setOnItemClickListener((parent, view, position, id) -> {
                    AutocompletePrediction prediction = destinationAdapter.getItem(position);
                    if (prediction != null) {
                        fetchPlaceAndUpdateDestination(prediction);
                    }
                });
            }

            Log.d(TAG, "Listeners configurados correctamente");
        } catch (Exception e) {
            Log.e(TAG, "Error configurando listeners", e);
        }
    }

    private void toggleSearchPanel() {
        if (isSearchPanelVisible) {
            hideSearchPanel();
        } else {
            showSearchPanel();
        }
    }

    private void hideSearchPanel() {
        if (searchPanelContainer != null) {
            searchPanelContainer.setVisibility(View.GONE);
            isSearchPanelVisible = false;

            if (toggleSearchPanelButton != null) {
                toggleSearchPanelButton.setImageResource(R.drawable.ic_arrow_down);
            }
        }
    }

    private void showSearchPanel() {
        if (searchPanelContainer != null) {
            searchPanelContainer.setVisibility(View.VISIBLE);
            isSearchPanelVisible = true;

            if (toggleSearchPanelButton != null) {
                toggleSearchPanelButton.setImageResource(R.drawable.ic_arrow_up);
            }
        }
    }

    private void fetchPlaceAndUpdateOrigin(AutocompletePrediction prediction) {
        if (prediction == null || prediction.getPlaceId() == null || placesClient == null) return;

        try {
            List<Place.Field> placeFields = Arrays.asList(Place.Field.LAT_LNG, Place.Field.ADDRESS);
            FetchPlaceRequest request = FetchPlaceRequest.builder(prediction.getPlaceId(), placeFields).build();

            placesClient.fetchPlace(request).addOnSuccessListener((response) -> {
                Place place = response.getPlace();
                if (place.getLatLng() != null) {
                    originLatLng = place.getLatLng();
                    originAddress = place.getAddress();
                    updateMapWithMarkers();
                }
            }).addOnFailureListener((exception) -> {
                Log.e(TAG, "Error obteniendo detalles del lugar", exception);
                Toast.makeText(RequestServiceActivity.this,
                        "Error al obtener el lugar seleccionado", Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) {
            Log.e(TAG, "Error en fetchPlaceAndUpdateOrigin", e);
        }
    }

    private void fetchPlaceAndUpdateDestination(AutocompletePrediction prediction) {
        if (prediction == null || prediction.getPlaceId() == null || placesClient == null) return;

        try {
            List<Place.Field> placeFields = Arrays.asList(Place.Field.LAT_LNG, Place.Field.ADDRESS);
            FetchPlaceRequest request = FetchPlaceRequest.builder(prediction.getPlaceId(), placeFields).build();

            placesClient.fetchPlace(request).addOnSuccessListener((response) -> {
                Place place = response.getPlace();
                if (place.getLatLng() != null) {
                    destinationLatLng = place.getLatLng();
                    destinationAddress = place.getAddress();
                    updateMapWithMarkers();
                }
            }).addOnFailureListener((exception) -> {
                Log.e(TAG, "Error obteniendo detalles del lugar", exception);
                Toast.makeText(RequestServiceActivity.this,
                        "Error al obtener el lugar seleccionado", Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) {
            Log.e(TAG, "Error en fetchPlaceAndUpdateDestination", e);
        }
    }

    private void checkLocationPermissions() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                    PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        LOCATION_PERMISSION_REQUEST_CODE);
            } else {
                // Ya tenemos permiso, proceder a obtener la ubicación
                getLastLocation();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error verificando permisos de ubicación", e);
            Toast.makeText(this, "Error al verificar permisos", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        try {
            Log.d(TAG, "Mapa listo");
            mMap = googleMap;

            // Configurar mapa
            mMap.getUiSettings().setZoomControlsEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);

            // Habilitar mi ubicación si hay permiso
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED) {
                mMap.setMyLocationEnabled(true);
                getLastLocation();
            }

            // Añadir listener para clicks largos en el mapa
            mMap.setOnMapLongClickListener(latLng -> showAddressSelectionDialog(latLng));

        } catch (Exception e) {
            Log.e(TAG, "Error en onMapReady", e);
            Toast.makeText(this, "Error al inicializar el mapa: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Método para mostrar el diálogo de selección
    private void showAddressSelectionDialog(final LatLng latLng) {
        if (latLng == null) return;

        try {
            // Convertir coordenadas a dirección
            getAddressFromLocation(latLng.latitude, latLng.longitude, address -> {
                if (isFinishing() || isDestroyed()) return;

                // Crear y mostrar diálogo
                AlertDialog.Builder builder = new AlertDialog.Builder(RequestServiceActivity.this);
                builder.setTitle("Seleccionar como")
                        .setMessage("¿Quieres usar este lugar (" + address + ") como origen o destino?")
                        .setPositiveButton("Destino", (dialog, which) -> {
                            // Establecer como destino
                            destinationLatLng = latLng;
                            destinationAddress = address;
                            if (destinationEditText != null) {
                                destinationEditText.setText(address);
                            }
                            updateMapWithMarkers();
                        })
                        .setNegativeButton("Origen", (dialog, which) -> {
                            // Establecer como origen
                            originLatLng = latLng;
                            originAddress = address;
                            if (pickupLocationEditText != null) {
                                pickupLocationEditText.setText(address);
                            }
                            updateMapWithMarkers();
                        })
                        .setNeutralButton("Cancelar", (dialog, which) -> dialog.dismiss())
                        .show();
            });
        } catch (Exception e) {
            Log.e(TAG, "Error mostrando diálogo de selección", e);
            Toast.makeText(this, "Error al seleccionar ubicación", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        try {
            if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (mMap != null) {
                        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                                == PackageManager.PERMISSION_GRANTED) {
                            mMap.setMyLocationEnabled(true);
                        }
                    }
                    getLastLocation();
                } else {
                    Toast.makeText(this, "Se requiere permiso de ubicación para solicitar servicios",
                            Toast.LENGTH_LONG).show();
                    finish();  // Sin permiso no podemos hacer nada, cerramos la actividad
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error en manejo de permisos", e);
        }
    }

    private void getLastLocation() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED && fusedLocationClient != null) {

                fusedLocationClient.getLastLocation()
                        .addOnSuccessListener(this, location -> {
                            if (location != null) {
                                Log.d(TAG, "Ubicación obtenida: " + location.getLatitude() + ", " + location.getLongitude());
                                currentLocation = location;
                                originLatLng = new LatLng(location.getLatitude(), location.getLongitude());

                                // Obtener dirección de la ubicación actual
                                getAddressFromLocation(location.getLatitude(), location.getLongitude(), address -> {
                                    originAddress = address;
                                    if (pickupLocationEditText != null && !isFinishing()) {
                                        pickupLocationEditText.setText(address);
                                    }
                                });

                                // Actualizar mapa con la ubicación obtenida
                                if (mMap != null) {
                                    updateMapWithCurrentLocation();
                                }

                                // Ahora que tenemos ubicación, es buen momento para inicializar Places si no se hizo antes
                                if (!placesInitialized) {
                                    initializePlacesAPI();
                                }
                            } else {
                                Log.w(TAG, "getLastLocation devolvió ubicación nula");
                                requestLocationUpdates();
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error obteniendo ubicación", e);
                            Toast.makeText(RequestServiceActivity.this,
                                    "Error al obtener ubicación: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                            requestLocationUpdates();
                        });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error en getLastLocation", e);
            Toast.makeText(this, "Error al obtener ubicación", Toast.LENGTH_SHORT).show();
            requestLocationUpdates();  // Intentar alternativa
        }
    }

    private void requestLocationUpdates() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED && fusedLocationClient != null) {

                // Crear solicitud de ubicación con alta precisión
                LocationRequest locationRequest = LocationRequest.create()
                        .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                        .setInterval(5000)
                        .setFastestInterval(2000)
                        .setNumUpdates(1);

                // Crear callback para recibir ubicación
                LocationCallback locationCallback = new LocationCallback() {
                    @Override
                    public void onLocationResult(@NonNull LocationResult locationResult) {
                        for (Location location : locationResult.getLocations()) {
                            if (isDestroyed() || isFinishing()) return;

                            // Usar primera ubicación recibida
                            currentLocation = location;
                            originLatLng = new LatLng(location.getLatitude(), location.getLongitude());

                            // Obtener dirección
                            getAddressFromLocation(location.getLatitude(), location.getLongitude(), address -> {
                                originAddress = address;
                                if (pickupLocationEditText != null && !isFinishing()) {
                                    pickupLocationEditText.setText(address);
                                }
                            });

                            // Actualizar mapa
                            if (mMap != null) {
                                updateMapWithCurrentLocation();
                            }

                            // Solo usamos la primera ubicación
                            break;
                        }

                        // Dejar de recibir actualizaciones
                        fusedLocationClient.removeLocationUpdates(this);
                    }
                };

                // Solicitar actualizaciones
                fusedLocationClient.requestLocationUpdates(
                        locationRequest, locationCallback, Looper.getMainLooper());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error solicitando actualizaciones de ubicación", e);
            Toast.makeText(this, "Error al actualizar ubicación", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateMapWithCurrentLocation() {
        try {
            if (currentLocation == null || mMap == null) return;

            LatLng latLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());

            // Mover cámara a la ubicación
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f));

            // Actualizar marcadores si es necesario
            updateMapWithMarkers();

        } catch (Exception e) {
            Log.e(TAG, "Error actualizando mapa con ubicación actual", e);
        }
    }

    private void updateMapWithMarkers() {
        try {
            if (mMap == null) return;

            // Limpiar mapa
            mMap.clear();

            // Añadir marcador para el origen si existe
            if (originLatLng != null) {
                mMap.addMarker(new MarkerOptions()
                        .position(originLatLng)
                        .title("Origen")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
            }

            // Añadir marcador para el destino si existe
            if (destinationLatLng != null) {
                mMap.addMarker(new MarkerOptions()
                        .position(destinationLatLng)
                        .title("Destino")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

                // Si tenemos origen y destino, ajustar cámara para mostrar ambos puntos
                if (originLatLng != null) {
                    try {
                        LatLngBounds.Builder builder = new LatLngBounds.Builder();
                        builder.include(originLatLng);
                        builder.include(destinationLatLng);

                        // Añadir padding
                        int padding = 100; // en píxeles
                        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), padding));
                    } catch (Exception e) {
                        Log.e(TAG, "Error ajustando cámara para mostrar origen y destino", e);
                        // Alternativa si falla el ajuste automático
                        LatLng center = new LatLng(
                                (originLatLng.latitude + destinationLatLng.latitude) / 2,
                                (originLatLng.longitude + destinationLatLng.longitude) / 2);
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 13f));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error actualizando marcadores en el mapa", e);
        }
    }

    private void getAddressFromLocation(double latitude, double longitude, AddressCallback callback) {
        try {
            if (Geocoder.isPresent()) {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());

                try {
                    List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);

                    if (addresses != null && !addresses.isEmpty()) {
                        Address address = addresses.get(0);
                        StringBuilder sb = new StringBuilder();

                        // Obtener la dirección de la calle
                        for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                            if (i > 0) sb.append(", ");
                            sb.append(address.getAddressLine(i));
                        }

                        callback.onAddressFound(sb.toString());
                    } else {
                        callback.onAddressFound("Dirección desconocida");
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error obteniendo dirección", e);
                    callback.onAddressFound("Dirección desconocida");
                }
            } else {
                // Geocoder no disponible
                callback.onAddressFound("Geocoder no disponible");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error en getAddressFromLocation", e);
            callback.onAddressFound("Error al obtener dirección");
        }
    }

    // Interfaz para manejar callback de geocoding
    interface AddressCallback {
        void onAddressFound(String address);
    }

    private void calculateRoute() {
        try {
            // Validar que tengamos origen y destino
            boolean originMissing = originLatLng == null || originAddress == null || originAddress.isEmpty();
            boolean destinationMissing = destinationLatLng == null || destinationAddress == null || destinationAddress.isEmpty();

            if (originMissing && destinationMissing) {
                Toast.makeText(this, "Por favor selecciona origen y destino", Toast.LENGTH_SHORT).show();
                return;
            } else if (originMissing) {
                Toast.makeText(this, "Por favor selecciona un origen", Toast.LENGTH_SHORT).show();
                return;
            } else if (destinationMissing) {
                Toast.makeText(this, "Por favor selecciona un destino", Toast.LENGTH_SHORT).show();
                return;
            }

            // Verificar que GeoApiContext esté inicializado
            if (geoApiContext == null) {
                Toast.makeText(this, "Error: Servicio de mapas no disponible", Toast.LENGTH_SHORT).show();
                return;
            }

            // Mostrar progreso
            showProgress(true);

            // Preparar solicitud de direcciones
            DirectionsApiRequest request = DirectionsApi.getDirections(geoApiContext,
                    originLatLng.latitude + "," + originLatLng.longitude,
                    destinationLatLng.latitude + "," + destinationLatLng.longitude);

            request.mode(TravelMode.DRIVING);
            request.language("es"); // Indicaciones en español
            request.units(com.google.maps.model.Unit.METRIC);
            request.region("co"); // Colombia

            // Ejecutar solicitud de manera asíncrona
            request.setCallback(new PendingResult.Callback<DirectionsResult>() {
                @Override
                public void onResult(DirectionsResult result) {
                    // Procesar resultado en el hilo principal
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;

                        directionsResult = result;
                        showProgress(false);

                        if (result != null && result.routes != null && result.routes.length > 0) {
                            drawRoute(result.routes[0]);
                            updateRouteInfo(result.routes[0]);
                            showServiceInfoPanel();
                        } else {
                            Toast.makeText(RequestServiceActivity.this,
                                    "No se pudo calcular la ruta entre estos puntos",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onFailure(Throwable e) {
                    // Manejar error en el hilo principal
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;

                        showProgress(false);
                        Toast.makeText(RequestServiceActivity.this,
                                "Error calculando la ruta: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Error calculando ruta", e);
                    });
                }
            });

        } catch (Exception e) {
            showProgress(false);
            Log.e(TAG, "Error en calculateRoute", e);
            Toast.makeText(this, "Error calculando la ruta: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void drawRoute(DirectionsRoute route) {
        try {
            if (mMap == null || route == null || route.overviewPolyline == null) return;

            // Limpiar mapa previo
            mMap.clear();

            // Decodificar polyline
            List<LatLng> path = new ArrayList<>();
            for (com.google.maps.model.LatLng point : route.overviewPolyline.decodePath()) {
                path.add(new LatLng(point.lat, point.lng));
            }

            // Dibujar polyline
            PolylineOptions polylineOptions = new PolylineOptions()
                    .addAll(path)
                    .width(10)
                    .color(Color.BLUE);

            mMap.addPolyline(polylineOptions);

            // Añadir marcadores de origen y destino
            if (originLatLng != null) {
                mMap.addMarker(new MarkerOptions()
                        .position(originLatLng)
                        .title("Origen")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
            }

            if (destinationLatLng != null) {
                mMap.addMarker(new MarkerOptions()
                        .position(destinationLatLng)
                        .title("Destino")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
            }

            // Ajustar la cámara para mostrar toda la ruta
            if (!path.isEmpty()) {
                try {
                    LatLngBounds.Builder builder = new LatLngBounds.Builder();
                    for (LatLng point : path) {
                        builder.include(point);
                    }

                    LatLngBounds bounds = builder.build();
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
                } catch (Exception e) {
                    Log.e(TAG, "Error ajustando cámara a la ruta", e);

                    // Alternativa: centrar en punto medio entre origen y destino
                    if (originLatLng != null && destinationLatLng != null) {
                        LatLng center = new LatLng(
                                (originLatLng.latitude + destinationLatLng.latitude) / 2,
                                (originLatLng.longitude + destinationLatLng.longitude) / 2);
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 12));
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error dibujando ruta", e);
            Toast.makeText(this, "Error al mostrar la ruta en el mapa", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateRouteInfo(DirectionsRoute route) {
        try {
            // Validar datos
            if (route == null || route.legs == null || route.legs.length == 0 ||
                    distanceTextView == null || durationTextView == null || priceTextView == null) {
                return;
            }

            // Obtener distancia en metros y convertir a kilómetros
            long distanceInMeters = route.legs[0].distance.inMeters;
            distanceInKm = distanceInMeters / 1000.0;

            // Obtener duración en segundos y convertir a minutos
            long durationInSeconds = route.legs[0].duration.inSeconds;
            durationInMinutes = (int) Math.ceil(durationInSeconds / 60.0);

            // Calcular precio estimado
            calculatePrice(distanceInKm, durationInMinutes);

            // Actualizar UI
            distanceTextView.setText(String.format(Locale.getDefault(), "%.1f km", distanceInKm));
            durationTextView.setText(String.format(Locale.getDefault(), "%d min", durationInMinutes));

            DecimalFormat format = new DecimalFormat("#,###");
            priceTextView.setText("$" + format.format(estimatedPrice));

        } catch (Exception e) {
            Log.e(TAG, "Error actualizando información de la ruta", e);
        }
    }

    private void calculatePrice(double distanceKm, int durationMinutes) {
        try {
            // Calcular precio basado en tarifa base + precio por km + precio por minuto
            double price = BASE_FARE + (distanceKm * PRICE_PER_KM) + (durationMinutes * PRICE_PER_MINUTE);

            // Aplicar un descuento del 15% para hacer el servicio más competitivo
            double discount = 0.15;
            price = price * (1 - discount);

            // Redondear a los 100 pesos más cercanos para facilitar el pago
            price = Math.ceil(price / 100) * 100;

            estimatedPrice = price;

            Log.d(TAG, "Precio calculado: " + estimatedPrice +
                    " (Distancia: " + distanceKm + " km, Duración: " + durationInMinutes + " min)");

        } catch (Exception e) {
            Log.e(TAG, "Error calculando precio", e);
            estimatedPrice = 0;
        }
    }

    private void showServiceInfoPanel() {
        try {
            if (serviceInfoCardView != null) {
                serviceInfoCardView.setVisibility(View.VISIBLE);

                // Mostrar botón de confirmar y ocultar botón de cancelar
                if (confirmServiceButton != null) confirmServiceButton.setVisibility(View.VISIBLE);
                if (cancelServiceButton != null) cancelServiceButton.setVisibility(View.GONE);

                // Ocultar información del conductor
                if (driverInfoLayout != null) driverInfoLayout.setVisibility(View.GONE);
                if (driverLoadingProgressBar != null) driverLoadingProgressBar.setVisibility(View.GONE);

                // Ocultar panel de búsqueda para dar más espacio a la visualización del mapa
                hideSearchPanel();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error mostrando panel de información del servicio", e);
        }
    }

    private void confirmService() {
        try {
            // Validar que tengamos la información necesaria
            if (directionsResult == null || estimatedPrice <= 0) {
                Toast.makeText(this, "No se pudo confirmar el servicio. Por favor, vuelve a calcular la ruta.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            // Verificar autenticación
            FirebaseUser currentUser = auth.getCurrentUser();
            if (currentUser == null || db == null) {
                Toast.makeText(this, "Error de autenticación. Por favor, vuelve a iniciar sesión.",
                        Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
                return;
            }

            // Mostrar progreso
            showProgress(true);

            // Crear datos del servicio
            Map<String, Object> serviceData = new HashMap<>();
            serviceData.put("passengerId", currentUser.getUid());
            serviceData.put("status", "requested");
            serviceData.put("pickupLocation", originAddress);
            serviceData.put("destination", destinationAddress);
            serviceData.put("distanceKm", distanceInKm);
            serviceData.put("durationMinutes", durationInMinutes);
            serviceData.put("estimatedPrice", estimatedPrice);
            serviceData.put("timestamp", System.currentTimeMillis());
            serviceData.put("rejectedByDrivers", new ArrayList<String>()); // Lista vacía para conductores que rechacen

            // Añadir coordenadas de recogida si están disponibles
            if (originLatLng != null) {
                serviceData.put("pickupCoordinates", new GeoPoint(
                        originLatLng.latitude,
                        originLatLng.longitude));
            }

            // Añadir coordenadas de destino si están disponibles
            if (destinationLatLng != null) {
                serviceData.put("destinationCoordinates", new GeoPoint(
                        destinationLatLng.latitude,
                        destinationLatLng.longitude));
            }

            // Guardar en Firestore
            db.collection("services")
                    .add(serviceData)
                    .addOnSuccessListener(documentReference -> {
                        if (isFinishing() || isDestroyed()) return;

                        Log.d(TAG, "Servicio solicitado con ID: " + documentReference.getId());
                        showProgress(false);

                        // Guardar ID del servicio
                        serviceId = documentReference.getId();
                        serviceConfirmed = true;

                        // Actualizar UI para mostrar estado de espera de conductor
                        updateUIForWaitingDriver();

                        // Escuchar actualizaciones del servicio (para cuando un conductor lo acepte)
                        listenForServiceUpdates();

                        // Simulación de conductor aceptando el servicio (para fines de demostración)
                        simulateDriverAccepting();
                    })
                    .addOnFailureListener(e -> {
                        if (isFinishing() || isDestroyed()) return;

                        Log.e(TAG, "Error al solicitar servicio: " + e.getMessage(), e);
                        showProgress(false);
                        Toast.makeText(this, "Error al solicitar servicio: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });

        } catch (Exception e) {
            showProgress(false);
            Log.e(TAG, "Error en confirmService", e);
            Toast.makeText(this, "Error al confirmar el servicio: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUIForWaitingDriver() {
        try {
            // Ocultar botón de confirmar
            if (confirmServiceButton != null) {
                confirmServiceButton.setVisibility(View.GONE);
            }

            // Ocultar panel de búsqueda
            hideSearchPanel();

            // Mostrar botón de cancelar
            if (cancelServiceButton != null) {
                cancelServiceButton.setVisibility(View.VISIBLE);
            }

            // Mostrar loader para esperar conductor
            if (driverLoadingProgressBar != null) {
                driverLoadingProgressBar.setVisibility(View.VISIBLE);
            }

            // Mostrar layout de información de conductor (con mensaje de espera)
            if (driverInfoLayout != null) {
                driverInfoLayout.setVisibility(View.VISIBLE);
                if (driverNameTextView != null) {
                    driverNameTextView.setText("Buscando conductor...");
                }
                if (vehicleInfoTextView != null) {
                    vehicleInfoTextView.setText("");
                }
                if (arrivalTimeTextView != null) {
                    arrivalTimeTextView.setText("");
                }
            }

            // Deshabilitar campos de entrada
            if (pickupLocationEditText != null) pickupLocationEditText.setEnabled(false);
            if (destinationEditText != null) destinationEditText.setEnabled(false);
            if (calculateRouteButton != null) calculateRouteButton.setEnabled(false);

        } catch (Exception e) {
            Log.e(TAG, "Error actualizando UI para espera de conductor", e);
        }
    }

    private void listenForServiceUpdates() {
        try {
            if (serviceId == null || db == null) return;

            // Detener listener anterior si existe
            if (serviceListener != null) {
                serviceListener.remove();
            }

            // Crear referencia al documento del servicio
            DocumentReference serviceRef = db.collection("services").document(serviceId);

            // Añadir listener en tiempo real
            serviceListener = serviceRef.addSnapshotListener((documentSnapshot, e) -> {
                if (e != null) {
                    Log.e(TAG, "Error escuchando cambios en el servicio", e);
                    return;
                }

                if (documentSnapshot != null && documentSnapshot.exists()) {
                    // Obtener estado del servicio
                    String status = documentSnapshot.getString("status");

                    // Si el servicio ha sido aceptado por un conductor
                    if ("accepted".equals(status)) {
                        // Obtener ID del conductor
                        String driverId = documentSnapshot.getString("driverId");

                        // Obtener información del conductor
                        if (driverId != null) {
                            fetchDriverInfo(driverId);
                        }
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error configurando listener de actualizaciones del servicio", e);
        }
    }

    private void fetchDriverInfo(String driverId) {
        try {
            if (db == null || isFinishing() || isDestroyed()) return;

            // Intentar primero obtener del perfil
            db.collection("profiles").document(driverId).get()
                    .addOnSuccessListener(profileDoc -> {
                        if (profileDoc.exists()) {
                            // Obtener nombre del conductor del perfil
                            String nombre = profileDoc.getString("nombre");
                            String apellido = profileDoc.getString("apellido");
                            String driverName = "";

                            if (nombre != null && !nombre.isEmpty()) {
                                driverName = nombre;
                                if (apellido != null && !apellido.isEmpty()) {
                                    driverName += " " + apellido;
                                }
                            } else {
                                driverName = "Conductor";
                            }

                            // Obtener información del vehículo si existe
                            fetchVehicleInfo(driverId, driverName);
                        } else {
                            // Si no hay perfil, buscar en colección de conductores
                            fetchFromDriversCollection(driverId);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error obteniendo información del conductor", e);
                        fetchFromDriversCollection(driverId);
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error en fetchDriverInfo", e);
        }
    }

    private void fetchFromDriversCollection(String driverId) {
        try {
            if (db == null || isFinishing() || isDestroyed()) return;

            db.collection("drivers").document(driverId).get()
                    .addOnSuccessListener(driverDoc -> {
                        if (driverDoc.exists()) {
                            // Obtener nombre del conductor
                            String driverName = driverDoc.getString("name");
                            if (driverName == null) driverName = "Conductor";

                            // Obtener información del vehículo
                            Map<String, Object> vehicleInfo = (Map<String, Object>) driverDoc.get("vehicleInfo");
                            String vehicleModel = "";
                            String vehiclePlate = "";

                            if (vehicleInfo != null) {
                                if (vehicleInfo.get("model") != null) {
                                    vehicleModel = (String) vehicleInfo.get("model");
                                }
                                if (vehicleInfo.get("plate") != null) {
                                    vehiclePlate = (String) vehicleInfo.get("plate");
                                }
                            }

                            // Actualizar UI con información del conductor
                            updateUIWithDriverInfo(driverName, vehicleModel, vehiclePlate);
                        } else {
                            // No hay información, mostrar valores por defecto
                            updateUIWithDriverInfo("Conductor", "", "");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error obteniendo información del conductor", e);
                        updateUIWithDriverInfo("Conductor", "", "");
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error en fetchFromDriversCollection", e);
        }
    }

    private void fetchVehicleInfo(String driverId, String driverName) {
        try {
            if (db == null || isFinishing() || isDestroyed()) return;

            db.collection("vehiculos").whereEqualTo("conductorId", driverId).limit(1).get()
                    .addOnSuccessListener(vehicleDocs -> {
                        if (!vehicleDocs.isEmpty()) {
                            DocumentSnapshot vehicleDoc = vehicleDocs.getDocuments().get(0);
                            String marca = vehicleDoc.getString("marca");
                            String modelo = vehicleDoc.getString("modelo");
                            String placa = vehicleDoc.getString("placa");

                            String vehicleModel = "";
                            if (marca != null && !marca.isEmpty()) {
                                vehicleModel = marca;
                                if (modelo != null && !modelo.isEmpty()) {
                                    vehicleModel += " " + modelo;
                                }
                            }

                            updateUIWithDriverInfo(driverName, vehicleModel, placa != null ? placa : "");
                        } else {
                            // No hay info de vehículo
                            updateUIWithDriverInfo(driverName, "", "");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error obteniendo información del vehículo", e);
                        updateUIWithDriverInfo(driverName, "", "");
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error en fetchVehicleInfo", e);
            updateUIWithDriverInfo(driverName, "", "");
        }
    }

    private void updateUIWithDriverInfo(String driverName, String vehicleModel, String vehiclePlate) {
        try {
            if (isFinishing() || isDestroyed()) return;

            // Ocultar loader
            if (driverLoadingProgressBar != null) {
                driverLoadingProgressBar.setVisibility(View.GONE);
            }

            // Actualizar información del conductor
            if (driverNameTextView != null) {
                driverNameTextView.setText(driverName);
            }

            if (vehicleInfoTextView != null) {
                if (!vehicleModel.isEmpty() && !vehiclePlate.isEmpty()) {
                    vehicleInfoTextView.setText(vehicleModel + " - " + vehiclePlate);
                } else if (!vehiclePlate.isEmpty()) {
                    vehicleInfoTextView.setText("Placa: " + vehiclePlate);
                } else if (!vehicleModel.isEmpty()) {
                    vehicleInfoTextView.setText(vehicleModel);
                } else {
                    vehicleInfoTextView.setText("");
                }
            }

            // Calcular tiempo estimado de llegada (simulado)
            if (arrivalTimeTextView != null) {
                // Simular un tiempo entre 3 y 8 minutos
                int arrivalTime = 3 + new Random().nextInt(6);
                arrivalTimeTextView.setText("Llegará en aproximadamente " + arrivalTime + " minutos");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error actualizando UI con información del conductor", e);
        }
    }

    // Para fines de demostración, simular que un conductor acepta el servicio
    private void simulateDriverAccepting() {
        try {
            if (!serviceConfirmed || serviceId == null || db == null) return;

            // Simular un retraso antes de que un conductor acepte (entre 3 y 6 segundos)
            new Handler().postDelayed(() -> {
                if (isFinishing() || isDestroyed()) return;

                // Generar un ID de conductor aleatorio (en producción, sería un conductor real)
                String simulatedDriverId = "driver_" + (100 + new Random().nextInt(900));

                // Información del vehículo simulada
                String[] vehicleModels = {"Toyota Corolla", "Kia Rio", "Chevrolet Spark", "Nissan Versa", "Renault Logan"};
                String vehicleModel = vehicleModels[new Random().nextInt(vehicleModels.length)];

                // Generar placa aleatoria (formato colombiano)
                String plate = "ABC" + (100 + new Random().nextInt(900));

                // Crear documento del conductor simulado
                Map<String, Object> driverData = new HashMap<>();
                driverData.put("name", "Conductor Simulado");
                driverData.put("phone", "300" + (1000000 + new Random().nextInt(9000000)));
                driverData.put("rating", 4.5 + (new Random().nextDouble() / 2)); // Rating entre 4.5 y 5.0

                Map<String, Object> vehicleInfo = new HashMap<>();
                vehicleInfo.put("model", vehicleModel);
                vehicleInfo.put("plate", plate);
                vehicleInfo.put("color", "Blanco");
                driverData.put("vehicleInfo", vehicleInfo);

                // Guardar información del conductor
                db.collection("drivers").document(simulatedDriverId).set(driverData)
                        .addOnSuccessListener(aVoid -> {
                            if (isFinishing() || isDestroyed()) return;

                            // Actualizar el servicio para indicar que ha sido aceptado
                            db.collection("services").document(serviceId)
                                    .update(
                                            "status", "accepted",
                                            "driverId", simulatedDriverId,
                                            "acceptedAt", System.currentTimeMillis()
                                    )
                                    .addOnSuccessListener(aVoid2 -> {
                                        Log.d(TAG, "Servicio aceptado por conductor simulado: " + simulatedDriverId);
                                        // La actualización de la UI se manejará a través del listener
                                    })
                                    .addOnFailureListener(e -> Log.e(TAG, "Error actualizando servicio con conductor simulado", e));
                        })
                        .addOnFailureListener(e -> Log.e(TAG, "Error guardando conductor simulado", e));

            }, 3000 + new Random().nextInt(3000));

        } catch (Exception e) {
            Log.e(TAG, "Error en simulateDriverAccepting", e);
        }
    }

    private void cancelService() {
        try {
            if (!serviceConfirmed || serviceId == null || db == null) {
                // Si no hay servicio confirmado, simplemente volver a la pantalla anterior
                finish();
                return;
            }

            // Mostrar diálogo de confirmación
            new AlertDialog.Builder(this)
                    .setTitle("Cancelar Servicio")
                    .setMessage("¿Estás seguro de que deseas cancelar este servicio?")
                    .setPositiveButton("Sí", (dialog, which) -> {
                        // Mostrar progreso
                        showProgress(true);

                        // Actualizar servicio en Firestore
                        db.collection("services").document(serviceId)
                                .update(
                                        "status", "cancelled",
                                        "cancelledByPassenger", true,
                                        "cancelledAt", System.currentTimeMillis()
                                )
                                .addOnSuccessListener(aVoid -> {
                                    if (isFinishing() || isDestroyed()) return;

                                    Log.d(TAG, "Servicio cancelado correctamente");
                                    showProgress(false);
                                    Toast.makeText(this, "Servicio cancelado", Toast.LENGTH_SHORT).show();
                                    finish();
                                })
                                .addOnFailureListener(e -> {
                                    if (isFinishing() || isDestroyed()) return;

                                    Log.e(TAG, "Error cancelando servicio", e);
                                    showProgress(false);
                                    Toast.makeText(this, "Error al cancelar servicio: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                    })
                    .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                    .show();

        } catch (Exception e) {
            Log.e(TAG, "Error en cancelService", e);
            Toast.makeText(this, "Error al cancelar servicio", Toast.LENGTH_SHORT).show();
        }
    }

    private void showProgress(boolean show) {
        try {
            if (progressBar != null) {
                progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error mostrando/ocultando barra de progreso", e);
        }
    }

    private void showErrorMessage(String message) {
        try {
            if (errorTextView != null) {
                errorTextView.setText(message);
                errorTextView.setVisibility(View.VISIBLE);
            } else {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }

            // Log del error para depuración
            Log.e(TAG, "Error mostrado al usuario: " + message);
        } catch (Exception e) {
            Log.e(TAG, "Error mostrando mensaje de error", e);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    private void createLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }

                for (Location location : locationResult.getLocations()) {
                    if (isDestroyed() || isFinishing()) return;

                    // Actualizar ubicación actual
                    currentLocation = location;
                    if (originLatLng == null) {
                        originLatLng = new LatLng(location.getLatitude(), location.getLongitude());

                        // Obtener dirección si no tenemos una
                        if (originAddress == null || originAddress.isEmpty()) {
                            getAddressFromLocation(location.getLatitude(), location.getLongitude(), address -> {
                                originAddress = address;
                                if (pickupLocationEditText != null && !isFinishing()) {
                                    pickupLocationEditText.setText(address);
                                }
                            });
                        }
                    }

                    // Actualizar mapa
                    if (mMap != null) {
                        updateMapWithCurrentLocation();
                    }

                    break;
                }
            }
        };
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Detener actualizaciones de ubicación si estamos en pausa
        if (fusedLocationClient != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reanudar actualizaciones de ubicación
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED && fusedLocationClient != null) {
            // Crear solicitud de ubicación con precisión equilibrada
            LocationRequest locationRequest = LocationRequest.create()
                    .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                    .setInterval(30000); // 30 segundos

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
    }

    @Override
    protected void onDestroy() {
        try {
            // Eliminar listener de actualizaciones del servicio si existe
            if (serviceListener != null) {
                serviceListener.remove();
                serviceListener = null;
            }

            // Liberar contexto de GeoApi
            if (geoApiContext != null) {
                geoApiContext.shutdown();
                geoApiContext = null;
            }

            // Detener actualizaciones de ubicación
            if (fusedLocationClient != null) {
                fusedLocationClient.removeLocationUpdates(locationCallback);
            }

            // Eliminar cualquier Handler pendiente
            if (new Handler().hasMessages(0)) {
                new Handler().removeCallbacksAndMessages(null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error en onDestroy", e);
        }

        super.onDestroy();
    }
}