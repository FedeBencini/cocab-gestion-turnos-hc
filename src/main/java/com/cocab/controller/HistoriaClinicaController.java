package com.cocab.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.cocab.dao.HistoriaClinicaDAO;
import com.cocab.dao.impl.HistoriaClinicaDAOImpl;
import com.cocab.exception.ValidationException;
import com.cocab.model.historia.EvolucionClinica;
import com.cocab.model.historia.HistoriaClinica;
import com.cocab.model.odontologo.Odontologo;
import com.cocab.model.paciente.Paciente;
import com.cocab.model.seguridad.RolUsuario;
import com.cocab.util.UserSession;
import com.cocab.util.Validador;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Controlador de la historia clínica, manejando la consulta, apertura, corrección de la ficha y anexos.
 * Las evoluciones nunca se editan.
 */
public final class HistoriaClinicaController {
    private final String[] CABECERAS_TABLA = {"Fecha y hora", "Autor", "Matrícula", "Pieza dental", "ID"};
    private static final DateTimeFormatter MOMENTO = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private final ArrayList<PacienteOpcion> pacientes = new ArrayList<>();
    private final ArrayList<EvolucionClinica> evoluciones = new ArrayList<>();
    private final HistoriaClinicaDAO dao = new HistoriaClinicaDAOImpl();
    private EjecutorFx ejecutor;
    private long revision;
    private boolean activado;
    private boolean cerrado;
    private boolean actualizandoSeleccion;
    private boolean catalogoVigente;
    private boolean fichaConsultada;
    private boolean requiereReconciliacion;
    private boolean edicionAbierta;
    private int idPacienteAnexoPendienteConsulta;
    private PacienteOpcion seleccionado;
    private Cabecera cabecera;
    private ChangeListener<PacienteOpcion> cambioPaciente;
    private ChangeListener<Boolean> cambioInactivos;
    private ChangeListener<EvolucionClinica> cambioEvolucion;

    @FXML private VBox raiz;
    @FXML private ScrollPane scrollPrincipal;
    @FXML private ComboBox<PacienteOpcion> cmbPaciente;
    @FXML private TextField txtBuscarDni;
    @FXML private CheckBox chkIncluirInactivos;
    @FXML private Button btnBuscarDni;
    @FXML private Button btnRecargarPacientes;
    @FXML private Button btnRecargarFicha;
    @FXML private Label lblPaciente;
    @FXML private Label lblCatalogo;
    @FXML private Label lblFicha;
    @FXML private Label lblResultado;
    @FXML private Label lblEstado;
    @FXML private ProgressIndicator progreso;
    @FXML private VBox panelCabecera;
    @FXML private Label lblLegajo;
    @FXML private Label lblActividadFicha;
    @FXML private Label lblEdad;
    @FXML private Label lblFechaApertura;
    @FXML private Label lblGrupoLectura;
    @FXML private Label lblAlergiasLectura;
    @FXML private Label lblAntecedentesLectura;
    @FXML private Button btnModificarFicha;
    @FXML private VBox panelEdicion;
    @FXML private TextField txtGrupoEdicion;
    @FXML private TextArea txtAlergiasEdicion;
    @FXML private TextArea txtAntecedentesEdicion;
    @FXML private Button btnGuardarCabecera;
    @FXML private Button btnRestaurarCabecera;
    @FXML private VBox panelApertura;
    @FXML private TextField txtGrupoInicial;
    @FXML private TextArea txtAlergiasIniciales;
    @FXML private TextArea txtAntecedentesIniciales;
    @FXML private Button btnAbrir;
    @FXML private Button btnLimpiarApertura;
    @FXML private TableView<EvolucionClinica> tablaEvoluciones;
    @FXML private Label lblDetalle;
    @FXML private Label lblDiagnosticoLectura;
    @FXML private Label lblTratamientoLectura;
    @FXML private Label lblObservacionesLectura;
    @FXML private VBox panelAnexo;
    @FXML private TextField txtPieza;
    @FXML private TextArea txtDiagnostico;
    @FXML private TextArea txtTratamiento;
    @FXML private TextArea txtObservaciones;
    @FXML private Button btnAnexar;
    @FXML private Button btnLimpiarAnexo;

    public HistoriaClinicaController() {
    }

