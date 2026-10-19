package com.cocab.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;

import com.cocab.controller.AyudaVista.CamposPersona;
import com.cocab.controller.AyudaVista.DatosPersona;
import com.cocab.dao.PacienteDAO;
import com.cocab.dao.impl.PacienteDAOImpl;
import com.cocab.exception.ValidationException;
import com.cocab.model.paciente.Paciente;
import com.cocab.model.seguridad.RolUsuario;
import com.cocab.util.AlgoritmosClinica;
import com.cocab.util.DatosClinica;
import com.cocab.util.UserSession;
import com.cocab.util.Validador;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/** Padrón administrativo: sin acceso a HC, sin cuentas y sin administración del Stage. */
public final class PacientesController {
    private final String[] CABECERAS_TABLA = {
            "DNI", "Apellido", "Nombre", "Nacimiento", "Obra social", "Estado",
            "Teléfono", "Correo electrónico", "Dirección"
    };
    private static final DateTimeFormatter FECHA = AyudaVista.FECHA;
    private static final DateTimeFormatter MOMENTO = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss");
    private final ArrayList<Paciente> pacientes = new ArrayList<>();
    private final Map<String, Paciente> filasPorDni = new HashMap<>();
    private String[] dnisOrdenados = new String[0];
    private CapturaPaciente original;
    private final PacienteDAO dao = new PacienteDAOImpl();
    private boolean formularioDisponible;
    private boolean activado;
    private boolean cerrado;
    private boolean requiereRecarga = true;
    private EjecutorFx ejecutor;
    private CamposPersona campos;
    private ChangeListener<Paciente> cambioSeleccion;

    @FXML private VBox raiz;
    @FXML private TableView<Paciente> tablaPacientes;
    @FXML private VBox formulario;
    @FXML private TextField txtBusquedaDni;
    @FXML private CheckBox chkIncluirInactivos;
    @FXML private TextField txtDni;
    @FXML private TextField txtNombre;
    @FXML private TextField txtApellido;
    @FXML private TextField txtTelefono;
    @FXML private TextField txtEmail;
    @FXML private TextField txtDireccion;
    @FXML private DatePicker fechaNacimiento;
    @FXML private ComboBox<String> cmbObraSocial;
    @FXML private Button btnBuscar;
    @FXML private Button btnNuevo;
    @FXML private Button btnGuardar;
    @FXML private Button btnBaja;
    @FXML private Button btnRecargar;
    @FXML private Label lblIdentidad;
    @FXML private Label lblEstado;
    @FXML private Label lblCarga;

    public PacientesController() {
    }

    /** Configura presentación y permiso local; no consulta datos. */
    @FXML
    private void initialize() {
        UserSession.getInstance().exigirRol(RolUsuario.Secretaria);
        campos = new CamposPersona(txtDni, txtNombre, txtApellido, txtTelefono, txtEmail, txtDireccion);
        AyudaVista.mascaraDni(txtBusquedaDni);
        chkIncluirInactivos.setSelected(false);
        cmbObraSocial.setEditable(true);
        cmbObraSocial.getItems().setAll(DatosClinica.obtenerObrasSociales());
        AyudaVista.limitar(cmbObraSocial.getEditor(), 50);
        AyudaVista.prepararFecha(fechaNacimiento);
        tablaPacientes.setEditable(false);
        tablaPacientes.setPlaceholder(new Label("Sin registros cargados. Use Recargar."));
        agregarColumna(0, Paciente::getDni);
        agregarColumna(1, Paciente::getApellido);
        agregarColumna(2, Paciente::getNombre);
        agregarColumna(3, paciente -> FECHA.format(paciente.getFechaNacimiento()));
        agregarColumna(4, Paciente::getObraSocial);
        agregarColumna(5, paciente -> paciente.isEstadoActivo() ? "Activo" : "Inactivo");
        agregarColumna(6, Paciente::getTelefono);
        agregarColumna(7, Paciente::getEmail);
        agregarColumna(8, Paciente::getDireccion);
        ejecutor = new EjecutorFx(raiz, this::actualizarControles, this::cerrar, RolUsuario.Secretaria);
        cambioSeleccion = (observable, anterior, seleccionado) -> seleccionar(seleccionado);
        tablaPacientes.getSelectionModel().selectedItemProperty().addListener(cambioSeleccion);
        limpiarDatos();
        actualizarControles();
    }

