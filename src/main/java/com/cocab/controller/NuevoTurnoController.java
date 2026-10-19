package com.cocab.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Objects;

import com.cocab.dao.TurnoDAO;
import com.cocab.dao.impl.TurnoDAOImpl;
import com.cocab.exception.ValidationException;
import com.cocab.model.odontologo.Odontologo;
import com.cocab.model.paciente.Paciente;
import com.cocab.model.seguridad.RolUsuario;
import com.cocab.model.turno.EstadoTurno;
import com.cocab.model.turno.Turno;
import com.cocab.util.DatosClinica;
import com.cocab.util.UserSession;
import com.cocab.util.Validador;

import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Formulario completo de secretaria. No modifica la instantánea recibida de la agenda. */
public final class NuevoTurnoController {
    private final TurnoDAO dao = new TurnoDAOImpl();
    private Turno original;
    private LocalDate fechaSugerida;
    private Stage ventana;
    private Runnable alConfirmar;
    private Runnable alInvalidar;
    private EjecutorFx ejecutor;
    private boolean configurado;
    private boolean activado;
    private boolean cerrado;
    private boolean datosVigentes;
    private boolean inhabilitado;
    private boolean resultadoIncierto;
    private boolean reprogramacion;
    private ChangeListener<LocalTime> cambioHora;

    @FXML private VBox raiz;
    @FXML private VBox campos;
    @FXML private Label lblTitulo;
    @FXML private Label lblOriginal;
    @FXML private Label lblPacienteFijo;
    @FXML private ComboBox<Paciente> cmbPaciente;
    @FXML private ComboBox<Odontologo> cmbOdontologo;
    @FXML private DatePicker fechaTurno;
    @FXML private ComboBox<LocalTime> cmbHora;
    @FXML private Label lblFin;
    @FXML private TextArea txtMotivo;
    @FXML private Button btnGuardar;
    @FXML private Button btnRecargar;
    @FXML private Button btnCerrar;
    @FXML private Label lblEstado;
    @FXML private ProgressIndicator progreso;

    public NuevoTurnoController() {
    }

    /** Configura el formulario para un nuevo turno o la reprogramación de uno existente, estableciendo los datos iniciales y los callbacks correspondientes. */
    void configurar(Turno turnoOriginal, LocalDate sugerida, Stage escenario,
                    Runnable confirmado, Runnable invalidado) {
        if (configurado || cerrado) throw new IllegalStateException("El formulario ya fue configurado o retirado.");
        UserSession.getInstance().exigirRol(RolUsuario.Secretaria);
        ventana = Objects.requireNonNull(escenario);
        alConfirmar = Objects.requireNonNull(confirmado);
        alInvalidar = Objects.requireNonNull(invalidado);
        if (turnoOriginal != null) {
            if (!AyudaVista.modificable(turnoOriginal)) {
                throw new ValidationException("Solo se reprograman turnos Asignados o Reprogramados.");
            }
            if (!turnoOriginal.getPaciente().estaHabilitado()) {
                throw new ValidationException("El paciente inactivo no admite reprogramación; puede cancelar su turno.");
            }
            original = AyudaVista.copiar(turnoOriginal);
        }
        reprogramacion = original != null;
        fechaSugerida = sugerida == null || sugerida.isBefore(LocalDate.now()) ? LocalDate.now() : sugerida;
        configurado = true;
    }

    /** Carga directa sin el protocolo y sin una sesión de secretaria: denegada. */
    @FXML
    private void initialize() {
        if (!configurado || cerrado) throw new IllegalStateException("Configure el formulario antes de cargar FXML.");
        UserSession.getInstance().exigirRol(RolUsuario.Secretaria);
        ejecutor = new EjecutorFx(raiz, this::actualizarControles, this::retirarPorSesion, RolUsuario.Secretaria);
        lblTitulo.setText(reprogramacion ? "Reprogramar turno" : "Nuevo turno");
        btnGuardar.setText(reprogramacion ? "Confirmar reprogramación" : "Confirmar asignación");
        cmbPaciente.setConverter(AyudaVista.etiquetas(AyudaVista::paciente));
        cmbOdontologo.setConverter(AyudaVista.etiquetas(AyudaVista::profesional));
        cmbHora.setConverter(AyudaVista.etiquetas(LocalTime::toString));
        cmbHora.getItems().setAll(DatosClinica.obtenerFranjasHorarias());
        AyudaVista.prepararFecha(fechaTurno);
        txtMotivo.setTextFormatter(new TextFormatter<String>(cambio -> {
            String texto = cambio.getControlNewText();
            return texto.codePointCount(0, texto.length()) <= 255 ? cambio : null;
        }));
        cambioHora = (observable, anterior, hora) -> lblFin.setText(
                hora == null ? "Fin: —" : "Fin: " + hora.plusMinutes(30) + " · Duración: 30 minutos");
        cmbHora.valueProperty().addListener(cambioHora);
        lblOriginal.setText("Preparando datos. No hay un formulario habilitado para guardar.");
        actualizarControles();
    }