    /** Solo guarda local y presentación; no consulta datos. */
    @FXML
    private void initialize() {
        if (cerrado) throw new IllegalStateException("La vista ya fue retirada.");
        UserSession.getInstance().exigirRol(RolUsuario.Odontologo);
        ejecutor = new EjecutorFx(raiz, this::actualizarControles, this::cerrar, RolUsuario.Odontologo);
        chkIncluirInactivos.setSelected(false);
        txtBuscarDni.setTextFormatter(new TextFormatter<String>(cambio ->
                cambio.getControlNewText().matches("[0-9]{0,8}") ? cambio : null));
        cmbPaciente.setConverter(new StringConverter<>() {
            @Override public String toString(PacienteOpcion valor) { return valor == null ? "" : valor.etiqueta(); }
            @Override public PacienteOpcion fromString(String texto) {
                throw new UnsupportedOperationException("Seleccione un paciente del catálogo.");
            }
        });
        // Los campos clínicos no filtran ni cortan texto: Validador rechaza excesos al confirmar.
        tablaEvoluciones.setEditable(false);
        agregarColumna(0, evolucion -> MOMENTO.format(evolucion.getFechaHora()), 185);
        agregarColumna(1, evolucion -> evolucion.getOdontologo().obtenerNombreCompleto(), 250);
        agregarColumna(2, evolucion -> evolucion.getOdontologo().getMatricula(), 140);
        agregarColumna(3, evolucion -> informado(evolucion.getPiezaDental()), 160);
        agregarColumna(4, evolucion -> Integer.toString(evolucion.getIdEvolucion()), 80);
        cambioPaciente = (observable, anterior, actual) -> seleccionarPaciente(actual);
        cmbPaciente.valueProperty().addListener(cambioPaciente);
        cambioInactivos = (observable, anterior, actual) -> {
            if (cerrado || actualizandoSeleccion) return;
            revision++;
            invalidarTodo();
            lblResultado.setText("Filtro cambiado. Los borradores fueron descartados; recargue pacientes.");
            actualizarControles();
        };
        chkIncluirInactivos.selectedProperty().addListener(cambioInactivos);
        cambioEvolucion = (observable, anterior, actual) -> mostrarDetalle(actual);
        tablaEvoluciones.getSelectionModel().selectedItemProperty().addListener(cambioEvolucion);
        invalidarTodo();
        actualizarControles();
    }

    /** Agrega una columna a la tabla de evoluciones con el índice, valor y ancho especificados. */
    private void agregarColumna(int indice, Function<EvolucionClinica, String> valor, double ancho) {
        TableColumn<EvolucionClinica, String> columna = new TableColumn<>(CABECERAS_TABLA[indice]);
        columna.setCellValueFactory(celda -> new SimpleStringProperty(valor.apply(celda.getValue())));
        columna.setPrefWidth(ancho);
        columna.setSortable(false); // Conservar fecha/hora + ID, incluidos empates de segundo.
        tablaEvoluciones.getColumns().add(columna);
    }

    /** Activa la vista, recargando pacientes y estableciendo el foco inicial. */
    public void activar() {
        if (cerrado) return;
        if (ejecutor == null) throw new IllegalStateException("Cargue FXML antes de activar la vista.");
        if (!activado && ejecutor.vigente()) {
            activado = true;
            recargarPacientes();
            // Sin esto el foco inicial cae en un campo de lectura y el panel se desplaza hasta él.
            irAlInicio();
            Platform.runLater(this::irAlInicio);
        }
    }

    /** Desplaza la vista al inicio, estableciendo el foco en el contenedor raíz. */
    private void irAlInicio() {
        if (cerrado || raiz == null || scrollPrincipal == null) return;
        raiz.requestFocus();
        scrollPrincipal.setVvalue(scrollPrincipal.getVmin());
    }

    /** Verifica si la vista está activa y se puede interactuar con ella. */
    private boolean autorizar() {
        return !cerrado && activado && ejecutor != null && ejecutor.vigente() && !ejecutor.ocupado();
    }

    /** Verifica si la selección actual de paciente sigue siendo válida. */
    private boolean seleccionVigente() {
        return catalogoVigente && seleccionado != null && seleccionado.equals(cmbPaciente.getValue())
                && pacientes.contains(seleccionado);
    }

    /** Verifica si se puede autorizar una operación de escritura, considerando apertura o modificación. */
    private boolean autorizarEscritura(boolean apertura) {
        if (!autorizar() || !seleccionVigente() || !fichaConsultada) return false;
        if (requiereReconciliacion) {
            ejecutor.advertir("Solo consulta", "Existe una escritura de resultado desconocido. Reconcilie con "
                    + "soporte antes de reabrir para operar. Recargar no habilita escrituras en esta ventana.");
            return false;
        }
        if (!seleccionado.activo() || (cabecera != null && !cabecera.activa())) {
            ejecutor.advertir("Solo lectura", "Un paciente inactivo no admite apertura ni anexos. "
                    + "Una HC inactiva no admite anexos ni una segunda ficha.");
            return false;
        }
        return apertura ? cabecera == null : cabecera != null && cabecera.idPaciente() == seleccionado.id();
    }

    /** Recarga la lista de pacientes desde el DAO, actualizando el catálogo y la selección. */
    @FXML
    private void recargarPacientes() {
        if (!autorizar()) return;
        final boolean incluir = chkIncluirInactivos.isSelected();
        final long solicitud = ++revision;
        invalidarTodo();
        lblResultado.setText(idPacienteAnexoPendienteConsulta > 0
                ? "El último anexo fue confirmado. Seleccione su paciente para verificarlo; no lo vuelva a enviar."
                : "Recarga de pacientes: ficha y borradores descartados.");
        ejecutor.ejecutar(false, () -> listarOpciones(incluir), opciones -> {
            if (solicitud != revision) return;
            aplicarCatalogo(opciones, null);
            lblFicha.setText("Seleccione un paciente para consultar. No se abre una HC automáticamente.");
        }, fallo -> fallar(fallo, false));
    }