    /** Agrega una columna a la tabla de pacientes con el valor especificado. */
    private void agregarColumna(int indice, Function<Paciente, String> valor) {
        TableColumn<Paciente, String> columna = new TableColumn<>(CABECERAS_TABLA[indice]);
        columna.setCellValueFactory(celda -> {
            String texto = valor.apply(celda.getValue());
            return new SimpleStringProperty(texto == null ? "—" : texto);
        });
        columna.setPrefWidth(indice == 8 ? 200 : 140);
        tablaPacientes.getColumns().add(columna);
    }

    /** Llama en FX después de abrir la vista; las llamadas posteriores no tienen efecto. */
    public void activar() {
        if (ejecutor == null) {
            throw new IllegalStateException("La vista debe cargarse mediante FXML antes de activarla.");
        }
        if (ejecutor.vigente() && !activado) {
            activado = true;
            recargar();
        }
    }

    /** Llama en FX al retirar la vista, también durante logout o cierre del propietario. */
    public void cerrar() {
        if (cerrado || ejecutor == null) return;
        cerrado = true;
        tablaPacientes.getSelectionModel().selectedItemProperty().removeListener(cambioSeleccion);
        ejecutor.cerrar();
        limpiarDatos();
    }

    /** Llama antes del cierre modal; cancelar/cerrar NO acredita rollback. */
    public boolean hayEscrituraEnCurso() {
        return ejecutor != null && ejecutor.hayEscrituraEnCurso();
    }

    /** Verifica si la acción está autorizada según el estado actual y si se necesitan datos. */
    private boolean autorizarAccion(boolean necesitaDatos) {
        if (!ejecutor.vigente() || !activado || ejecutor.ocupado()) {
            return false;
        }
        if (necesitaDatos && requiereRecarga) {
            ejecutor.advertir("Recarga necesaria", "Recargue el padrón correctamente antes de consultar o guardar datos.");
            return false;
        }
        return true;
    }

    /** Verifica si se permiten operaciones que requieren datos cargados. */
    private boolean permiteDatos() {
        return activado && !cerrado && !requiereRecarga && !ejecutor.ocupado();
    }

    /** Verifica si se permite recargar los datos de la tabla. */
    private boolean permiteRecarga() {
        return activado && !cerrado && !ejecutor.ocupado();
    }

    /** Descarta lo mostrado; ante un fallo o una escritura se exige recargar antes de seguir. Actualiza la interfaz en consecuencia. */
    private void invalidar() {
        requiereRecarga = true;
        limpiarDatos();
        actualizarControles();
    }

    /** Maneja un fallo en la operación, invalidando los datos y notificando al ejecutor. */
    private void fallar(Throwable fallo, boolean escritura) {
        invalidar();
        ejecutor.fallo(fallo, escritura);
    }

    /** Ejecuta una operación de escritura, invalidando los datos y notificando al ejecutor al completarse. */
    private <T> void escribir(Callable<T> operacion, String mensajeExito) {
        if (!autorizarAccion(true)) {
            return;
        }
        ejecutor.ejecutar(true, operacion, resultado -> {
            invalidar();
            ejecutor.informar("Operación confirmada", mensajeExito);
            recargar();
        }, fallo -> fallar(fallo, true));
    }

    /** Ejecuta la recarga de datos de la tabla, invalidando los datos actuales y actualizando la interfaz. */
    @FXML
    private void recargar() {
        if (!autorizarAccion(false)) {
            return;
        }
        invalidar();
        final boolean incluirInactivos = chkIncluirInactivos.isSelected();
        ejecutor.ejecutar(false, () -> dao.listar(incluirInactivos), resultado -> {
            aplicarLista(resultado);
            requiereRecarga = false;
        }, fallo -> fallar(fallo, false));
    }

    /** Maneja el cambio en la selección de incluir inactivos, recargando la tabla. */
    @FXML
    private void cambiarInactivos() {
        recargar();
    }

