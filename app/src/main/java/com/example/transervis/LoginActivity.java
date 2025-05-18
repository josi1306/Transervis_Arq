package com.example.transervis;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import androidx.annotation.NonNull;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.transervis.services.AuthService;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;

public class LoginActivity extends AppCompatActivity {
    private EditText loginEmail, loginPassword;
    private TextView signupRedirectText;
    private Button loginButton;
    private TextView forgotPassword;
    private AuthService authService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authService = new AuthService();

        loginEmail = findViewById(R.id.login_email);
        loginPassword = findViewById(R.id.login_password);
        loginButton = findViewById(R.id.login_button);
        signupRedirectText = findViewById(R.id.signUpRedirectText);
        forgotPassword = findViewById(R.id.forgot_password);

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = loginEmail.getText().toString().trim();
                String pass = loginPassword.getText().toString().trim();

                if (!email.isEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    if (!pass.isEmpty()) {
                        loginButton.setEnabled(false);

                        // Iniciar sesión con verificación de rol
                        authService.loginWithRoleCheck(email, pass, "pasajero", roleCheckListener)
                                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                                    @Override
                                    public void onComplete(@NonNull Task<AuthResult> task) {
                                        if (!task.isSuccessful()) {
                                            Toast.makeText(LoginActivity.this,
                                                    "Error al iniciar sesión: " + task.getException().getMessage(),
                                                    Toast.LENGTH_SHORT).show();
                                            loginButton.setEnabled(true);
                                        }
                                    }
                                });
                    } else {
                        loginPassword.setError("La contraseña es obligatoria");
                    }
                } else if (email.isEmpty()) {
                    loginEmail.setError("El correo es obligatorio");
                } else {
                    loginEmail.setError("Por favor ingresa un correo válido");
                }
            }
        });

        signupRedirectText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(LoginActivity.this, SignUpActivity.class));
            }
        });

        forgotPassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showForgotPasswordDialog();
            }
        });
    }

    private OnCompleteListener<String> roleCheckListener = new OnCompleteListener<String>() {
        @Override
        public void onComplete(@NonNull Task<String> task) {
            loginButton.setEnabled(true);

            if (task.isSuccessful() && task.getResult() != null) {
                // El rol coincide, redirigir a la pantalla principal
                Toast.makeText(LoginActivity.this, "Bienvenido a Transervis", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(LoginActivity.this, PassengerMainActivity.class));
                finish();
            } else {
                // Verificar si es admin antes de denegar acceso
                String userId = authService.getCurrentUser().getUid();
                authService.isAdmin(userId, adminCheckTask -> {
                    if (adminCheckTask.isSuccessful() && Boolean.TRUE.equals(adminCheckTask.getResult())) {

                        //Ir a la interfaz de admin (descomentar si implementas AdminDashboardActivity)
                        startActivity(new Intent(LoginActivity.this, AdminDashboardActivity.class));

                        finish();
                    } else {
                        // Ni rol correcto ni admin, denegar acceso
                        authService.signOut();
                        Toast.makeText(LoginActivity.this,
                                "Acceso denegado. Esta cuenta no está registrada como pasajero.",
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }
    };

    private void showForgotPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(LoginActivity.this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_forgot, null);
        EditText emailBox = dialogView.findViewById(R.id.emailBox);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        dialogView.findViewById(R.id.btnReset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String userEmail = emailBox.getText().toString().trim();
                if (TextUtils.isEmpty(userEmail) || !Patterns.EMAIL_ADDRESS.matcher(userEmail).matches()) {
                    Toast.makeText(LoginActivity.this, "Ingresa un correo válido", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Usar el nuevo método de AuthService
                authService.sendPasswordResetEmail(userEmail)
                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> task) {
                                if (task.isSuccessful()) {
                                    Toast.makeText(LoginActivity.this, "Revisa tu correo", Toast.LENGTH_SHORT).show();
                                    dialog.dismiss();
                                } else {
                                    Toast.makeText(LoginActivity.this, "No se pudo enviar el correo", Toast.LENGTH_SHORT).show();
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