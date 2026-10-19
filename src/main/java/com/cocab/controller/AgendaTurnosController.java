package com.cocab.controller;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Function;

import com.cocab.dao.TurnoDAO;
import com.cocab.dao.impl.TurnoDAOImpl;
import com.cocab.exception.ValidationException;
import com.cocab.model.odontologo.Odontologo;
import com.cocab.model.seguridad.RolUsuario;
import com.cocab.model.turno.EstadoTurno;
import com.cocab.model.turno.Turno;
import com.cocab.util.UserSession;
import com.cocab.util.Validador;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/** Agenda diaria/semanal. El DAO resuelve y restringe la agenda propia del odontólogo. */
public final class AgendaTurnosController {
    private final String[] CABECERAS_TABLA = {
            "Fecha", "Horario", "DNI", "Paciente · actividad", "Profesional · actividad", "Estado", "Motivo"
    };
    private static final FiltroProfesional TODOS = new FiltroProfesional(null, "Todos los profesionales");
    private final ArrayList<Turno> turnos = new ArrayList<>();
    private final TurnoDAO dao = new TurnoDAOImpl();
    private long revisionFiltros;
    private boolean secretaria;
    private boolean activado;
    private boolean cerrado;
    private boolean datosVigentes;
    private boolean actualizandoFiltros;
    private boolean retirandoFormulario;
    private boolean escrituraRetirada;
    private boolean requiereVerificacion;
    private EjecutorFx ejecutor;
    private NuevoTurnoController formulario;
    private Stage ventanaFormulario;
    private ChangeListener<Object> cambioFiltros;
    private ChangeListener<Turno> cambioSeleccion;
    private ChangeListener<String> cambioEstado;
    private Button btnNuevo;
    private Button btnReprogramar;
    private Button btnCancelar;

    @FXML private BorderPane raiz;
    @FXML private FlowPane filtros;
    @FXML private VBox panelProfesional;
    @FXML private ComboBox<FiltroProfesional> cmbProfesional;
    @FXML private ComboBox<String> cmbPeriodo;
    @FXML private ComboBox<String> cmbEstado;
    @FXML private DatePicker fechaDesde;
    @FXML private CheckBox chkIncluirInactivos;
    @FXML private TableView<Turno> tablaTurnos;
    @FXML private FlowPane acciones;
    @FXML private Button btnRecargar;
    @FXML private Label lblRango;
    @FXML private ProgressIndicator progreso;

    public AgendaTurnosController() {
    }