    /** Lista las opciones de pacientes sin consultar historias clínicas ni reutilizar el catálogo administrativo de secretaria. */
    private ArrayList<PacienteOpcion> listarOpciones(boolean incluir) {
        ArrayList<PacienteOpcion> opciones = new ArrayList<>();
        for (Paciente paciente : dao.listarPacientes(incluir)) {
            if (incluir || paciente.estaHabilitado()) {
                opciones.add(new PacienteOpcion(paciente.getIdPaciente(), paciente.getDni(),
                        paciente.obtenerNombreCompleto(), paciente.estaHabilitado(), paciente.calcularEdad()));
            }
        }
        return opciones;
    }

    /** Aplica el catálogo de pacientes al combo, actualizando la selección y la etiqueta correspondiente. */
    private void aplicarCatalogo(ArrayList<PacienteOpcion> opciones, PacienteOpcion elegido) {
        actualizandoSeleccion = true;
        try {
            pacientes.clear();
            pacientes.addAll(opciones);
            cmbPaciente.getItems().setAll(pacientes);
            cmbPaciente.setValue(elegido);
            seleccionado = elegido;
            catalogoVigente = true;
            lblCatalogo.setText(pacientes.size() + " pacientes cargados · Catálogo sin precarga de historias clínicas.");
            lblPaciente.setText(elegido == null ? "Sin paciente seleccionado." : elegido.etiqueta());
        } finally {
            actualizandoSeleccion = false;
        }
    }

    /** Selecciona un paciente del catálogo, limpiando la ficha y actualizando la interfaz. */
    private void seleccionarPaciente(PacienteOpcion paciente) {
        if (cerrado || actualizandoSeleccion) return;
        revision++;
        limpiarFicha();
        seleccionado = paciente;
        lblPaciente.setText(paciente == null ? "Sin paciente seleccionado." : paciente.etiqueta());
        lblResultado.setText("Selección cambiada: se descartaron la ficha anterior y todos sus borradores.");
        if (autorizar() && seleccionVigente()) cargarFicha();
        actualizarControles();
    }

    /** Busca un paciente por DNI en el catálogo cargado y actualiza la selección. */
    @FXML
    private void buscarDni() {
        if (!autorizar() || !catalogoVigente) return;
        try {
            String dni = Validador.validarDni(txtBuscarDni.getText());
            // Búsqueda exacta sobre el catálogo cargado, independiente de las filas del historial.
            PacienteOpcion encontrado = null;
            for (PacienteOpcion paciente : pacientes) {
                if (paciente.dni().equals(dni)) {
                    encontrado = paciente;
                    break;
                }
            }
            if (encontrado == null) {
                cmbPaciente.setValue(null);
                ejecutor.advertir("Paciente no encontrado", "El DNI no está en el catálogo cargado. "
                        + "Puede incluir inactivos y recargar pacientes para consultar históricos.");
            } else if (encontrado.equals(cmbPaciente.getValue())) {
                recargarFicha();
            } else {
                cmbPaciente.setValue(encontrado);
            }
        } catch (ValidationException excepcion) {
            ejecutor.advertir("Revise el DNI", excepcion.getMessage());
        }
    }

    /** Recarga la ficha del paciente seleccionado, descartando borradores y releyendo la actividad. */
    @FXML
    private void recargarFicha() {
        if (!autorizar() || !seleccionVigente()) return;
        revision++;
        lblResultado.setText(idPacienteAnexoPendienteConsulta == seleccionado.id()
                ? "Anexo confirmado. Consultando nuevamente, sin repetir la escritura."
                : "Recarga de ficha: los borradores se descartan; se relee también la actividad del paciente.");
        cargarFicha();
    }

    /** Carga la ficha del paciente seleccionado desde el DAO, aplicando el catálogo y la historia clínica. */
    private void cargarFicha() {
        final int idPaciente = seleccionado.id();
        final long solicitud = revision;
        final boolean incluir = chkIncluirInactivos.isSelected();
        limpiarFicha();
        lblFicha.setText("Consultando la ficha del paciente seleccionado…");
        ejecutor.ejecutar(false, () -> {
            ArrayList<PacienteOpcion> opciones = listarOpciones(incluir);
            PacienteOpcion pacienteActual = null;
            for (PacienteOpcion opcion : opciones) {
                if (opcion.id() == idPaciente) { pacienteActual = opcion; break; }
            }
            if (pacienteActual == null) {
                throw new ValidationException("El paciente ya no está disponible para el filtro vigente.");
            }
            // Mismo ID capturado; nunca leer ComboBox ni campos desde el trabajador.
            return new CargaFicha(opciones, pacienteActual, dao.buscarPorPaciente(idPaciente));
        }, carga -> {
            if (!coincide(solicitud, idPaciente)) return;
            if (carga.paciente().id() != idPaciente) throw new ValidationException("Paciente de respuesta inconsistente.");
            aplicarCatalogo(carga.pacientes(), carga.paciente());
            aplicarFicha(carga.historia());
            if (idPacienteAnexoPendienteConsulta == idPaciente) {
                lblResultado.setText("Consulta actualizada después del anexo confirmado. No se repitió la escritura.");
                idPacienteAnexoPendienteConsulta = 0;
            }
        }, fallo -> fallar(fallo, false));
    }

