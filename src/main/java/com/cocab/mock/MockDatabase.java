package com.cocab.mock;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import com.cocab.exception.ValidationException;
import com.cocab.model.historia.EvolucionClinica;
import com.cocab.model.historia.HistoriaClinica;
import com.cocab.model.odontologo.Odontologo;
import com.cocab.model.paciente.Paciente;
import com.cocab.model.seguridad.RolUsuario;
import com.cocab.model.seguridad.Usuario;
import com.cocab.model.turno.EstadoTurno;
import com.cocab.model.turno.Turno;
import com.cocab.util.SeguridadClaves;
import com.cocab.util.Validador;

/**
 * Repositorio en memoria que sirve de almacenamiento a los DAO de esta entrega.
 * Los DAO validan con el dominio; aquí solo se guardan las colecciones,
 * se controla la unicidad (DNI, matrícula), la existencia de referencias y el solapamiento de turnos.
 * Los datos se reinician en cada ejecución; la persistencia en base de datos se implementará en la próxima entrega.
 */
public final class MockDatabase {
    private static final MockDatabase INSTANCIA = new MockDatabase();

    private final List<Usuario> usuarios = new ArrayList<>();
    private final List<Paciente> pacientes = new ArrayList<>();
    private final List<Odontologo> odontologos = new ArrayList<>();
    private final List<Turno> turnos = new ArrayList<>();
    private final Map<Integer, HistoriaClinica> historiasPorPaciente = new HashMap<>();

    private int siguienteIdPersona = 1;
    private int siguienteIdUsuario = 1;
    private int siguienteIdPaciente = 1;
    private int siguienteIdOdontologo = 1;
    private int siguienteIdTurno = 1;
    private int siguienteIdHistoria = 1;
    private int siguienteIdEvolucion = 1;

    private MockDatabase() {
        sembrarDatos();
    }

    public static MockDatabase getInstance() {
        return INSTANCIA;
    }

    // =====================================================================
    // Usuarios / autenticación
    // =====================================================================

    public synchronized Optional<Usuario> buscarUsuarioHabilitado(String nombreUsuario) {
        for (Usuario usuario : usuarios) {
            if (usuario.getNombreUsuario().equals(nombreUsuario) && usuario.isEstadoActivo()
                    && cuentaHabilitadaPorRol(usuario)) {
                return Optional.of(copiarUsuario(usuario));
            }
        }
        return Optional.empty();
    }

    public synchronized OptionalInt buscarIdOdontologoActivo(int idPersona) {
        Odontologo odontologo = buscarOdontologoPorPersona(idPersona);
        return (odontologo != null && odontologo.isEstadoActivo())
                ? OptionalInt.of(odontologo.getIdOdontologo()) : OptionalInt.empty();
    }

    private boolean cuentaHabilitadaPorRol(Usuario usuario) {
        if (usuario.tieneRol(RolUsuario.Odontologo)) {
            Odontologo odontologo = buscarOdontologoPorPersona(usuario.getIdPersona());
            return odontologo != null && odontologo.isEstadoActivo();
        }
        // Secretaria: no hay padrón propio de secretarias.
        return true;
    }

    // =====================================================================
    // Pacientes
    // =====================================================================

    public synchronized ArrayList<Paciente> listarPacientes(boolean incluirInactivos) {
        ArrayList<Paciente> resultado = new ArrayList<>();
        for (Paciente paciente : pacientes) {
            if (incluirInactivos || paciente.isEstadoActivo()) {
                resultado.add(copiarPaciente(paciente));
            }
        }
        resultado.sort(Comparator.comparing(Paciente::getApellido).thenComparing(Paciente::getNombre)
                .thenComparingInt(Paciente::getIdPaciente));
        return resultado;
    }

    public synchronized Paciente registrarPaciente(Paciente nuevo) {
        exigirDniLibre(nuevo.getDni(), 0);
        int idPersona = siguienteIdPersona++;
        int idPaciente = siguienteIdPaciente++;
        Paciente almacenado = new Paciente(idPaciente, idPersona, nuevo.getDni(), nuevo.getNombre(),
                nuevo.getApellido(), nuevo.getTelefono(), nuevo.getEmail(), nuevo.getDireccion(),
                nuevo.getFechaNacimiento(), nuevo.getObraSocial(), true);
        pacientes.add(almacenado);
        return copiarPaciente(almacenado);
    }

