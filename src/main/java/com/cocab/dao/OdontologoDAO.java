package com.cocab.dao;

import java.util.ArrayList;

import com.cocab.model.odontologo.Odontologo;

/** Padrón profesional exclusivo de secretaria; no aprovisiona cuentas. */
public interface OdontologoDAO {
    ArrayList<Odontologo> listar(boolean incluirInactivos);

    Odontologo registrar(Odontologo nuevo);

    /** Modifica los datos de un odontólogo existente, comparando con la instantánea original. */
    Odontologo modificar(Odontologo original, Odontologo cambios);

    void darDeBaja(Odontologo original);
}