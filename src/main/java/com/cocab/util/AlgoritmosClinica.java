package com.cocab.util;

import java.util.ArrayList;

import com.cocab.model.turno.Turno;

/** Algoritmos de ordenación y búsqueda. */
public final class AlgoritmosClinica {
    private AlgoritmosClinica() {
    }

    /** Ordena un arreglo de turnos por la hora de inicio de manera estable. */
    public static void ordenarTurnosPorHora(Turno[] turnos) {
        Validador.requerirNoNulo(turnos, "El arreglo de turnos");
        for (Turno turno : turnos) {
            Validador.requerirNoNulo(turno, "Cada turno del arreglo");
            Validador.requerirNoNulo(turno.getHoraInicio(), "La hora de inicio de cada turno");
        }
        for (int indice = 1; indice < turnos.length; indice++) {
            Turno actual = turnos[indice];
            int anterior = indice - 1;
            while (anterior >= 0 && turnos[anterior].getHoraInicio().isAfter(actual.getHoraInicio())) {
                turnos[anterior + 1] = turnos[anterior];
                anterior--;
            }
            turnos[anterior + 1] = actual;
        }
    }

    /** Ordena una lista de turnos por la hora de inicio de manera estable. */
    public static void ordenarTurnosPorHora(ArrayList<Turno> turnos) {
        Validador.requerirNoNulo(turnos, "La lista de turnos");
        Turno[] arreglo = turnos.toArray(new Turno[0]);
        ordenarTurnosPorHora(arreglo);
        for (int indice = 0; indice < arreglo.length; indice++) {
            turnos.set(indice, arreglo[indice]);
        }
    }

    /** Ordena un arreglo de DNIs de manera estable usando inserción. */
    public static void ordenarDnis(String[] dnis) {
        Validador.requerirNoNulo(dnis, "El arreglo de DNI");
        for (String dni : dnis) {
            Validador.validarDni(dni);
        }
        for (int indice = 1; indice < dnis.length; indice++) {
            String actual = dnis[indice];
            int anterior = indice - 1;
            while (anterior >= 0 && dnis[anterior].compareTo(actual) > 0) {
                dnis[anterior + 1] = dnis[anterior];
                anterior--;
            }
            dnis[anterior + 1] = actual;
        }
    }

    /** Busca un paciente por DNI en un arreglo ordenado de DNIs usando búsqueda binaria. */
    public static int buscarPacientePorDni(String[] dnisOrdenados, String dni) {
        Validador.requerirNoNulo(dnisOrdenados, "El arreglo ordenado de DNI");
        Validador.validarDni(dni);
        int inferior = 0;
        int superior = dnisOrdenados.length - 1;
        while (inferior <= superior) {
            int medio = inferior + (superior - inferior) / 2;
            String dniMedio = Validador.validarDni(dnisOrdenados[medio]);
            int comparacion = dniMedio.compareTo(dni);
            if (comparacion == 0) {
                return medio;
            }
            if (comparacion < 0) {
                inferior = medio + 1;
            } else {
                superior = medio - 1;
            }
        }
        return -1;
    }
}