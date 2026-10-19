package com.cocab.model.historia;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;

import com.cocab.exception.ValidationException;
import com.cocab.util.Validador;

/** Representa la historia clínica de un paciente, incluyendo sus datos médicos y evoluciones clínicas. */
public class HistoriaClinica {
    private static final Comparator<EvolucionClinica> ORDEN_CRONOLOGICO = Comparator
            .comparing(EvolucionClinica::getFechaHora)
            .thenComparingInt(EvolucionClinica::getIdEvolucion);

    private int idHistoriaClinica;
    private int idPaciente;
    private String numeroLegajo;
    private LocalDate fechaApertura;
    private String grupoSanguineo;
    private String alergias;
    private String antecedentesMedicos;
    private boolean estadoActivo = true;
    private final ArrayList<EvolucionClinica> evoluciones = new ArrayList<>();

    public HistoriaClinica() {
    }

    public HistoriaClinica(int idHistoriaClinica, int idPaciente, String numeroLegajo,
                           LocalDate fechaApertura, String grupoSanguineo, String alergias,
                           String antecedentesMedicos, boolean estadoActivo) {
        setIdHistoriaClinica(idHistoriaClinica);
        setIdPaciente(idPaciente);
        setNumeroLegajo(numeroLegajo);
        setFechaApertura(fechaApertura);
        setGrupoSanguineo(grupoSanguineo);
        setAlergias(alergias);
        setAntecedentesMedicos(antecedentesMedicos);
        setEstadoActivo(estadoActivo);
    }

    public HistoriaClinica(int idHistoriaClinica, int idPaciente, String numeroLegajo,
                           LocalDate fechaApertura, String grupoSanguineo, String alergias,
                           String antecedentesMedicos, boolean estadoActivo, ArrayList<EvolucionClinica> evoluciones) {
        this(idHistoriaClinica, idPaciente, numeroLegajo, fechaApertura, grupoSanguineo,
                alergias, antecedentesMedicos, estadoActivo);
        Validador.requerirNoNulo(evoluciones, "El historial de evoluciones");
        HashSet<Integer> identificadores = new HashSet<>();
        for (EvolucionClinica evolucion : evoluciones) {
            EvolucionClinica copia = new EvolucionClinica(evolucion);
            validarRelacion(copia);
            if (!copia.asegurarInmutabilidad()) {
                throw new ValidationException("La hidratación del historial requiere evoluciones persistidas.");
            }
            if (!identificadores.add(copia.getIdEvolucion())) {
                throw new ValidationException("El historial contiene un identificador de evolución duplicado.");
            }
            this.evoluciones.add(copia);
        }
        this.evoluciones.sort(ORDEN_CRONOLOGICO);
    }

    public HistoriaClinica(int idPaciente, String numeroLegajo, LocalDate fechaApertura,
                           String grupoSanguineo, String alergias, String antecedentesMedicos) {
        this(0, idPaciente, numeroLegajo, fechaApertura, grupoSanguineo,
                alergias, antecedentesMedicos, true);
    }

    /** Agrega una nueva evolución clínica a la historia, asegurando consistencia cronológica y persistencia. */
    public void agregarEvolucion(EvolucionClinica nuevaEvolucion) {
        if (!estadoActivo) {
            throw new ValidationException("Una historia clínica inactiva no admite nuevas evoluciones.");
        }
        Validador.validarIdPersistido(idHistoriaClinica, "El identificador de historia clínica");
        EvolucionClinica copia = new EvolucionClinica(nuevaEvolucion);
        validarRelacion(copia);
        copia.validarParaRegistro();
        for (EvolucionClinica existente : evoluciones) {
            if (copia.getIdEvolucion() > 0 && existente.getIdEvolucion() == copia.getIdEvolucion()) {
                throw new ValidationException("La evolución ya está incorporada a esta historia clínica.");
            }
        }
        if (!evoluciones.isEmpty()
                && copia.getFechaHora().isBefore(evoluciones.get(evoluciones.size() - 1).getFechaHora())) {
            throw new ValidationException("Una nueva evolución no puede ser anterior a la última asentada.");
        }
        evoluciones.add(copia);
        evoluciones.sort(ORDEN_CRONOLOGICO);
    }

    /** Valida que la evolución pertenezca a esta historia clínica. */
    private void validarRelacion(EvolucionClinica evolucion) {
        if (evolucion.getIdHistoriaClinica() != idHistoriaClinica) {
            throw new ValidationException("La evolución debe pertenecer a esta historia clínica.");
        }
    }

    /** Obtiene el historial de evoluciones clínicas en orden cronológico, incluyendo copias independientes de cada evolución. */
    public ArrayList<EvolucionClinica> obtenerHistorialCronologico() {
        ArrayList<EvolucionClinica> historial = new ArrayList<>(evoluciones.size());
        for (EvolucionClinica evolucion : evoluciones) {
            historial.add(new EvolucionClinica(evolucion));
        }
        historial.sort(ORDEN_CRONOLOGICO);
        return historial;
    }

    /* Getters y Setters con validaciones */
    public ArrayList<EvolucionClinica> getEvoluciones() {
        return obtenerHistorialCronologico();
    }

    // No necesario: definido en TP2, pero ninguna funcionalidad lo utiliza; el sistema no ofrece baja de historias clínicas.
    //public void registrarBajaLogica() {
    //}

    public int getIdHistoriaClinica() {
        return idHistoriaClinica;
    }

    public void setIdHistoriaClinica(int idHistoriaClinica) {
        Validador.validarId(idHistoriaClinica, "El identificador de historia clínica");
        for (EvolucionClinica evolucion : evoluciones) {
            if (evolucion.getIdHistoriaClinica() != idHistoriaClinica) {
                throw new ValidationException("El identificador no puede desvincular las evoluciones del legajo.");
            }
        }
        this.idHistoriaClinica = idHistoriaClinica;
    }

    public int getIdPaciente() {
        return idPaciente;
    }

    public void setIdPaciente(int idPaciente) {
        this.idPaciente = Validador.validarId(idPaciente, "El identificador de paciente");
    }

    public String getNumeroLegajo() {
        return numeroLegajo;
    }

    public void setNumeroLegajo(String numeroLegajo) {
        this.numeroLegajo = Validador.validarObligatorio(numeroLegajo, "El número de legajo", 30);
    }

    public LocalDate getFechaApertura() {
        return fechaApertura;
    }

    public void setFechaApertura(LocalDate fechaApertura) {
        this.fechaApertura = Validador.validarFechaSql(fechaApertura, "La fecha de apertura");
    }

    public String getGrupoSanguineo() {
        return grupoSanguineo;
    }

    public void setGrupoSanguineo(String grupoSanguineo) {
        this.grupoSanguineo = Validador.validarOpcional(grupoSanguineo, "El grupo sanguíneo", 10);
    }

    public String getAlergias() {
        return alergias;
    }

    public void setAlergias(String alergias) {
        this.alergias = Validador.validarTextoOpcional(alergias, "Las alergias");
    }

    public String getAntecedentesMedicos() {
        return antecedentesMedicos;
    }

    public void setAntecedentesMedicos(String antecedentesMedicos) {
        this.antecedentesMedicos = Validador.validarTextoOpcional(antecedentesMedicos, "Los antecedentes médicos");
    }

    public boolean isEstadoActivo() {
        return estadoActivo;
    }

    public void setEstadoActivo(boolean estadoActivo) {
        this.estadoActivo = estadoActivo;
    }

    @Override
    public String toString() {
        return "HistoriaClinica{idHistoriaClinica=" + idHistoriaClinica + ", estadoActivo=" + estadoActivo + "}";
    }
}