package com.example.transervis;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
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
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class CurrentServiceActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LatLng currentLocation;

    private TextView serviceStatusText;
    private TextView pickupLocationText;
    private TextView destinationLocationText;
    private TextView requestDistanceText;
    private TextView estimatedPriceText;
    private TextView passengerNameText;
    private TextView activeDistanceText;

    private Button acceptButton;
    private Button rejectButton;
    private Button navigateButton;
    private Button completeButton;

    private CardView serviceRequestCard;
    private CardView activeServiceCard;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ListenerRegistration serviceListener;

    private String currentServiceId;
    private LatLng pickupLocation;
    private LatLng destinationLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_current_service);

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize UI elements
        serviceStatusText = findViewById(R.id.serviceStatusText);
        pickupLocationText = findViewById(R.id.pickupLocationText);
        destinationLocationText = findViewById(R.id.destinationLocationText);
        requestDistanceText = findViewById(R.id.requestDistanceText);
        estimatedPriceText = findViewById(R.id.estimatedPriceText);
        passengerNameText = findViewById(R.id.passengerNameText);
        activeDistanceText = findViewById(R.id.activeDistanceText);

        acceptButton = findViewById(R.id.acceptButton);
        rejectButton = findViewById(R.id.rejectButton);
        navigateButton = findViewById(R.id.navigateButton);
        completeButton = findViewById(R.id.completeButton);

        serviceRequestCard = findViewById(R.id.serviceRequestCard);
        activeServiceCard = findViewById(R.id.activeServiceCard);

        // Set button listeners
        acceptButton.setOnClickListener(v -> acceptService());
        rejectButton.setOnClickListener(v -> rejectService());
        navigateButton.setOnClickListener(v -> navigateToPickup());
        completeButton.setOnClickListener(v -> completeService());

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

    @Override
    protected void onStart() {
        super.onStart();
        // Start listening for service requests when the activity becomes visible
        listenForServiceRequests();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Stop listening for service requests when the activity is no longer visible
        if (serviceListener != null) {
            serviceListener.remove();
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
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15));

                                // Update driver location in Firestore
                                updateDriverLocation();
                            }
                        }
                    });
        }
    }

    private void updateDriverLocation() {
        if (currentLocation == null || auth.getCurrentUser() == null) return;

        String driverId = auth.getCurrentUser().getUid();
        Map<String, Object> locationData = new HashMap<>();
        locationData.put("location", new com.google.firebase.firestore.GeoPoint(
                currentLocation.latitude, currentLocation.longitude));
        locationData.put("lastUpdated", new Date());
        locationData.put("isAvailable", true);

        db.collection("driver_locations")
                .document(driverId)
                .set(locationData)
                .addOnFailureListener(e -> {
                    Toast.makeText(CurrentServiceActivity.this,
                            "Error al actualizar ubicación", Toast.LENGTH_SHORT).show();
                });
    }

    private void listenForServiceRequests() {
        if (auth.getCurrentUser() == null) return;

        String driverId = auth.getCurrentUser().getUid();

        // First check if there's any active service
        db.collection("serviceRequests")
                .whereEqualTo("driverId", driverId)
                .whereEqualTo("status", "accepted")
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        // We have an active service
                        DocumentSnapshot activeService = queryDocumentSnapshots.getDocuments().get(0);
                        currentServiceId = activeService.getId();
                        displayActiveService(activeService);
                    } else {
                        // No active service, listen for new requests
                        listenForNewRequests();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(CurrentServiceActivity.this,
                            "Error al verificar servicios activos", Toast.LENGTH_SHORT).show();
                });
    }

    private void listenForNewRequests() {
        // In a real app, you'd have a more sophisticated algorithm to match drivers with requests
        // For this demo, we'll just display a random pending request

        // For demo purposes, create a mock service request after a delay
        serviceStatusText.setText("Buscando pasajeros cercanos...");

        // Simulate a delay before showing a service request
        new android.os.Handler().postDelayed(
                () -> createMockServiceRequest(),
                3000);
    }

    private void createMockServiceRequest() {
        if (currentLocation == null) return;

        // Create a mock pickup location nearby
        double latOffset = (new Random().nextDouble() - 0.5) * 0.005;
        double lngOffset = (new Random().nextDouble() - 0.5) * 0.005;
        pickupLocation = new LatLng(
                currentLocation.latitude + latOffset,
                currentLocation.longitude + lngOffset);

        // Create a mock destination location further away
        latOffset = (new Random().nextDouble() - 0.5) * 0.02;
        lngOffset = (new Random().nextDouble() - 0.5) * 0.02;
        destinationLocation = new LatLng(
                currentLocation.latitude + latOffset,
                currentLocation.longitude + lngOffset);

        // Calculate distance
        float[] results = new float[1];
        Location.distanceBetween(
                pickupLocation.latitude, pickupLocation.longitude,
                destinationLocation.latitude, destinationLocation.longitude,
                results);
        float distanceKm = results[0] / 1000;

        // Get addresses for locations
        getAddressFromLocation(pickupLocation, pickupLocationText);
        getAddressFromLocation(destinationLocation, destinationLocationText);

        // Update UI
        requestDistanceText.setText(String.format(Locale.getDefault(), "%.1f km", distanceKm));

        // Calculate estimated price (simple formula: base fee + per km fee)
        int baseFee = 5000; // COP
        int perKmFee = 2000; // COP per km
        int estimatedPrice = baseFee + (int)(distanceKm * perKmFee);
        estimatedPriceText.setText(String.format(Locale.getDefault(), "$%,d", estimatedPrice));

        // Show the request card
        serviceRequestCard.setVisibility(View.VISIBLE);

        // Update status text
        serviceStatusText.setText("¡Nueva solicitud de servicio!");

        // Add markers to the map
        mMap.clear();
        mMap.addMarker(new MarkerOptions()
                .position(pickupLocation)
                .title("Recoger aquí")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        mMap.addMarker(new MarkerOptions()
                .position(destinationLocation)
                .title("Destino"));

        // Draw a line between pickup and destination
        mMap.addPolyline(new PolylineOptions()
                .add(pickupLocation, destinationLocation)
                .width(5)
                .color(ContextCompat.getColor(this, R.color.lavender)));

        // Move camera to show both markers
        com.google.android.gms.maps.model.LatLngBounds.Builder builder = new com.google.android.gms.maps.model.LatLngBounds.Builder();
        builder.include(pickupLocation);
        builder.include(destinationLocation);
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
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

    private void acceptService() {
        // In a real app, this would update the service request in Firestore
        // For this demo, we'll just update the UI

        // Hide request card
        serviceRequestCard.setVisibility(View.GONE);

        // Show active service card
        activeServiceCard.setVisibility(View.VISIBLE);

        // Update status text
        serviceStatusText.setText("Servicio aceptado - Dirigiéndose a recoger al pasajero");

        // Set passenger info
        passengerNameText.setText("Juan Pérez");
        activeDistanceText.setText(requestDistanceText.getText() + " - " + estimatedPriceText.getText());

        // Focus map on pickup location
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pickupLocation, 15));
    }

    private void rejectService() {
        // In a real app, this would update the service request in Firestore
        // For this demo, we'll just hide the request card and listen for new requests

        // Hide request card
        serviceRequestCard.setVisibility(View.GONE);

        // Update status text
        serviceStatusText.setText("Servicio rechazado - Buscando nuevos pasajeros...");

        // Clear the map
        mMap.clear();

        // Focus map on current location
        if (currentLocation != null) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15));
        }

        // Listen for new requests after a delay
        new android.os.Handler().postDelayed(
                this::listenForNewRequests,
                2000);
    }

    private void navigateToPickup() {
        if (pickupLocation == null) return;

        // Open Google Maps for navigation
        Uri gmmIntentUri = Uri.parse("google.navigation:q=" +
                pickupLocation.latitude + "," + pickupLocation.longitude);
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");

        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            Toast.makeText(this, "No se encontró la aplicación de Google Maps",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void completeService() {
        // Show confirmation dialog
        new AlertDialog.Builder(this)
                .setTitle("Completar Servicio")
                .setMessage("¿Estás seguro de que deseas completar este servicio?")
                .setPositiveButton("Sí", (dialog, which) -> {
                    // In a real app, this would update the service request in Firestore
                    // For this demo, we'll just update the UI

                    // Hide active service card
                    activeServiceCard.setVisibility(View.GONE);

                    // Update status text
                    serviceStatusText.setText("Servicio completado - Buscando nuevos pasajeros...");

                    // Clear the map
                    mMap.clear();

                    // Focus map on current location
                    if (currentLocation != null) {
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15));
                    }

                    // Show rating dialog
                    showRatingDialog();
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void showRatingDialog() {
        // In a real app, this would be a custom dialog with a rating bar
        // For this demo, we'll just show a toast
        Toast.makeText(this, "¡Servicio calificado con 5 estrellas!", Toast.LENGTH_LONG).show();

        // Listen for new requests after a delay
        new android.os.Handler().postDelayed(
                this::listenForNewRequests,
                2000);
    }

    private void displayActiveService(DocumentSnapshot serviceDoc) {
        // In a real app, this would load data from the service document
        // For this demo, we'll just simulate it

        // Create mock pickup and destination locations
        double lat = currentLocation != null ? currentLocation.latitude : 4.6097;
        double lng = currentLocation != null ? currentLocation.longitude : -74.0817;

        pickupLocation = new LatLng(lat + 0.005, lng + 0.005);
        destinationLocation = new LatLng(lat + 0.02, lng + 0.02);

        // Get addresses for locations
        getAddressFromLocation(pickupLocation, pickupLocationText);
        getAddressFromLocation(destinationLocation, destinationLocationText);

        // Calculate distance
        float[] results = new float[1];
        Location.distanceBetween(
                pickupLocation.latitude, pickupLocation.longitude,
                destinationLocation.latitude, destinationLocation.longitude,
                results);
        float distanceKm = results[0] / 1000;

        // Update UI
        passengerNameText.setText("Juan Pérez");
        activeDistanceText.setText(String.format(Locale.getDefault(), "%.1f km - $%,d",
                distanceKm, 5000 + (int)(distanceKm * 2000)));

        // Show active service card
        activeServiceCard.setVisibility(View.VISIBLE);

        // Update status text
        serviceStatusText.setText("Servicio activo - Dirigiéndose a recoger al pasajero");

        // Add markers to the map
        mMap.clear();
        mMap.addMarker(new MarkerOptions()
                .position(pickupLocation)
                .title("Recoger aquí")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        mMap.addMarker(new MarkerOptions()
                .position(destinationLocation)
                .title("Destino"));

        // Draw a line between pickup and destination
        mMap.addPolyline(new PolylineOptions()
                .add(pickupLocation, destinationLocation)
                .width(5)
                .color(ContextCompat.getColor(this, R.color.lavender)));

        // Move camera to show both markers
        com.google.android.gms.maps.model.LatLngBounds.Builder builder = new com.google.android.gms.maps.model.LatLngBounds.Builder();
        builder.include(pickupLocation);
        builder.include(destinationLocation);
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
    }
}