    /** Verifica si la solicitud y el paciente coinciden con la selección vigente. */
    private boolean coincide(long solicitud, int idPaciente) {
        return solicitud == revision && seleccionVigente() && seleccionado.id() == idPaciente;
    }

    /** Aplica la historia clínica obtenida al formulario, validando consistencia y actualizando la interfaz. */
    private void aplicarFicha(Optional<HistoriaClinica> respuesta) {
        if (respuesta.isEmpty()) {
            cabecera = null;
            fichaConsultada = true;
            lblFicha.setText(seleccionado.activo()
                    ? "No hay HC. Puede completar datos iniciales y confirmar explícitamente su apertura."
                    : "Paciente inactivo sin HC. Solo consulta: no se permite abrir una ficha.");
            return;
        }
        HistoriaClinica historia = respuesta.get();
        if (historia.getIdPaciente() != seleccionado.id()) {
            throw new ValidationException("La respuesta no corresponde al paciente seleccionado.");
        }
        Validador.validarIdPersistido(historia.getIdHistoriaClinica(), "La historia clínica");
        ArrayList<EvolucionClinica> historial = historia.obtenerHistorialCronologico();
        for (EvolucionClinica evolucion : historial) {
            if (!evolucion.asegurarInmutabilidad() || evolucion.getIdHistoriaClinica() != historia.getIdHistoriaClinica()) {
                throw new ValidationException("El historial no contiene exclusivamente registros persistidos del legajo.");
            }
        }
        cabecera = new Cabecera(historia.getIdHistoriaClinica(), historia.getIdPaciente(), historia.isEstadoActivo(),
                historia.getNumeroLegajo(), historia.getFechaApertura(), historia.getGrupoSanguineo(),
                historia.getAlergias(), historia.getAntecedentesMedicos());
        evoluciones.clear();
        evoluciones.addAll(historial); // Copias profundas persistidas e inmutables del modelo.
        tablaEvoluciones.getItems().setAll(evoluciones);
        tablaEvoluciones.setPlaceholder(new Label("La HC no tiene evoluciones registradas."));
        lblLegajo.setText("Legajo: " + cabecera.legajo());
        lblActividadFicha.setText("HC INACTIVA · Solo lectura · No admite otra HC");
        mostrarPanel(lblActividadFicha, !cabecera.activa());
        lblEdad.setText(seleccionado.edad() + (seleccionado.edad() == 1 ? " año" : " años"));
        lblFechaApertura.setText(FECHA.format(cabecera.fechaApertura()));
        lblGrupoLectura.setText(informado(historia.getGrupoSanguineo()));
        lblAlergiasLectura.setText(informado(historia.getAlergias()));
        lblAntecedentesLectura.setText(informado(historia.getAntecedentesMedicos()));
        cargarCamposEdicion();
        fichaConsultada = true;
        lblFicha.setText("Ficha consultada · " + evoluciones.size()
                + " evoluciones de todos sus autores · Orden fecha/hora e ID.");
    }

    /** Abre una nueva historia clínica para el paciente seleccionado, solicitando confirmación y aplicando los datos iniciales. */
    @FXML
    private void abrirHistoria() {
        if (!autorizarEscritura(true)) return;
        try {
            final String grupo = opcional(Validador.validarOpcional(txtGrupoInicial.getText(), "El grupo sanguíneo", 10));
            final String alergias = opcional(Validador.validarTextoOpcional(txtAlergiasIniciales.getText(), "Las alergias"));
            final String antecedentes = opcional(Validador.validarTextoOpcional(txtAntecedentesIniciales.getText(), "Los antecedentes médicos"));
            final int idPaciente = seleccionado.id();
            final long solicitud = revision;
            if (!ejecutor.confirmar("Confirmar apertura de ficha", "Paciente: " + seleccionado.etiqueta()
                    + "\n\nBorrador de Ficha NO GUARDADA\n- Grupo sanguíneo:\n" + informado(grupo)
                    + "\n- Alergias:\n" + informado(alergias) + "\n- Antecedentes médicos:\n" + informado(antecedentes)
                    + "\n\nEstos campos podrán corregirse después desde la sección de datos de la ficha. "
                    + "El servidor asignará legajo y fecha de apertura. Si otra apertura ya existe, se devolverá esa "
                    + "ficha y se descartarán TODOS estos datos propuestos, sin sobrescribirla. ¿Continuar?")
                    || !coincide(solicitud, idPaciente) || !autorizarEscritura(true)) return;
            limpiarFicha();
            lblResultado.setText("Apertura solicitada. El resultado aún no está confirmado.");
            ejecutor.ejecutar(true, () -> dao.abrir(idPaciente, grupo, alergias, antecedentes),
                    historia -> {
                        if (!coincide(solicitud, idPaciente)) return;
                        aplicarFicha(Optional.of(historia));
                        lblResultado.setText("Ficha disponible. Puede ser una ficha ya existente; en ese caso se "
                                + "descartaron los datos iniciales propuestos. Revise la cabecera devuelta por el servidor.");
                    }, fallo -> fallar(fallo, true));
        } catch (ValidationException excepcion) {
            ejecutor.advertir("Revise los datos iniciales", excepcion.getMessage());
        }
    }

