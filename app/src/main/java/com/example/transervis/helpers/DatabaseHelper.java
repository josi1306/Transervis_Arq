package com.example.transervis.helpers;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class DatabaseHelper {
    private final FirebaseFirestore db;

    public DatabaseHelper() {
        db = FirebaseFirestore.getInstance();
    }

    // Usuarios
    public Task<DocumentSnapshot> getUserData(String userId) {
        return db.collection("users").document(userId).get();
    }

    public Task<Void> updateUserData(String userId, Map<String, Object> data) {
        return db.collection("users").document(userId).update(data);
    }

    // Perfiles
    public Task<DocumentSnapshot> getUserProfile(String userId) {
        return db.collection("profiles").document(userId).get();
    }

    public Task<Void> updateUserProfile(String userId, Map<String, Object> data) {
        return db.collection("profiles").document(userId).update(data);
    }

    // Ubicaciones de conductores
    public Task<DocumentSnapshot> getDriverLocation(String driverId) {
        return db.collection("driver_locations").document(driverId).get();
    }

    public Task<QuerySnapshot> getAvailableDrivers() {
        return db.collection("driver_locations")
                .whereEqualTo("isAvailable", true)
                .get();
    }

    public Task<Void> updateDriverLocation(String driverId, GeoPoint location, double heading, double speed) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("location", location);
        updates.put("heading", heading);
        updates.put("speed", speed);
        updates.put("timestamp", new Date());

        return db.collection("driver_locations").document(driverId).update(updates);
    }

    public Task<Void> updateDriverAvailability(String driverId, boolean isAvailable) {
        return db.collection("driver_locations").document(driverId)
                .update("isAvailable", isAvailable);
    }

    // Ganancias de conductores
    public Task<DocumentSnapshot> getDriverEarnings(String driverId) {
        return db.collection("driver_earnings").document(driverId).get();
    }

    // Calificaciones
    public Task<DocumentReference> addRating(Map<String, Object> ratingData) {
        return db.collection("ratings").add(ratingData);
    }

    public Task<QuerySnapshot> getUserRatings(String userId) {
        return db.collection("ratings")
                .whereEqualTo("toUserId", userId)
                .get();
    }
}