    public synchronized Paciente modificarPaciente(Paciente esperado, Paciente propuesto) {
        Paciente actual = exigirExistente(buscarPacientePorId(esperado.getIdPaciente()));
        exigirDniLibre(propuesto.getDni(), actual.getIdPaciente());
        Paciente actualizado = new Paciente(actual.getIdPaciente(), actual.getIdPersona(), propuesto.getDni(),
                propuesto.getNombre(), propuesto.getApellido(), propuesto.getTelefono(), propuesto.getEmail(),
                propuesto.getDireccion(), propuesto.getFechaNacimiento(), propuesto.getObraSocial(),
                actual.isEstadoActivo());
        reemplazar(pacientes, actual, actualizado);
        return copiarPaciente(actualizado);
    }

    public synchronized void darDeBajaPaciente(Paciente esperado) {
        Paciente actual = exigirExistente(buscarPacientePorId(esperado.getIdPaciente()));
        actual.registrarBaja();
    }

    private Paciente buscarPacientePorId(int idPaciente) {
        for (Paciente paciente : pacientes) {
            if (paciente.getIdPaciente() == idPaciente) {
                return paciente;
            }
        }
        return null;
    }

    // =====================================================================
    // Odontólogos
    // =====================================================================

    public synchronized ArrayList<Odontologo> listarOdontologos(boolean incluirInactivos) {
        ArrayList<Odontologo> resultado = new ArrayList<>();
        for (Odontologo odontologo : odontologos) {
            if (incluirInactivos || odontologo.isEstadoActivo()) {
                resultado.add(new Odontologo(odontologo));
            }
        }
        resultado.sort(Comparator.comparing(Odontologo::getApellido).thenComparing(Odontologo::getNombre)
                .thenComparingInt(Odontologo::getIdOdontologo));
        return resultado;
    }

    public synchronized Odontologo registrarOdontologo(Odontologo nuevo) {
        exigirDniLibre(nuevo.getDni(), 0);
        exigirMatriculaLibre(nuevo.getMatricula(), 0);
        int idPersona = siguienteIdPersona++;
        int idOdontologo = siguienteIdOdontologo++;
        Odontologo almacenado = new Odontologo(idOdontologo, idPersona, nuevo.getDni(), nuevo.getNombre(),
                nuevo.getApellido(), nuevo.getTelefono(), nuevo.getEmail(), nuevo.getDireccion(),
                nuevo.getMatricula(), nuevo.getEspecialidad(), true);
        odontologos.add(almacenado);
        return new Odontologo(almacenado);
    }

    public synchronized Odontologo modificarOdontologo(Odontologo esperado, Odontologo propuesto) {
        Odontologo actual = exigirExistente(buscarOdontologoPorId(esperado.getIdOdontologo()));
        exigirDniLibre(propuesto.getDni(), 0, actual.getIdPersona());
        exigirMatriculaLibre(propuesto.getMatricula(), actual.getIdOdontologo());
        Odontologo actualizado = new Odontologo(actual.getIdOdontologo(), actual.getIdPersona(), propuesto.getDni(),
                propuesto.getNombre(), propuesto.getApellido(), propuesto.getTelefono(), propuesto.getEmail(),
                propuesto.getDireccion(), propuesto.getMatricula(), propuesto.getEspecialidad(),
                actual.isEstadoActivo());
        reemplazar(odontologos, actual, actualizado);
        return new Odontologo(actualizado);
    }

    public synchronized void darDeBajaOdontologo(Odontologo esperado) {
        Odontologo actual = exigirExistente(buscarOdontologoPorId(esperado.getIdOdontologo()));
        exigirSinTurnosPendientes(actual.getIdOdontologo());
        actual.registrarBaja();
    }

    private Odontologo buscarOdontologoPorId(int idOdontologo) {
        for (Odontologo odontologo : odontologos) {
            if (odontologo.getIdOdontologo() == idOdontologo) {
                return odontologo;
            }
        }
        return null;
    }

    private Odontologo buscarOdontologoPorPersona(int idPersona) {
        for (Odontologo odontologo : odontologos) {
            if (odontologo.getIdPersona() == idPersona) {
                return odontologo;
            }
        }
        return null;
    }

