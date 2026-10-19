package com.cocab.controller;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.cocab.exception.ValidationException;
import com.cocab.model.seguridad.RolUsuario;
import com.cocab.util.AlertHelper;
import com.cocab.util.UserSession;
import com.cocab.util.UserSession.Identidad;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.stage.Modality;
import javafx.stage.Stage;

/** Contenedor de sesión y módulos; el contenido clínico solo se carga en su vista autorizada. */
public final class MainShellController {
    private static final Logger LOGGER = Logger.getLogger(MainShellController.class.getName());
    private final UserSession sesion = UserSession.getInstance();
    private Consumer<String> alCerrarSesion;
    private Runnable alSalir;
    private boolean cerrado;
    private Stage ventanaPadron;
    private Runnable limpiarPadron;
    private BooleanSupplier escrituraPadron;
    private Alert avisoCierre;
    @FXML private Menu menuModulos;
    @FXML private MenuItem itemPacientes;
    @FXML private MenuItem itemOdontologos;
    @FXML private MenuItem itemAgenda;
    @FXML private MenuItem itemHistorias;
    @FXML private Label lblUsuario;
    @FXML private Label lblRol;
    @FXML private Label lblAlcance;

    /** Inicializa el controlador, configurando la visibilidad de los módulos y los labels de usuario y rol. */
    @FXML
    private void initialize() {
        // Cargar directamente el FXML sin sesión debe fallar, no solo ocultar opciones.
        sesion.exigirRol(RolUsuario.Secretaria, RolUsuario.Odontologo);
        ocultarModulos();
        lblUsuario.setText("");
        lblRol.setText("");
        lblAlcance.setText("");
    }

    /** Configura los callbacks de navegación para el cierre de sesión y la salida de la aplicación. */
    public void configurarNavegacion(Consumer<String> alCerrarSesion, Runnable alSalir) {
        this.alCerrarSesion = Objects.requireNonNull(alCerrarSesion);
        this.alSalir = Objects.requireNonNull(alSalir);
    }

    /** Activa el controlador, mostrando el perfil del usuario actual y verificando la sesión. */
    public void activar() {
        if (cerrado) return;
        if (alCerrarSesion == null || alSalir == null) {
            throw new IllegalStateException("La navegación de sesión no está configurada.");
        }
        Identidad identidad = exigirAccion(RolUsuario.Secretaria, RolUsuario.Odontologo);
        if (identidad == null) return;
        mostrarPerfil(identidad);
    }

    /** Muestra el perfil del usuario en los labels y ajusta la visibilidad y habilitación de los módulos según el rol. */
    private void mostrarPerfil(Identidad identidad) {
        boolean secretaria = identidad.getRol() == RolUsuario.Secretaria;
        lblUsuario.setText(identidad.getNombreUsuario());
        lblRol.setText(secretaria ? "Secretaria" : "Odontólogo");
        lblAlcance.setText(secretaria
                ? "Su perfil contempla pacientes, profesionales y agenda general. Sin acceso a historias clínicas."
                : "Su perfil contempla su agenda propia e historias clínicas compartidas entre odontólogos. Sin acceso a padrones administrativos.");
        itemPacientes.setVisible(secretaria);
        itemOdontologos.setVisible(secretaria);
        itemPacientes.setDisable(!secretaria);
        itemOdontologos.setDisable(!secretaria);
        itemHistorias.setVisible(!secretaria);
        itemHistorias.setDisable(secretaria);
        itemAgenda.setText(secretaria ? "Agenda general" : "Mi agenda");
        itemAgenda.setVisible(true);
        itemAgenda.setDisable(false);
        menuModulos.setVisible(true);
    }

    /** Muestra el módulo de pacientes, verificando los permisos de acceso. */
    @FXML
    private void mostrarPacientes() {
        abrirModulo(Modulo.PACIENTES);
    }

    /** Muestra el módulo de odontólogos, verificando los permisos de acceso. */
    @FXML
    private void mostrarOdontologos() {
        abrirModulo(Modulo.ODONTOLOGOS);
    }