    /** Guarda los cambios realizados en la cabecera de la historia clínica del paciente seleccionado, solicitando confirmación y aplicando las actualizaciones. */
    @FXML
    private void guardarCabecera() {
        if (!autorizarEscritura(false)) return;
        try {
            final String grupo = opcional(Validador.validarOpcional(txtGrupoEdicion.getText(), "El grupo sanguíneo", 10));
            final String alergias = opcional(Validador.validarTextoOpcional(txtAlergiasEdicion.getText(), "Las alergias"));
            final String antecedentes = opcional(Validador.validarTextoOpcional(txtAntecedentesEdicion.getText(), "Los antecedentes médicos"));
            final String grupoActual = opcional(cabecera.grupoSanguineo());
            final String alergiasActuales = opcional(cabecera.alergias());
            final String antecedentesActuales = opcional(cabecera.antecedentesMedicos());
            if (Objects.equals(grupo, grupoActual) && Objects.equals(alergias, alergiasActuales)
                    && Objects.equals(antecedentes, antecedentesActuales)) {
                ejecutor.advertir("Sin cambios", "Los datos ingresados son iguales a los registrados. No se realizó ninguna escritura.");
                return;
            }
            final int idPaciente = seleccionado.id();
            final int idHistoria = cabecera.id();
            final long solicitud = revision;
            if (!ejecutor.confirmar("Confirmar cambios de la ficha", "Paciente: " + seleccionado.etiqueta()
                    + "\nLegajo: " + cabecera.legajo()
                    + "\n\nBorrador de Cambios NO GUARDADOS"
                    + diferencia("Grupo sanguíneo", grupoActual, grupo)
                    + diferencia("Alergias", alergiasActuales, alergias)
                    + diferencia("Antecedentes médicos", antecedentesActuales, antecedentes)
                    + "\n\nLos valores nuevos reemplazarán a los persistidos; no se conserva un historial de la ficha. "
                    + "Legajo, apertura y evoluciones no se modifican. ¿Confirmar?")
                    || !coincide(solicitud, idPaciente) || !autorizarEscritura(false)
                    || cabecera.id() != idHistoria) return;
            limpiarFicha();
            lblResultado.setText("Actualización de la ficha solicitada. El resultado aún no está confirmado.");
            ejecutor.ejecutar(true, () -> dao.actualizarCabecera(
                    idPaciente, idHistoria, grupo, alergias, antecedentes), historia -> {
                if (!coincide(solicitud, idPaciente)) return;
                aplicarFicha(Optional.of(historia));
                lblResultado.setText("Ficha actualizada. Revise los valores devueltos por el servidor.");
            }, fallo -> fallar(fallo, true));
        } catch (ValidationException excepcion) {
            ejecutor.advertir("Revise los datos de la ficha", excepcion.getMessage());
        }
    }

    /** Abre la sección de edición de la cabecera de la historia clínica, cargando los campos actuales y actualizando la interfaz. */
    @FXML
    private void abrirEdicion() {
        if (!autorizar() || cabecera == null) return;
        cargarCamposEdicion();
        edicionAbierta = true;
        actualizarControles();
        txtGrupoEdicion.requestFocus();
    }

    /** Restaura los valores originales de la cabecera de la historia clínica, descartando los cambios en edición y actualizando la interfaz. */
    @FXML
    private void restaurarCabecera() {
        if (!autorizar()) return;
        cargarCamposEdicion();
        edicionAbierta = false;
        actualizarControles();
        lblResultado.setText("Cambios de la ficha descartados. No se realizó ninguna escritura.");
    }

    /** Compara los valores antes y después de un campo, devolviendo una descripción de la diferencia. */
    private static String diferencia(String campo, String antes, String despues) {
        if (Objects.equals(antes, despues)) {
            return "\n- " + campo + ": sin cambios";
        }
        return "\n- " + campo + " (MODIFICADO)\n  Antes: " + informado(antes) + "\n  Después: " + informado(despues);
    }

    /** Carga los valores actuales de la cabecera de la historia clínica en los campos de edición. */
    private void cargarCamposEdicion() {
        if (cabecera == null) return;
        txtGrupoEdicion.setText(cabecera.grupoSanguineo() == null ? "" : cabecera.grupoSanguineo());
        txtAlergiasEdicion.setText(cabecera.alergias() == null ? "" : cabecera.alergias());
        txtAntecedentesEdicion.setText(cabecera.antecedentesMedicos() == null ? "" : cabecera.antecedentesMedicos());
    }