    /** Solo presentación y guarda local; activar() inicia la primera consulta después de show(). */
    @FXML
    private void initialize() {
        if (cerrado) throw new IllegalStateException("La vista ya fue retirada.");
        secretaria = UserSession.getInstance().exigirRol(
                RolUsuario.Secretaria, RolUsuario.Odontologo).getRol() == RolUsuario.Secretaria;
        ejecutor = new EjecutorFx(raiz, this::actualizarControles, this::cerrar,
                RolUsuario.Secretaria, RolUsuario.Odontologo);
        AyudaVista.prepararFecha(fechaDesde);
        fechaDesde.setValue(LocalDate.now());
        fechaDesde.getEditor().setText(AyudaVista.fecha(LocalDate.now()));
        cmbPeriodo.getItems().setAll("Día", "Semana · 7 días desde la fecha");
        cmbPeriodo.getSelectionModel().selectFirst();
        cmbEstado.getItems().setAll("Todos", "Activo", "Inactivo");
        cmbEstado.getSelectionModel().selectFirst();
        chkIncluirInactivos.setSelected(false);
        cmbProfesional.setConverter(AyudaVista.etiquetas(FiltroProfesional::etiqueta));
        cmbProfesional.getItems().setAll(TODOS);
        cmbProfesional.setValue(TODOS);
        if (secretaria) {
            // No se crean botones de escritura en el perfil odontólogo.
            btnNuevo = new Button("Nuevo turno");
            btnNuevo.setOnAction(evento -> nuevo());
            btnReprogramar = new Button("Reprogramar");
            btnReprogramar.getStyleClass().add("boton-secundario");
            btnReprogramar.setOnAction(evento -> reprogramar());
            btnCancelar = new Button("Cancelar turno");
            btnCancelar.getStyleClass().add("boton-secundario");
            btnCancelar.setOnAction(evento -> cancelar());
            acciones.getChildren().addAll(btnNuevo, btnReprogramar, btnCancelar);
        } else {
            filtros.getChildren().remove(panelProfesional);
            raiz.setBottom(null);
            cmbProfesional.getItems().clear();
        }
        tablaTurnos.setEditable(false);
        tablaTurnos.setPlaceholder(new Label("Sin carga vigente. Seleccione el período y pulse Recargar."));
        agregarColumna(0, turno -> AyudaVista.fecha(turno.getFecha()), 110);
        agregarColumna(1, turno -> turno.getHoraInicio() + " – " + turno.getHoraFin(), 130);
        agregarColumna(2, turno -> turno.getPaciente().getDni(), 100);
        agregarColumna(3, turno -> turno.getPaciente().obtenerNombreCompleto()
                + (turno.getPaciente().estaHabilitado() ? " · Activo" : " · INACTIVO"), 230);
        agregarColumna(4, turno -> AyudaVista.profesional(turno.getOdontologo()), 270);
        agregarColumnaEstado();
        agregarColumna(6, Turno::getMotivoConsulta, 270);
        // ChangeListener observa cada edición; no depende de volver a leer una propiedad
        // invalidada para recibir notificaciones posteriores del editor de fecha.
        cambioFiltros = (observable, anterior, actual) -> filtrosModificados();
        fechaDesde.valueProperty().addListener(cambioFiltros);
        fechaDesde.getEditor().textProperty().addListener(cambioFiltros);
        cmbPeriodo.valueProperty().addListener(cambioFiltros);
        cmbProfesional.valueProperty().addListener(cambioFiltros);
        chkIncluirInactivos.selectedProperty().addListener(cambioFiltros);
        cambioSeleccion = (observable, anterior, actual) -> actualizarControles();
        tablaTurnos.getSelectionModel().selectedItemProperty().addListener(cambioSeleccion);
        // Filtro de estado; solo de presentación, no recarga los datos.
        cambioEstado = (observable, anterior, actual) -> aplicarFiltroEstado();
        cmbEstado.valueProperty().addListener(cambioEstado);
        invalidarDatos();
        actualizarControles();
    }

    /** Agrega una columna genérica a la tabla de turnos. */
    private void agregarColumna(int indice, Function<Turno, String> valor, double ancho) {
        TableColumn<Turno, String> columna = new TableColumn<>(CABECERAS_TABLA[indice]);
        columna.setCellValueFactory(celda -> new SimpleStringProperty(valor.apply(celda.getValue())));
        columna.setPrefWidth(ancho);
        // Conservar el orden fecha/hora con inserción por día que entrega el DAO.
        columna.setSortable(false);
        tablaTurnos.getColumns().add(columna);
    }

    /** Agrega la columna de estado a la tabla de turnos. */
    private void agregarColumnaEstado() {
        TableColumn<Turno, Turno> columna = new TableColumn<>(CABECERAS_TABLA[5]);
        columna.setCellValueFactory(celda -> new ReadOnlyObjectWrapper<>(celda.getValue()));
        columna.setPrefWidth(125);
        columna.setSortable(false);
        columna.setCellFactory(col -> new TableCell<Turno, Turno>() {
            @Override
            protected void updateItem(Turno turno, boolean vacio) {
                super.updateItem(turno, vacio);
                if (vacio || turno == null) {
                    setGraphic(null);
                    return;
                }
                Label etiqueta = new Label(turno.getEstado().getValor());
                etiqueta.getStyleClass().setAll("estado-badge", claseEstado(turno.getEstado()));
                setGraphic(etiqueta);
            }
        });
        tablaTurnos.getColumns().add(columna);
    }