    /** Abre el módulo especificado, verificando los permisos de acceso y gestionando la ventana correspondiente. */
    private void abrirModulo(Modulo modulo) {
        RolUsuario[] roles = switch (modulo) {
            case AGENDA -> new RolUsuario[] {RolUsuario.Secretaria, RolUsuario.Odontologo};
            case HISTORIAS -> new RolUsuario[] {RolUsuario.Odontologo};
            case PACIENTES, ODONTOLOGOS -> new RolUsuario[] {RolUsuario.Secretaria};
        };
        if (exigirAccion(roles) == null) return;
        if (ventanaPadron != null) {
            ventanaPadron.toFront();
            return;
        }
        try {
            String ruta = switch (modulo) {
                case PACIENTES -> "/views/PacientesView.fxml";
                case ODONTOLOGOS -> "/views/OdontologosView.fxml";
                case AGENDA -> "/views/AgendaTurnosView.fxml";
                case HISTORIAS -> "/views/HistoriaClinicaView.fxml";
            };
            URL recurso = getClass().getResource(ruta);
            if (recurso == null) throw new IOException("Falta la vista del módulo.");
            Object controlador;
            Runnable activar;
            // Registrar limpieza antes de FXMLLoader.load(): initialize también puede fallar.
            if (modulo == Modulo.HISTORIAS) {
                HistoriaClinicaController vista = new HistoriaClinicaController();
                controlador = vista;
                limpiarPadron = vista::cerrar;
                escrituraPadron = vista::hayEscrituraEnCurso;
                activar = vista::activar;
            } else if (modulo == Modulo.AGENDA) {
                AgendaTurnosController vista = new AgendaTurnosController();
                controlador = vista;
                limpiarPadron = vista::cerrar;
                escrituraPadron = vista::hayEscrituraEnCurso;
                activar = vista::activar;
            } else if (modulo == Modulo.PACIENTES) {
                PacientesController vista = new PacientesController();
                controlador = vista;
                limpiarPadron = vista::cerrar;
                escrituraPadron = vista::hayEscrituraEnCurso;
                activar = vista::activar;
            } else {
                OdontologosController vista = new OdontologosController();
                controlador = vista;
                limpiarPadron = vista::cerrar;
                escrituraPadron = vista::hayEscrituraEnCurso;
                activar = vista::activar;
            }
            FXMLLoader cargador = new FXMLLoader(recurso);
            cargador.setControllerFactory(tipo -> {
                if (!tipo.isInstance(controlador)) throw new IllegalStateException("Controlador inesperado.");
                return controlador;
            });
            Parent raiz = cargador.load();
            Stage ventana = new Stage();
            ventanaPadron = ventana;
            ventana.initOwner(lblUsuario.getScene().getWindow());
            ventana.initModality(Modality.WINDOW_MODAL);
            ventana.setTitle(switch (modulo) {
                case PACIENTES -> "COCAB | Pacientes";
                case ODONTOLOGOS -> "COCAB | Odontólogos";
                case AGENDA -> "COCAB | Agenda de turnos";
                case HISTORIAS -> "COCAB | Historia clínica";
            });
            ventana.setScene(new Scene(raiz, 1180, 780));
            ventana.setMinWidth(940);
            ventana.setMinHeight(620);
            ventana.setOnCloseRequest(evento -> {
                if (escrituraPadron != null && escrituraPadron.getAsBoolean()) {
                    avisoCierre = new Alert(Alert.AlertType.CONFIRMATION,
                            "Hay una escritura en curso o pendiente de verificación. Cerrar no garantiza rollback: verifique los datos "
                                    + "antes de repetir la operación. ¿Cerrar igualmente?", ButtonType.OK, ButtonType.CANCEL);
                    avisoCierre.initOwner(ventana);
                    avisoCierre.setTitle("Escritura en curso");
                    avisoCierre.setHeaderText(null);
                    try {
                        if (avisoCierre.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) evento.consume();
                    } finally {
                        avisoCierre = null;
                    }
                }
            });
            ventana.setOnHidden(evento -> cerrarPadron());
            ventana.show();
            // La carga inicial vuelve a autorizar el acceso en la misma operación de consulta.
            activar.run();
        } catch (IOException | RuntimeException excepcion) {
            cerrarPadron();
            LOGGER.warning("No se pudo abrir el módulo solicitado.");
            if (sesion.haySesion() && !cerrado) {
                AlertHelper.error("Vista no disponible", "No se pudo abrir el módulo. Revise su acceso e instalación con soporte.");
            }
        }
    }

