package com.example.transervis;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Random;

public class ViewAvailabilityActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LatLng currentLocation;

    private TextView availableDriversText;
    private FloatingActionButton refreshButton;
    private Button backButton;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private int driversCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_availability);

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize UI elements
        availableDriversText = findViewById(R.id.availableDriversText);
        refreshButton = findViewById(R.id.refreshButton);
        backButton = findViewById(R.id.backButton);

        // Set button listeners
        refreshButton.setOnClickListener(v -> fetchAvailableDrivers());
        backButton.setOnClickListener(v -> finish());

        // Initialize map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
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

                                // Move camera to current location
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 14));

                                // Fetch available drivers
                                fetchAvailableDrivers();
                            }
                        }
                    });
        }
    }

    private void fetchAvailableDrivers() {
        if (currentLocation == null) return;

        // Clear previous markers
        mMap.clear();
        driversCount = 0;

        // In a real app, you would fetch drivers from Firestore
        // For this demo, we'll just simulate random drivers nearby
        showMockDrivers();
    }

    private void showMockDrivers() {
        if (currentLocation == null) return;

        // Generate 3-7 random drivers nearby
        int numDrivers = new Random().nextInt(5) + 3;
        driversCount = numDrivers;

        for (int i = 0; i < numDrivers; i++) {
            // Create a random offset from current location (within ~500m)
            double latOffset = (new Random().nextDouble() - 0.5) * 0.01;
            double lngOffset = (new Random().nextDouble() - 0.5) * 0.01;

            LatLng driverLocation = new LatLng(
                    currentLocation.latitude + latOffset,
                    currentLocation.longitude + lngOffset);

            // Add marker for driver
            mMap.addMarker(new MarkerOptions()
                    .position(driverLocation)
                    .title("Conductor disponible")
                    .snippet("Vehículo: SUV")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        }

        // Update UI
        availableDriversText.setText(driversCount + " conductores disponibles cerca de ti");

        // Show toast
        Toast.makeText(this, "Se encontraron " + driversCount + " conductores disponibles",
                Toast.LENGTH_SHORT).show();
    }
}