    /** Inicia catálogos frescos solo después de mostrar el formulario. */
    public void activar() {
        if (cerrado) return;
        if (ejecutor == null || ventana == null || !ventana.isShowing()) {
            throw new IllegalStateException("Cargue y muestre el formulario antes de activarlo.");
        }
        if (!activado && ejecutor.vigente()) {
            activado = true;
            recargarCatalogos();
        }
    }

    /** Verifica si la acción actual está autorizada según el estado del ejecutor y la sesión de secretaria. */
    private boolean autorizar() {
        return ejecutor.vigente() && !ejecutor.ocupado();
    }

    /** Recarga los catálogos de pacientes y odontólogos desde la base de datos, actualizando los elementos de los ComboBox correspondientes. */
    @FXML
    private void recargarCatalogos() {
        if (cerrado || !activado || inhabilitado || !autorizar()) return;
        datosVigentes = false;
        limpiarCampos();
        ejecutor.ejecutar(false, () -> new Catalogos(dao.listarPacientesActivos(),
                dao.listarOdontologos(false)), this::aplicarCatalogos, fallo -> fallar(fallo, false));
    }

    /** Aplica los catálogos de pacientes y odontólogos al formulario, preservando las selecciones originales si es una reprogramación. */
    private void aplicarCatalogos(Catalogos catalogos) {
        Paciente fijo = null;
        Odontologo anterior = null;
        for (Paciente paciente : catalogos.pacientes()) {
            if (!paciente.estaHabilitado()) continue;
            Paciente copia = AyudaVista.copiar(paciente);
            if (!reprogramacion) cmbPaciente.getItems().add(copia);
            if (original != null && copia.getIdPaciente() == original.getPaciente().getIdPaciente()) fijo = copia;
        }
        for (Odontologo odontologo : catalogos.odontologos()) {
            if (!odontologo.estaHabilitado()) continue;
            Odontologo copia = new Odontologo(odontologo);
            cmbOdontologo.getItems().add(copia);
            if (original != null && copia.getIdOdontologo() == original.getOdontologo().getIdOdontologo()) anterior = copia;
        }
        if (reprogramacion) {
            if (fijo == null || !original.getPaciente().estaHabilitado()) {
                throw new ValidationException("El paciente original no está activo. Cierre el formulario; puede cancelar el turno desde la agenda.");
            }
            cmbPaciente.getItems().setAll(fijo);
            cmbPaciente.setValue(fijo);
            cmbOdontologo.setValue(anterior); // Inactivo/ausente: exige seleccionar otro activo.
            lblPacienteFijo.setText("Paciente fijo. No se permite cambiarlo al reprogramar.");
            lblOriginal.setText("Original · Turno #" + original.getIdTurno() + " · " + original.getEstado().getValor()
                    + "\n" + AyudaVista.paciente(original.getPaciente())
                    + "\n" + AyudaVista.profesional(original.getOdontologo())
                    + "\n" + AyudaVista.fecha(original.getFecha()) + " · " + original.getHoraInicio()
                    + " – " + original.getHoraFin() + "\nMotivo: " + original.getMotivoConsulta());
            LocalDate propuesta = original.getFecha().isBefore(LocalDate.now()) ? LocalDate.now() : original.getFecha();
            fechaTurno.setValue(propuesta);
            fechaTurno.getEditor().setText(AyudaVista.fecha(propuesta));
            if (cmbHora.getItems().contains(original.getHoraInicio())) cmbHora.setValue(original.getHoraInicio());
            txtMotivo.setText(original.getMotivoConsulta());
        } else {
            lblPacienteFijo.setText("Solo pacientes activos · Nombre y DNI en el selector.");
            lblOriginal.setText("Nueva asignación · Estado inicial: Asignado · Sin cambios en otros turnos.");
            LocalDate propuesta = fechaSugerida.isBefore(LocalDate.now()) ? LocalDate.now() : fechaSugerida;
            fechaTurno.setValue(propuesta);
            fechaTurno.getEditor().setText(AyudaVista.fecha(propuesta));
        }
        if (cmbPaciente.getItems().isEmpty() || cmbOdontologo.getItems().isEmpty()) {
            throw new ValidationException("Se necesita al menos un paciente y un profesional activos. Cierre y revise los padrones.");
        }
        datosVigentes = true;
        actualizarControles();
    }