    /** Determina la clase CSS correspondiente a un estado de turno. */
    private static String claseEstado(EstadoTurno estado) {
        return switch (estado) {
            case Asignado -> "estado-asignado";
            case Reprogramado -> "estado-reprogramado";
            case Cancelado -> "estado-cancelado";
            case Atendido -> "estado-atendido";
        };
    }

    /**
     * Determina si un estado coincide con el filtro de estado seleccionado:
     * Activo agrupa Asignado/Reprogramado; Inactivo agrupa Cancelado/Atendido.
     */
    private static boolean coincideEstado(EstadoTurno estado, String filtro) {
        if (filtro == null || "Todos".equals(filtro)) return true;
        boolean activo = estado == EstadoTurno.Asignado || estado == EstadoTurno.Reprogramado;
        return "Activo".equals(filtro) ? activo : !activo;
    }

    /** Reaplica el filtro de estado sobre los turnos ya cargados, sin volver a consultar el DAO. */
    private void aplicarFiltroEstado() {
        String seleccion = cmbEstado.getValue();
        ArrayList<Turno> filtrados = new ArrayList<>();
        for (Turno turno : turnos) {
            if (coincideEstado(turno.getEstado(), seleccion)) filtrados.add(turno);
        }
        tablaTurnos.getItems().setAll(filtrados);
    }

    /** Debe invocarse una sola vez, en FX y después de mostrar el Stage. */
    public void activar() {
        if (cerrado) return;
        if (ejecutor == null || raiz.getScene() == null || raiz.getScene().getWindow() == null
                || !raiz.getScene().getWindow().isShowing()) {
            throw new IllegalStateException("Cargue y muestre la agenda antes de activarla.");
        }
        if (!activado && ejecutor.vigente()) {
            activado = true;
            recargar();
        }
    }

    /** Maneja modificaciones en los filtros de la vista. */
    private void filtrosModificados() {
        if (cerrado || actualizandoFiltros) return;
        revisionFiltros++;
        invalidarDatos();
        actualizarControles();
    }

    /** Invalida los datos actuales y limpia la tabla de turnos. */
    private void invalidarDatos() {
        datosVigentes = false;
        turnos.clear();
        tablaTurnos.getSelectionModel().clearSelection();
        tablaTurnos.getItems().clear();
        lblRango.setText("");
    }

    /** Verifica si la operación está autorizada según el estado actual y los permisos del usuario. */
    private boolean autorizar(boolean escritura, boolean necesitaDatos) {
        if (cerrado || !activado || formulario != null || retirandoFormulario
                || !ejecutor.vigente() || ejecutor.ocupado()) return false;
        if (escritura && !secretaria) {
            ejecutor.advertir("Acceso restringido", "Solo secretaria puede modificar turnos.");
            return false;
        }
        if (escritura && (requiereVerificacion || escrituraRetirada)) {
            ejecutor.advertir("Verificación necesaria", "Hay una escritura cuyo resultado requiere verificación. "
                    + "Recargue solo para consultar y reconcilie con soporte; no repita la operación.");
            return false;
        }
        if (necesitaDatos && !datosVigentes) {
            ejecutor.advertir("Recarga necesaria", "Los filtros o datos cambiaron. Recargue correctamente antes de operar.");
            return false;
        }
        return true;
    }

