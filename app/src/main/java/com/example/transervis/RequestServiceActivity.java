package com.example.transervis;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
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
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RequestServiceActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LatLng currentLocation;
    private LatLng destinationLocation;

    private TextView currentLocationText;
    private EditText destinationEditText;
    private TextView vehicleInfoText;
    private TextView distanceText;
    private TextView timeText;
    private Button requestButton;
    private CardView serviceInfoCard;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_request_service);

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize UI elements
        currentLocationText = findViewById(R.id.currentLocationText);
        destinationEditText = findViewById(R.id.destinationEditText);
        vehicleInfoText = findViewById(R.id.vehicleInfoText);
        distanceText = findViewById(R.id.distanceText);
        timeText = findViewById(R.id.timeText);
        requestButton = findViewById(R.id.requestButton);
        serviceInfoCard = findViewById(R.id.serviceInfoCard);

        // Hide service info card initially
        serviceInfoCard.setVisibility(View.GONE);

        // Initialize map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Setup destination input
        destinationEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                findDestinationFromAddress(destinationEditText.getText().toString());
            }
        });

        // Setup request button
        requestButton.setOnClickListener(v -> {
            requestService();
        });

        // Set current time
        timeText.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Enable my location if permission is granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation();
            } else {
                Toast.makeText(this, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);

            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if (location != null) {
                                currentLocation = new LatLng(location.getLatitude(), location.getLongitude());

                                // Update current location text
                                getAddressFromLocation(currentLocation, currentLocationText);

                                // Move camera to current location
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15));

                                // For demo purposes, also show some drivers nearby
                                showNearbyDrivers();
                            }
                        }
                    });
        }
    }

    private void getAddressFromLocation(LatLng location, TextView textView) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(
                    location.latitude, location.longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                StringBuilder sb = new StringBuilder();

                // Get the thoroughfare (street name)
                if (address.getThoroughfare() != null) {
                    sb.append(address.getThoroughfare());
                    if (address.getSubThoroughfare() != null) {
                        sb.append(" ").append(address.getSubThoroughfare());
                    }
                } else {
                    // If no street name is available, use locality or sub-locality
                    if (address.getSubLocality() != null) {
                        sb.append(address.getSubLocality());
                    } else if (address.getLocality() != null) {
                        sb.append(address.getLocality());
                    }
                }

                textView.setText(sb.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void findDestinationFromAddress(String address) {
        if (address.isEmpty()) return;

        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocationName(address, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address location = addresses.get(0);
                destinationLocation = new LatLng(location.getLatitude(), location.getLongitude());

                // Add marker for destination
                mMap.clear();
                mMap.addMarker(new MarkerOptions().position(destinationLocation).title("Destino"));

                // Move camera to include both locations
                if (currentLocation != null) {
                    // Calculate distance
                    float[] results = new float[1];
                    Location.distanceBetween(
                            currentLocation.latitude, currentLocation.longitude,
                            destinationLocation.latitude, destinationLocation.longitude,
                            results);

                    // Update distance text (convert meters to kilometers)
                    float distanceKm = results[0] / 1000;
                    distanceText.setText(String.format(Locale.getDefault(), "%.1f", distanceKm));

                    // Show service info card
                    serviceInfoCard.setVisibility(View.VISIBLE);
                }
            } else {
                Toast.makeText(this, "No se encontró la dirección", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error al buscar dirección", Toast.LENGTH_SHORT).show();
        }
    }

    private void showNearbyDrivers() {
        // This is a mockup function to show some drivers nearby
        // In a real app, you would fetch this data from Firebase
        if (currentLocation != null) {
            // Show a driver at a nearby location
            LatLng driverLocation = new LatLng(
                    currentLocation.latitude + 0.002,
                    currentLocation.longitude + 0.002);

            mMap.addMarker(new MarkerOptions()
                    .position(driverLocation)
                    .title("Conductor disponible"));
        }
    }

    private void requestService() {
        if (currentLocation == null || destinationLocation == null) {
            Toast.makeText(this, "Por favor selecciona origen y destino", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create service request in Firestore
        Map<String, Object> serviceRequest = new HashMap<>();
        serviceRequest.put("userId", auth.getCurrentUser().getUid());
        serviceRequest.put("originLat", currentLocation.latitude);
        serviceRequest.put("originLng", currentLocation.longitude);
        serviceRequest.put("destinationLat", destinationLocation.latitude);
        serviceRequest.put("destinationLng", destinationLocation.longitude);
        serviceRequest.put("distance", Float.parseFloat(distanceText.getText().toString()));
        serviceRequest.put("status", "pending");
        serviceRequest.put("timestamp", new Date());

        // Save to Firestore
        db.collection("serviceRequests")
                .add(serviceRequest)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(RequestServiceActivity.this,
                            "Servicio solicitado con éxito", Toast.LENGTH_SHORT).show();

                    // In a real app, you would now show a waiting screen
                    // and listen for changes in the service request document

                    // For this demo, just close the activity after a delay
                    requestButton.setText("Buscando conductor...");
                    requestButton.setEnabled(false);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(RequestServiceActivity.this,
                            "Error al solicitar servicio: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }
}