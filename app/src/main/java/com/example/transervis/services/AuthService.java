package com.example.transervis.services;

import androidx.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AuthService {
    private static final String TAG = "AuthService";
    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    // Constantes de roles
    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_CONDUCTOR = "conductor";
    public static final String ROLE_PASAJERO = "pasajero";

    public AuthService() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    public void signOut() {
        auth.signOut();
    }

    // Registrar un nuevo usuario con rol específico
    public Task<AuthResult> registerUser(String email, String password, String role, String phone) {
        return auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user != null) {
                        // Crear documento de usuario en Firestore
                        createUserDocument(user.getUid(), email, role, phone);
                    }
                });
    }

    // Iniciar sesión verificando el rol
    public Task<AuthResult> loginWithRoleCheck(String email, String password, String expectedRole,
                                               OnCompleteListener<String> roleCheckListener) {
        return auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user != null) {
                        // Verificar el rol del usuario
                        checkUserRole(user.getUid(), expectedRole, roleCheckListener);
                    }
                });
    }

    // Crear documento de usuario en Firestore
    private void createUserDocument(String userId, String email, String role, String phone) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("email", email);
        userData.put("role", role);
        userData.put("createdAt", System.currentTimeMillis());
        userData.put("updatedAt", System.currentTimeMillis());
        userData.put("profileCompleted", false);
        userData.put("fcmToken", "");
        userData.put("phone", phone != null ? phone : "");

        db.collection("users").document(userId)
                .set(userData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Usuario creado con éxito"))
                .addOnFailureListener(e -> Log.e(TAG, "Error al crear usuario", e));
    }

    // Crear perfil del usuario
    public void createUserProfile(String userId, Map<String, Object> profileData) {
        db.collection("profiles").document(userId)
                .set(profileData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Perfil creado con éxito"))
                .addOnFailureListener(e -> Log.e(TAG, "Error al crear perfil", e));
    }

    // Verificar rol del usuario
    public void checkUserRole(String userId, String expectedRole, OnCompleteListener<String> listener) {
        db.collection("users").document(userId).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            String role = document.getString("role");
                            boolean matches = role != null && role.equals(expectedRole);

                            TaskCompletionSource<String> tcs = new TaskCompletionSource<>();
                            if (matches) {
                                tcs.setResult(role);
                            } else {
                                tcs.setResult(null);
                            }
                            listener.onComplete(tcs.getTask());
                        } else {
                            TaskCompletionSource<String> tcs = new TaskCompletionSource<>();
                            tcs.setResult(null);
                            listener.onComplete(tcs.getTask());
                        }
                    } else {
                        TaskCompletionSource<String> tcs = new TaskCompletionSource<>();
                        tcs.setResult(null);
                        listener.onComplete(tcs.getTask());
                    }
                });
    }

    // Verificar si el usuario es administrador
    public void isAdmin(String userId, OnCompleteListener<Boolean> listener) {
        db.collection("users").document(userId).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            String role = document.getString("role");
                            boolean isAdmin = ROLE_ADMIN.equals(role);

                            TaskCompletionSource<Boolean> tcs = new TaskCompletionSource<>();
                            tcs.setResult(isAdmin);
                            listener.onComplete(tcs.getTask());
                        } else {
                            TaskCompletionSource<Boolean> tcs = new TaskCompletionSource<>();
                            tcs.setResult(false);
                            listener.onComplete(tcs.getTask());
                        }
                    } else {
                        TaskCompletionSource<Boolean> tcs = new TaskCompletionSource<>();
                        tcs.setResult(false);
                        listener.onComplete(tcs.getTask());
                    }
                });
    }

    // Método para restablecer contraseña
    public Task<Void> sendPasswordResetEmail(String email) {
        return auth.sendPasswordResetEmail(email);
    }
}