    private void exigirSinTurnosPendientes(int idOdontologo) {
        LocalDate hoy = LocalDate.now();
        for (Turno turno : turnos) {
            if (turno.getOdontologo().getIdOdontologo() == idOdontologo
                    && !turno.getFecha().isBefore(hoy)
                    && (turno.getEstado() == EstadoTurno.Asignado || turno.getEstado() == EstadoTurno.Reprogramado)) {
                throw new ValidationException("El profesional tiene turnos asignados o reprogramados "
                        + "desde hoy. Cancele o reasigne esos turnos antes de darlo de baja.");
            }
        }
    }

    // =====================================================================
    // Turnos
    // =====================================================================

    public synchronized ArrayList<Turno> listarTurnos(LocalDate desde, LocalDate hasta, Integer idOdontologo,
                                                      boolean incluirInactivos) {
        ArrayList<Turno> resultado = new ArrayList<>();
        for (Turno turno : turnos) {
            if (turno.getFecha().isBefore(desde) || turno.getFecha().isAfter(hasta)) {
                continue;
            }
            if (idOdontologo != null && turno.getOdontologo().getIdOdontologo() != idOdontologo) {
                continue;
            }
            if (!incluirInactivos && (!turno.getPaciente().isEstadoActivo() || !turno.getOdontologo().isEstadoActivo())) {
                continue;
            }
            resultado.add(copiarTurno(turno));
        }
        resultado.sort(Comparator.comparing(Turno::getFecha).thenComparingInt(Turno::getIdTurno));
        return resultado;
    }

    public synchronized Turno registrarTurno(Turno propuesta) {
        Paciente paciente = exigirExistente(buscarPacientePorId(propuesta.getPaciente().getIdPaciente()));
        Odontologo odontologo = exigirExistente(buscarOdontologoPorId(propuesta.getOdontologo().getIdOdontologo()));
        Turno resultado = new Turno(0, copiarPaciente(paciente), new Odontologo(odontologo), propuesta.getFecha(),
                propuesta.getHoraInicio(), propuesta.getHoraFin(), propuesta.getMotivoConsulta(),
                EstadoTurno.Asignado);
        resultado.validarParaAsignacion();
        exigirSinColision(resultado, false);
        resultado.setIdTurno(siguienteIdTurno++);
        turnos.add(resultado);
        return copiarTurno(resultado);
    }

    public synchronized Turno reprogramarTurno(Turno esperado, Turno propuesto) {
        Turno actual = exigirExistente(buscarTurnoPorId(esperado.getIdTurno()));
        exigirPendiente(actual);
        Paciente paciente = exigirExistente(buscarPacientePorId(actual.getPaciente().getIdPaciente()));
        Odontologo odontologo = exigirExistente(buscarOdontologoPorId(propuesto.getOdontologo().getIdOdontologo()));
        Turno resultado = new Turno(actual.getIdTurno(), copiarPaciente(paciente), new Odontologo(odontologo),
                propuesto.getFecha(), propuesto.getHoraInicio(), propuesto.getHoraFin(),
                propuesto.getMotivoConsulta(), EstadoTurno.Reprogramado);
        resultado.validarParaAsignacion();
        exigirSinColision(resultado, true);
        reemplazar(turnos, actual, resultado);
        return copiarTurno(resultado);
    }

    public synchronized void cancelarTurno(Turno esperado) {
        Turno actual = exigirExistente(buscarTurnoPorId(esperado.getIdTurno()));
        exigirPendiente(actual);
        actual.cancelar();
    }

    private Turno buscarTurnoPorId(int idTurno) {
        for (Turno turno : turnos) {
            if (turno.getIdTurno() == idTurno) {
                return turno;
            }
        }
        return null;
    }

    private void exigirPendiente(Turno turno) {
        if (turno.getEstado() != EstadoTurno.Asignado && turno.getEstado() != EstadoTurno.Reprogramado) {
            throw new ValidationException("Solo se pueden reprogramar o cancelar turnos Asignado o Reprogramado. "
                    + "Actualice la agenda antes de continuar.");
        }
    }

