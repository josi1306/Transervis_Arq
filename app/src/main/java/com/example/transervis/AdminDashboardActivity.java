package com.example.transervis;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.transervis.services.AuthService;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class AdminDashboardActivity extends AppCompatActivity {

    private AuthService authService;
    private FirebaseFirestore db;
    private TextView welcomeText;
    private CardView pasajerosCard, conductoresCard, estadisticasCard, configCard;
    private Button logoutButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        // Inicializar servicios
        authService = new AuthService();
        db = FirebaseFirestore.getInstance();

        // Inicializar vistas
        initializeViews();

        // Verificar usuario actual
        checkCurrentUser();

        // Configurar listeners
        setupListeners();
    }

    private void initializeViews() {
        welcomeText = findViewById(R.id.welcomeTextAdmin);
        pasajerosCard = findViewById(R.id.pasajerosCard);
        conductoresCard = findViewById(R.id.conductoresCard);
        estadisticasCard = findViewById(R.id.estadisticasCard);
        configCard = findViewById(R.id.configCard);
        logoutButton = findViewById(R.id.logoutButtonAdmin);
    }

    private void checkCurrentUser() {
        FirebaseUser currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            // Si no hay usuario, regresar al login
            startActivity(new Intent(this, TypeSelection.class));
            finish();
            return;
        }

        // Verificar que sea un administrador
        String userId = currentUser.getUid();
        authService.isAdmin(userId, task -> {
            if (task.isSuccessful() && Boolean.TRUE.equals(task.getResult())) {
                // El usuario es un administrador, cargar sus datos
                String email = currentUser.getEmail();
                welcomeText.setText("Bienvenido Administrador" + (email != null ? ": " + email : ""));
            } else {
                // No es un administrador, cerrar sesión
                authService.signOut();
                Toast.makeText(AdminDashboardActivity.this,
                        "Acceso denegado. Esta cuenta no tiene permisos de administrador.",
                        Toast.LENGTH_LONG).show();
                startActivity(new Intent(AdminDashboardActivity.this, TypeSelection.class));
                finish();
            }
        });
    }

    private void setupListeners() {
        // Gestión de pasajeros
        pasajerosCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Redirigir a la interfaz de pasajero
                Intent intent = new Intent(AdminDashboardActivity.this, PassengerMainActivity.class);
                startActivity(intent);
            }
        });

        // Gestión de conductores
        conductoresCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Redirigir a la interfaz de conductor
                Intent intent = new Intent(AdminDashboardActivity.this, DriverMainActivity.class);
                startActivity(intent);
            }
        });

        // Estadísticas
        estadisticasCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Lanzar actividad de estadísticas
                Toast.makeText(AdminDashboardActivity.this,
                        "Estadísticas próximamente", Toast.LENGTH_SHORT).show();
            }
        });

        // Configuración
        configCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Lanzar actividad de configuración
                Toast.makeText(AdminDashboardActivity.this,
                        "Configuración próximamente", Toast.LENGTH_SHORT).show();
            }
        });

        // Cerrar sesión
        logoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                authService.signOut();
                startActivity(new Intent(AdminDashboardActivity.this, TypeSelection.class));
                finish();
            }
        });
    }
}