    /** Aplica la lista de pacientes obtenida, actualizando la tabla y los índices internos. */
    private void aplicarLista(ArrayList<Paciente> resultado) {
        // ArrayList controlado; cada fila es una copia exclusivamente administrativa.
        for (Paciente paciente : resultado) {
            Paciente copia = CapturaPaciente.de(paciente).modelo();
            pacientes.add(copia);
            filasPorDni.put(copia.getDni(), copia);
        }
        dnisOrdenados = new String[pacientes.size()];
        for (int indice = 0; indice < pacientes.size(); indice++) {
            dnisOrdenados[indice] = pacientes.get(indice).getDni();
        }
        // Orden lexical explícito; independiente del orden de carga y de la ordenación visual.
        AlgoritmosClinica.ordenarDnis(dnisOrdenados);
        tablaPacientes.getItems().setAll(pacientes);
        lblCarga.setText("Última carga local: " + MOMENTO.format(LocalDateTime.now())
                + " · Registros: " + pacientes.size());
    }

    /** Busca un paciente por DNI en la lista cargada y lo selecciona en la tabla. */
    @FXML
    private void buscarDni() {
        if (!autorizarAccion(true)) {
            return;
        }
        try {
            String dni = Validador.validarDni(txtBusquedaDni.getText());
            int indice = AlgoritmosClinica.buscarPacientePorDni(dnisOrdenados, dni);
            if (indice < 0) {
                ejecutor.informar("DNI no encontrado", "No figura en la lista cargada. "
                        + "Active Incluir inactivos si corresponde y use Recargar para actualizar el padrón.");
                return;
            }
            Paciente encontrado = filasPorDni.get(dnisOrdenados[indice]);
            tablaPacientes.getSelectionModel().select(encontrado);
            seleccionar(encontrado);
            tablaPacientes.scrollTo(encontrado);
        } catch (ValidationException excepcion) {
            ejecutor.advertir("DNI incompleto", excepcion.getMessage());
        }
    }

    /** Maneja la selección de un paciente en la tabla, actualizando el formulario y la interfaz. */
    private void seleccionar(Paciente seleccionado) {
        if (!ejecutor.vigente() || !permiteDatos()) {
            return;
        }
        if (seleccionado == null) {
            limpiarFormulario();
        } else {
            original = CapturaPaciente.de(seleccionado);
            formularioDisponible = true;
            campos.mostrar(original.persona());
            fechaNacimiento.setValue(original.nacimiento());
            fechaNacimiento.getEditor().setText(FECHA.format(original.nacimiento()));
            cmbObraSocial.setValue(original.obraSocial());
            cmbObraSocial.getEditor().setText(AyudaVista.texto(original.obraSocial()));
            lblIdentidad.setText("Paciente: " + original.idPaciente() + " · Persona: "
                    + original.persona().idPersona() + " · "
                    + (original.activo() ? "Activo" : "Inactivo: editable, sin reactivación"));
        }
        actualizarControles();
    }

    /** Prepara el formulario para registrar un nuevo paciente. */
    @FXML
    private void nuevo() {
        if (!autorizarAccion(true)) {
            return;
        }
        tablaPacientes.getSelectionModel().clearSelection();
        limpiarFormulario();
        formularioDisponible = true;
        cmbObraSocial.setValue("Particular");
        cmbObraSocial.getEditor().setText("Particular");
        lblIdentidad.setText("Nuevo paciente · Identificadores automáticos · Activo");
        actualizarControles();
        txtDni.requestFocus();
    }

    /** Guarda los cambios realizados en el formulario, ya sea registrando un nuevo paciente o modificando uno existente. */
    @FXML
    private void guardar() {
        if (!autorizarAccion(true) || !formularioDisponible) {
            return;
        }
        try {
            final CapturaPaciente esperado = original;
            final CapturaPaciente cambios = capturarFormulario(esperado);
            cambios.modelo(); // Validación completa en FX antes de entregar una captura al trabajador.
            escribir(() -> esperado == null
                            ? dao.registrar(cambios.modelo())
                            : dao.modificar(esperado.modelo(), cambios.modelo()),
                    esperado == null ? "Paciente registrado. Se recargará el padrón."
                            : "Datos administrativos modificados sin cambiar la actividad. Se recargará el padrón.");
        } catch (ValidationException excepcion) {
            ejecutor.advertir("Revise los datos", excepcion.getMessage());
        }
    }

