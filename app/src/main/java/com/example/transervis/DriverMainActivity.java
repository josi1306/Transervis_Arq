package com.example.transervis;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.transervis.services.AuthService;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.firestore.GeoPoint;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import android.util.Log;


public class DriverMainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private AuthService authService;
    private FirebaseFirestore db;
    private TextView welcomeText, statusText;
    private Switch availabilitySwitch;
    private CardView currentServiceCard, earningsCard, ratingsCard, historyCard;
    private Button logoutButton;
    private boolean isAvailable = false;
    private CardView profileCard;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver);

        // Inicializar servicios
        authService = new AuthService();
        db = FirebaseFirestore.getInstance();

        // Inicializar vistas
        initializeViews();

        // Verificar usuario actual
        checkCurrentUser();

        // Configurar listeners
        setupListeners();

        // Verificar permisos de ubicación
        checkLocationPermissions();
    }

    private void initializeViews() {
        welcomeText = findViewById(R.id.welcomeTextDriver);
        statusText = findViewById(R.id.statusText);
        availabilitySwitch = findViewById(R.id.availabilitySwitch);
        currentServiceCard = findViewById(R.id.currentServiceCard);
        earningsCard = findViewById(R.id.earningsCard);
        ratingsCard = findViewById(R.id.ratingsCard);
        historyCard = findViewById(R.id.historyCard);
        logoutButton = findViewById(R.id.logoutButtonDriver);
        profileCard = findViewById(R.id.profileCard);
    }

    private void checkCurrentUser() {
        FirebaseUser currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            // Si no hay usuario, regresar al login
            startActivity(new Intent(this, LoginConductor.class));
            finish();
            return;
        }

        // Verificar que sea un conductor o un administrador
        String userId = currentUser.getUid();

        // Primero verificamos si es administrador
        authService.isAdmin(userId, adminTask -> {
            if (adminTask.isSuccessful() && Boolean.TRUE.equals(adminTask.getResult())) {
                // Es administrador, permitir acceso
                loadDriverProfile(userId);
                loadDriverAvailability(userId);
            } else {
                // No es admin, verificar si es conductor
                authService.checkUserRole(userId, "conductor", task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        // El usuario es un conductor, cargar sus datos
                        loadDriverProfile(userId);
                        loadDriverAvailability(userId);
                    } else {
                        // No es conductor ni admin, cerrar sesión
                        authService.signOut();
                        Toast.makeText(DriverMainActivity.this,
                                "Acceso denegado. Esta cuenta no está registrada como conductor.",
                                Toast.LENGTH_LONG).show();
                        startActivity(new Intent(DriverMainActivity.this, TypeSelection.class));
                        finish();
                    }
                });
            }
        });
    }

    private void loadDriverProfile(String userId) {
        // Primero verificamos si es administrador
        authService.isAdmin(userId, adminTask -> {
            if (adminTask.isSuccessful() && Boolean.TRUE.equals(adminTask.getResult())) {
                // Es administrador
                welcomeText.setText("Administrador: Vista de Conductor");
            } else {
                // No es admin, cargar perfil normal
                db.collection("profiles").document(userId).get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists()) {
                                String nombre = documentSnapshot.getString("nombre");
                                if (nombre != null && !nombre.isEmpty()) {
                                    welcomeText.setText("Bienvenido, " + nombre);
                                } else {
                                    welcomeText.setText("Bienvenido Conductor");
                                }
                            } else {
                                welcomeText.setText("Bienvenido Conductor");
                            }
                        })
                        .addOnFailureListener(e -> {
                            // En caso de error, usar texto genérico
                            welcomeText.setText("Bienvenido Conductor");
                        });
            }
        });
    }

    private void loadDriverAvailability(String userId) {
        db.collection("driver_locations").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Boolean available = documentSnapshot.getBoolean("isAvailable");
                        if (available != null) {
                            isAvailable = available;
                            availabilitySwitch.setChecked(isAvailable);
                            updateStatusText();
                        }
                    }
                });
    }

    private void updateDriverAvailability(boolean available) {
        if (authService.getCurrentUser() == null) return;

        String userId = authService.getCurrentUser().getUid();

        // Obtener ubicación actual para actualizar junto con disponibilidad
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("isAvailable", available);

                        // Si tenemos ubicación, actualizar también
                        if (location != null) {
                            updates.put("location", new GeoPoint(location.getLatitude(), location.getLongitude()));
                            updates.put("timestamp", new Date());
                        }

                        // Actualizar Firestore
                        db.collection("driver_locations").document(userId)
                                .set(updates, com.google.firebase.firestore.SetOptions.merge())
                                .addOnSuccessListener(aVoid -> Log.d("DriverMain", "Disponibilidad actualizada"))
                                .addOnFailureListener(e -> Log.e("DriverMain", "Error actualizando disponibilidad", e));
                    });
        } else {
            // Si no hay permisos, actualizar solo la disponibilidad
            Map<String, Object> updates = new HashMap<>();
            updates.put("isAvailable", available);
            updates.put("timestamp", new Date());

            db.collection("driver_locations").document(userId)
                    .set(updates, com.google.firebase.firestore.SetOptions.merge())
                    .addOnFailureListener(e -> Log.e("DriverMain", "Error actualizando disponibilidad", e));
        }
    }

    private void setupListeners() {
        // Switch de disponibilidad
        availabilitySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isAvailable = isChecked;
            updateStatusText();
            updateDriverAvailability(isChecked);

            if (isChecked) {
                Toast.makeText(this, "Ahora estás disponible para recibir solicitudes", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Ya no estás disponible para recibir solicitudes", Toast.LENGTH_SHORT).show();
            }
        });

