package com.example.transervis;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.example.transervis.services.AuthService;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;

import java.util.HashMap;
import java.util.Map;

public class SignUpActivity extends AppCompatActivity {
    private AuthService authService;
    private EditText signupEmail, signupPassword, signupName, signupLastName, signupPhone;
    private RadioGroup roleRadioGroup;
    private Button signupButton;
    private TextView loginRedirectText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        authService = new AuthService();

        // Inicializar vistas
        signupEmail = findViewById(R.id.signup_email);
        signupPassword = findViewById(R.id.signup_password);
        signupName = findViewById(R.id.signup_name);
        signupLastName = findViewById(R.id.signup_lastname);
        signupPhone = findViewById(R.id.signup_phone);
        roleRadioGroup = findViewById(R.id.role_radio_group);
        signupButton = findViewById(R.id.signup_button);
        loginRedirectText = findViewById(R.id.loginRedirectText);

        signupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String email = signupEmail.getText().toString().trim();
                String password = signupPassword.getText().toString().trim();
                String name = signupName.getText().toString().trim();
                String lastName = signupLastName.getText().toString().trim();
                String phone = signupPhone.getText().toString().trim();

                // Obtener rol seleccionado
                int selectedRoleId = roleRadioGroup.getCheckedRadioButtonId();
                String role = "pasajero"; // Valor por defecto

                if (selectedRoleId == R.id.radio_conductor) {
                    role = "conductor";
                }

                // Validar campos
                if (validateFields(email, password, name, lastName)) {
                    registerUser(email, password, name, lastName, phone, role);
                }
            }
        });

        loginRedirectText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(SignUpActivity.this, TypeSelection.class));
                finish();
            }
        });
    }

    private boolean validateFields(String email, String password, String name, String lastName) {
        if (email.isEmpty()) {
            signupEmail.setError("El correo es obligatorio");
            return false;
        }

        if (password.isEmpty()) {
            signupPassword.setError("La contraseña es obligatoria");
            return false;
        }

        if (password.length() < 6) {
            signupPassword.setError("La contraseña debe tener al menos 6 caracteres");
            return false;
        }

        if (name.isEmpty()) {
            signupName.setError("El nombre es obligatorio");
            return false;
        }

        if (lastName.isEmpty()) {
            signupLastName.setError("El apellido es obligatorio");
            return false;
        }

        return true;
    }

    // En el método registerUser de SignUpActivity
    private void registerUser(String email, String password, String name, String lastName,
                              String phone, String role) {
        // Mostrar progreso
        signupButton.setEnabled(false);

        authService.registerUser(email, password, role, phone)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful() && task.getResult() != null) {
                            // Crear perfil de usuario
                            String userId = task.getResult().getUser().getUid();
                            createUserProfile(userId, name, lastName, phone);

                            Toast.makeText(SignUpActivity.this,
                                    "Registro exitoso", Toast.LENGTH_SHORT).show();

                            // Redireccionar según el rol
                            Intent intent;
                            if (role.equals("conductor")) {
                                intent = new Intent(SignUpActivity.this, LoginConductor.class);
                            } else {
                                intent = new Intent(SignUpActivity.this, LoginActivity.class);
                            }
                            startActivity(intent);
                            finish();
                        } else {
                            Toast.makeText(SignUpActivity.this,
                                    "Error al registrar: " + task.getException().getMessage(),
                                    Toast.LENGTH_SHORT).show();
                            signupButton.setEnabled(true);
                        }
                    }
                });
    }

    private void createUserProfile(String userId, String name, String lastName, String phone) {
        Map<String, Object> profileData = new HashMap<>();
        profileData.put("nombre", name);
        profileData.put("apellido", lastName);
        profileData.put("telefono", phone);
        profileData.put("fechaNacimiento", null);
        profileData.put("direccion", "");
        profileData.put("documentoIdentidad", "");
        profileData.put("tipoDocumento", "");
        profileData.put("genero", "");
        profileData.put("fotoPerfil", "");

        authService.createUserProfile(userId, profileData);
    }
}