    /** Cierra la ventana del padrón, ejecutando la limpieza correspondiente y liberando recursos. */
    private void cerrarPadron() {
        Stage ventana = ventanaPadron;
        Runnable limpiar = limpiarPadron;
        ventanaPadron = null;
        limpiarPadron = null;
        escrituraPadron = null;
        if (avisoCierre != null) {
            avisoCierre.setResult(ButtonType.CANCEL);
            avisoCierre.close();
            avisoCierre = null;
        }
        if (limpiar != null) limpiar.run();
        if (ventana != null) {
            ventana.setOnHidden(null);
            ventana.setOnCloseRequest(null);
            ventana.hide();
            ventana.setScene(null);
        }
    }

    @FXML
    /** Muestra el módulo de agenda, verificando los permisos de acceso. */
    private void mostrarAgenda() {
        abrirModulo(Modulo.AGENDA);
    }

    @FXML
    /** Muestra el módulo de historias clínicas, verificando los permisos de acceso. */
    private void mostrarHistorias() {
        abrirModulo(Modulo.HISTORIAS);
    }

    /** Exige que el usuario tenga uno de los roles especificados para realizar una acción. Devuelve la identidad del usuario si tiene el rol requerido, o null en caso contrario. */
    private Identidad exigirAccion(RolUsuario... roles) {
        if (cerrado || !sesion.haySesion()) return null;
        try {
            return sesion.exigirRol(roles);
        } catch (ValidationException excepcion) {
            AlertHelper.advertencia("Acceso no autorizado", "Su sesión no permite realizar esta acción.");
            return null;
        }
    }

    /** Cierra la sesión actual si el usuario tiene los permisos necesarios. */
    @FXML
    private void cerrarSesion() {
        if (exigirAccion(RolUsuario.Secretaria, RolUsuario.Odontologo) != null) {
            finalizarSesion(null);
        }
    }

    /** Sale de la aplicación si el usuario tiene los permisos necesarios. */
    @FXML
    private void salir() {
        if (exigirAccion(RolUsuario.Secretaria, RolUsuario.Odontologo) != null && alSalir != null) {
            alSalir.run();
        }
    }

    /** Finaliza la sesión actual, notificando a la aplicación principal con un aviso opcional. */
    private void finalizarSesion(String aviso) {
        if (!cerrado && sesion.haySesion() && alCerrarSesion != null) {
            // MainApp invalida sesión, limpia esta vista y carga un login nuevo antes de alertar.
            alCerrarSesion.accept(aviso);
        }
    }

    /** Oculta todos los módulos del menú, deshabilitándolos y ajustando su visibilidad. */
    private void ocultarModulos() {
        if (menuModulos != null) menuModulos.setVisible(false);
        for (MenuItem item : new MenuItem[] {itemPacientes, itemOdontologos, itemAgenda, itemHistorias}) {
            if (item != null) {
                item.setVisible(false);
                item.setDisable(true);
            }
        }
    }

    /** Cierra la vista principal, liberando recursos, ocultando módulos y limpiando la información del usuario. */
    public void cerrar() {
        if (cerrado) return;
        cerrado = true;
        cerrarPadron();
        alCerrarSesion = null;
        alSalir = null;
        ocultarModulos();
        if (lblUsuario != null) lblUsuario.setText("");
        if (lblRol != null) lblRol.setText("");
        if (lblAlcance != null) lblAlcance.setText("");
        if (itemAgenda != null) itemAgenda.setText("");
    }

    private enum Modulo { PACIENTES, ODONTOLOGOS, AGENDA, HISTORIAS }
}