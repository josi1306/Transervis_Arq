package com.example.transervis;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class RegistrerConductor extends AppCompatActivity {

private Button Regconduntor;
    @Override

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_registrer_conductor);

        Regconduntor = findViewById(R.id.signup_buttonCondutor);

        Spinner documento = findViewById(R.id.spinnerTipoDocumento);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.tipos_documento,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        documento.setAdapter(adapter);

        Spinner Marca = findViewById(R.id.spinnerMarca);
        ArrayAdapter<CharSequence> adapmarca = ArrayAdapter.createFromResource(
                this,
                R.array.tipos_marca,
                android.R.layout.simple_spinner_item
        );
        adapmarca.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Marca.setAdapter(adapmarca);


        Regconduntor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(RegistrerConductor.this, "Registro exitoso Bienvenido al Ruedo", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(RegistrerConductor.this, DriverMainActivity.class));
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}