    /** Anexa una nueva evolución a la historia clínica del paciente seleccionado, solicitando confirmación y aplicando los datos ingresados. */
    @FXML
    private void anexarEvolucion() {
        if (!autorizarEscritura(false)) return;
        try {
            final String pieza = opcional(Validador.validarOpcional(txtPieza.getText(), "La pieza dental", 20));
            final String diagnostico = Validador.validarTextoObligatorio(txtDiagnostico.getText(), "El diagnóstico");
            final String tratamiento = Validador.validarTextoObligatorio(txtTratamiento.getText(), "El tratamiento realizado");
            final String observaciones = opcional(Validador.validarTextoOpcional(txtObservaciones.getText(), "Las observaciones"));
            final int idPaciente = seleccionado.id();
            final int idHistoria = cabecera.id();
            final long solicitud = revision;
            if (!ejecutor.confirmar("Confirmar evolución inmutable", "Paciente: " + seleccionado.etiqueta()
                    + "\nLegajo: " + cabecera.legajo() + "\n\nBorrador de Evolución NO GUARDADO\n- Pieza dental:\n" + informado(pieza)
                    + "\n- Diagnóstico:\n" + diagnostico + "\n- Tratamiento realizado:\n" + tratamiento
                    + "\n- Observaciones:\n" + informado(observaciones)
                    + "\n\nSe anexará una NUEVA evolución. Una vez persistida no podrá editarse ni eliminarse."
                    + "\nEl servidor resolverá el autor desde su sesión y asignará fecha/hora automáticamente. "
                    + "¿Confirmar?")
                    || !coincide(solicitud, idPaciente) || !autorizarEscritura(false)
                    || cabecera.id() != idHistoria) return;
            limpiarFicha();
            lblResultado.setText("Anexo solicitado. No repita el envío; todavía no está confirmado.");
            ejecutor.ejecutar(true, () -> dao.anexarEvolucion(
                    idPaciente, idHistoria, pieza, diagnostico, tratamiento, observaciones), evolucion -> {
                idPacienteAnexoPendienteConsulta = idPaciente;
                if (!coincide(solicitud, idPaciente)) return;
                lblResultado.setText("Anexo confirmado. Consultando la ficha actual, sin repetir la escritura.");
                // No insertar la respuesta en la tabla ni simular un registro confirmado sobre un borrador.
                cargarFicha();
            }, fallo -> fallar(fallo, true));
        } catch (ValidationException excepcion) {
            ejecutor.advertir("Revise el borrador", excepcion.getMessage());
        }
    }

    /** Muestra el detalle de una evolución específica, cargando los campos de solo lectura y actualizando la interfaz. */
    private void mostrarDetalle(EvolucionClinica evolucion) {
        limpiarDetalle();
        if (cerrado || ejecutor == null || !ejecutor.vigente() || !fichaConsultada || evolucion == null
                || !evoluciones.contains(evolucion) || cabecera == null
                || evolucion.getIdHistoriaClinica() != cabecera.id()) return;
        Odontologo autor = evolucion.getOdontologo();
        lblDetalle.setText("Evolución #" + evolucion.getIdEvolucion() + " · " + MOMENTO.format(evolucion.getFechaHora())
                + "\nAutor: " + autor.obtenerNombreCompleto() + " · Matrícula: " + autor.getMatricula()
                + "\nPieza dental: " + informado(evolucion.getPiezaDental()) + " · Registro persistido, solo lectura.");
        lblDiagnosticoLectura.setText(informado(evolucion.getDiagnostico()));
        lblTratamientoLectura.setText(informado(evolucion.getTratamientoRealizado()));
        lblObservacionesLectura.setText(informado(evolucion.getObservaciones()));
    }

    /** Limpia los campos de apertura de la historia clínica, descartando el borrador y actualizando la interfaz. */
    @FXML
    private void limpiarApertura() {
        if (!autorizar()) return;
        limpiarCamposApertura();
        lblResultado.setText("Borrador de apertura descartado. No se realizó ninguna escritura.");
    }

    /** Limpia los campos de anexo de la historia clínica, descartando el borrador y actualizando la interfaz. */
    @FXML
    private void limpiarAnexo() {
        if (!autorizar()) return;
        limpiarCamposAnexo();
        lblResultado.setText("Borrador de evolución descartado. El historial persistido no se modificó.");
    }

    /** Maneja un fallo ocurrido durante una operación de escritura o consulta, actualizando la interfaz y registrando el error. */
    private void fallar(Throwable fallo, boolean escritura) {
        if (escritura && !(fallo instanceof ValidationException)) requiereReconciliacion = true;
        boolean falloConsultaPosterior = !escritura && seleccionado != null
                && idPacienteAnexoPendienteConsulta == seleccionado.id();
        revision++;
        invalidarTodo();
        lblResultado.setText(requiereReconciliacion
                ? "Resultado de escritura desconocido. Solo consulta hasta reconciliación externa y reapertura."
                : falloConsultaPosterior
                ? "El anexo fue confirmado, pero no se pudo actualizar la ficha. No lo vuelva a enviar."
                : "Datos y borradores descartados por un fallo. Recargue pacientes antes de continuar.");
        actualizarControles();
        if (falloConsultaPosterior) {
            ejecutor.error("Operación no confirmada", "El anexo fue confirmado, pero no se pudo actualizar la ficha. "
                    + "No lo repita: recargue la ficha para verificarlo.");
        } else {
            ejecutor.fallo(fallo, escritura);
        }
    }

