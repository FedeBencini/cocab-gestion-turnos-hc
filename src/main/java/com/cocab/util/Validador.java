package com.cocab.util;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.cocab.exception.ValidationException;

/** Utilidad para validar datos de dominio, DAO y presentación. */
public final class Validador {
    private static final int MAXIMO_BYTES_TEXT = 65_535;
    private static final LocalTime APERTURA = LocalTime.of(8, 0);
    private static final LocalTime CIERRE = LocalTime.of(18, 0);

    private Validador() {
    }

    /** Valida que un campo obligatorio no sea nulo ni vacío y que cumpla con la longitud máxima. */
    public static String validarObligatorio(String valor, String campo, int longitudMaxima) {
        if (valor == null || valor.isBlank()) {
            throw new ValidationException(campo + " es obligatorio.");
        }
        return validarOpcional(valor, campo, longitudMaxima);
    }

    /** Valida un campo opcional, asegurando que cumpla con la longitud máxima si no es nulo. */
    public static String validarOpcional(String valor, String campo, int longitudMaxima) {
        if (longitudMaxima < 1) {
            throw new ValidationException("La longitud máxima debe ser positiva.");
        }
        if (valor != null) {
            validarUnicode(valor, campo);
            if (valor.codePointCount(0, valor.length()) > longitudMaxima) {
                throw new ValidationException(campo + " admite hasta " + longitudMaxima + " caracteres.");
            }
        }
        return valor;
    }

    /** Valida que un campo de texto obligatorio no sea nulo ni vacío y que cumpla con la capacidad máxima de bytes establecida. */
    public static String validarTextoObligatorio(String valor, String campo) {
        validarObligatorio(valor, campo, MAXIMO_BYTES_TEXT);
        return validarTextoOpcional(valor, campo);
    }

    /** Valida que un campo de texto opcional cumpla con la capacidad máxima de bytes establecida si no es nulo. */
    public static String validarTextoOpcional(String valor, String campo) {
        validarOpcional(valor, campo, MAXIMO_BYTES_TEXT);
        if (valor != null && valor.getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES_TEXT) {
            throw new ValidationException(campo + " supera la capacidad del campo TEXT en UTF-8.");
        }
        return valor;
    }

    /** Valida que un campo de texto cumpla con la capacidad máxima de bytes establecida si no es nulo. */
    private static void validarUnicode(String valor, String campo) {
        for (int indice = 0; indice < valor.length(); indice++) {
            char caracter = valor.charAt(indice);
            if (Character.isHighSurrogate(caracter)) {
                if (++indice >= valor.length() || !Character.isLowSurrogate(valor.charAt(indice))) {
                    throw new ValidationException(campo + " contiene una secuencia Unicode inválida.");
                }
            } else if (Character.isLowSurrogate(caracter)) {
                throw new ValidationException(campo + " contiene una secuencia Unicode inválida.");
            }
        }
    }

    /** Valida que un identificador sea no negativo. */
    public static int validarId(int identificador, String campo) {
        if (identificador < 0) {
            throw new ValidationException(campo + " no puede ser negativo.");
        }
        return identificador;
    }

    /** Valida que un identificador corresponda a un registro persistido (mayor que cero). */
    public static int validarIdPersistido(int identificador, String campo) {
        if (identificador <= 0) {
            throw new ValidationException(campo + " debe identificar un registro persistido.");
        }
        return identificador;
    }

    /** Valida que un valor no sea nulo. */
    public static <T> T requerirNoNulo(T valor, String campo) {
        if (valor == null) {
            throw new ValidationException(campo + " es obligatorio.");
        }
        return valor;
    }

    /** Valida que un DNI sea válido según las reglas de formato. */
    public static boolean esDniValido(String dni) {
        if (dni == null || dni.length() < 7 || dni.length() > 8) {
            return false;
        }
        for (int indice = 0; indice < dni.length(); indice++) {
            if (dni.charAt(indice) < '0' || dni.charAt(indice) > '9') {
                return false;
            }
        }
        return true;
    }

    /** Valida que un DNI sea válido y lanza una excepción si no lo es. */
    public static String validarDni(String dni) {
        if (!esDniValido(dni)) {
            throw new ValidationException("El DNI ingresado no es válido. Debe contener entre 7 y 8 dígitos numéricos sin puntos ni letras.");
        }
        return dni;
    }

    /** Valida que una fecha SQL tenga un año de cuatro dígitos dentro del rango admitido por la aplicación. */
    public static LocalDate validarFechaSql(LocalDate fecha, String campo) {
        requerirNoNulo(fecha, campo);
        if (fecha.getYear() < 1000 || fecha.getYear() > 9999) {
            throw new ValidationException(campo + " está fuera del rango de fechas admitido.");
        }
        return fecha;
    }

    /** Valida que una fecha de nacimiento sea anterior a la fecha actual. */
    public static LocalDate validarFechaNacimiento(LocalDate fecha) {
        validarFechaSql(fecha, "La fecha de nacimiento");
        if (!fecha.isBefore(LocalDate.now())) {
            throw new ValidationException("La fecha de nacimiento debe ser anterior a hoy.");
        }
        return fecha;
    }

    /** Valida que una fecha de asignación no sea anterior a la fecha actual. */
    public static LocalDate validarFechaAsignacion(LocalDate fecha) {
        validarFechaSql(fecha, "La fecha del turno");
        if (fecha.isBefore(LocalDate.now())) {
            throw new ValidationException("La fecha del turno no puede ser anterior a hoy.");
        }
        return fecha;
    }

    /** Valida que una fecha y hora SQL tenga precisión de segundos y un año de cuatro dígitos dentro del rango admitido. */
    public static LocalDateTime validarFechaHoraSql(LocalDateTime fechaHora, String campo) {
        requerirNoNulo(fechaHora, campo);
        validarFechaSql(fechaHora.toLocalDate(), campo);
        return fechaHora.withNano(0);
    }

    /** Valida que una hora SQL no tenga fracciones de segundo. */
    public static LocalTime validarHoraSql(LocalTime hora, String campo) {
        requerirNoNulo(hora, campo);
        if (hora.getNano() != 0) {
            throw new ValidationException(campo + " no admite fracciones de segundo.");
        }
        return hora;
    }

    /** Valida que un intervalo de tiempo tenga una hora de fin posterior a la hora de inicio. */
    public static void validarIntervalo(LocalTime inicio, LocalTime fin) {
        validarHoraSql(inicio, "La hora de inicio");
        validarHoraSql(fin, "La hora de fin");
        if (!fin.isAfter(inicio)) {
            throw new ValidationException("La hora de fin debe ser posterior a la de inicio.");
        }
    }

    /** Valida que una franja horaria cumpla con las restricciones de duración, inicio y fin dentro del horario de atención. */
    public static void validarFranjaHoraria(LocalTime inicio, LocalTime fin) {
        validarIntervalo(inicio, fin);
        if (inicio.isBefore(APERTURA) || fin.isAfter(CIERRE)
                || inicio.getSecond() != 0 || inicio.getMinute() % 30 != 0
                || !Duration.between(inicio, fin).equals(Duration.ofMinutes(30))) {
            throw new ValidationException("El turno debe durar 30 minutos, iniciar cada media hora entre 08:00 y 17:30 y finalizar hasta las 18:00.");
        }
    }
}