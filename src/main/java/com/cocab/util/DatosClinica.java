package com.cocab.util;

import java.time.LocalTime;

/** Contiene datos fijos de la clínica, como franjas horarias y obras sociales. */
public final class DatosClinica {
    private static final LocalTime[] FRANJAS_HORARIAS = crearFranjas();
    private static final String[] OBRAS_SOCIALES = {"OSDE", "Swiss Medical", "IOMA", "Particular"};

    private DatosClinica() {
    }

    /** Devuelve una copia de las franjas horarias disponibles en la clínica. */
    public static LocalTime[] obtenerFranjasHorarias() {
        return FRANJAS_HORARIAS.clone();
    }

    /** Devuelve una copia del listado de obras sociales aceptadas por la clínica. */
    public static String[] obtenerObrasSociales() {
        return OBRAS_SOCIALES.clone();
    }

    /** Crea las franjas horarias predeterminadas de la clínica. */
    private static LocalTime[] crearFranjas() {
        LocalTime[] franjas = new LocalTime[20];
        for (int indice = 0; indice < franjas.length; indice++) {
            franjas[indice] = LocalTime.of(8, 0).plusMinutes(30L * indice);
        }
        return franjas;
    }
}