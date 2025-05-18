package com.example.transervis;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.transervis.services.AuthService;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;

public class LoginConductor extends AppCompatActivity {
    private EditText loginConductorEmail, loginConductorPassword;
    private Button loginConductorButton;
    private TextView forgotConductorPassword;
    private AuthService authService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_conductor);

        authService = new AuthService();

        loginConductorEmail = findViewById(R.id.loginConductor_email);
        loginConductorPassword = findViewById(R.id.loginConductor_password);
        loginConductorButton = findViewById(R.id.loginConductor_button);
        forgotConductorPassword = findViewById(R.id.forgotConductor_password);

        loginConductorButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = loginConductorEmail.getText().toString().trim();
                String pass = loginConductorPassword.getText().toString().trim();

                if (!email.isEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    if (!pass.isEmpty()) {
                        loginConductorButton.setEnabled(false);

                        // Iniciar sesión con verificación de rol
                        authService.loginWithRoleCheck(email, pass, "conductor", roleCheckListener)
                                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                                    @Override
                                    public void onComplete(@NonNull Task<AuthResult> task) {
                                        if (!task.isSuccessful()) {
                                            Toast.makeText(LoginConductor.this,
                                                    "Error al iniciar sesión: " + task.getException().getMessage(),
                                                    Toast.LENGTH_SHORT).show();
                                            loginConductorButton.setEnabled(true);
                                        }
                                    }
                                });
                    } else {
                        loginConductorPassword.setError("La contraseña es obligatoria");
                    }
                } else if (email.isEmpty()) {
                    loginConductorEmail.setError("El correo es obligatorio");
                } else {
                    loginConductorEmail.setError("Por favor ingresa un correo válido");
                }
            }
        });

        forgotConductorPassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showForgotPasswordDialog();
            }
        });
    }

    private OnCompleteListener<String> roleCheckListener = new OnCompleteListener<String>() {
        @Override
        public void onComplete(@NonNull Task<String> task) {
            loginConductorButton.setEnabled(true);

            if (task.isSuccessful() && task.getResult() != null) {
                // El rol coincide, redirigir a la pantalla principal
                Toast.makeText(LoginConductor.this, "Inicio de sesión exitoso", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(LoginConductor.this, DriverMainActivity.class));
                finish();
            } else {
                // Verificar si es admin antes de denegar acceso
                String userId = authService.getCurrentUser().getUid();
                authService.isAdmin(userId, adminCheckTask -> {
                    if (adminCheckTask.isSuccessful() && Boolean.TRUE.equals(adminCheckTask.getResult())) {

                        // Opción 2: Ir a la interfaz de admin (descomentar si implementas AdminDashboardActivity)
                        startActivity(new Intent(LoginConductor.this, AdminDashboardActivity.class));

                        finish();
                    } else {
                        // Ni rol correcto ni admin, denegar acceso
                        authService.signOut();
                        Toast.makeText(LoginConductor.this,
                                "Acceso denegado. Esta cuenta no está registrada como conductor.",
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }
    };
    private void showForgotPasswordDialog() {
        // [Código del diálogo, igual que en LoginActivity]
        AlertDialog.Builder builder = new AlertDialog.Builder(LoginConductor.this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_forgot, null);
        EditText emailBox = dialogView.findViewById(R.id.emailBox);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        // En LoginConductor.java, haz el mismo cambio
        dialogView.findViewById(R.id.btnReset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String userEmail = emailBox.getText().toString().trim();
                if (TextUtils.isEmpty(userEmail) || !Patterns.EMAIL_ADDRESS.matcher(userEmail).matches()) {
                    Toast.makeText(LoginConductor.this, "Ingresa un correo válido", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Usar el nuevo método de AuthService
                authService.sendPasswordResetEmail(userEmail)
                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> task) {
                                if (task.isSuccessful()) {
                                    Toast.makeText(LoginConductor.this, "Revisa tu correo", Toast.LENGTH_SHORT).show();
                                    dialog.dismiss();
                                } else {
                                    Toast.makeText(LoginConductor.this, "No se pudo enviar el correo", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
            }
        });

        dialogView.findViewById(R.id.btnCancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0));
        }
        dialog.show();
    }
}