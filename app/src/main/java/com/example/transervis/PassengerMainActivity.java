package com.example.transervis;

import android.util.Log;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
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

public class PassengerMainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private AuthService authService;
    private FirebaseFirestore db;
    private TextView welcomeText;
    private CardView requestRideCard, viewAvailabilityCard, historyCard, profileCard;
    private Button logoutButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passenger);

        // Inicializar servicios
        authService = new AuthService();
        db = FirebaseFirestore.getInstance();

        // Inicializar vistas
        initializeViews();

        // Verificar usuario y cargar datos
        checkCurrentUser();

        // Configurar listeners
        setupListeners();

        // Verificar permisos de ubicación
        checkLocationPermissions();
    }

    private void initializeViews() {
        welcomeText = findViewById(R.id.welcomeTextPassenger);
        requestRideCard = findViewById(R.id.requestRideCard);
        viewAvailabilityCard = findViewById(R.id.viewAvailabilityCard);
        historyCard = findViewById(R.id.historyCard);
        profileCard = findViewById(R.id.profileCard);
        logoutButton = findViewById(R.id.logoutButtonPassenger);
    }

    private void checkCurrentUser() {
        FirebaseUser currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            // Si no hay usuario, regresar al login
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // Verificar que sea un pasajero o un administrador
        String userId = currentUser.getUid();

        // Primero verificamos si es administrador
        authService.isAdmin(userId, adminTask -> {
            if (adminTask.isSuccessful() && Boolean.TRUE.equals(adminTask.getResult())) {
                // Es administrador, permitir acceso
                loadUserProfile(userId);
            } else {
                // No es admin, verificar si es pasajero
                authService.checkUserRole(userId, "pasajero", task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        // El usuario es un pasajero, cargar sus datos
                        loadUserProfile(userId);
                    } else {
                        // No es pasajero ni admin, cerrar sesión
                        authService.signOut();
                        Toast.makeText(PassengerMainActivity.this,
                                "Acceso denegado. Esta cuenta no está registrada como pasajero.",
                                Toast.LENGTH_LONG).show();
                        startActivity(new Intent(PassengerMainActivity.this, TypeSelection.class));
                        finish();
                    }
                });
            }
        });
    }

    private void loadUserProfile(String userId) {
        // Primero verificamos si es administrador
        authService.isAdmin(userId, adminTask -> {
            if (adminTask.isSuccessful() && Boolean.TRUE.equals(adminTask.getResult())) {
                // Es administrador
                welcomeText.setText("Administrador: Vista de Pasajero");
            } else {
                // No es admin, cargar perfil normal
                db.collection("profiles").document(userId).get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists()) {
                                String nombre = documentSnapshot.getString("nombre");
                                if (nombre != null && !nombre.isEmpty()) {
                                    welcomeText.setText("Bienvenido, " + nombre);
                                } else {
                                    String email = authService.getCurrentUser().getEmail();
                                    welcomeText.setText("Bienvenido, " + email);
                                }
                            } else {
                                String email = authService.getCurrentUser().getEmail();
                                welcomeText.setText("Bienvenido, " + email);
                            }
                        })
                        .addOnFailureListener(e -> {
                            // En caso de error, usar el email
                            String email = authService.getCurrentUser().getEmail();
                            welcomeText.setText("Bienvenido, " + email);
                        });
            }
        });
    }

    private void setupListeners() {
        // Solicitar servicio
        requestRideCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent intent = new Intent(PassengerMainActivity.this, RequestServiceActivity.class);
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e("PassengerMainActivity", "Error al iniciar RequestServiceActivity", e);
                    Toast.makeText(PassengerMainActivity.this,
                            "Error al abrir pantalla de solicitud: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Ver disponibilidad
        viewAvailabilityCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(PassengerMainActivity.this,
                        "Ver vehículos disponibles próximamente",
                        Toast.LENGTH_SHORT).show();
                // TODO: Iniciar ViewAvailabilityActivity
            }
        });

        // Historial
        historyCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(PassengerMainActivity.this,
                        "Historial de viajes próximamente",
                        Toast.LENGTH_SHORT).show();
                // TODO: Iniciar HistoryActivity
            }
        });

        // Perfil
        profileCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(PassengerMainActivity.this,
                        "Perfil de usuario próximamente",
                        Toast.LENGTH_SHORT).show();
                // TODO: Iniciar ProfileActivity
            }
        });

        // Cerrar sesión
        logoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                authService.signOut();
                startActivity(new Intent(PassengerMainActivity.this, TypeSelection.class));
                finish();
            }
        });
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