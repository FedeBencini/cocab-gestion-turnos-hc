package com.cocab.dao.impl;

import java.util.ArrayList;

import com.cocab.dao.OdontologoDAO;
import com.cocab.exception.ValidationException;
import com.cocab.mock.MockDatabase;
import com.cocab.model.odontologo.Odontologo;
import com.cocab.model.seguridad.RolUsuario;
import com.cocab.util.UserSession;
import com.cocab.util.Validador;

/**
 * Implementación de {@link OdontologoDAO} sobre el repositorio en memoria {@link MockDatabase},
 * con las reglas de validación y autorización del dominio (incluida RN-06).
 * Al depender solo de la interfaz, la próxima entrega incorporará una base de datos sin modificar los controladores.
 */
public final class OdontologoDAOImpl implements OdontologoDAO {

    /** Lista los odontólogos, incluyendo o excluyendo los inactivos según el parámetro. */
    @Override
    public ArrayList<Odontologo> listar(boolean incluirInactivos) {
        exigirSesion();
        return MockDatabase.getInstance().listarOdontologos(incluirInactivos);
    }

    /** Registra un nuevo odontólogo en el sistema. */
    @Override
    public Odontologo registrar(Odontologo nuevo) {
        exigirSesion();
        Odontologo copia = copiar(nuevo, true);
        return MockDatabase.getInstance().registrarOdontologo(copia);
    }

    /** Modifica los datos de un odontólogo existente. */
    @Override
    public Odontologo modificar(Odontologo original, Odontologo cambios) {
        exigirSesion();
        Odontologo esperado = copiar(original, false);
        Odontologo propuesto = copiar(cambios, false);
        exigirMismaIdentidadYActividad(esperado, propuesto);
        return MockDatabase.getInstance().modificarOdontologo(esperado, propuesto);
    }

    /** Da de baja a un odontólogo, dejándolo inactivo en el sistema. */
    @Override
    public void darDeBaja(Odontologo original) {
        exigirSesion();
        Odontologo esperado = copiar(original, false);
        MockDatabase.getInstance().darDeBajaOdontologo(esperado);
    }

    /** Exige que la sesión esté activa y que el usuario tenga el rol de secretaria. */
    private void exigirSesion() {
        UserSession.getInstance().exigirRol(RolUsuario.Secretaria);
    }

    /** Crea una copia de un odontólogo, validando los campos según si es un alta o una modificación. */
    private static Odontologo copiar(Odontologo origen, boolean alta) {
        Validador.requerirNoNulo(origen, "El odontólogo");
        int idOdontologo = origen.getIdOdontologo();
        int idPersona = origen.getIdPersona();
        boolean activo = origen.isEstadoActivo();
        if (alta) {
            if (idOdontologo != 0 || idPersona != 0 || !activo) {
                throw new ValidationException("Un odontólogo nuevo debe tener identificadores cero y estar activo.");
            }
        } else {
            Validador.validarIdPersistido(idOdontologo, "El identificador de odontólogo");
            Validador.validarIdPersistido(idPersona, "El identificador de persona");
        }
        // Los constructores validan todos los campos sin imponer una regex de matrícula.
        return new Odontologo(idOdontologo, idPersona, origen.getDni(), origen.getNombre(), origen.getApellido(),
                origen.getTelefono(), origen.getEmail(), origen.getDireccion(), origen.getMatricula(),
                origen.getEspecialidad(), activo);
    }

    /** Exige que la identidad y el estado de actividad de un odontólogo no cambien durante la modificación. */
    private static void exigirMismaIdentidadYActividad(Odontologo original, Odontologo cambios) {
        if (original.getIdOdontologo() != cambios.getIdOdontologo()
                || original.getIdPersona() != cambios.getIdPersona()
                || original.isEstadoActivo() != cambios.isEstadoActivo()) {
            throw new ValidationException("La modificación no permite cambiar identificadores ni actividad del odontólogo.");
        }
    }
}