// Servicio actual
        currentServiceCard.setOnClickListener(view -> {
            try {
                // Verificar permisos de ubicación antes de iniciar la actividad
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
                    // Si no tiene permisos, solicitarlos
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                            LOCATION_PERMISSION_REQUEST_CODE);
                    // Mostrar mensaje al usuario
                    Toast.makeText(this,
                            "Se requieren permisos de ubicación para esta funcionalidad",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                // Si tiene permisos, iniciar la actividad
                Intent intent = new Intent(DriverMainActivity.this, CurrentServiceActivity.class);
                startActivity(intent);

            } catch (Exception e) {
                // Registro detallado del error
                Log.e("DriverMainActivity", "Error al iniciar CurrentServiceActivity", e);
                Toast.makeText(DriverMainActivity.this,
                        "Error al abrir pantalla de servicio: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });

        // Ganancias
        earningsCard.setOnClickListener(view -> {
            Toast.makeText(DriverMainActivity.this,
                    "Ver ganancias próximamente",
                    Toast.LENGTH_SHORT).show();
            // TODO: Iniciar EarningsActivity
        });

        // Calificaciones
        ratingsCard.setOnClickListener(view -> {
            Toast.makeText(DriverMainActivity.this,
                    "Ver calificaciones próximamente",
                    Toast.LENGTH_SHORT).show();
            // TODO: Iniciar RatingsActivity
        });

        // Historial
        historyCard.setOnClickListener(view -> {
            Toast.makeText(DriverMainActivity.this,
                    "Ver historial próximamente",
                    Toast.LENGTH_SHORT).show();
            // TODO: Iniciar HistoryActivity
        });

        // Perfil
        profileCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(DriverMainActivity.this, EdicionPerfil.class));
            }
        });

        // Cerrar sesión
        logoutButton.setOnClickListener(view -> {
            authService.signOut();
            startActivity(new Intent(DriverMainActivity.this, TypeSelection.class));
            finish();
        });
    }

    private void updateStatusText() {
        if (isAvailable) {
            statusText.setText("Estado: Disponible");
            statusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            statusText.setText("Estado: No disponible");
            statusText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        }
    }

    private void checkLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permiso de ubicación concedido",
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this,
                        "Se requiere permiso de ubicación para usar la app",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
}