    /** Recarga la lista de turnos según los filtros actuales. */
    @FXML
    private void recargar() {
        if (!autorizar(false, false)) return;
        try {
            final LocalDate desde = AyudaVista.leerFecha(fechaDesde);
            if (cmbPeriodo.getValue() == null) throw new ValidationException("Seleccione Día o Semana.");
            final LocalDate hasta = Validador.validarFechaSql(
                    desde.plusDays(cmbPeriodo.getSelectionModel().getSelectedIndex() == 1 ? 6 : 0), "El fin del período");
            final FiltroProfesional filtro = secretaria ? cmbProfesional.getValue() : null;
            final Integer idProfesional = filtro == null ? null : filtro.id();
            final boolean incluirInactivos = chkIncluirInactivos.isSelected();
            final boolean cargarCatalogo = secretaria;
            final long revision = revisionFiltros;
            invalidarDatos();
            ejecutor.ejecutar(false, () -> {
                // Nunca enumerar catálogos con rol odontólogo. Null no equivale a todos:
                // el DAO exige agenda propia y resuelve id_odontologo desde id_persona.
                ArrayList<Odontologo> profesionales = cargarCatalogo
                        ? dao.listarOdontologos(true) : new ArrayList<>();
                return new CargaAgenda(dao.listar(desde, hasta, idProfesional, incluirInactivos), profesionales);
            }, resultado -> {
                if (revision != revisionFiltros) return;
                if (secretaria) aplicarCatalogo(resultado.profesionales(), idProfesional);
                for (Turno turno : resultado.turnos()) turnos.add(AyudaVista.copiar(turno));
                aplicarFiltroEstado();
                datosVigentes = true;
                lblRango.setText(AyudaVista.fecha(desde) + " → " + AyudaVista.fecha(hasta));
                tablaTurnos.setPlaceholder(new Label("No hay turnos para los filtros cargados."));
                actualizarControles();
            }, fallo -> fallar(fallo, false));
        } catch (ValidationException excepcion) {
            invalidarDatos();
            actualizarControles();
            ejecutor.advertir("Revise el período", excepcion.getMessage());
        }
    }

    /** Aplica el catálogo de profesionales a la vista y selecciona el profesional indicado. */
    private void aplicarCatalogo(ArrayList<Odontologo> profesionales, Integer idSeleccionado) {
        actualizandoFiltros = true;
        try {
            cmbProfesional.getItems().setAll(TODOS);
            FiltroProfesional seleccionado = TODOS;
            for (Odontologo profesional : profesionales) {
                FiltroProfesional opcion = new FiltroProfesional(profesional.getIdOdontologo(),
                        AyudaVista.profesional(profesional));
                cmbProfesional.getItems().add(opcion);
                if (Objects.equals(idSeleccionado, opcion.id())) seleccionado = opcion;
            }
            if (idSeleccionado != null && seleccionado == TODOS) {
                cmbProfesional.setValue(TODOS);
                throw new ValidationException("El profesional filtrado ya no está disponible. Recargue con Todos.");
            }
            cmbProfesional.setValue(seleccionado);
        } finally {
            actualizandoFiltros = false;
        }
    }

    /** Maneja la acción de crear un nuevo turno. */
    @FXML
    private void nuevo() {
        if (autorizar(true, true)) abrirFormulario(null);
    }

    /** Maneja la acción de reprogramar un turno existente. */
    @FXML
    private void reprogramar() {
        if (!autorizar(true, true)) return;
        Turno seleccionado = tablaTurnos.getSelectionModel().getSelectedItem();
        if (!AyudaVista.modificable(seleccionado)) return;
        if (!seleccionado.getPaciente().estaHabilitado()) {
            ejecutor.advertir("Paciente inactivo", "No se puede reprogramar este turno. Sí puede cancelarlo.");
            return;
        }
        abrirFormulario(AyudaVista.copiar(seleccionado));
    }

