package com.cocab.dao.impl;

import java.util.Optional;

import com.cocab.dao.UsuarioDAO;
import com.cocab.mock.MockDatabase;
import com.cocab.model.seguridad.Usuario;
import com.cocab.util.Validador;

/**
 * Implementación de {@link UsuarioDAO} sobre el repositorio en memoria {@link MockDatabase}.
 * Al depender solo de la interfaz, la próxima entrega incorporará una base de datos sin modificar los controladores.
 */
public final class UsuarioDAOImpl implements UsuarioDAO {

    public UsuarioDAOImpl() {
        // Sin estado propio: los datos viven en MockDatabase.
    }

    /** Autentica a un usuario verificando su nombre de usuario y la clave ingresada. */
    @Override
    public Optional<Usuario> autenticar(String nombreUsuario, String claveIngresada) {
        Validador.validarObligatorio(nombreUsuario, "El nombre de usuario", 50);
        if (claveIngresada == null || claveIngresada.isEmpty()) {
            return Optional.empty();
        }
        Optional<Usuario> encontrado = MockDatabase.getInstance().buscarUsuarioHabilitado(nombreUsuario);
        if (encontrado.isEmpty() || !encontrado.get().verificarCredenciales(claveIngresada)) {
            return Optional.empty();
        }
        return encontrado;
    }

    // No necesario: definido en TP2, pero no se utiliza; ver UsuarioDAO.
    // public OptionalInt buscarIdOdontologoActivo(int idPersona) {
    // }
}