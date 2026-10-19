package com.cocab.controller;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

import com.cocab.exception.DatabaseException;
import com.cocab.exception.ValidationException;
import com.cocab.model.seguridad.RolUsuario;
import com.cocab.util.UserSession;

import javafx.concurrent.Task;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.stage.Modality;
import javafx.stage.Window;

/**
 * Ejecuta las operaciones de datos de una vista fuera del hilo JavaFX y centraliza sus avisos.
 * Garantiza una sola operación a la vez, descarta respuestas de una vista cerrada o de una sesión
 * sin el rol requerido, y muestra los diálogos con la ventana de la vista como propietaria.
 */
final class EjecutorFx {
    private static final int LIMITE_TEXTO_SIMPLE = 300;
    private final Parent raiz;
    private final Runnable actualizar;
    private final Runnable retirar;
    private final RolUsuario[] roles;
    private final ExecutorService hilo = Executors.newSingleThreadExecutor(operacion -> {
        Thread nuevo = new Thread(operacion, "cocab-tareas");
        nuevo.setDaemon(true);
        return nuevo;
    });
    private Task<?> tarea;
    private Alert dialogo;
    private boolean escritura;
    private boolean cerrado;

    /**
     * @param actualizar refresca los controles de la vista cuando cambia el estado ocupado
     * @param retirar    retira la vista si la sesión deja de ser válida para los roles indicados
     */
    EjecutorFx(Parent raiz, Runnable actualizar, Runnable retirar, RolUsuario... roles) {
        this.raiz = raiz;
        this.actualizar = actualizar;
        this.retirar = retirar;
        this.roles = roles;
        raiz.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/styles.css"),
                "Falta la hoja de estilos.").toExternalForm());
    }

    /** Verifica que la vista siga abierta y que la sesión conserve alguno de los roles permitidos. */
    boolean vigente() {
        if (cerrado) return false;
        try {
            UserSession.getInstance().exigirRol(roles);
            return true;
        } catch (ValidationException excepcion) {
            retirar.run();
            return false;
        }
    }

    /** Determina si la vista está ocupada con una tarea o un diálogo. */
    boolean ocupado() {
        return tarea != null || dialogo != null;
    }

    /** Indica si hay una operación de escritura en curso, incluso si la vista se cierra. */
    boolean hayEscrituraEnCurso() {
        return escritura;
    }

    /** Ejecuta una operación en segundo plano, aplicando los resultados o manejando fallos según corresponda. */
    <T> void ejecutar(boolean esEscritura, Callable<T> operacion, Consumer<T> aplicar,
                      Consumer<Throwable> fallar) {
        if (!vigente() || ocupado()) return;
        Task<T> nueva = new Task<>() {
            @Override
            protected T call() throws Exception {
                return operacion.call();
            }
        };
        tarea = nueva;
        escritura = esEscritura;
        actualizar.run();
        nueva.setOnSucceeded(evento -> entregar(nueva, () -> {
            try {
                aplicar.accept(nueva.getValue());
            } catch (RuntimeException excepcion) {
                // Si falla la presentación luego de una escritura, nunca se asume que fue revertida.
                fallar.accept(esEscritura
                        ? new DatabaseException("No se pudo presentar una escritura confirmada.") : excepcion);
            }
        }));
        nueva.setOnFailed(evento -> entregar(nueva, () -> fallar.accept(nueva.getException())));
        nueva.setOnCancelled(evento -> entregar(nueva, () -> fallar.accept(null)));
        try {
            hilo.execute(nueva);
        } catch (RejectedExecutionException excepcion) {
            tarea = null;
            escritura = false;
            fallar.accept(excepcion);
        }
    }

    /** Entrega el resultado de una tarea si sigue siendo vigente y corresponde a la tarea actual. */
    private void entregar(Task<?> candidata, Runnable entrega) {
        if (!vigente() || tarea != candidata) return;
        tarea = null;
        escritura = false;
        entrega.run();
        if (!cerrado) actualizar.run();
    }

    /** Muestra una advertencia con el título y mensaje proporcionados. */
    void advertir(String titulo, String mensaje) {
        mostrar(Alert.AlertType.WARNING, titulo, mensaje);
    }

    /** Muestra una información con el título y mensaje proporcionados. */
    void informar(String titulo, String mensaje) {
        mostrar(Alert.AlertType.INFORMATION, titulo, mensaje);
    }

    /** Muestra un error con el título y mensaje proporcionados. */
    void error(String titulo, String mensaje) {
        mostrar(Alert.AlertType.ERROR, titulo, mensaje);
    }

    /** Muestra un cuadro de confirmación con el título y mensaje proporcionados y devuelve true si se confirma. */
    boolean confirmar(String titulo, String mensaje) {
        return mostrar(Alert.AlertType.CONFIRMATION, titulo, mensaje) == ButtonType.OK;
    }

    /** Maneja el fallo de una operación, mostrando advertencias o errores según corresponda. */
    void fallo(Throwable fallo, boolean esEscritura) {
        if (fallo instanceof ValidationException validacion) {
            advertir("Operación rechazada", validacion.getMessage()
                    + "\nSe descartaron los datos. Recargue y revise antes de continuar.");
        } else {
            error("Operación no confirmada", esEscritura
                    ? "No se pudo confirmar el resultado. Puede haber cambios guardados. No repita la operación: "
                    + "recargue y verifique el resultado antes de decidir otra acción."
                    : "No se pudieron cargar los datos. Revise la conexión y la configuración y vuelva a recargar.");
        }
    }

    /** Muestra un cuadro de diálogo con el tipo, título y mensaje proporcionados y devuelve el botón presionado. */
    private ButtonType mostrar(Alert.AlertType tipo, String titulo, String mensaje) {
        if (!vigente() || dialogo != null) return ButtonType.CANCEL;
        if (raiz.getScene() == null) return ButtonType.CANCEL;
        Window ventana = raiz.getScene().getWindow();
        if (ventana == null || !ventana.isShowing()) return ButtonType.CANCEL;
        Alert alerta = new Alert(tipo);
        alerta.initOwner(ventana);
        alerta.initModality(Modality.WINDOW_MODAL);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        if (mensaje.length() > LIMITE_TEXTO_SIMPLE) {
            // Vista previa completa y desplazable de textos largos (por ejemplo, datos clínicos).
            TextArea contenido = new TextArea(mensaje);
            contenido.setEditable(false);
            contenido.setWrapText(true);
            contenido.setPrefRowCount(14);
            contenido.setPrefColumnCount(60);
            alerta.getDialogPane().setContent(contenido);
            alerta.setResizable(true);
        } else {
            alerta.setContentText(mensaje);
        }
        if (tipo == Alert.AlertType.CONFIRMATION) {
            // Enter no confirma por accidente una escritura.
            ((Button) alerta.getDialogPane().lookupButton(ButtonType.OK)).setDefaultButton(false);
            ((Button) alerta.getDialogPane().lookupButton(ButtonType.CANCEL)).setDefaultButton(true);
        }
        dialogo = alerta;
        actualizar.run();
        ButtonType respuesta;
        try {
            respuesta = alerta.showAndWait().orElse(ButtonType.CANCEL);
        } finally {
            dialogo = null;
            if (!cerrado) actualizar.run();
        }
        return vigente() ? respuesta : ButtonType.CANCEL;
    }

    /** Cancela la tarea en curso sin esperarla y deshabilita la vista. Es idempotente. */
    void cerrar() {
        if (cerrado) return;
        cerrado = true;
        if (tarea != null) {
            tarea.setOnSucceeded(null);
            tarea.setOnFailed(null);
            tarea.setOnCancelled(null);
            tarea.cancel(true);
            tarea = null;
        }
        hilo.shutdownNow();
        if (dialogo != null) {
            Alert pendiente = dialogo;
            dialogo = null;
            pendiente.setResult(ButtonType.CANCEL);
            pendiente.close();
        }
        raiz.setDisable(true);
    }
}