    /** Intervalos estrictos, excluyendo cancelados y, en reprogramación, el turno propio. */
    private void exigirSinColision(Turno propuesta, boolean reprogramacion) {
        for (Turno existente : turnos) {
            if (existente.getOdontologo().getIdOdontologo() != propuesta.getOdontologo().getIdOdontologo()
                    || !existente.getFecha().equals(propuesta.getFecha())) {
                continue;
            }
            if (reprogramacion && existente.getIdTurno() == propuesta.getIdTurno()) {
                continue;
            }
            if (existente.validarSuperposicion(propuesta.getHoraInicio(), propuesta.getHoraFin())) {
                throw new ValidationException("El profesional seleccionado ya cuenta con un turno asignado "
                        + "en dicha franja horaria. Seleccione otro horario.");
            }
        }
    }

    // =====================================================================
    // Historias clínicas
    // =====================================================================

    public synchronized Optional<HistoriaClinica> buscarHistoriaPorPaciente(int idPaciente) {
        HistoriaClinica historia = historiasPorPaciente.get(idPaciente);
        return historia == null ? Optional.empty() : Optional.of(copiarHistoria(historia));
    }

    public synchronized HistoriaClinica abrirHistoria(int idPaciente, String grupoSanguineo, String alergias,
                                                      String antecedentesMedicos) {
        Paciente paciente = buscarPacientePorId(idPaciente);
        if (paciente == null) {
            throw new ValidationException("El paciente ya no existe. Recargue la consulta clínica.");
        }
        HistoriaClinica existente = historiasPorPaciente.get(idPaciente);
        if (existente != null) {
            return copiarHistoria(existente);
        }
        if (!paciente.isEstadoActivo()) {
            throw new ValidationException("Un paciente inactivo solo admite consulta; no puede abrir una HC.");
        }
        LocalDate fecha = LocalDate.now();
        String legajo = "HC-" + fecha.getYear() + "-" + idPaciente;
        HistoriaClinica nueva = new HistoriaClinica(siguienteIdHistoria++, idPaciente, legajo, fecha,
                grupoSanguineo, alergias, antecedentesMedicos, true);
        historiasPorPaciente.put(idPaciente, nueva);
        return copiarHistoria(nueva);
    }

    public synchronized HistoriaClinica actualizarCabeceraHistoria(int idPaciente, int idHistoriaClinica,
                                                                   String grupoSanguineo, String alergias, String antecedentesMedicos) {
        Paciente paciente = buscarPacientePorId(idPaciente);
        HistoriaClinica historia = historiasPorPaciente.get(idPaciente);
        if (paciente == null || historia == null || historia.getIdHistoriaClinica() != idHistoriaClinica) {
            throw new ValidationException("La HC seleccionada no corresponde al paciente. Recargue la consulta.");
        }
        if (!paciente.isEstadoActivo() || !historia.isEstadoActivo()) {
            throw new ValidationException("El paciente o la HC están inactivos y solo admiten consulta.");
        }
        // Validar los tres antes de asignar: la actualización es todo o nada.
        Validador.validarOpcional(grupoSanguineo, "El grupo sanguíneo", 10);
        Validador.validarTextoOpcional(alergias, "Las alergias");
        Validador.validarTextoOpcional(antecedentesMedicos, "Los antecedentes médicos");
        historia.setGrupoSanguineo(grupoSanguineo);
        historia.setAlergias(alergias);
        historia.setAntecedentesMedicos(antecedentesMedicos);
        return copiarHistoria(historia);
    }

    public synchronized EvolucionClinica anexarEvolucion(int idPaciente, int idHistoriaClinica, Odontologo autor,
                                                         String piezaDental, String diagnostico, String tratamientoRealizado, String observaciones) {
        Paciente paciente = buscarPacientePorId(idPaciente);
        HistoriaClinica historia = historiasPorPaciente.get(idPaciente);
        if (paciente == null || historia == null || historia.getIdHistoriaClinica() != idHistoriaClinica) {
            throw new ValidationException("La HC seleccionada no corresponde al paciente. Recargue la consulta.");
        }
        if (!paciente.isEstadoActivo() || !historia.isEstadoActivo()) {
            throw new ValidationException("El paciente o la HC están inactivos y solo admiten consulta.");
        }
        LocalDateTime ahora = LocalDateTime.now().withNano(0);
        if (ahora.toLocalDate().isBefore(historia.getFechaApertura())) {
            throw new ValidationException("La hora del equipo es anterior a la apertura de la HC. "
                    + "Revise el reloj antes de continuar.");
        }
        EvolucionClinica nueva = new EvolucionClinica(siguienteIdEvolucion++, idHistoriaClinica,
                new Odontologo(autor), ahora, piezaDental, diagnostico, tratamientoRealizado, observaciones);
        historia.agregarEvolucion(nueva);
        return new EvolucionClinica(nueva);
    }

