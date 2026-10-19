package com.cocab.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.function.Function;

import com.cocab.exception.ValidationException;
import com.cocab.model.base.Persona;
import com.cocab.model.odontologo.Odontologo;
import com.cocab.model.paciente.Paciente;
import com.cocab.model.turno.EstadoTurno;
import com.cocab.model.turno.Turno;
import com.cocab.util.Validador;

import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.util.StringConverter;

/** Utilidades comunes de las vistas: fechas, etiquetas, máscaras de texto y copias de modelos. */
final class AyudaVista {
    static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);

    private AyudaVista() {
    }

    /** Formatea una fecha como cadena en el formato dd/MM/aaaa. */
    static String fecha(LocalDate valor) {
        return valor == null ? "" : FECHA.format(valor);
    }

    /** Configura un DatePicker para mostrar y leer fechas con formato dd/MM/aaaa; un texto inválido produce null. */
    static void prepararFecha(DatePicker campo) {
        campo.setConverter(new StringConverter<>() {
            @Override public String toString(LocalDate valor) { return fecha(valor); }
            @Override public LocalDate fromString(String texto) {
                try {
                    return texto == null || texto.isBlank() ? null : LocalDate.parse(texto, FECHA);
                } catch (DateTimeParseException excepcion) {
                    return null;
                }
            }
        });
    }

    /** Lee la fecha de un DatePicker y la valida; lanza ValidationException si el formato es incorrecto. */
    static LocalDate leerFecha(DatePicker campo) {
        try {
            return Validador.validarFechaSql(LocalDate.parse(campo.getEditor().getText(), FECHA), "La fecha");
        } catch (DateTimeParseException excepcion) {
            throw new ValidationException("Ingrese una fecha válida con formato dd/MM/aaaa.");
        }
    }

    /** Crea un StringConverter genérico para mostrar etiquetas de objetos en controles de selección. */
    static <T> StringConverter<T> etiquetas(Function<T, String> etiqueta) {
        return new StringConverter<>() {
            @Override public String toString(T valor) { return valor == null ? "" : etiqueta.apply(valor); }
            @Override public T fromString(String texto) {
                throw new UnsupportedOperationException("El selector no admite texto libre.");
            }
        };
    }

    /** Formatea la información de un paciente como cadena legible. */
    static String paciente(Paciente valor) {
        return valor.obtenerNombreCompleto() + " · DNI " + valor.getDni()
                + (valor.estaHabilitado() ? " · Activo" : " · INACTIVO");
    }

    /** Formatea la información de un profesional como cadena legible. */
    static String profesional(Odontologo valor) {
        return valor.obtenerNombreCompleto() + " · " + valor.getMatricula()
                + (valor.estaHabilitado() ? " · Activo" : " · INACTIVO");
    }

    /** Determina si un turno es modificable según su estado. */
    static boolean modificable(Turno turno) {
        return turno != null && (turno.getEstado() == EstadoTurno.Asignado
                || turno.getEstado() == EstadoTurno.Reprogramado);
    }

    /** Crea una copia independiente de un objeto Paciente. */
    static Paciente copiar(Paciente origen) {
        return new Paciente(origen.getIdPaciente(), origen.getIdPersona(), origen.getDni(),
                origen.getNombre(), origen.getApellido(), origen.getTelefono(), origen.getEmail(),
                origen.getDireccion(), origen.getFechaNacimiento(), origen.getObraSocial(), origen.isEstadoActivo());
    }

    /** Crea una copia independiente de un objeto Turno. */
    static Turno copiar(Turno origen) {
        return new Turno(origen.getIdTurno(), copiar(origen.getPaciente()), new Odontologo(origen.getOdontologo()),
                origen.getFecha(), origen.getHoraInicio(), origen.getHoraFin(), origen.getMotivoConsulta(),
                origen.getEstado());
    }

    /** Devuelve un texto seguro, reemplazando null por cadena vacía. */
    static String texto(String valor) {
        return valor == null ? "" : valor;
    }

    /** Devuelve un texto opcional, convirtiendo cadenas vacías o null en null. */
    static String opcional(String valor) {
        return valor == null || valor.isEmpty() ? null : valor;
    }

    /** Aplica una máscara de DNI (hasta 8 dígitos) a un campo de texto. */
    static void mascaraDni(TextField campo) {
        campo.setTextFormatter(new TextFormatter<String>(cambio ->
                cambio.getControlNewText().matches("[0-9]{0,8}") ? cambio : null));
    }

    /** Limita la longitud máxima de un campo de texto. */
    static void limitar(TextField campo, int maximo) {
        campo.setTextFormatter(new TextFormatter<String>(cambio -> {
            String texto = cambio.getControlNewText();
            return texto.codePointCount(0, texto.length()) <= maximo ? cambio : null;
        }));
    }

    /** Representa los datos comunes de una persona, inmutables. */
    record DatosPersona(int idPersona, String dni, String nombre, String apellido,
                        String telefono, String email, String direccion) {
        static DatosPersona de(Persona persona) {
            return new DatosPersona(persona.getIdPersona(), persona.getDni(), persona.getNombre(),
                    persona.getApellido(), persona.getTelefono(), persona.getEmail(), persona.getDireccion());
        }
    }

    /** Agrupa y gestiona los seis campos de texto que comparten los formularios de pacientes y odontólogos. */
    static final class CamposPersona {
        private final TextField dni;
        private final TextField nombre;
        private final TextField apellido;
        private final TextField telefono;
        private final TextField email;
        private final TextField direccion;

        CamposPersona(TextField dni, TextField nombre, TextField apellido,
                      TextField telefono, TextField email, TextField direccion) {
            this.dni = dni;
            this.nombre = nombre;
            this.apellido = apellido;
            this.telefono = telefono;
            this.email = email;
            this.direccion = direccion;
            mascaraDni(dni);
            limitar(nombre, 50);
            limitar(apellido, 50);
            limitar(telefono, 20);
            limitar(email, 100);
            limitar(direccion, 150);
        }

        /** Captura los datos ingresados en los campos de texto y los empaqueta en un objeto DatosPersona. */
        DatosPersona capturar(int idPersona) {
            return new DatosPersona(idPersona, Validador.validarDni(dni.getText()),
                    Validador.validarObligatorio(nombre.getText(), "El nombre", 50),
                    Validador.validarObligatorio(apellido.getText(), "El apellido", 50),
                    Validador.validarOpcional(opcional(telefono.getText()), "El teléfono", 20),
                    Validador.validarOpcional(opcional(email.getText()), "El correo electrónico", 100),
                    Validador.validarOpcional(opcional(direccion.getText()), "La dirección", 150));
        }

        /** Muestra los datos de una persona en los campos de texto correspondientes. */
        void mostrar(DatosPersona datos) {
            dni.setText(datos == null ? "" : texto(datos.dni()));
            nombre.setText(datos == null ? "" : texto(datos.nombre()));
            apellido.setText(datos == null ? "" : texto(datos.apellido()));
            telefono.setText(datos == null ? "" : texto(datos.telefono()));
            email.setText(datos == null ? "" : texto(datos.email()));
            direccion.setText(datos == null ? "" : texto(datos.direccion()));
        }
    }
}
