package com.example.transervis;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class TypeSelection extends AppCompatActivity {
    private Button BotonConductor;
    private Button BotonPasajero;
    private TextView TrabajarUs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_type_selection);
        TrabajarUs = findViewById(R.id.loginRedirectText);
        BotonConductor = findViewById(R.id.Conductor_button);
        BotonPasajero = findViewById(R.id.Pasajero_button);

        // Botón para pasajeros dirige al login de pasajeros
        BotonPasajero.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(TypeSelection.this, LoginActivity.class));
            }
        });

        // Botón para conductores dirige al login de conductores
        BotonConductor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(TypeSelection.this, LoginConductor.class));
            }
        });

        TrabajarUs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(TypeSelection.this, RegistrerConductor.class));
            }
        });
    }
}