    /** Guarda el turno actual, ya sea como una nueva asignación o como una reprogramación, validando los datos y aplicando los cambios mediante el ejecutor. */
    @FXML
    private void guardar() {
        if (cerrado || !activado || inhabilitado || !datosVigentes || !autorizar()) return;
        try {
            Paciente paciente = Validador.requerirNoNulo(cmbPaciente.getValue(), "El paciente");
            Odontologo odontologo = Validador.requerirNoNulo(cmbOdontologo.getValue(), "El profesional");
            if (!cmbPaciente.getItems().contains(paciente) || !cmbOdontologo.getItems().contains(odontologo)) {
                throw new ValidationException("Seleccione referencias del catálogo activo cargado.");
            }
            if (reprogramacion && (original == null || !AyudaVista.modificable(original)
                    || paciente.getIdPaciente() != original.getPaciente().getIdPaciente())) {
                throw new ValidationException("El paciente original y el estado del turno no pueden alterarse.");
            }
            LocalDate fecha = Validador.validarFechaAsignacion(AyudaVista.leerFecha(fechaTurno));
            LocalTime inicio = Validador.requerirNoNulo(cmbHora.getValue(), "La hora de inicio");
            if (!cmbHora.getItems().contains(inicio)) throw new ValidationException("Seleccione una de las veinte franjas disponibles.");
            Validador.validarFranjaHoraria(inicio, inicio.plusMinutes(30));
            String motivo = Validador.validarObligatorio(txtMotivo.getText(), "El motivo de consulta", 255);
            // Capturas exclusivas y profundas, tomadas en FX; el trabajador no lee controles,
            // catálogos ni el Turno mutable compartido con la tabla o el resumen.
            final Turno esperado = original == null ? null : AyudaVista.copiar(original);
            final Turno cambios = new Turno(esperado == null ? 0 : esperado.getIdTurno(),
                    AyudaVista.copiar(paciente), new Odontologo(odontologo), fecha,
                    inicio, inicio.plusMinutes(30), motivo,
                    esperado == null ? EstadoTurno.Asignado : EstadoTurno.Reprogramado);
            cambios.validarParaAsignacion();
            ejecutor.ejecutar(true, () -> esperado == null ? dao.registrar(cambios)
                            : dao.reprogramar(esperado, cambios), resultado -> confirmarGuardado(),
                    fallo -> fallar(fallo, true));
        } catch (ValidationException excepcion) {
            ejecutor.advertir("Revise los datos", excepcion.getMessage());
        }
    }

    /** Confirma que el guardado del turno se realizó correctamente, cierra el formulario y ejecuta el callback correspondiente. */
    private void confirmarGuardado() {
        Runnable confirmado = alConfirmar;
        Stage escenario = ventana;
        // El DAO ya confirmó el guardado. Primero onHidden limpia y retira este formulario;
        // recién después se recarga la agenda, evitando limpiar callbacks de una nueva carga.
        escenario.hide();
        cerrar();
        if (UserSession.getInstance().haySesion()) confirmado.run();
    }

    /** Maneja el fallo en la operación de guardado, actualizando el estado del formulario y mostrando un mensaje de error. */
    private void fallar(Throwable fallo, boolean escritura) {
        resultadoIncierto = escritura && !(fallo instanceof ValidationException);
        datosVigentes = false;
        inhabilitado = true;
        original = null;
        limpiarCampos();
        lblOriginal.setText("Datos descartados. Cierre este formulario y recargue la agenda antes de volver a abrirlo.");
        if (alInvalidar != null) alInvalidar.run();
        actualizarControles();
        ejecutor.fallo(fallo, escritura);
    }

    /** Solicita el cierre del formulario desde el botón, mostrando la advertencia correspondiente si hay escritura en curso. */
    @FXML
    private void solicitarCierreDesdeBoton() {
        solicitarCierre();
    }

