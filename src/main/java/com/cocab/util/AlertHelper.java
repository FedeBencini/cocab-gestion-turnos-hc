package com.cocab.util;

import javafx.application.Platform;
import javafx.scene.control.Alert;

/** Utilidad para mostrar diálogos de alerta en la interfaz JavaFX. */
public final class AlertHelper {
    private AlertHelper() {
    }

    /** Muestra un diálogo de advertencia con el título y mensaje especificados. */
    public static void advertencia(String titulo, String mensaje) {
        mostrar(Alert.AlertType.WARNING, titulo, mensaje);
    }

    /** Muestra un diálogo de error con el título y mensaje especificados. */
    public static void error(String titulo, String mensaje) {
        mostrar(Alert.AlertType.ERROR, titulo, mensaje);
    }

    /** Muestra un diálogo de alerta del tipo especificado con el título y mensaje proporcionados. */
    private static void mostrar(Alert.AlertType tipo, String titulo, String mensaje) {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Los diálogos deben abrirse en el hilo JavaFX.");
        }
        Alert alerta = new Alert(tipo);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        alerta.showAndWait();
    }
}