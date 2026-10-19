package com.cocab.model.turno;

/** Representa los posibles estados de un turno odontológico. */
public enum EstadoTurno {
    Asignado("Asignado"),
    Reprogramado("Reprogramado"),
    Cancelado("Cancelado"),
    Atendido("Atendido");

    private final String valor;

    EstadoTurno(String valor) {
        this.valor = valor;
    }

    public String getValor() {
        return valor;
    }
}