    // =====================================================================
    // Utilidades internas comunes
    // =====================================================================

    private void exigirDniLibre(String dni, int idPacientePropio) {
        exigirDniLibre(dni, idPacientePropio, 0);
    }

    private void exigirDniLibre(String dni, int idPacientePropio, int idPersonaOdontologoPropio) {
        for (Paciente paciente : pacientes) {
            if (paciente.getIdPaciente() != idPacientePropio && paciente.getDni().equals(dni)) {
                throw new ValidationException(
                        "El DNI ya se encuentra registrado en el padrón de la clínica.");
            }
        }
        for (Odontologo odontologo : odontologos) {
            if (odontologo.getIdPersona() != idPersonaOdontologoPropio && odontologo.getDni().equals(dni)) {
                throw new ValidationException(
                        "No se pudo guardar: el DNI ya se encuentra registrado en el padrón de la clínica.");
            }
        }
    }

    private void exigirMatriculaLibre(String matricula, int idOdontologoPropio) {
        for (Odontologo odontologo : odontologos) {
            if (odontologo.getIdOdontologo() != idOdontologoPropio && odontologo.getMatricula().equals(matricula)) {
                throw new ValidationException(
                        "No se pudo guardar el profesional: la matrícula ya se encuentra registrada.");
            }
        }
    }

    private static <T> void reemplazar(List<T> lista, T actual, T nuevo) {
        int indice = lista.indexOf(actual);
        if (indice >= 0) {
            lista.set(indice, nuevo);
        }
    }

    private static <T> T exigirExistente(T registro) {
        if (registro == null) {
            throw new ValidationException("El registro ya no existe. Actualice los datos antes de continuar.");
        }
        return registro;
    }

    private static Usuario copiarUsuario(Usuario origen) {
        return new Usuario(origen.getIdUsuario(), origen.getIdPersona(), origen.getNombreUsuario(),
                origen.getClave(), origen.getRol(), origen.isEstadoActivo());
    }

    private static Paciente copiarPaciente(Paciente origen) {
        return new Paciente(origen.getIdPaciente(), origen.getIdPersona(), origen.getDni(), origen.getNombre(),
                origen.getApellido(), origen.getTelefono(), origen.getEmail(), origen.getDireccion(),
                origen.getFechaNacimiento(), origen.getObraSocial(), origen.isEstadoActivo());
    }

    private static Turno copiarTurno(Turno origen) {
        return new Turno(origen.getIdTurno(), copiarPaciente(origen.getPaciente()),
                new Odontologo(origen.getOdontologo()), origen.getFecha(), origen.getHoraInicio(),
                origen.getHoraFin(), origen.getMotivoConsulta(), origen.getEstado());
    }

    private static HistoriaClinica copiarHistoria(HistoriaClinica origen) {
        return new HistoriaClinica(origen.getIdHistoriaClinica(), origen.getIdPaciente(), origen.getNumeroLegajo(),
                origen.getFechaApertura(), origen.getGrupoSanguineo(), origen.getAlergias(),
                origen.getAntecedentesMedicos(), origen.isEstadoActivo(), origen.getEvoluciones());
    }

    // =====================================================================
    // Datos de demostración
    // =====================================================================

