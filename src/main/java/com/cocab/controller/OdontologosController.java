package com.cocab.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;

import com.cocab.controller.AyudaVista.CamposPersona;
import com.cocab.controller.AyudaVista.DatosPersona;
import com.cocab.dao.OdontologoDAO;
import com.cocab.dao.impl.OdontologoDAOImpl;
import com.cocab.exception.ValidationException;
import com.cocab.model.odontologo.Odontologo;
import com.cocab.model.seguridad.RolUsuario;
import com.cocab.util.AlgoritmosClinica;
import com.cocab.util.UserSession;
import com.cocab.util.Validador;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/** Administración profesional de secretaria; no crea cuentas ni accede a contenido clínico. */
public final class OdontologosController {
    private final String[] CABECERAS_TABLA = {
            "DNI", "Apellido", "Nombre", "Matrícula", "Especialidad", "Estado",
            "Teléfono", "Correo electrónico", "Dirección"
    };
    private static final DateTimeFormatter MOMENTO = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss");
    private final ArrayList<Odontologo> odontologos = new ArrayList<>();
    private final Map<String, Odontologo> filasPorDni = new HashMap<>();
    private String[] dnisOrdenados = new String[0];
    private CapturaOdontologo original;
    private final OdontologoDAO dao = new OdontologoDAOImpl();
    private boolean formularioDisponible;
    private boolean activado;
    private boolean cerrado;
    private boolean requiereRecarga = true;
    private EjecutorFx ejecutor;
    private CamposPersona campos;
    private ChangeListener<Odontologo> cambioSeleccion;

    @FXML private VBox raiz;
    @FXML private TableView<Odontologo> tablaOdontologos;
    @FXML private VBox formulario;
    @FXML private TextField txtBusquedaDni;
    @FXML private CheckBox chkIncluirInactivos;
    @FXML private TextField txtDni;
    @FXML private TextField txtNombre;
    @FXML private TextField txtApellido;
    @FXML private TextField txtTelefono;
    @FXML private TextField txtEmail;
    @FXML private TextField txtDireccion;
    @FXML private TextField txtMatricula;
    @FXML private TextField txtEspecialidad;
    @FXML private Button btnBuscar;
    @FXML private Button btnNuevo;
    @FXML private Button btnGuardar;
    @FXML private Button btnBaja;
    @FXML private Button btnRecargar;
    @FXML private Label lblIdentidad;
    @FXML private Label lblEstado;
    @FXML private Label lblCarga;

    public OdontologosController() {
    }

    /** Inicialización local; no consulta datos. */
    @FXML
    private void initialize() {
        UserSession.getInstance().exigirRol(RolUsuario.Secretaria);
        campos = new CamposPersona(txtDni, txtNombre, txtApellido, txtTelefono, txtEmail, txtDireccion);
        AyudaVista.mascaraDni(txtBusquedaDni);
        AyudaVista.limitar(txtMatricula, 20);
        AyudaVista.limitar(txtEspecialidad, 100);
        chkIncluirInactivos.setSelected(false);
        tablaOdontologos.setEditable(false);
        tablaOdontologos.setPlaceholder(new Label("Sin registros cargados. Use Recargar."));
        agregarColumna(0, Odontologo::getDni);
        agregarColumna(1, Odontologo::getApellido);
        agregarColumna(2, Odontologo::getNombre);
        agregarColumna(3, Odontologo::getMatricula);
        agregarColumna(4, Odontologo::getEspecialidad);
        agregarColumna(5, odontologo -> odontologo.isEstadoActivo() ? "Activo" : "Inactivo");
        agregarColumna(6, Odontologo::getTelefono);
        agregarColumna(7, Odontologo::getEmail);
        agregarColumna(8, Odontologo::getDireccion);
        ejecutor = new EjecutorFx(raiz, this::actualizarControles, this::cerrar, RolUsuario.Secretaria);
        cambioSeleccion = (observable, anterior, seleccionado) -> seleccionar(seleccionado);
        tablaOdontologos.getSelectionModel().selectedItemProperty().addListener(cambioSeleccion);
        limpiarDatos();
        actualizarControles();
    }

    /** Agrega una columna a la tabla de odontólogos con el índice y la función de valor especificados. */
    private void agregarColumna(int indice, Function<Odontologo, String> valor) {
        TableColumn<Odontologo, String> columna = new TableColumn<>(CABECERAS_TABLA[indice]);
        columna.setCellValueFactory(celda -> {
            String texto = valor.apply(celda.getValue());
            return new SimpleStringProperty(texto == null ? "—" : texto);
        });
        columna.setPrefWidth(indice == 8 ? 200 : 140);
        tablaOdontologos.getColumns().add(columna);
    }