    /** Abre el formulario de turno, ya sea para crear uno nuevo o reprogramar uno existente. */
    private void abrirFormulario(Turno original) {
        LocalDate sugerida;
        try {
            sugerida = Validador.validarFechaAsignacion(AyudaVista.leerFecha(fechaDesde));
        } catch (ValidationException excepcion) {
            sugerida = LocalDate.now();
        }
        try {
            NuevoTurnoController vista = new NuevoTurnoController();
            formulario = vista; // Registrar limpieza ANTES de configurar/cargar FXML.
            Stage ventana = new Stage();
            ventanaFormulario = ventana;
            ventana.initOwner(raiz.getScene().getWindow());
            ventana.initModality(Modality.WINDOW_MODAL);
            vista.configurar(original, sugerida, ventana,
                    this::recargar, this::invalidarTrasFormulario);
            FXMLLoader cargador = new FXMLLoader(Objects.requireNonNull(
                    getClass().getResource("/views/NuevoTurnoView.fxml"), "Falta el formulario de turno."));
            cargador.setControllerFactory(tipo -> {
                if (tipo != NuevoTurnoController.class) throw new IllegalStateException("Controlador inesperado.");
                return vista;
            });
            Parent contenido = cargador.load();
            ventana.setTitle(original == null ? "COCAB | Nuevo turno" : "COCAB | Reprogramar turno");
            ventana.setScene(new Scene(contenido, 760, 790));
            ventana.setMinWidth(580);
            ventana.setMinHeight(580);
            ventana.setOnCloseRequest(evento -> {
                evento.consume();
                vista.solicitarCierre();
            });
            ventana.setOnHidden(evento -> cerrarFormulario());
            actualizarControles();
            ventana.show();
            vista.activar();
        } catch (IOException | RuntimeException excepcion) {
            cerrarFormulario();
            invalidarDatos();
            actualizarControles();
            ejecutor.advertir("Vista no disponible", "No se pudo abrir el formulario. Recargue y revise acceso e instalación con soporte.");
        }
    }

    /** Invalida los datos tras cerrar el formulario de turno. */
    private void invalidarTrasFormulario() {
        if (cerrado) return;
        if (formulario != null && formulario.requiereVerificacion()) requiereVerificacion = true;
        invalidarDatos();
        actualizarControles();
    }

    /** Maneja la acción de cancelar un turno seleccionado. */
    @FXML
    private void cancelar() {
        if (!autorizar(true, true)) return;
        Turno seleccionado = tablaTurnos.getSelectionModel().getSelectedItem();
        if (!AyudaVista.modificable(seleccionado)) return;
        final Turno esperado = AyudaVista.copiar(seleccionado);
        final long revision = revisionFiltros;
        if (!ejecutor.confirmar("Cancelar turno", "Se cancelará el turno seleccionado y se liberará su franja. "
                + "No se borrará el registro ni se modificarán sus demás datos. "
                + "La cancelación admite fechas pasadas y referencias inactivas. ¿Continuar?")
                || !autorizar(true, true) || revision != revisionFiltros) return;
        invalidarDatos();
        ejecutor.ejecutar(true, () -> {
            dao.cancelar(esperado);
            return Boolean.TRUE;
        }, resultado -> recargar(), fallo -> fallar(fallo, true));
    }

    /** Maneja fallos en las operaciones de escritura y actualiza la vista en consecuencia. */
    private void fallar(Throwable fallo, boolean escritura) {
        if (escritura && !(fallo instanceof ValidationException)) requiereVerificacion = true;
        invalidarDatos();
        actualizandoFiltros = true;
        try {
            cmbProfesional.getItems().clear();
            if (secretaria) {
                cmbProfesional.getItems().add(TODOS);
                cmbProfesional.setValue(TODOS);
            }
        } finally {
            actualizandoFiltros = false;
        }
        actualizarControles();
        ejecutor.fallo(fallo, escritura);
    }