    /** Actualiza el estado de los controles de la interfaz según la situación actual de la historia clínica y la autorización del usuario. */
    private void actualizarControles() {
        if (cerrado || ejecutor == null) return;
        boolean libre = activado && !ejecutor.ocupado();
        boolean seleccion = seleccionVigente();
        boolean lectura = seleccion && fichaConsultada;
        boolean escritura = libre && lectura && seleccionado.activo() && !requiereReconciliacion;
        boolean apertura = escritura && cabecera == null;
        boolean anexo = escritura && cabecera != null && cabecera.activa();
        cmbPaciente.setDisable(!libre || !catalogoVigente);
        chkIncluirInactivos.setDisable(!libre);
        txtBuscarDni.setDisable(!libre || !catalogoVigente);
        btnBuscarDni.setDisable(!libre || !catalogoVigente);
        btnRecargarPacientes.setDisable(!libre);
        btnRecargarFicha.setDisable(!libre || !seleccion);
        mostrarPanel(panelCabecera, lectura && cabecera != null);
        mostrarPanel(panelApertura, lectura && cabecera == null);
        panelApertura.setDisable(!apertura);
        if (!(lectura && cabecera != null && cabecera.activa() && seleccionado.activo() && !requiereReconciliacion)) {
            edicionAbierta = false;
        }
        mostrarPanel(panelEdicion, lectura && cabecera != null && edicionAbierta);
        panelEdicion.setDisable(!anexo);
        btnModificarFicha.setDisable(!anexo);
        mostrarPanel(btnModificarFicha, lectura && cabecera != null && !edicionAbierta);
        btnGuardarCabecera.setDisable(!anexo);
        btnRestaurarCabecera.setDisable(!anexo);
        btnAbrir.setDisable(!apertura);
        btnLimpiarApertura.setDisable(!apertura);
        panelAnexo.setDisable(!anexo);
        btnAnexar.setDisable(!anexo);
        btnLimpiarAnexo.setDisable(!anexo);
        tablaEvoluciones.setDisable(!libre || !lectura || cabecera == null);
        progreso.setVisible(ejecutor.ocupado());
        progreso.setManaged(ejecutor.ocupado());
        lblEstado.setText(requiereReconciliacion
                ? "SOLO CONSULTA: escritura de resultado incierto. Recargar no desbloquea esta ventana. "
                + "Reconciliación externa con soporte y reapertura necesarias; no repita la operación."
                : ejecutor.hayEscrituraEnCurso() ? "Escritura en curso. Cerrar o cancelar no garantiza rollback."
                : ejecutor.ocupado() ? "Consulta o confirmación en curso…"
                : !catalogoVigente ? "Sin catálogo vigente. Recargue pacientes."
                : !seleccion ? "Seleccione un paciente o busque su DNI exacto (7 u 8 dígitos)."
                : !fichaConsultada ? "Sin consulta vigente de HC. Recargue la ficha."
                : !seleccionado.activo() ? "Paciente INACTIVO: solo lectura, incluso si su HC está activa."
                : cabecera != null && !cabecera.activa() ? "HC INACTIVA: solo lectura. No se permiten anexos ni una segunda HC."
                : "El servidor revalida acceso y actividad al operar. Los datos cargados no son una autorización permanente.");
    }

    /** Muestra u oculta un panel de la interfaz según el valor de 'mostrar', ajustando su visibilidad y gestión de layout. */
    private static void mostrarPanel(Node panel, boolean mostrar) {
        panel.setVisible(mostrar);
        panel.setManaged(mostrar);
    }

    /** Invalida toda la información cargada en la interfaz, restableciendo el estado inicial y limpiando la selección de paciente. */
    private void invalidarTodo() {
        catalogoVigente = false;
        seleccionado = null;
        actualizandoSeleccion = true;
        try {
            if (cmbPaciente != null) { cmbPaciente.setValue(null); cmbPaciente.getItems().clear(); }
            pacientes.clear();
        } finally {
            actualizandoSeleccion = false;
        }
        limpiarFicha();
        if (txtBuscarDni != null) txtBuscarDni.clear();
        if (lblPaciente != null) lblPaciente.setText("Sin paciente seleccionado.");
        if (lblCatalogo != null) lblCatalogo.setText("Sin catálogo vigente. Recargue pacientes.");
    }

    /** Limpia toda la información de la ficha de la historia clínica, restableciendo el estado de edición y la interfaz asociada. */
    private void limpiarFicha() {
        fichaConsultada = false;
        cabecera = null;
        edicionAbierta = false;
        evoluciones.clear();
        if (tablaEvoluciones != null) {
            tablaEvoluciones.getSelectionModel().clearSelection();
            tablaEvoluciones.getItems().clear();
            tablaEvoluciones.setPlaceholder(new Label("Sin ficha consultada."));
        }
        limpiarDetalle();
        limpiarCamposApertura();
        limpiarCamposAnexo();
        limpiarTextos(txtGrupoEdicion, txtAlergiasEdicion, txtAntecedentesEdicion);
        for (Label etiqueta : new Label[] {lblLegajo, lblActividadFicha, lblEdad, lblFechaApertura, lblGrupoLectura,
                lblAlergiasLectura, lblAntecedentesLectura}) {
            if (etiqueta != null) etiqueta.setText("");
        }
        if (lblFicha != null) lblFicha.setText("Sin ficha consultada. Recargue para conocer el estado persistido.");
    }