    /** Solicita el cierre del formulario, manejando la advertencia por escritura en curso y ocultando la ventana según corresponda. */
    void solicitarCierre() {
        if (cerrado || !ejecutor.vigente()) return;
        if (ejecutor.hayEscrituraEnCurso()) {
            if (ejecutor.confirmar("Escritura en curso", "Cerrar no garantiza rollback. La operación puede persistir "
                    + "aunque esta ventana se cierre. Verifique el resultado con soporte antes de repetir. ¿Cerrar igualmente?")
                    && !cerrado) {
                resultadoIncierto = true;
                if (alInvalidar != null) alInvalidar.run();
                ventana.hide();
            }
        } else {
            ventana.hide();
        }
    }

    /** Retira el formulario debido a un cambio en la sesión, cerrando y ocultando la ventana correspondiente. */
    private void retirarPorSesion() {
        Stage escenario = ventana;
        cerrar();
        if (escenario != null) escenario.hide();
    }

    /** Indica si actualmente hay una operación de escritura en curso. */
    public boolean hayEscrituraEnCurso() {
        return ejecutor != null && ejecutor.hayEscrituraEnCurso();
    }

    /** Indica si el formulario requiere verificación debido a un resultado incierto de la última operación. */
    boolean requiereVerificacion() {
        return resultadoIncierto;
    }

    /** Actualiza el estado de los controles de la interfaz según la disponibilidad de datos y el estado del formulario. */
    private void actualizarControles() {
        if (ejecutor == null || cerrado) return;
        boolean libre = activado && !ejecutor.ocupado() && !inhabilitado;
        campos.setDisable(!libre || !datosVigentes);
        cmbPaciente.setDisable(reprogramacion);
        btnGuardar.setDisable(!libre || !datosVigentes);
        btnRecargar.setDisable(!libre);
        // Cerrar permanece disponible durante escritura, pero exige confirmación propia.
        btnCerrar.setDisable(false);
        progreso.setVisible(ejecutor.ocupado());
        progreso.setManaged(ejecutor.ocupado());
        lblEstado.setText(inhabilitado ? "Formulario descartado. Cierre y recargue la agenda; no reenvíe una escritura incierta."
                : ejecutor.hayEscrituraEnCurso() ? "Guardando… No cierre ni repita. El resultado solo se confirma al terminar el DAO."
                : ejecutor.ocupado() ? "Cargando catálogos o esperando un aviso…"
                : datosVigentes ? "Catálogos activos cargados. El servidor revalidará actividad, estado y disponibilidad al guardar."
                : "Esperando carga de catálogos activos.");
    }

    /** Limpia los campos del formulario, restableciendo las selecciones y valores a su estado inicial. */
    private void limpiarCampos() {
        if (cmbPaciente != null) { cmbPaciente.setValue(null); cmbPaciente.getItems().clear(); }
        if (cmbOdontologo != null) { cmbOdontologo.setValue(null); cmbOdontologo.getItems().clear(); }
        if (fechaTurno != null) { fechaTurno.setValue(null); fechaTurno.getEditor().clear(); }
        if (cmbHora != null) cmbHora.setValue(null);
        if (txtMotivo != null) txtMotivo.clear();
        if (lblOriginal != null) lblOriginal.setText("");
        if (lblPacienteFijo != null) lblPacienteFijo.setText("");
        if (lblFin != null) lblFin.setText("Fin: —");
    }

    /** Cierra el formulario, cancelando tareas en curso, removiendo listeners y limpiando los datos asociados. */
    public void cerrar() {
        if (cerrado) return;
        cerrado = true;
        if (ejecutor != null) ejecutor.cerrar();
        if (cmbHora != null && cambioHora != null) cmbHora.valueProperty().removeListener(cambioHora);
        cambioHora = null;
        for (Button boton : new Button[] {btnGuardar, btnRecargar, btnCerrar}) {
            if (boton != null) boton.setOnAction(null);
        }
        limpiarCampos();
        if (txtMotivo != null) txtMotivo.setTextFormatter(null);
        if (lblEstado != null) lblEstado.setText("");
        if (raiz != null) raiz.setDisable(true);
        original = null;
        fechaSugerida = null;
        datosVigentes = false;
        alConfirmar = null;
        alInvalidar = null;
        ventana = null;
    }

    /** Contiene los catálogos de pacientes y odontólogos cargados desde el servidor. */
    private record Catalogos(ArrayList<Paciente> pacientes, ArrayList<Odontologo> odontologos) { }
}