    /** Llama en FX después de abrir la vista; inicia una sola carga inicial. */
    public void activar() {
        if (ejecutor == null) {
            throw new IllegalStateException("La vista debe cargarse mediante FXML antes de activarla.");
        }
        if (ejecutor.vigente() && !activado) {
            activado = true;
            recargar();
        }
    }

    /** Cancela sin esperar a la operación en curso. El propietario decide el cierre de su Stage. */
    public void cerrar() {
        if (cerrado || ejecutor == null) return;
        cerrado = true;
        tablaOdontologos.getSelectionModel().selectedItemProperty().removeListener(cambioSeleccion);
        ejecutor.cerrar();
        limpiarDatos();
    }

    /** Consulta antes del cierre modal; true requiere advertir sobre verificación posterior. */
    public boolean hayEscrituraEnCurso() {
        return ejecutor != null && ejecutor.hayEscrituraEnCurso();
    }

    /** Autoriza una acción según el estado de la vista y la necesidad de datos. */
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

    /** Indica si los datos están disponibles para su visualización y edición. */
    private boolean permiteDatos() {
        return activado && !cerrado && !requiereRecarga && !ejecutor.ocupado();
    }

    /** Indica si la recarga de datos está permitida en el estado actual de la vista. */
    private boolean permiteRecarga() {
        return activado && !cerrado && !ejecutor.ocupado();
    }

    /** Descarta lo mostrado: ante un fallo o una escritura se exige recargar antes de seguir. Actualiza los controles de la interfaz. */
    private void invalidar() {
        requiereRecarga = true;
        limpiarDatos();
        actualizarControles();
    }

    /** Maneja un fallo ocurrido durante una operación, invalidando los datos y notificando al ejecutor. */
    private void fallar(Throwable fallo, boolean escritura) {
        invalidar();
        ejecutor.fallo(fallo, escritura);
    }

    /** Ejecuta una operación de escritura, mostrando mensajes de éxito o fallo según corresponda. */
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

    /** Recarga la lista de odontólogos desde el DAO, aplicando filtros y actualizando la interfaz. */
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

    /** Maneja el cambio en la selección de incluir inactivos, recargando la lista según corresponda. */
    @FXML
    private void cambiarInactivos() {
        recargar();
    }

    /** Aplica la lista de odontólogos obtenida del DAO, actualizando la tabla y los índices internos. */
    private void aplicarLista(ArrayList<Odontologo> resultado) {
        for (Odontologo odontologo : resultado) {
            Odontologo copia = CapturaOdontologo.de(odontologo).modelo();
            odontologos.add(copia);
            filasPorDni.put(copia.getDni(), copia);
        }
        dnisOrdenados = new String[odontologos.size()];
        for (int indice = 0; indice < odontologos.size(); indice++) {
            dnisOrdenados[indice] = odontologos.get(indice).getDni();
        }
        AlgoritmosClinica.ordenarDnis(dnisOrdenados);
        tablaOdontologos.getItems().setAll(odontologos);
        lblCarga.setText("Última carga local: " + MOMENTO.format(LocalDateTime.now())
                + " · Registros: " + odontologos.size());
    }