    private void sembrarDatos() {
        // Misma numeración que database/cocab-datos.sql: persona 1 = secretaria (sin padrón propio).
        siguienteIdPersona++;
        Odontologo bencini = new Odontologo(siguienteIdOdontologo++, siguienteIdPersona++, "18456789",
                "Carlos Alberto", "Bencini", "2214998877", "dr.bencini@cocab.com", "Calle 50 N° 1240, La Plata",
                "MAT-14258", "Cirugía e Implantes", true);
        odontologos.add(bencini);
        Odontologo gomez = new Odontologo(siguienteIdOdontologo++, siguienteIdPersona++, "27333444", "Mariana",
                "Gómez", "2214556677", "dra.gomez@cocab.com", "Diag. 74 N° 320, La Plata",
                "MAT-18965", "Ortodoncia y Endodoncia", true);
        odontologos.add(gomez);

        usuarios.add(new Usuario(siguienteIdUsuario++, 1, "admin",
                SeguridadClaves.generarHash("admin".toCharArray()), RolUsuario.Secretaria, true));
        usuarios.add(new Usuario(siguienteIdUsuario++, bencini.getIdPersona(), "odontologo",
                SeguridadClaves.generarHash("admin".toCharArray()), RolUsuario.Odontologo, true));

        Paciente juan = new Paciente(siguienteIdPaciente++, siguienteIdPersona++, "30123456", "Juan", "Pérez",
                "2214567890", "juan.perez@email.com", "Calle 12 N° 450, La Plata",
                LocalDate.of(1984, 5, 15), "OSDE", true);
        pacientes.add(juan);
        Paciente maria = new Paciente(siguienteIdPaciente++, siguienteIdPersona++, "28654987", "María", "Gómez",
                "2215678901", "maria.gomez@email.com", "Calle 60 N° 110, La Plata",
                LocalDate.of(1981, 11, 20), "Swiss Medical", true);
        pacientes.add(maria);
        Paciente lucas = new Paciente(siguienteIdPaciente++, siguienteIdPersona++, "35789123", "Lucas",
                "Rodríguez", "2216789012", "lucas.rod@email.com", "Calle 13 N° 920, La Plata",
                LocalDate.of(1991, 3, 8), "IOMA", true);
        pacientes.add(lucas);
        Paciente florencia = new Paciente(siguienteIdPaciente++, siguienteIdPersona++, "38999888", "Florencia",
                "Díaz", "2217890123", "flor.diaz@email.com", "Calle 45 N° 340, La Plata",
                LocalDate.of(1995, 7, 22), "Particular", true);
        pacientes.add(florencia);
        // Casos adicionales a cocab-datos.sql: paciente y profesional inactivos (bajas lógicas) sin turnos.
        pacientes.add(new Paciente(siguienteIdPaciente++, siguienteIdPersona++, "33456789", "Sofía", "Torres",
                "2218901234", "sofia.torres@email.com", "Calle 24 N° 700, La Plata",
                LocalDate.of(1992, 6, 30), "Particular", false));
        odontologos.add(new Odontologo(siguienteIdOdontologo++, siguienteIdPersona++, "24555666", "Roberto",
                "Sosa", "2219012345", "dr.sosa@cocab.com", "Calle 3 N° 210, La Plata",
                "MAT-09321", "Endodoncia", false));

        // Semana laboral visible: lunes de la semana en curso; en fin de semana, el lunes siguiente (19/10/2026 si hoy es 18/10/2026).
        LocalDate hoy = LocalDate.now();
        LocalDate lunes = hoy.getDayOfWeek().getValue() >= DayOfWeek.SATURDAY.getValue()
                ? hoy.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                : hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        agregarTurno(juan, bencini, lunes, 9, 0, "Limpieza y control post-operatorio", EstadoTurno.Asignado);
        agregarTurno(maria, bencini, lunes, 10, 0, "Evaluación por dolor en molar", EstadoTurno.Asignado);
        agregarTurno(lucas, bencini, lunes, 10, 30, "Extracción de resto radicular", EstadoTurno.Asignado);
        agregarTurno(florencia, bencini, lunes, 11, 0, "Primera consulta y diagnóstico general",
                EstadoTurno.Asignado);
        agregarTurno(juan, gomez, lunes, 9, 30, "Control de ortodoncia", EstadoTurno.Asignado);
        agregarTurno(maria, gomez, lunes, 10, 0, "Ajuste de brackets", EstadoTurno.Cancelado);
        agregarTurno(lucas, gomez, lunes, 11, 0, "Consulta por dolor articular", EstadoTurno.Reprogramado);
        agregarTurno(florencia, bencini, lunes.plusDays(1), 9, 0,
                "Apertura de historia clínica y plan de tratamiento", EstadoTurno.Asignado);
        agregarTurno(maria, gomez, lunes.plusDays(2), 14, 0, "Reagenda de ajuste de brackets",
                EstadoTurno.Asignado);
        agregarTurno(lucas, bencini, lunes.plusDays(4), 15, 30, "Control posterior a extracción",
                EstadoTurno.Asignado);
        LocalDate viernesAnterior = lunes.minusDays(3);
        agregarTurno(maria, bencini, viernesAnterior, 9, 30, "Control de reconstrucción en resina",
                EstadoTurno.Atendido);

        HistoriaClinica historiaJuan = abrirHistoriaDemostracion(juan, "HC-2026-0001", LocalDate.of(2026, 3, 10),
                "0+", "Penicilina", "Hipertensión arterial controlada");
        anexarEvolucionDemostracion(historiaJuan, bencini, LocalDateTime.of(2026, 3, 15, 10, 15), "1.8",
                "Caries profunda con compromiso pulpar", "Apertura de cámara y colocación de cura provisional",
                "Se indica analgésico cada 8 hs.");
        anexarEvolucionDemostracion(historiaJuan, bencini, LocalDateTime.of(2026, 3, 22, 11, 0), "1.8",
                "Tratamiento de conducto concluido", "Obturación de conductos con gutapercha y sellado coronal",
                "Control clínico a los 15 días.");

        HistoriaClinica historiaMaria = abrirHistoriaDemostracion(maria, "HC-2026-0002", LocalDate.of(2026, 4, 15),
                "A+", "Ninguna", "Sin antecedentes de relevancia");
        anexarEvolucionDemostracion(historiaMaria, bencini, LocalDateTime.of(2026, 4, 20, 9, 30), "4.6",
                "Fractura de cúspide lingual", "Reconstrucción con resina de fotocurado y pulido oclusal",
                "Buena adaptación marginal.");
        anexarEvolucionDemostracion(historiaMaria, gomez, LocalDateTime.of(2026, 5, 18, 16, 0), "General",
                "Gingivitis marginal generalizada", "Profilaxis mecánica y tartrectomía supragingival",
                "Instrucción de técnica de cepillado.");

        HistoriaClinica historiaLucas = abrirHistoriaDemostracion(lucas, "HC-2026-0003", LocalDate.of(2026, 6, 1),
                "B+", "Ibuprofeno", "Asma bronquial leve");
        anexarEvolucionDemostracion(historiaLucas, gomez, LocalDateTime.of(2026, 6, 10, 14, 30), "General",
                "Maloclusión Clase II", "Instalación de aparatología fija multibrackets arco superior",
                "Control de activación en 30 días.");

        anexarEvolucionDemostracion(historiaMaria, bencini, viernesAnterior.atTime(9, 45), "4.6",
                "Control de reconstrucción en resina", "Verificación de oclusión y pulido final",
                "Sin molestias referidas.");
    }