    /** Limpia la información detallada de la evolución seleccionada, restableciendo los campos de solo lectura. */
    private void limpiarDetalle() {
        for (Label etiqueta : new Label[] {lblDiagnosticoLectura, lblTratamientoLectura, lblObservacionesLectura}) {
            if (etiqueta != null) etiqueta.setText("");
        }
        if (lblDetalle != null) lblDetalle.setText("Seleccione una evolución para leer su texto completo. No se copia al borrador.");
    }

    /** Limpia los campos de apertura de la historia clínica, descartando el borrador y actualizando la interfaz. */
    private void limpiarCamposApertura() {
        limpiarTextos(txtGrupoInicial, txtAlergiasIniciales, txtAntecedentesIniciales);
    }

    /** Limpia los campos de anexo de la historia clínica, descartando el borrador y actualizando la interfaz. */
    private void limpiarCamposAnexo() {
        limpiarTextos(txtPieza, txtDiagnostico, txtTratamiento, txtObservaciones);
    }

    /** Limpia los textos de los controles de entrada proporcionados, descartando cualquier contenido previo. */
    private static void limpiarTextos(TextInputControl... controles) {
        for (TextInputControl control : controles) {
            if (control != null) control.clear();
        }
    }

    /** Devuelve un valor informado o un texto por defecto si no se proporciona. */
    private static String informado(String valor) {
        return valor == null || valor.isBlank() ? "No informado" : valor;
    }

    /** Devuelve un valor opcional, considerando solo vacío o espacios como no informados; nunca recorta texto clínico. */
    private static String opcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }

    /** Indica si hay una escritura en curso en la historia clínica. */
    public boolean hayEscrituraEnCurso() {
        return ejecutor != null && ejecutor.hayEscrituraEnCurso();
    }

    /** Cierra la historia clínica, liberando recursos, eliminando listeners y deshabilitando la interfaz asociada. */
    public void cerrar() {
        if (cerrado) return;
        cerrado = true;
        revision++;
        if (ejecutor != null) ejecutor.cerrar();
        if (cmbPaciente != null && cambioPaciente != null) cmbPaciente.valueProperty().removeListener(cambioPaciente);
        if (chkIncluirInactivos != null && cambioInactivos != null) chkIncluirInactivos.selectedProperty().removeListener(cambioInactivos);
        if (tablaEvoluciones != null && cambioEvolucion != null) {
            tablaEvoluciones.getSelectionModel().selectedItemProperty().removeListener(cambioEvolucion);
        }
        cambioPaciente = null;
        cambioInactivos = null;
        cambioEvolucion = null;
        idPacienteAnexoPendienteConsulta = 0;
        invalidarTodo();
        if (tablaEvoluciones != null) tablaEvoluciones.getColumns().clear();
        if (cmbPaciente != null) { cmbPaciente.hide(); cmbPaciente.setConverter(null); }
        if (txtBuscarDni != null) { txtBuscarDni.setTextFormatter(null); txtBuscarDni.setOnAction(null); }
        for (Button boton : new Button[] {btnBuscarDni, btnRecargarPacientes, btnRecargarFicha,
                btnAbrir, btnLimpiarApertura, btnAnexar, btnLimpiarAnexo, btnGuardarCabecera,
                btnRestaurarCabecera, btnModificarFicha}) {
            if (boton != null) boton.setOnAction(null);
        }
        for (Label etiqueta : new Label[] {lblPaciente, lblCatalogo, lblFicha, lblResultado, lblEstado, lblDetalle}) {
            if (etiqueta != null) etiqueta.setText("");
        }
        if (raiz != null) raiz.setDisable(true);
    }

    /** Representa una opción de paciente en la interfaz, incluyendo su estado de actividad y edad. */
    private record PacienteOpcion(int id, String dni, String nombre, boolean activo, int edad) {
        private String etiqueta() { return nombre + " · DNI " + dni + (activo ? " · Activo" : " · INACTIVO"); }
    }

    /** Representa la cabecera de la historia clínica, incluyendo información del paciente y detalles médicos básicos. */
    private record Cabecera(int id, int idPaciente, boolean activa, String legajo, LocalDate fechaApertura,
                            String grupoSanguineo, String alergias, String antecedentesMedicos) { }
    /** Representa la carga inicial de la ficha de la historia clínica, incluyendo la lista de pacientes, el paciente seleccionado y la historia clínica opcional. */
    private record CargaFicha(ArrayList<PacienteOpcion> pacientes, PacienteOpcion paciente,
                              Optional<HistoriaClinica> historia) { }
}