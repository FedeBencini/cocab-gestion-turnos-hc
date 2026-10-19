package com.cocab;

import java.io.IOException;
import java.net.URL;
import java.util.logging.Logger;

import com.cocab.controller.LoginController;
import com.cocab.controller.MainShellController;
import com.cocab.util.AlertHelper;
import com.cocab.util.UserSession;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Ciclo de vida de la aplicación. */
public final class MainApp extends Application {
    private static final Logger LOGGER = Logger.getLogger(MainApp.class.getName());
    private LoginController controlador;
    private MainShellController controladorPrincipal;
    private Stage escenarioPrincipal;
    private boolean cerrando;

    public static void main(String[] argumentos) {
        launch(argumentos);
    }

    /** Ciclo de vida de la aplicación: inicio, cierre y gestión de escenas. */
    @Override
    public void start(Stage escenario) {
        escenarioPrincipal = escenario;
        escenario.setTitle("COCAB | Gestión de turnos e historias clínicas");
        escenario.setMinWidth(680);
        escenario.setMinHeight(520);
        escenario.setOnCloseRequest(evento -> salir());
        mostrarLogin(null);
        if (!cerrando) {
            escenario.show();
            LOGGER.info("Ventana inicial cargada.");
        }
    }

    /** Mostrar la pantalla de login. */
    private void mostrarLogin(String aviso) {
        if (cerrando) {
            return;
        }
        UserSession.getInstance().cerrarSesion();
        cerrarControladores();
        try {
            // Registrar antes de cargar permite limpiar incluso un FXML que falle en initialize.
            controlador = new LoginController();
            controlador.configurarNavegacion(this::mostrarPrincipal);
            cargarEscena("/views/LoginView.fxml", controlador);
        } catch (IOException | RuntimeException excepcion) {
            LOGGER.severe("No se pudo cargar la interfaz de ingreso.");
            UserSession.getInstance().cerrarSesion();
            cerrarControladores();
            escenarioPrincipal.setScene(null);
            try {
                AlertHelper.error("No se pudo iniciar COCAB", "Revise la instalación y los recursos de la aplicación con soporte.");
            } finally {
                salir();
            }
            return;
        }
        if (aviso != null) {
            AlertHelper.advertencia("Sesión finalizada", aviso);
        }
    }

    /** Mostrar la pantalla principal de la aplicación. */
    private void mostrarPrincipal() {
        if (cerrando) {
            return;
        }
        cerrarControladores();
        try {
            controladorPrincipal = new MainShellController();
            controladorPrincipal.configurarNavegacion(this::mostrarLogin, this::salir);
            cargarEscena("/views/MainShellView.fxml", controladorPrincipal);
            controladorPrincipal.activar();
        } catch (IOException | RuntimeException excepcion) {
            LOGGER.severe("No se pudo abrir el menú; se descarta la sesión.");
            mostrarLogin("No se pudo abrir el menú. Vuelva a ingresar o contacte a soporte.");
        }
    }

    /** Cargar una escena FXML con su controlador correspondiente. */
    private void cargarEscena(String ruta, Object controladorVista) throws IOException {
        URL vista = getClass().getResource(ruta);
        URL estilos = getClass().getResource("/css/styles.css");
        if (vista == null || estilos == null) {
            throw new IOException("Faltan recursos de la distribución.");
        }
        FXMLLoader cargador = new FXMLLoader(vista);
        cargador.setControllerFactory(tipo -> {
            if (!tipo.isInstance(controladorVista)) {
                throw new IllegalStateException("Controlador de vista inesperado.");
            }
            return controladorVista;
        });
        Parent raiz = cargador.load();
        Scene escena = new Scene(raiz, 960, 640);
        escena.getStylesheets().add(estilos.toExternalForm());
        escenarioPrincipal.setScene(escena);
    }

    /** Cerrar los controladores activos y liberar recursos asociados. */
    private void cerrarControladores() {
        if (controlador != null) {
            controlador.cerrar();
            controlador = null;
        }
        if (controladorPrincipal != null) {
            controladorPrincipal.cerrar();
            controladorPrincipal = null;
        }
    }

    /** Cerrar la aplicación y liberar recursos. */
    private void salir() {
        if (!cerrando) {
            cerrando = true;
            UserSession.getInstance().cerrarSesion();
            cerrarControladores();
            if (escenarioPrincipal != null) {
                escenarioPrincipal.hide();
                escenarioPrincipal.setScene(null);
            }
            Platform.exit();
        }
    }

    /** Método invocado al detener la aplicación. */
    @Override
    public void stop() {
        salir();
        escenarioPrincipal = null;
        LOGGER.info("Aplicación cerrada.");
    }
}