    private void agregarTurno(Paciente paciente, Odontologo odontologo, LocalDate fecha, int hora, int minuto,
                              String motivo, EstadoTurno estado) {
        LocalTime inicio = LocalTime.of(hora, minuto);
        turnos.add(new Turno(siguienteIdTurno++, copiarPaciente(paciente), new Odontologo(odontologo), fecha,
                inicio, inicio.plusMinutes(30), motivo, estado));
    }

    private HistoriaClinica abrirHistoriaDemostracion(Paciente paciente, String legajo, LocalDate apertura,
                                                      String grupoSanguineo, String alergias, String antecedentes) {
        HistoriaClinica historia = new HistoriaClinica(siguienteIdHistoria++, paciente.getIdPaciente(), legajo,
                apertura, grupoSanguineo, alergias, antecedentes, true);
        historiasPorPaciente.put(paciente.getIdPaciente(), historia);
        return historia;
    }

    private void anexarEvolucionDemostracion(HistoriaClinica historia, Odontologo autor, LocalDateTime fechaHora,
                                             String pieza, String diagnostico, String tratamiento, String observaciones) {
        historia.agregarEvolucion(new EvolucionClinica(siguienteIdEvolucion++, historia.getIdHistoriaClinica(),
                new Odontologo(autor), fechaHora, pieza, diagnostico, tratamiento, observaciones));
    }
}