    /** Busca un odontólogo por DNI en la lista cargada y selecciona la fila correspondiente en la tabla. */
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
            // El índice pertenece al arreglo lexical, no a la posición visual ni al orden de carga.
            Odontologo encontrado = filasPorDni.get(dnisOrdenados[indice]);
            tablaOdontologos.getSelectionModel().select(encontrado);
            seleccionar(encontrado);
            tablaOdontologos.scrollTo(encontrado);
        } catch (ValidationException excepcion) {
            ejecutor.advertir("DNI incompleto", excepcion.getMessage());
        }
    }

    /** Maneja la selección de un odontólogo en la tabla, actualizando el formulario según corresponda. */
    private void seleccionar(Odontologo seleccionado) {
        if (!ejecutor.vigente() || !permiteDatos()) {
            return;
        }
        if (seleccionado == null) {
            limpiarFormulario();
        } else {
            original = CapturaOdontologo.de(seleccionado);
            formularioDisponible = true;
            campos.mostrar(original.persona());
            txtMatricula.setText(original.matricula());
            txtEspecialidad.setText(original.especialidad());
            lblIdentidad.setText("Odontólogo: " + original.idOdontologo() + " · Persona: "
                    + original.persona().idPersona() + " · "
                    + (original.activo() ? "Activo" : "Inactivo: editable, sin reactivación"));
        }
        actualizarControles();
    }

    /** Prepara el formulario para la creación de un nuevo odontólogo, limpiando los campos y habilitando la edición. */
    @FXML
    private void nuevo() {
        if (!autorizarAccion(true)) {
            return;
        }
        tablaOdontologos.getSelectionModel().clearSelection();
        limpiarFormulario();
        formularioDisponible = true;
        lblIdentidad.setText("Nuevo odontólogo · Identificadores automáticos · Activo");
        actualizarControles();
        txtDni.requestFocus();
    }

    /** Guarda los cambios realizados en el formulario, ya sea creando un nuevo odontólogo o modificando uno existente. */
    @FXML
    private void guardar() {
        if (!autorizarAccion(true) || !formularioDisponible) {
            return;
        }
        try {
            final CapturaOdontologo esperado = original;
            final CapturaOdontologo cambios = new CapturaOdontologo(
                    esperado == null ? 0 : esperado.idOdontologo(),
                    campos.capturar(esperado == null ? 0 : esperado.persona().idPersona()),
                    txtMatricula.getText(), txtEspecialidad.getText(), esperado == null || esperado.activo());
            cambios.modelo(); // Validar el modelo antes de despachar; no imponer prefijos de matrícula.
            escribir(() -> esperado == null
                            ? dao.registrar(cambios.modelo())
                            : dao.modificar(esperado.modelo(), cambios.modelo()),
                    esperado == null ? "Odontólogo registrado. No se creó una cuenta de acceso. Se recargará el padrón."
                            : "Datos administrativos modificados sin cambiar la actividad. Se recargará el padrón.");
        } catch (ValidationException excepcion) {
            ejecutor.advertir("Revise los datos", excepcion.getMessage());
        }
    }

    /** Da de baja lógicamente al odontólogo seleccionado, si está activo y se confirma la acción. */
    @FXML
    private void darDeBaja() {
        if (!autorizarAccion(true) || original == null || !original.activo()) {
            return;
        }
        final CapturaOdontologo esperado = original;
        if (!ejecutor.confirmar("Confirmar baja lógica", "Se dará de baja al odontólogo seleccionado. "
                + "La baja se rechaza si tiene turnos asignados o reprogramados desde hoy, "
                + "aunque su hora ya haya pasado. No se cancelan ni reasignan turnos automáticamente. "
                + "Los cambios sin guardar del formulario no se aplicarán. ¿Continuar?")
                || !autorizarAccion(true)) {
            return;
        }
        escribir(() -> {
            dao.darDeBaja(esperado.modelo());
            return null;
        }, "Baja lógica confirmada. Se conservan los antecedentes del profesional.");
    }

    /** Limpia el formulario, restableciendo los campos y la interfaz a su estado inicial. */
    private void limpiarFormulario() {
        original = null;
        formularioDisponible = false;
        campos.mostrar(null);
        txtMatricula.clear();
        txtEspecialidad.clear();
        lblIdentidad.setText("Seleccione una fila o pulse Nuevo después de cargar el padrón.");
    }

    /** Limpia los datos cargados en la tabla y el formulario, dejando la interfaz lista para una nueva carga. */
    private void limpiarDatos() {
        odontologos.clear();
        filasPorDni.clear();
        dnisOrdenados = new String[0];
        tablaOdontologos.getSelectionModel().clearSelection();
        tablaOdontologos.getItems().clear();
        txtBusquedaDni.clear();
        limpiarFormulario();
        lblCarga.setText("Sin carga vigente. Recargue antes de guardar.");
        lblEstado.setText("Datos no disponibles para escritura.");
    }

    /** Actualiza el estado de los controles de la interfaz según la disponibilidad de datos y el estado del formulario. */
    private void actualizarControles() {
        boolean disponibles = permiteDatos();
        tablaOdontologos.setDisable(!disponibles);
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
                : disponibles ? "Padrón cargado. Puede crear o editar; los inactivos no se reactivan; el alta no crea cuenta de acceso."
                : "Recargue correctamente para habilitar Nuevo y la edición.");
    }

    /** Representa una captura de los datos de un odontólogo, utilizada para la edición y creación en el formulario. */
    private record CapturaOdontologo(int idOdontologo, DatosPersona persona, String matricula,
                                     String especialidad, boolean activo) {
        private static CapturaOdontologo de(Odontologo odontologo) {
            return new CapturaOdontologo(odontologo.getIdOdontologo(), DatosPersona.de(odontologo),
                    odontologo.getMatricula(), odontologo.getEspecialidad(), odontologo.isEstadoActivo());
        }

        /** Convierte un objeto Odontologo en una captura para su edición en el formulario. */
        private Odontologo modelo() {
            return new Odontologo(idOdontologo, persona.idPersona(), persona.dni(), persona.nombre(),
                    persona.apellido(), persona.telefono(), persona.email(), persona.direccion(),
                    matricula, especialidad, activo);
        }
    }
}