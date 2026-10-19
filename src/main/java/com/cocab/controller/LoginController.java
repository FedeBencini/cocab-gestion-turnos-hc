package com.cocab.controller;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Logger;

import com.cocab.dao.UsuarioDAO;
import com.cocab.dao.impl.UsuarioDAOImpl;
import com.cocab.exception.ValidationException;
import com.cocab.util.AlertHelper;
import com.cocab.util.UserSession;
import com.cocab.util.UserSession.Identidad;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;

/** Autenticación en segundo plano, sin PBKDF2 en el hilo FX. */
public final class LoginController {
    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());
    private static final String ACCESO_INVALIDO = "No se pudo iniciar sesión con los datos ingresados.";
    private final UserSession sesion = UserSession.getInstance();
    private final UsuarioDAO usuarioDAO = new UsuarioDAOImpl();
    private final ExecutorService ejecutor = Executors.newSingleThreadExecutor(tarea -> {
        Thread hilo = new Thread(tarea, "cocab-ingreso");
        hilo.setDaemon(true);
        return hilo;
    });
    private Task<?> tareaActiva;
    private Runnable alAutenticar;
    private boolean ocupado;
    private boolean cerrado;
    @FXML private TextField txtUsuario;
    @FXML private PasswordField txtClave;
    @FXML private Button btnIngresar;
    @FXML private Label lblEstado;
    @FXML private ProgressIndicator progreso;

    /** Inicializa el controlador de inicio de sesión. */
    @FXML
    private void initialize() {
        if (sesion.obtenerIdentidad().isPresent()) {
            throw new ValidationException("Cierre la sesión antes de ingresar nuevamente.");
        }
        lblEstado.setText("Ingrese con su cuenta personal.");
        indicarTrabajo(false);
    }

    /** Configura el callback que se ejecuta en FX con la sesión ya iniciada. */
    public void configurarNavegacion(Runnable alAutenticar) {
        this.alAutenticar = Objects.requireNonNull(alAutenticar);
    }

    /** Maneja el intento de inicio de sesión con las credenciales proporcionadas. */
    @FXML
    private void ingresar() {
        if (!puedeTrabajar()) {
            return;
        }
        String nombre = txtUsuario.getText();
        String clave = txtClave.getText();
        txtClave.clear();
        if (nombre.isBlank() || clave.isEmpty()) {
            clave = null;
            lblEstado.setText("Complete el usuario y la contraseña.");
            AlertHelper.advertencia("Datos incompletos", "Complete el usuario y la contraseña.");
            return;
        }
        if (alAutenticar == null) {
            clave = null;
            AlertHelper.error("Ingreso no disponible", "No se pudo preparar la navegación. Reinicie la aplicación.");
            return;
        }
        TareaAutenticacion tarea = new TareaAutenticacion(usuarioDAO, nombre, clave);
        clave = null;
        tareaActiva = tarea;
        indicarTrabajo(true);
        lblEstado.setText("Verificando credenciales y habilitación de la cuenta…");
        tarea.setOnSucceeded(evento -> {
            Optional<Identidad> identidad = tarea.getValue();
            if (!finalizar(tarea)) {
                return;
            }
            if (identidad.isEmpty()) {
                informarAccesoInvalido();
                return;
            }
            // Solo el hilo FX puede iniciar sesión; nunca el trabajador.
            if (sesion.iniciar(identidad.get())) {
                alAutenticar.run();
            }
        });
        tarea.setOnFailed(evento -> {
            Throwable error = tarea.getException();
            if (!finalizar(tarea)) {
                return;
            }
            if (error instanceof ValidationException) {
                informarAccesoInvalido();
            } else {
                LOGGER.warning("No se pudo completar la autenticación.");
                lblEstado.setText("No se pudo comprobar el acceso. Intente nuevamente o contacte a soporte.");
                AlertHelper.error("Acceso no disponible", lblEstado.getText());
            }
        });
        tarea.setOnCancelled(evento -> finalizarCancelacion(tarea));
        ejecutar(tarea);
    }

    /** Invoca en FX al retirar la vista, incluso si falló la carga FXML. */
    public void cerrar() {
        if (!cerrado) {
            cerrado = true;
            if (tareaActiva != null) {
                liberar(tareaActiva);
                tareaActiva.cancel(true);
                tareaActiva = null;
            }
            ejecutor.shutdownNow();
            alAutenticar = null;
            if (txtClave != null) txtClave.clear();
            if (txtUsuario != null) txtUsuario.clear();
            if (lblEstado != null) lblEstado.setText("");
        }
    }

    /** Verifica si el controlador puede iniciar operaciones de trabajo. */
    private boolean puedeTrabajar() {
        return !cerrado && !ocupado && sesion.obtenerIdentidad().isEmpty();
    }

    /** Informa al usuario que el acceso es inválido. */
    private void informarAccesoInvalido() {
        lblEstado.setText(ACCESO_INVALIDO);
        txtClave.requestFocus();
        AlertHelper.advertencia("Ingreso no autorizado", ACCESO_INVALIDO);
    }

    /** Ejecuta la tarea en el ejecutor, manejando posibles rechazos. */
    private void ejecutar(Task<?> tarea) {
        try {
            ejecutor.execute(tarea);
        } catch (RejectedExecutionException excepcion) {
            boolean vigente = finalizar(tarea);
            tarea.cancel(true);
            if (vigente) {
                lblEstado.setText("No se pudo iniciar la operación. Reinicie la aplicación.");
                AlertHelper.error("Operación no disponible", lblEstado.getText());
            }
        }
    }

    /** Finaliza la tarea activa si corresponde y actualiza el estado de trabajo. */
    private boolean finalizar(Task<?> tarea) {
        boolean vigente = !cerrado && tareaActiva == tarea && !tarea.isCancelled();
        liberar(tarea);
        if (tareaActiva == tarea) tareaActiva = null;
        if (vigente) indicarTrabajo(false);
        return vigente;
    }

    /** Finaliza la cancelación de la tarea activa y actualiza el estado de trabajo. */
    private void finalizarCancelacion(Task<?> tarea) {
        boolean vigente = !cerrado && tareaActiva == tarea;
        finalizar(tarea);
        if (vigente) {
            indicarTrabajo(false);
            lblEstado.setText("Operación cancelada.");
        }
    }

    /** Libera los recursos asociados a la tarea, incluyendo listeners y entradas sensibles. */
    private void liberar(Task<?> tarea) {
        tarea.setOnSucceeded(null);
        tarea.setOnFailed(null);
        tarea.setOnCancelled(null);
        if (tarea instanceof TareaAutenticacion autenticacion) autenticacion.limpiarEntradas();
    }

    /** Actualiza la interfaz para reflejar si hay trabajo en curso. */
    private void indicarTrabajo(boolean ocupado) {
        this.ocupado = ocupado;
        txtUsuario.setDisable(ocupado);
        txtClave.setDisable(ocupado);
        btnIngresar.setDisable(ocupado);
        progreso.setManaged(ocupado);
        progreso.setVisible(ocupado);
    }

    /** Representa una tarea de autenticación que no captura el controlador ni deja contraseñas en una clausura de eventos. */
    private static final class TareaAutenticacion extends Task<Optional<Identidad>> {
        private final UsuarioDAO dao;
        private String nombre;
        private String clave;

        private TareaAutenticacion(UsuarioDAO dao, String nombre, String clave) {
            this.dao = dao;
            this.nombre = nombre;
            this.clave = clave;
        }

        /** Ejecuta la autenticación en un hilo de fondo y devuelve un Optional con la identidad si tiene éxito. */
        @Override
        protected Optional<Identidad> call() {
            String nombreLocal;
            String claveLocal;
            synchronized (this) {
                nombreLocal = nombre;
                claveLocal = clave;
                limpiarEntradas();
            }
            try {
                if (isCancelled() || claveLocal == null) return Optional.empty();
                // El Usuario con hash solo existe durante este mapeo, en el trabajador.
                return dao.autenticar(nombreLocal, claveLocal).map(usuario -> new Identidad(
                        usuario.getIdUsuario(), usuario.getIdPersona(), usuario.getNombreUsuario(), usuario.getRol()));
            } finally {
                nombreLocal = null;
                claveLocal = null;
                limpiarEntradas();
            }
        }

        private synchronized void limpiarEntradas() {
            nombre = null;
            clave = null;
        }
    }
}