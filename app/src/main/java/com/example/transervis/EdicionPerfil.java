package com.example.transervis;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.transervis.services.AuthService;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class EdicionPerfil extends AppCompatActivity {

    private static final String TAG = "EdicionPerfil";

    // Componentes de la UI - Información General
    private Button btnEditarPerfil, btnGuardarPerfil, btnCancelar;
    private EditText editTextNombre, editTextApellido, editTextTelefono;
    private EditText editTextDireccion, editTextDocumento, editTextCiudad;
    private EditText editTextFechaNacimiento;
    private Spinner spinnerGenero, spinnerTipoDocumento;
    private TextView textBienvenido, textRolUsuario;
    private ImageButton btnDatePicker;

    // Componentes de la UI - Conductor (Licencia)
    private CardView cardViewLicencia;
    private EditText editTextLicencia, editTextFechaExpedicion, editTextFechaVencimiento;
    private Spinner spinnerCategoriaLicencia;
    private ImageButton btnExpedicionPicker, btnVencimientoPicker;

    // Componentes de la UI - Conductor (Vehículo)
    private CardView cardViewVehiculo;
    private EditText editTextPlaca, editTextModelo, editTextCapacidad;
    private Spinner spinnerMarcaVehiculo, spinnerTipoVehiculo;
    private CheckBox checkBoxSoat, checkBoxTecnomecanica;
    private Button btnAdjuntarSoat, btnAdjuntarTecnomecanica;
    private TextView textViewSoatFileName, textViewTecnomecanicaFileName;

    // Firebase
    private AuthService authService;
    private FirebaseFirestore db;
    private String userId;
    private String userRole;
    private String vehiculoId;

    // Datos originales (para restaurar en caso de cancelar)
    private Map<String, Object> datosOriginalesPerfil = new HashMap<>();
    private Map<String, Object> datosOriginalesLicencia = new HashMap<>();
    private Map<String, Object> datosOriginalesVehiculo = new HashMap<>();

    // Date format
    private SimpleDateFormat dateFormatter = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    // Archivo seleccionado (simulación)
    private Uri soatFileUri = null;
    private Uri tecnoFileUri = null;

    // Activity Result Launcher para selección de archivos
    private ActivityResultLauncher<String> soatPickerLauncher;
    private ActivityResultLauncher<String> tecnoPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edicion_perfil);

        // Inicializar Firebase
        authService = new AuthService();
        db = FirebaseFirestore.getInstance();

        // Verificar si hay usuario autenticado
        FirebaseUser currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "No hay sesión activa", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        userId = currentUser.getUid();

        // Inicializar Activity Result Launchers para selección de archivos
        initFilePickers();

        // Inicializar componentes
        initViews();

        // Configurar adapters para spinners
        setupSpinners();

        // Determinar rol de usuario
        determineUserRole();

        // Configurar listeners
        setupListeners();
    }

    private void initFilePickers() {
        // Configurar launcher para selección de SOAT
        soatPickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        soatFileUri = uri;
                        String fileName = getFileNameFromUri(uri);
                        textViewSoatFileName.setText(fileName);
                    }
                });

        // Configurar launcher para selección de Tecnicomecanica
        tecnoPickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        tecnoFileUri = uri;
                        String fileName = getFileNameFromUri(uri);
                        textViewTecnomecanicaFileName.setText(fileName);
                    }
                });
    }

    private String getFileNameFromUri(Uri uri) {
        String result = "Archivo seleccionado";
        try {
            String path = uri.getPath();
            if (path != null) {
                int cut = path.lastIndexOf('/');
                if (cut != -1) {
                    result = path.substring(cut + 1);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting file name", e);
        }
        return result;
    }

    private void initViews() {
        // Encabezado
        textBienvenido = findViewById(R.id.textBienvenido);
        textRolUsuario = findViewById(R.id.textRolUsuario);

        // Botones principales
        btnEditarPerfil = findViewById(R.id.btnEditarPerfil);
        btnGuardarPerfil = findViewById(R.id.btnGuardarPerfil);
        btnCancelar = findViewById(R.id.btnCancelar);

        // Información personal
        editTextNombre = findViewById(R.id.editTextNombre);
        editTextApellido = findViewById(R.id.editTextApellido);
        editTextTelefono = findViewById(R.id.editTextTelefono);
        editTextFechaNacimiento = findViewById(R.id.editTextFechaNacimiento);
        btnDatePicker = findViewById(R.id.btnDatePicker);

        // Identificación
        spinnerTipoDocumento = findViewById(R.id.spinnerTipoDocumento);
        editTextDocumento = findViewById(R.id.editTextDocumento);

        // Contacto y ubicación
        editTextCiudad = findViewById(R.id.editTextCiudad);
        editTextDireccion = findViewById(R.id.editTextDireccion);
        spinnerGenero = findViewById(R.id.spinnerGenero);

        // Licencia de conducción (sección conductor)
        cardViewLicencia = findViewById(R.id.cardViewLicencia);
        editTextLicencia = findViewById(R.id.editTextLicencia);
        spinnerCategoriaLicencia = findViewById(R.id.spinnerCategoriaLicencia);
        editTextFechaExpedicion = findViewById(R.id.editTextFechaExpedicion);
        editTextFechaVencimiento = findViewById(R.id.editTextFechaVencimiento);
        btnExpedicionPicker = findViewById(R.id.btnExpedicionPicker);
        btnVencimientoPicker = findViewById(R.id.btnVencimientoPicker);

        // Vehículo (sección conductor)
        cardViewVehiculo = findViewById(R.id.cardViewVehiculo);
        editTextPlaca = findViewById(R.id.editTextPlaca);
        spinnerMarcaVehiculo = findViewById(R.id.spinnerMarcaVehiculo);
        editTextModelo = findViewById(R.id.editTextModelo);
        editTextCapacidad = findViewById(R.id.editTextCapacidad);
        spinnerTipoVehiculo = findViewById(R.id.spinnerTipoVehiculo);
        checkBoxSoat = findViewById(R.id.checkBoxSoat);
        checkBoxTecnomecanica = findViewById(R.id.checkBoxTecnomecanica);
        btnAdjuntarSoat = findViewById(R.id.btnAdjuntarSoat);
        btnAdjuntarTecnomecanica = findViewById(R.id.btnAdjuntarTecnomecanica);
        textViewSoatFileName = findViewById(R.id.textViewSoatFileName);
        textViewTecnomecanicaFileName = findViewById(R.id.textViewTecnomecanicaFileName);

        // Configurar filtros para placa (solo mayúsculas y máximo 6 caracteres)
        editTextPlaca.setFilters(new InputFilter[]{
                new InputFilter.AllCaps(),
                new InputFilter.LengthFilter(6)
        });

        // Configurar capitalization para el nombre y apellido
        editTextNombre.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        editTextApellido.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
    }

    private void setupSpinners() {
        // Spinner de género
        ArrayAdapter<CharSequence> generoAdapter = ArrayAdapter.createFromResource(this,
                R.array.generos, android.R.layout.simple_spinner_item);
        generoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGenero.setAdapter(generoAdapter);

        // Spinner de tipo de documento
        ArrayAdapter<CharSequence> tipoDocumentoAdapter = ArrayAdapter.createFromResource(this,
                R.array.tipos_documento, android.R.layout.simple_spinner_item);
        tipoDocumentoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTipoDocumento.setAdapter(tipoDocumentoAdapter);

        // Spinner de categoría de licencia
        ArrayAdapter<CharSequence> categoriaLicenciaAdapter = ArrayAdapter.createFromResource(this,
                R.array.categorias_licencia, android.R.layout.simple_spinner_item);
        categoriaLicenciaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategoriaLicencia.setAdapter(categoriaLicenciaAdapter);

        // Spinner de marca de vehículo
        ArrayAdapter<CharSequence> marcaAdapter = ArrayAdapter.createFromResource(this,
                R.array.tipos_marca, android.R.layout.simple_spinner_item);
        marcaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMarcaVehiculo.setAdapter(marcaAdapter);

        // Spinner de tipo de vehículo
        ArrayAdapter<CharSequence> tipoVehiculoAdapter = ArrayAdapter.createFromResource(this,
                R.array.tipos_vehiculo, android.R.layout.simple_spinner_item);
        tipoVehiculoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTipoVehiculo.setAdapter(tipoVehiculoAdapter);
    }

    private void determineUserRole() {
        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        userRole = documentSnapshot.getString("role");

                        // Actualizar UI según el rol
                        if (userRole != null) {
                            textRolUsuario.setText("Tipo de usuario: " + userRole.substring(0, 1).toUpperCase() + userRole.substring(1));

                            // Mostrar secciones de conductor si el rol es "conductor"
                            if (userRole.equals("conductor")) {
                                cardViewLicencia.setVisibility(View.VISIBLE);
                                cardViewVehiculo.setVisibility(View.VISIBLE);
                            } else {
                                cardViewLicencia.setVisibility(View.GONE);
                                cardViewVehiculo.setVisibility(View.GONE);
                            }
                        }

                        // Cargar datos del perfil
                        loadProfileData();
                    } else {
                        Toast.makeText(EdicionPerfil.this,
                                "No se encontró información de usuario",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(EdicionPerfil.this,
                            "Error al determinar el rol: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error determining user role", e);
                });
    }

    private void loadProfileData() {
        // Mostrar mensaje de carga
        Toast.makeText(this, "Cargando perfil...", Toast.LENGTH_SHORT).show();

        // 1. Consultar datos del perfil básico
        db.collection("profiles").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        updateUIWithProfileData(documentSnapshot);
                    } else {
                        Toast.makeText(EdicionPerfil.this,
                                "No se encontró información de perfil",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(EdicionPerfil.this,
                            "Error al cargar perfil: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error loading profile data", e);
                });

        // 2. Si es conductor, cargar datos adicionales
        if (userRole != null && userRole.equals("conductor")) {
            // Cargar información de licencia
            db.collection("conductor_profiles").document(userId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            updateUIWithLicenseData(documentSnapshot);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error loading conductor profile data", e);
                    });

            // Cargar información del vehículo
            db.collection("vehiculos")
                    .whereEqualTo("conductorId", userId)
                    .limit(1)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        if (!queryDocumentSnapshots.isEmpty()) {
                            for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                                vehiculoId = document.getId();
                                updateUIWithVehicleData(document);
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error loading vehicle data", e);
                    });
        }
    }

    private void updateUIWithProfileData(DocumentSnapshot document) {
        // Guardar datos originales
        datosOriginalesPerfil.put("nombre", document.getString("nombre"));
        datosOriginalesPerfil.put("apellido", document.getString("apellido"));
        datosOriginalesPerfil.put("telefono", document.getString("telefono"));
        datosOriginalesPerfil.put("direccion", document.getString("direccion"));
        datosOriginalesPerfil.put("ciudad_residencia", document.getString("ciudad_residencia"));
        datosOriginalesPerfil.put("documentoIdentidad", document.getString("documentoIdentidad"));
        datosOriginalesPerfil.put("tipoDocumento", document.getString("tipoDocumento"));
        datosOriginalesPerfil.put("genero", document.getString("genero"));

        // Fecha de nacimiento (convertir de timestamp a string)
        Date fechaNacimiento = document.getDate("fechaNacimiento");
        if (fechaNacimiento != null) {
            String fechaStr = dateFormatter.format(fechaNacimiento);
            datosOriginalesPerfil.put("fechaNacimiento", fechaStr);
        } else {
            datosOriginalesPerfil.put("fechaNacimiento", "");
        }

        // Actualizar UI
        editTextNombre.setText(document.getString("nombre"));
        editTextApellido.setText(document.getString("apellido"));
        editTextTelefono.setText(document.getString("telefono"));
        editTextDireccion.setText(document.getString("direccion"));
        editTextCiudad.setText(document.getString("ciudad_residencia"));
        editTextDocumento.setText(document.getString("documentoIdentidad"));

        // Actualizar fecha de nacimiento
        if (fechaNacimiento != null) {
            editTextFechaNacimiento.setText(dateFormatter.format(fechaNacimiento));
        }

        // Actualizar spinner de tipo de documento
        String tipoDocumento = document.getString("tipoDocumento");
        if (tipoDocumento != null && !tipoDocumento.isEmpty()) {
            ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>) spinnerTipoDocumento.getAdapter();
            for (int i = 0; i < adapter.getCount(); i++) {
                if (adapter.getItem(i).toString().equalsIgnoreCase(tipoDocumento)) {
                    spinnerTipoDocumento.setSelection(i);
                    break;
                }
            }
        }

        // Actualizar spinner de género
        String genero = document.getString("genero");
        if (genero != null && !genero.isEmpty()) {
            ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>) spinnerGenero.getAdapter();
            for (int i = 0; i < adapter.getCount(); i++) {
                if (adapter.getItem(i).toString().equalsIgnoreCase(genero)) {
                    spinnerGenero.setSelection(i);
                    break;
                }
            }
        }

        // Actualizar bienvenida con nombre completo
        String nombreCompleto = (document.getString("nombre") != null ? document.getString("nombre") : "") +
                " " +
                (document.getString("apellido") != null ? document.getString("apellido") : "");
        if (!nombreCompleto.trim().isEmpty()) {
            textBienvenido.setText("Perfil de " + nombreCompleto.trim());
        }
    }

    private void updateUIWithLicenseData(DocumentSnapshot document) {
        // Guardar datos originales
        datosOriginalesLicencia.put("licencia_conduccion", document.getString("licencia_conduccion"));
        datosOriginalesLicencia.put("categoria_licencia", document.getString("categoria_licencia"));

        // Fechas
        Date fechaExpedicion = document.getDate("fecha_expedicion");
        Date fechaVencimiento = document.getDate("fecha_vencimiento");

        if (fechaExpedicion != null) {
            String fechaExpStr = dateFormatter.format(fechaExpedicion);
            datosOriginalesLicencia.put("fecha_expedicion", fechaExpStr);
        } else {
            datosOriginalesLicencia.put("fecha_expedicion", "");
        }

        if (fechaVencimiento != null) {
            String fechaVencStr = dateFormatter.format(fechaVencimiento);
            datosOriginalesLicencia.put("fecha_vencimiento", fechaVencStr);
        } else {
            datosOriginalesLicencia.put("fecha_vencimiento", "");
        }

        // Actualizar UI
        editTextLicencia.setText(document.getString("licencia_conduccion"));

        // Actualizar spinner de categoría de licencia
        String categoriaLicencia = document.getString("categoria_licencia");
        if (categoriaLicencia != null && !categoriaLicencia.isEmpty()) {
            ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>) spinnerCategoriaLicencia.getAdapter();
            for (int i = 0; i < adapter.getCount(); i++) {
                if (adapter.getItem(i).toString().equalsIgnoreCase(categoriaLicencia)) {
                    spinnerCategoriaLicencia.setSelection(i);
                    break;
                }
            }
        }

        // Actualizar fechas
        if (fechaExpedicion != null) {
            editTextFechaExpedicion.setText(dateFormatter.format(fechaExpedicion));
        }

        if (fechaVencimiento != null) {
            editTextFechaVencimiento.setText(dateFormatter.format(fechaVencimiento));
        }
    }

    private void updateUIWithVehicleData(DocumentSnapshot document) {
        // Guardar datos originales
        datosOriginalesVehiculo.put("placa", document.getString("placa"));
        datosOriginalesVehiculo.put("marca", document.getString("marca"));
        datosOriginalesVehiculo.put("modelo", document.getString("modelo"));
        datosOriginalesVehiculo.put("capacidad_pasajeros", document.getLong("capacidad_pasajeros"));
        datosOriginalesVehiculo.put("tipo_vehiculo", document.getString("tipo_vehiculo"));
        datosOriginalesVehiculo.put("soat_vigente", document.getBoolean("soat_vigente"));
        datosOriginalesVehiculo.put("tecnomecanica_vigente", document.getBoolean("tecnomecanica_vigente"));

        // Actualizar UI
        editTextPlaca.setText(document.getString("placa"));
        editTextModelo.setText(document.getString("modelo"));

        // Capacidad de pasajeros
        Long capacidad = document.getLong("capacidad_pasajeros");
        if (capacidad != null) {
            editTextCapacidad.setText(String.valueOf(capacidad));
        }

        // Checkboxes
        Boolean soatVigente = document.getBoolean("soat_vigente");
        if (soatVigente != null) {
            checkBoxSoat.setChecked(soatVigente);
        }

        Boolean tecnoVigente = document.getBoolean("tecnomecanica_vigente");
        if (tecnoVigente != null) {
            checkBoxTecnomecanica.setChecked(tecnoVigente);
        }

        // Actualizar spinner de marca de vehículo
        String marca = document.getString("marca");
        if (marca != null && !marca.isEmpty()) {
            ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>) spinnerMarcaVehiculo.getAdapter();
            for (int i = 0; i < adapter.getCount(); i++) {
                if (adapter.getItem(i).toString().equalsIgnoreCase(marca)) {
                    spinnerMarcaVehiculo.setSelection(i);
                    break;
                }
            }
        }

        // Actualizar spinner de tipo de vehículo
        String tipoVehiculo = document.getString("tipo_vehiculo");
        if (tipoVehiculo != null && !tipoVehiculo.isEmpty()) {
            ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>) spinnerTipoVehiculo.getAdapter();
            for (int i = 0; i < adapter.getCount(); i++) {
                if (adapter.getItem(i).toString().equalsIgnoreCase(tipoVehiculo)) {
                    spinnerTipoVehiculo.setSelection(i);
                    break;
                }
            }
        }

        // Mostrar nombres de archivos si existen
        Map<String, Object> documentos = (Map<String, Object>) document.get("documentos");
        if (documentos != null) {
            String soatArchivo = (String) documentos.get("soat_archivo");
            if (soatArchivo != null && !soatArchivo.isEmpty()) {
                textViewSoatFileName.setText("Archivo guardado en la base de datos");
            }

            String tecnoArchivo = (String) documentos.get("tecnomecanica_archivo");
            if (tecnoArchivo != null && !tecnoArchivo.isEmpty()) {
                textViewTecnomecanicaFileName.setText("Archivo guardado en la base de datos");
            }
        }
    }

    private void setupListeners() {
        // Botón Editar Perfil
        btnEditarPerfil.setOnClickListener(v -> {
            // Habilitar edición
            setEditMode(true);
        });

        // Botón Guardar Perfil
        btnGuardarPerfil.setOnClickListener(v -> {
            // Validar y guardar cambios
            if (validateFields()) {
                saveProfileChanges();
            }
        });

        // Botón Cancelar
        btnCancelar.setOnClickListener(v -> {
            // Cancelar edición
            cancelEdit();
        });

        // DatePicker para fecha de nacimiento
        btnDatePicker.setOnClickListener(v -> {
            showDatePickerDialog(editTextFechaNacimiento);
        });

        // DatePicker para fechas de licencia
        btnExpedicionPicker.setOnClickListener(v -> {
            showDatePickerDialog(editTextFechaExpedicion);
        });

        btnVencimientoPicker.setOnClickListener(v -> {
            showDatePickerDialog(editTextFechaVencimiento);
        });

        // Botones para adjuntar archivos
        btnAdjuntarSoat.setOnClickListener(v -> {
            soatPickerLauncher.launch("application/pdf");
        });

        btnAdjuntarTecnomecanica.setOnClickListener(v -> {
            tecnoPickerLauncher.launch("application/pdf");
        });
    }

    private void updateUIAfterSave() {
        // Actualizar nombre en la bienvenida
        String nombreCompleto = editTextNombre.getText().toString().trim() + " " +
                editTextApellido.getText().toString().trim();
        textBienvenido.setText("Perfil de " + nombreCompleto);

        // Mostrar mensaje de éxito
        Toast.makeText(EdicionPerfil.this,
                "Perfil actualizado correctamente",
                Toast.LENGTH_SHORT).show();

        // Reiniciar estados de archivos seleccionados
        soatFileUri = null;
        tecnoFileUri = null;

        // Volver a modo visualización
        setEditMode(false);
        btnGuardarPerfil.setEnabled(true);
    }

    private void cancelEdit() {
        // Restaurar datos originales del perfil básico
        editTextNombre.setText((String) datosOriginalesPerfil.get("nombre"));
        editTextApellido.setText((String) datosOriginalesPerfil.get("apellido"));
        editTextTelefono.setText((String) datosOriginalesPerfil.get("telefono"));
        editTextDireccion.setText((String) datosOriginalesPerfil.get("direccion"));
        editTextCiudad.setText((String) datosOriginalesPerfil.get("ciudad_residencia"));
        editTextDocumento.setText((String) datosOriginalesPerfil.get("documentoIdentidad"));
        editTextFechaNacimiento.setText((String) datosOriginalesPerfil.get("fechaNacimiento"));

        // Restaurar selección de spinner tipo documento
        String tipoDocumento = (String) datosOriginalesPerfil.get("tipoDocumento");
        if (tipoDocumento != null && !tipoDocumento.isEmpty()) {
            ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>) spinnerTipoDocumento.getAdapter();
            for (int i = 0; i < adapter.getCount(); i++) {
                if (adapter.getItem(i).toString().equalsIgnoreCase(tipoDocumento)) {
                    spinnerTipoDocumento.setSelection(i);
                    break;
                }
            }
        }

        // Restaurar selección de spinner género
        String genero = (String) datosOriginalesPerfil.get("genero");
        if (genero != null && !genero.isEmpty()) {
            ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>) spinnerGenero.getAdapter();
            for (int i = 0; i < adapter.getCount(); i++) {
                if (adapter.getItem(i).toString().equalsIgnoreCase(genero)) {
                    spinnerGenero.setSelection(i);
                    break;
                }
            }
        }

        // Si es conductor, restaurar datos adicionales
        if (userRole != null && userRole.equals("conductor")) {
            // Restaurar datos de licencia
            editTextLicencia.setText((String) datosOriginalesLicencia.get("licencia_conduccion"));
            editTextFechaExpedicion.setText((String) datosOriginalesLicencia.get("fecha_expedicion"));
            editTextFechaVencimiento.setText((String) datosOriginalesLicencia.get("fecha_vencimiento"));

            // Restaurar categoría de licencia
            String categoriaLicencia = (String) datosOriginalesLicencia.get("categoria_licencia");
            if (categoriaLicencia != null && !categoriaLicencia.isEmpty()) {
                ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>) spinnerCategoriaLicencia.getAdapter();
                for (int i = 0; i < adapter.getCount(); i++) {
                    if (adapter.getItem(i).toString().equalsIgnoreCase(categoriaLicencia)) {
                        spinnerCategoriaLicencia.setSelection(i);
                        break;
                    }
                }
            }

            // Restaurar datos de vehículo
            editTextPlaca.setText((String) datosOriginalesVehiculo.get("placa"));
            editTextModelo.setText((String) datosOriginalesVehiculo.get("modelo"));

            // Restaurar capacidad
            Long capacidad = (Long) datosOriginalesVehiculo.get("capacidad_pasajeros");
            if (capacidad != null) {
                editTextCapacidad.setText(String.valueOf(capacidad));
            }

            // Restaurar marca de vehículo
            String marca = (String) datosOriginalesVehiculo.get("marca");
            if (marca != null && !marca.isEmpty()) {
                ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>) spinnerMarcaVehiculo.getAdapter();
                for (int i = 0; i < adapter.getCount(); i++) {
                    if (adapter.getItem(i).toString().equalsIgnoreCase(marca)) {
                        spinnerMarcaVehiculo.setSelection(i);
                        break;
                    }
                }
            }

            // Restaurar tipo de vehículo
            String tipoVehiculo = (String) datosOriginalesVehiculo.get("tipo_vehiculo");
            if (tipoVehiculo != null && !tipoVehiculo.isEmpty()) {
                ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>) spinnerTipoVehiculo.getAdapter();
                for (int i = 0; i < adapter.getCount(); i++) {
                    if (adapter.getItem(i).toString().equalsIgnoreCase(tipoVehiculo)) {
                        spinnerTipoVehiculo.setSelection(i);
                        break;
                    }
                }
            }

            // Restaurar checkboxes
            Boolean soatVigente = (Boolean) datosOriginalesVehiculo.get("soat_vigente");
            if (soatVigente != null) {
                checkBoxSoat.setChecked(soatVigente);
            }

            Boolean tecnoVigente = (Boolean) datosOriginalesVehiculo.get("tecnomecanica_vigente");
            if (tecnoVigente != null) {
                checkBoxTecnomecanica.setChecked(tecnoVigente);
            }

            // Reiniciar estados de archivos seleccionados
            soatFileUri = null;
            tecnoFileUri = null;
            textViewSoatFileName.setText("No hay archivo seleccionado");
            textViewTecnomecanicaFileName.setText("No hay archivo seleccionado");
        }

        // Volver a modo visualización
        setEditMode(false);

        Toast.makeText(this, "Edición cancelada", Toast.LENGTH_SHORT).show();
    }

    private void showDatePickerDialog(final EditText editText) {
        Calendar calendar = Calendar.getInstance();

        // Si ya hay una fecha, usarla como fecha inicial
        if (!editText.getText().toString().isEmpty()) {
            try {
                Date date = dateFormatter.parse(editText.getText().toString());
                if (date != null) {
                    calendar.setTime(date);
                }
            } catch (ParseException e) {
                Log.e(TAG, "Error parsing date", e);
            }
        }

        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                        Calendar selectedCalendar = Calendar.getInstance();
                        selectedCalendar.set(year, month, dayOfMonth);
                        editText.setText(dateFormatter.format(selectedCalendar.getTime()));
                    }
                },
                year, month, day);

        datePickerDialog.show();
    }

    private boolean validateFields() {
        boolean isValid = true;

        // Validar campos básicos
        if (editTextNombre.getText().toString().trim().isEmpty()) {
            editTextNombre.setError("El nombre es obligatorio");
            isValid = false;
        }

        if (editTextApellido.getText().toString().trim().isEmpty()) {
            editTextApellido.setError("El apellido es obligatorio");
            isValid = false;
        }

        if (editTextTelefono.getText().toString().trim().isEmpty()) {
            editTextTelefono.setError("El teléfono es obligatorio");
            isValid = false;
        }

        if (editTextDocumento.getText().toString().trim().isEmpty()) {
            editTextDocumento.setError("El documento es obligatorio");
            isValid = false;
        }

        // Si es conductor, validar campos adicionales
        if (userRole != null && userRole.equals("conductor")) {
            // Validar licencia
            if (editTextLicencia.getText().toString().trim().isEmpty()) {
                editTextLicencia.setError("El número de licencia es obligatorio");
                isValid = false;
            }

            if (editTextFechaExpedicion.getText().toString().trim().isEmpty()) {
                editTextFechaExpedicion.setError("La fecha de expedición es obligatoria");
                isValid = false;
            }

            if (editTextFechaVencimiento.getText().toString().trim().isEmpty()) {
                editTextFechaVencimiento.setError("La fecha de vencimiento es obligatoria");
                isValid = false;
            }

            // Validar vehículo
            if (editTextPlaca.getText().toString().trim().isEmpty()) {
                editTextPlaca.setError("La placa del vehículo es obligatoria");
                isValid = false;
            }

            if (editTextModelo.getText().toString().trim().isEmpty()) {
                editTextModelo.setError("El modelo del vehículo es obligatorio");
                isValid = false;
            }

            // Validar capacidad (mínimo 8 pasajeros)
            if (editTextCapacidad.getText().toString().trim().isEmpty()) {
                editTextCapacidad.setError("La capacidad de pasajeros es obligatoria");
                isValid = false;
            } else {
                try {
                    int capacidad = Integer.parseInt(editTextCapacidad.getText().toString().trim());
                    if (capacidad < 8) {
                        editTextCapacidad.setError("La capacidad mínima debe ser 8 pasajeros");
                        isValid = false;
                    }
                } catch (NumberFormatException e) {
                    editTextCapacidad.setError("Ingrese un número válido");
                    isValid = false;
                }
            }

            // Validar selección de spinners
            if (spinnerCategoriaLicencia.getSelectedItemPosition() == 0) {
                Toast.makeText(this, "Seleccione una categoría de licencia", Toast.LENGTH_SHORT).show();
                isValid = false;
            }

            if (spinnerTipoVehiculo.getSelectedItemPosition() == 0) {
                Toast.makeText(this, "Seleccione un tipo de vehículo", Toast.LENGTH_SHORT).show();
                isValid = false;
            }
        }

        return isValid;
    }

    private void setEditMode(boolean editMode) {
        // Habilitar o deshabilitar campos básicos
        editTextNombre.setEnabled(editMode);
        editTextApellido.setEnabled(editMode);
        editTextTelefono.setEnabled(editMode);
        editTextDireccion.setEnabled(editMode);
        editTextDocumento.setEnabled(editMode);
        editTextCiudad.setEnabled(editMode);
        editTextFechaNacimiento.setEnabled(editMode);
        btnDatePicker.setEnabled(editMode);
        spinnerGenero.setEnabled(editMode);
        spinnerTipoDocumento.setEnabled(editMode);

        // Si es conductor, habilitar campos adicionales
        if (userRole != null && userRole.equals("conductor")) {
            // Licencia
            editTextLicencia.setEnabled(editMode);
            editTextFechaExpedicion.setEnabled(editMode);
            editTextFechaVencimiento.setEnabled(editMode);
            btnExpedicionPicker.setEnabled(editMode);
            btnVencimientoPicker.setEnabled(editMode);
            spinnerCategoriaLicencia.setEnabled(editMode);

            // Vehículo
            editTextPlaca.setEnabled(editMode);
            editTextModelo.setEnabled(editMode);
            editTextCapacidad.setEnabled(editMode);
            spinnerMarcaVehiculo.setEnabled(editMode);
            spinnerTipoVehiculo.setEnabled(editMode);
            checkBoxSoat.setEnabled(editMode);
            checkBoxTecnomecanica.setEnabled(editMode);
            btnAdjuntarSoat.setEnabled(editMode);
            btnAdjuntarTecnomecanica.setEnabled(editMode);
        }

        // Mostrar u ocultar botones
        btnEditarPerfil.setVisibility(editMode ? View.GONE : View.VISIBLE);
        btnGuardarPerfil.setVisibility(editMode ? View.VISIBLE : View.GONE);
        btnCancelar.setVisibility(editMode ? View.VISIBLE : View.GONE);
    }

    private void saveProfileChanges() {
        // Mostrar indicador de carga
        Toast.makeText(this, "Guardando cambios...", Toast.LENGTH_SHORT).show();
        btnGuardarPerfil.setEnabled(false);

        // 1. Guardar información básica del perfil
        saveBasicProfile(() -> {
            // 2. Si es conductor, guardar información adicional
            if (userRole != null && userRole.equals("conductor")) {
                saveConductorProfile(() -> {
                    saveVehicleData(() -> {
                        // Todo guardado, actualizar UI
                        updateUIAfterSave();
                    });
                });
            } else {
                // Solo era perfil básico, actualizar UI
                updateUIAfterSave();
            }
        });
    }

    private void saveBasicProfile(Runnable onSuccess) {
        // Preparar datos del perfil básico
        Map<String, Object> profileData = new HashMap<>();
        profileData.put("nombre", editTextNombre.getText().toString().trim());
        profileData.put("apellido", editTextApellido.getText().toString().trim());
        profileData.put("telefono", editTextTelefono.getText().toString().trim());
        profileData.put("direccion", editTextDireccion.getText().toString().trim());
        profileData.put("ciudad_residencia", editTextCiudad.getText().toString().trim());
        profileData.put("documentoIdentidad", editTextDocumento.getText().toString().trim());
        profileData.put("tipoDocumento", spinnerTipoDocumento.getSelectedItem().toString());
        profileData.put("genero", spinnerGenero.getSelectedItem().toString());

        // Fecha de nacimiento
        String fechaNacimientoStr = editTextFechaNacimiento.getText().toString().trim();
        if (!fechaNacimientoStr.isEmpty()) {
            try {
                Date fechaNacimiento = dateFormatter.parse(fechaNacimientoStr);
                profileData.put("fechaNacimiento", fechaNacimiento);
            } catch (ParseException e) {
                Log.e(TAG, "Error parsing birth date", e);
            }
        }

        // Actualizar en Firestore
        db.collection("profiles").document(userId)
                .update(profileData)
                .addOnSuccessListener(aVoid -> {
                    // Actualizar datos originales
                    datosOriginalesPerfil.clear();
                    datosOriginalesPerfil.putAll(profileData);

                    // Continuar con el siguiente paso
                    onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving basic profile", e);
                    Toast.makeText(EdicionPerfil.this,
                            "Error al guardar perfil básico: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    btnGuardarPerfil.setEnabled(true);
                });
    }

    private void saveConductorProfile(Runnable onSuccess) {
        // Preparar datos del perfil de conductor
        Map<String, Object> conductorData = new HashMap<>();
        conductorData.put("licencia_conduccion", editTextLicencia.getText().toString().trim());
        conductorData.put("categoria_licencia", spinnerCategoriaLicencia.getSelectedItem().toString());

        // Fechas de expedición y vencimiento
        String fechaExpedicionStr = editTextFechaExpedicion.getText().toString().trim();
        if (!fechaExpedicionStr.isEmpty()) {
            try {
                Date fechaExpedicion = dateFormatter.parse(fechaExpedicionStr);
                conductorData.put("fecha_expedicion", fechaExpedicion);
            } catch (ParseException e) {
                Log.e(TAG, "Error parsing expedition date", e);
            }
        }

        String fechaVencimientoStr = editTextFechaVencimiento.getText().toString().trim();
        if (!fechaVencimientoStr.isEmpty()) {
            try {
                Date fechaVencimiento = dateFormatter.parse(fechaVencimientoStr);
                conductorData.put("fecha_vencimiento", fechaVencimiento);
            } catch (ParseException e) {
                Log.e(TAG, "Error parsing expiration date", e);
            }
        }

        // Verificar si el documento existe primero
        db.collection("conductor_profiles").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Actualizar documento existente
                        db.collection("conductor_profiles").document(userId)
                                .update(conductorData)
                                .addOnSuccessListener(aVoid -> {
                                    // Actualizar datos originales
                                    datosOriginalesLicencia.clear();
                                    datosOriginalesLicencia.putAll(conductorData);

                                    // Continuar con el siguiente paso
                                    onSuccess.run();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error updating conductor profile", e);
                                    Toast.makeText(EdicionPerfil.this,
                                            "Error al actualizar perfil de conductor: " + e.getMessage(),
                                            Toast.LENGTH_SHORT).show();
                                    btnGuardarPerfil.setEnabled(true);
                                });
                    } else {
                        // Crear nuevo documento
                        db.collection("conductor_profiles").document(userId)
                                .set(conductorData)
                                .addOnSuccessListener(aVoid -> {
                                    // Actualizar datos originales
                                    datosOriginalesLicencia.clear();
                                    datosOriginalesLicencia.putAll(conductorData);

                                    // Continuar con el siguiente paso
                                    onSuccess.run();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error creating conductor profile", e);
                                    Toast.makeText(EdicionPerfil.this,
                                            "Error al crear perfil de conductor: " + e.getMessage(),
                                            Toast.LENGTH_SHORT).show();
                                    btnGuardarPerfil.setEnabled(true);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking conductor profile", e);
                    btnGuardarPerfil.setEnabled(true);
                });
    }

    private void saveVehicleData(Runnable onSuccess) {
        // Preparar datos del vehículo
        Map<String, Object> vehicleData = new HashMap<>();
        vehicleData.put("conductorId", userId);
        vehicleData.put("placa", editTextPlaca.getText().toString().trim().toUpperCase());
        vehicleData.put("marca", spinnerMarcaVehiculo.getSelectedItem().toString());
        vehicleData.put("modelo", editTextModelo.getText().toString().trim());

        // Capacidad de pasajeros (convertir a número)
        try {
            int capacidad = Integer.parseInt(editTextCapacidad.getText().toString().trim());
            vehicleData.put("capacidad_pasajeros", capacidad);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Error parsing capacity", e);
            vehicleData.put("capacidad_pasajeros", 8); // Valor por defecto
        }

        vehicleData.put("tipo_vehiculo", spinnerTipoVehiculo.getSelectedItem().toString());
        vehicleData.put("soat_vigente", checkBoxSoat.isChecked());
        vehicleData.put("tecnomecanica_vigente", checkBoxTecnomecanica.isChecked());

        // Gestionar documentos (simulado)
        Map<String, Object> documentos = new HashMap<>();

        // Si hay archivos seleccionados, simular que se han guardado URLs
        if (soatFileUri != null) {
            documentos.put("soat_archivo", "https://firebase-storage.example.com/soat_" + userId + ".pdf");
            documentos.put("soat_fecha_vencimiento", new Date(System.currentTimeMillis() + 31536000000L)); // Un año después
        }

        if (tecnoFileUri != null) {
            documentos.put("tecnomecanica_archivo", "https://firebase-storage.example.com/tecno_" + userId + ".pdf");
            documentos.put("tecnomecanica_fecha_vencimiento", new Date(System.currentTimeMillis() + 31536000000L)); // Un año después
        }

        if (!documentos.isEmpty()) {
            vehicleData.put("documentos", documentos);
        }

        // Verificar si ya existe un vehículo para este conductor
        if (vehiculoId != null && !vehiculoId.isEmpty()) {
            // Actualizar vehículo existente
            db.collection("vehiculos").document(vehiculoId)
                    .update(vehicleData)
                    .addOnSuccessListener(aVoid -> {
                        // Actualizar datos originales
                        datosOriginalesVehiculo.clear();
                        datosOriginalesVehiculo.putAll(vehicleData);

                        // Continuar con el siguiente paso
                        onSuccess.run();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error updating vehicle", e);
                        Toast.makeText(EdicionPerfil.this,
                                "Error al actualizar vehículo: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        btnGuardarPerfil.setEnabled(true);
                    });
        } else {
            // Crear nuevo vehículo
            db.collection("vehiculos")
                    .add(vehicleData)
                    .addOnSuccessListener(documentReference -> {
                        // Guardar ID del nuevo vehículo
                        vehiculoId = documentReference.getId();

                        // Actualizar datos originales
                        datosOriginalesVehiculo.clear();
                        datosOriginalesVehiculo.putAll(vehicleData);

                        // Continuar con el siguiente paso
                        onSuccess.run();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error creating vehicle", e);
                        Toast.makeText(EdicionPerfil.this,
                                "Error al crear vehículo: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        btnGuardarPerfil.setEnabled(true);
                    });
        }
    }
}