    /** Captura los datos ingresados en el formulario y los devuelve como un objeto CapturaPaciente. */
    private CapturaPaciente capturarFormulario(CapturaPaciente esperado) {
        LocalDate nacimiento;
        try {
            // Lee el editor, no getValue(): incluye texto escrito aún no confirmado por el DatePicker.
            nacimiento = LocalDate.parse(fechaNacimiento.getEditor().getText(), FECHA);
        } catch (DateTimeParseException excepcion) {
            throw new ValidationException("Ingrese una fecha de nacimiento válida con formato dd/MM/aaaa.");
        }
        return new CapturaPaciente(esperado == null ? 0 : esperado.idPaciente(),
                campos.capturar(esperado == null ? 0 : esperado.persona().idPersona()),
                Validador.validarFechaNacimiento(nacimiento),
                AyudaVista.opcional(cmbObraSocial.getEditor().getText()),
                esperado == null || esperado.activo());
    }

    /** Da de baja lógicamente al paciente seleccionado, si está activo y se confirma la acción. */
    @FXML
    private void darDeBaja() {
        if (!autorizarAccion(true) || original == null || !original.activo()) {
            return;
        }
        final CapturaPaciente esperado = original;
        if (!ejecutor.confirmar("Confirmar baja lógica", "Se dará de baja al paciente seleccionado. "
                + "No se cancelan turnos ni se alteran historias clínicas. "
                + "Los cambios sin guardar del formulario no se aplicarán. ¿Continuar?")
                || !autorizarAccion(true)) {
            return;
        }
        escribir(() -> {
            dao.darDeBaja(esperado.modelo());
            return null;
        }, "Baja lógica confirmada. No se cancelaron turnos ni se modificaron historias clínicas.");
    }

    /** Limpia el formulario, restableciendo los campos y la interfaz a su estado inicial. */
    private void limpiarFormulario() {
        original = null;
        formularioDisponible = false;
        campos.mostrar(null);
        fechaNacimiento.setValue(null);
        fechaNacimiento.getEditor().clear();
        cmbObraSocial.setValue(null);
        cmbObraSocial.getEditor().clear();
        lblIdentidad.setText("Seleccione una fila o pulse Nuevo después de cargar el padrón.");
    }

    /** Limpia los datos cargados en la tabla y el formulario, dejando la interfaz lista para una nueva carga. */
    private void limpiarDatos() {
        pacientes.clear();
        filasPorDni.clear();
        dnisOrdenados = new String[0];
        tablaPacientes.getSelectionModel().clearSelection();
        tablaPacientes.getItems().clear();
        txtBusquedaDni.clear();
        limpiarFormulario();
        lblCarga.setText("Sin carga vigente. Recargue antes de guardar.");
        lblEstado.setText("Datos no disponibles para escritura.");
    }

    /** Actualiza el estado de los controles de la interfaz según la disponibilidad de datos y el estado del formulario. */
    private void actualizarControles() {
        boolean disponibles = permiteDatos();
        tablaPacientes.setDisable(!disponibles);
        formulario.setDisable(!disponibles || !formularioDisponible);
        btnNuevo.setDisable(!disponibles);
        btnGuardar.setDisable(!disponibles || !formularioDisponible);
        btnBaja.setDisable(!disponibles || original == null || !original.activo());
        btnRecargar.setDisable(!permiteRecarga());
        chkIncluirInactivos.setDisable(!permiteRecarga());
        txtBusquedaDni.setDisable(!disponibles);
        btnBuscar.setDisable(!disponibles);
        lblEstado.setText(ejecutor.hayEscrituraEnCurso()
                ? "Escritura en curso. Cerrar o cancelar no garantiza rollback; verifique el resultado antes de repetir."
                : ejecutor.ocupado() ? "Operación o diálogo en curso; espere para continuar."
                : disponibles ? "Padrón cargado. Puede crear o editar; los inactivos no se reactivan."
                : "Recargue correctamente para habilitar Nuevo y la edición.");
    }

    /** Representa una captura de los datos de un paciente, utilizada para la edición y creación en el formulario. */
    private record CapturaPaciente(int idPaciente, DatosPersona persona, LocalDate nacimiento,
                                   String obraSocial, boolean activo) {
        private static CapturaPaciente de(Paciente paciente) {
            return new CapturaPaciente(paciente.getIdPaciente(), DatosPersona.de(paciente),
                    paciente.getFechaNacimiento(), paciente.getObraSocial(), paciente.isEstadoActivo());
        }

        private Paciente modelo() {
            return new Paciente(idPaciente, persona.idPersona(), persona.dni(), persona.nombre(),
                    persona.apellido(), persona.telefono(), persona.email(), persona.direccion(),
                    nacimiento, obraSocial, activo);
        }
    }
}