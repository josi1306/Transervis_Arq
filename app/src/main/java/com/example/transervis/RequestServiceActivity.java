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
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
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
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
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

    // UI Elements
    private AutoCompleteTextView pickupLocationEditText, destinationEditText;
    private Button calculateRouteButton, confirmServiceButton, cancelServiceButton;
    private TextView distanceTextView, durationTextView, priceTextView;
    private TextView driverNameTextView, vehicleInfoTextView, arrivalTimeTextView;
    private LinearLayout driverInfoLayout;
    private ProgressBar progressBar, driverLoadingProgressBar;
    private CardView serviceInfoCardView;
    private TextView errorTextView;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_request_service);

            Log.d(TAG, "Iniciando RequestServiceActivity");

            // Inicializar Firebase Auth con manejo de excepciones
            try {
                auth = FirebaseAuth.getInstance();

                // Verificar si el usuario está autenticado
                if (auth.getCurrentUser() == null) {
                    Toast.makeText(this, "Sesión expirada, por favor inicia sesión nuevamente", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error inicializando Firebase Auth", e);
                Toast.makeText(this, "Error de autenticación: " + e.getMessage(), Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            // Inicializar FirebaseFirestore con manejo de excepciones
            try {
                db = FirebaseFirestore.getInstance();
            } catch (Exception e) {
                Log.e(TAG, "Error inicializando Firestore", e);
                Toast.makeText(this, "Error al conectar con la base de datos: " + e.getMessage(), Toast.LENGTH_LONG).show();
                // Continuar sin Firestore, otras funcionalidades pueden seguir trabajando
            }

            // Inicializar vistas con manejo de excepciones
            try {
                initializeViews();
            } catch (Exception e) {
                Log.e(TAG, "Error inicializando vistas", e);
                Toast.makeText(this, "Error al inicializar la interfaz: " + e.getMessage(), Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            // Configurar listeners con manejo de excepciones
            try {
                setupListeners();
            } catch (Exception e) {
                Log.e(TAG, "Error configurando listeners", e);
                Toast.makeText(this, "Error configurando la interfaz: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }

            // Inicializar servicios de ubicación con manejo de excepciones
            try {
                fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            } catch (Exception e) {
                Log.e(TAG, "Error inicializando servicios de ubicación", e);
                Toast.makeText(this, "Error al inicializar servicios de ubicación: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }

            // Verificar permisos de ubicación
            checkLocationPermissions();

            // Inicializar Places API con manejo de excepciones
            try {
                if (!Places.isInitialized()) {
                    Places.initialize(getApplicationContext(), getString(R.string.google_maps_key));
                }
                placesClient = Places.createClient(this);

                // Configurar adaptadores de autocompletado
                pickupAdapter = new PlacesAutoCompleteAdapter(this, placesClient);
                destinationAdapter = new PlacesAutoCompleteAdapter(this, placesClient);

                if (pickupLocationEditText != null) {
                    pickupLocationEditText.setAdapter(pickupAdapter);
                }

                if (destinationEditText != null) {
                    destinationEditText.setAdapter(destinationAdapter);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error inicializando Places API", e);
                Toast.makeText(this, "Error al inicializar servicios de mapas: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }

            // Inicializar mapa con manejo de excepciones
            try {
                // IMPORTANTE: Verificar que el ID del fragmento sea correcto
                SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.mapFragment);

                if (mapFragment == null) {
                    Log.e(TAG, "Error: mapFragment es nulo. Verificar ID en XML.");
                    Toast.makeText(this, "Error al inicializar el mapa. Contacte al soporte.", Toast.LENGTH_LONG).show();
                } else {
                    mapFragment.getMapAsync(this);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error inicializando el mapa", e);
                Toast.makeText(this, "Error al inicializar el mapa: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }

            // Inicializar GeoApiContext para Directions API
            try {
                geoApiContext = new GeoApiContext.Builder()
                        .apiKey(getString(R.string.google_maps_key))
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .writeTimeout(10, TimeUnit.SECONDS)
                        .build();
            } catch (Exception e) {
                Log.e(TAG, "Error inicializando GeoApiContext", e);
                Toast.makeText(this, "Error inicializando servicios de rutas: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            // Capturar cualquier excepción no controlada en onCreate
            Log.e(TAG, "Error general en onCreate", e);
            Toast.makeText(this, "Ha ocurrido un error inesperado: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void initializeViews() {
        try {
            // Inicializar todas las vistas
            pickupLocationEditText = findViewById(R.id.pickupLocationEditText);
            destinationEditText = findViewById(R.id.destinationEditText);
            calculateRouteButton = findViewById(R.id.calculateRouteButton);
            confirmServiceButton = findViewById(R.id.confirmServiceButton);
            cancelServiceButton = findViewById(R.id.cancelServiceButton);
            distanceTextView = findViewById(R.id.distanceTextView);
            durationTextView = findViewById(R.id.durationTextView);
            priceTextView = findViewById(R.id.priceTextView);
            driverNameTextView = findViewById(R.id.driverNameTextView);
            vehicleInfoTextView = findViewById(R.id.vehicleInfoTextView);
            arrivalTimeTextView = findViewById(R.id.arrivalTimeTextView);
            driverInfoLayout = findViewById(R.id.driverInfoLayout);
            progressBar = findViewById(R.id.progressBar);
            driverLoadingProgressBar = findViewById(R.id.driverLoadingProgressBar);
            serviceInfoCardView = findViewById(R.id.serviceInfoCardView);
            errorTextView = findViewById(R.id.errorTextView);

        } catch (Exception e) {
            Log.e("RequestService", "Error inicializando vistas", e);
            Toast.makeText(this, "Error inicializando la interfaz: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void setupListeners() {
        try {
            // Configurar listeners
            if (calculateRouteButton != null) {
                calculateRouteButton.setOnClickListener(v -> calculateRoute());
            }

            if (confirmServiceButton != null) {
                confirmServiceButton.setOnClickListener(v -> confirmService());
            }

            if (cancelServiceButton != null) {
                cancelServiceButton.setOnClickListener(v -> cancelService());
            }

            // Configurar listeners para autocompletado
            if (pickupLocationEditText != null) {
                pickupLocationEditText.setOnItemClickListener((parent, view, position, id) -> {
                    AutocompletePrediction prediction = pickupAdapter.getItem(position);
                    if (prediction != null) {
                        fetchPlaceAndUpdateOrigin(prediction);
                    }
                });
            }

            if (destinationEditText != null) {
                destinationEditText.setOnItemClickListener((parent, view, position, id) -> {
                    AutocompletePrediction prediction = destinationAdapter.getItem(position);
                    if (prediction != null) {
                        fetchPlaceAndUpdateDestination(prediction);
                    }
                });
            }

        } catch (Exception e) {
            Log.e("RequestService", "Error configurando listeners", e);
        }
    }

    private void fetchPlaceAndUpdateOrigin(AutocompletePrediction prediction) {
        if (prediction == null || prediction.getPlaceId() == null) return;

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
                Log.e(TAG, "Error fetching place details", exception);
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in fetchPlaceAndUpdateOrigin", e);
        }
    }

    private void fetchPlaceAndUpdateDestination(AutocompletePrediction prediction) {
        if (prediction == null || prediction.getPlaceId() == null) return;

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
                Log.e(TAG, "Error fetching place details", exception);
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in fetchPlaceAndUpdateDestination", e);
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
            Log.e("RequestService", "Error verificando permisos de ubicación", e);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        try {
            Log.d(TAG, "Mapa listo");
            mMap = googleMap;

            if (mMap == null) {
                Log.e(TAG, "Error: googleMap es nulo en onMapReady");
                return;
            }

            // Configurar mapa
            mMap.getUiSettings().setZoomControlsEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);

            // Habilitar mi ubicación si hay permiso
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED) {
                mMap.setMyLocationEnabled(true);
                getLastLocation();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error en onMapReady", e);
            Toast.makeText(this, "Error al inicializar el mapa: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        try {
            if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getLastLocation();
                } else {
                    Toast.makeText(this, "Se requiere permiso de ubicación para solicitar servicios",
                            Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            Log.e("RequestService", "Error en manejo de permisos", e);
        }
    }

    private void getLastLocation() {
        try {
            Log.d(TAG, "Obteniendo última ubicación conocida");
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
                                    pickupLocationEditText.setText(address);
                                });

                                // Actualizar mapa con la ubicación obtenida
                                if (mMap != null) {
                                    updateMapWithCurrentLocation();
                                }
                            } else {
                                Log.w(TAG, "getLastLocation devolvió ubicación nula");
                                Toast.makeText(RequestServiceActivity.this, "No se pudo obtener tu ubicación actual", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error obteniendo ubicación", e);
                            Toast.makeText(RequestServiceActivity.this, "Error al obtener ubicación: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
            } else {
                Log.w(TAG, "Intentando obtener ubicación sin permisos o con fusedLocationClient nulo");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error en getLastLocation", e);
            Toast.makeText(this, "Error al acceder a los servicios de ubicación: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
                    LatLngBounds.Builder builder = new LatLngBounds.Builder();
                    builder.include(originLatLng);
                    builder.include(destinationLatLng);

                    // Añadir padding
                    int padding = 100; // en píxeles
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), padding));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error actualizando marcadores en el mapa", e);
        }
    }

    private void getAddressFromLocation(double latitude, double longitude, AddressCallback callback) {
        try {
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
        } catch (Exception e) {
            Log.e(TAG, "Error en getAddressFromLocation", e);
            callback.onAddressFound("Dirección desconocida");
        }
    }

    // Interfaz para manejar callback de geocoding
    interface AddressCallback {
        void onAddressFound(String address);
    }

    private void calculateRoute() {
        try {
            // Validar que tengamos origen y destino
            if (originLatLng == null || destinationLatLng == null) {
                Toast.makeText(this, "Por favor selecciona origen y destino", Toast.LENGTH_SHORT).show();
                return;
            }

            // Mostrar progreso
            showProgress(true);

            // Preparar solicitud de direcciones
            DirectionsApiRequest request = DirectionsApi.getDirections(geoApiContext,
                    originLatLng.latitude + "," + originLatLng.longitude,
                    destinationLatLng.latitude + "," + destinationLatLng.longitude);

            request.mode(TravelMode.DRIVING);
            request.language("es");
            request.units(com.google.maps.model.Unit.METRIC);

            // Ejecutar solicitud de manera asíncrona
            request.setCallback(new PendingResult.Callback<DirectionsResult>() {
                @Override
                public void onResult(DirectionsResult result) {
                    // Procesar resultado en el hilo principal
                    runOnUiThread(() -> {
                        directionsResult = result;
                        showProgress(false);

                        if (result.routes != null && result.routes.length > 0) {
                            drawRoute(result.routes[0]);
                            updateRouteInfo(result.routes[0]);
                            showServiceInfoPanel();
                        } else {
                            Toast.makeText(RequestServiceActivity.this, "No se pudo calcular la ruta", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onFailure(Throwable e) {
                    // Manejar error en el hilo principal
                    runOnUiThread(() -> {
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
            if (mMap == null) return;

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
            mMap.addMarker(new MarkerOptions()
                    .position(originLatLng)
                    .title("Origen")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

            mMap.addMarker(new MarkerOptions()
                    .position(destinationLatLng)
                    .title("Destino")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

            // Ajustar la cámara para mostrar toda la ruta
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            for (LatLng point : path) {
                builder.include(point);
            }

            LatLngBounds bounds = builder.build();
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));

        } catch (Exception e) {
            Log.e(TAG, "Error dibujando ruta", e);
        }
    }

    private void updateRouteInfo(DirectionsRoute route) {
        try {
            // Actualizar distancia
            if (route.legs != null && route.legs.length > 0) {
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
            }
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

            Log.d(TAG, "Precio calculado: " + estimatedPrice + " (Distancia: " + distanceKm + " km, Duración: " + durationMinutes + " min)");

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
            }
        } catch (Exception e) {
            Log.e(TAG, "Error mostrando panel de información del servicio", e);
        }
    }

    private void confirmService() {
        try {
            // Validar que tengamos la información necesaria
            if (directionsResult == null || estimatedPrice <= 0) {
                Toast.makeText(this, "No se pudo confirmar el servicio. Por favor, vuelve a calcular la ruta.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Verificar autenticación
            if (auth == null || auth.getCurrentUser() == null || db == null) {
                Toast.makeText(this, "Error de autenticación. Por favor, vuelve a iniciar sesión.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Mostrar progreso
            showProgress(true);

            // Crear datos del servicio
            Map<String, Object> serviceData = new HashMap<>();
            serviceData.put("passengerId", auth.getCurrentUser().getUid());
            serviceData.put("status", "requested");
            serviceData.put("pickupLocation", originAddress);
            serviceData.put("destination", destinationAddress);
            serviceData.put("distanceKm", distanceInKm);
            serviceData.put("durationMinutes", durationInMinutes);
            serviceData.put("estimatedPrice", estimatedPrice);
            serviceData.put("timestamp", System.currentTimeMillis());

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
                        // En producción, esto vendría de la actualización real de un conductor aceptando
                        simulateDriverAccepting();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error al solicitar servicio: " + e.getMessage(), e);
                        showProgress(false);
                        Toast.makeText(this, "Error al solicitar servicio: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });

        } catch (Exception e) {
            showProgress(false);
            Log.e(TAG, "Error en confirmService", e);
            Toast.makeText(this, "Error al confirmar el servicio: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUIForWaitingDriver() {
        try {
            // Ocultar botón de confirmar
            if (confirmServiceButton != null) {
                confirmServiceButton.setVisibility(View.GONE);
            }

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
            if (db == null) return;

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
                        }
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Error obteniendo información del conductor", e));

        } catch (Exception e) {
            Log.e(TAG, "Error en fetchDriverInfo", e);
        }
    }

    private void updateUIWithDriverInfo(String driverName, String vehicleModel, String vehiclePlate) {
        try {
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
                                    Log.d(TAG, "Servicio cancelado correctamente");
                                    showProgress(false);
                                    Toast.makeText(this, "Servicio cancelado", Toast.LENGTH_SHORT).show();
                                    finish();
                                })
                                .addOnFailureListener(e -> {
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
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void showErrorMessage(String message) {
        if (errorTextView != null) {
            errorTextView.setText(message);
            errorTextView.setVisibility(View.VISIBLE);
        } else {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }

        // Log del error para depuración
        Log.e(TAG, "Error mostrado al usuario: " + message);
    }

    @Override
    protected void onDestroy() {
        // Eliminar listener de actualizaciones del servicio si existe
        if (serviceListener != null) {
            serviceListener.remove();
        }

        // Liberar contexto de GeoApi
        if (geoApiContext != null) {
            geoApiContext.shutdown();
        }

        super.onDestroy();
    }
}