    /** Actualiza el estado de los controles de la interfaz según el estado actual y los permisos. */
    private void actualizarControles() {
        if (ejecutor == null || cerrado) return;
        boolean libre = activado && !ejecutor.ocupado() && formulario == null && !retirandoFormulario;
        boolean permiteEscritura = libre && datosVigentes && !requiereVerificacion && !escrituraRetirada;
        filtros.setDisable(!libre);
        btnRecargar.setDisable(!libre);
        tablaTurnos.setDisable(!libre || !datosVigentes);
        Turno seleccionado = tablaTurnos.getSelectionModel().getSelectedItem();
        if (btnNuevo != null) {
            btnNuevo.setDisable(!permiteEscritura);
            btnReprogramar.setDisable(!permiteEscritura || !AyudaVista.modificable(seleccionado));
            btnCancelar.setDisable(!permiteEscritura || !AyudaVista.modificable(seleccionado));
        }
        progreso.setVisible(ejecutor.ocupado());
        progreso.setManaged(ejecutor.ocupado());
    }

    /** Cierra el formulario de turno y libera los recursos asociados. */
    private void cerrarFormulario() {
        if (retirandoFormulario) return;
        retirandoFormulario = true;
        try {
            NuevoTurnoController vista = formulario;
            Stage ventana = ventanaFormulario;
            if (vista != null) {
                escrituraRetirada |= vista.hayEscrituraEnCurso();
                requiereVerificacion |= vista.requiereVerificacion() || escrituraRetirada;
                vista.cerrar();
            }
            if (ventana != null) {
                ventana.setOnCloseRequest(null);
                ventana.setOnHidden(null);
                ventana.hide();
                ventana.setScene(null);
            }
            formulario = null;
            ventanaFormulario = null;
        } finally {
            retirandoFormulario = false;
        }
        if (!cerrado) actualizarControles();
    }

    /** Verifica si hay escritura en curso en el formulario o en el ejecutor de turnos. */
    public boolean hayEscrituraEnCurso() {
        return escrituraRetirada || (ejecutor != null && ejecutor.hayEscrituraEnCurso())
                || (formulario != null && formulario.hayEscrituraEnCurso());
    }

    /** Cierra el controlador de la agenda de turnos y libera todos los recursos asociados. */
    public void cerrar() {
        if (cerrado) return;
        cerrado = true;
        cerrarFormulario();
        if (ejecutor != null) ejecutor.cerrar();
        if (cambioFiltros != null) {
            fechaDesde.valueProperty().removeListener(cambioFiltros);
            fechaDesde.getEditor().textProperty().removeListener(cambioFiltros);
            cmbPeriodo.valueProperty().removeListener(cambioFiltros);
            cmbProfesional.valueProperty().removeListener(cambioFiltros);
            chkIncluirInactivos.selectedProperty().removeListener(cambioFiltros);
            cambioFiltros = null;
        }
        if (cambioEstado != null) {
            cmbEstado.valueProperty().removeListener(cambioEstado);
            cambioEstado = null;
        }
        if (tablaTurnos != null) {
            if (cambioSeleccion != null) tablaTurnos.getSelectionModel().selectedItemProperty().removeListener(cambioSeleccion);
            tablaTurnos.getItems().clear();
            tablaTurnos.getColumns().clear();
        }
        cambioSeleccion = null;
        for (Button boton : new Button[] {btnNuevo, btnReprogramar, btnCancelar, btnRecargar}) {
            if (boton != null) boton.setOnAction(null);
        }
        if (cmbProfesional != null) { cmbProfesional.setValue(null); cmbProfesional.getItems().clear(); }
        if (fechaDesde != null) { fechaDesde.setValue(null); fechaDesde.getEditor().clear(); }
        if (lblRango != null) lblRango.setText("");
        turnos.clear();
        datosVigentes = false;
        if (raiz != null) raiz.setDisable(true);
    }

    /** Representa un filtro de profesional con su ID y etiqueta. */
    private record FiltroProfesional(Integer id, String etiqueta) { }
    /** Representa la carga de la agenda con los turnos y profesionales correspondientes. */
    private record CargaAgenda(ArrayList<Turno> turnos, ArrayList<Odontologo> profesionales) { }
}