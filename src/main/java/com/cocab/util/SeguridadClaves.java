package com.cocab.util;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.cocab.exception.ValidationException;

/** Utilidad para la gestión segura de contraseñas mediante PBKDF2 con SHA-256. */
public final class SeguridadClaves {
    private static final int ITERACIONES = 600_000;
    private static final int MAXIMO_ITERACIONES = 2_000_000;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private SeguridadClaves() {
    }

    /** Genera un hash seguro de la contraseña; el llamador conserva la responsabilidad de limpiar su arreglo de entrada. */
    public static String generarHash(char[] clave) {
        if (clave == null || clave.length == 0) {
            throw new ValidationException("La contraseña no puede estar vacía.");
        }
        byte[] sal = new byte[16];
        ALEATORIO.nextBytes(sal);
        byte[] derivacion = null;
        try {
            derivacion = derivar(clave, sal, ITERACIONES);
            return "v1$pbkdf2-sha256$" + ITERACIONES + "$"
                    + Base64.getEncoder().encodeToString(sal) + "$"
                    + Base64.getEncoder().encodeToString(derivacion);
        } finally {
            Arrays.fill(sal, (byte) 0);
            if (derivacion != null) {
                Arrays.fill(derivacion, (byte) 0);
            }
        }
    }

    /** Rechaza formatos o costos no admitidos antes de cualquier derivación. */
    public static boolean esFormatoValido(String hash) {
        if (hash == null || hash.length() > 255) {
            return false;
        }
        String[] partes = hash.split("\\$", -1);
        if (partes.length != 5 || !"v1".equals(partes[0])
                || !"pbkdf2-sha256".equals(partes[1])
                || !partes[2].matches("[1-9][0-9]{5,6}")
                || partes[3].length() != 24 || partes[4].length() != 44) {
            return false;
        }
        int iteraciones = Integer.parseInt(partes[2]);
        return iteraciones >= ITERACIONES && iteraciones <= MAXIMO_ITERACIONES
                && esBase64Canonico(partes[3], 16) && esBase64Canonico(partes[4], 32);
    }

    /** Verifica si la contraseña coincide con el hash seguro proporcionado. */
    public static boolean verificarClave(char[] clave, String hash) {
        if (clave == null || clave.length == 0 || !esFormatoValido(hash)) {
            return false;
        }
        String[] partes = hash.split("\\$", -1);
        byte[] sal = Base64.getDecoder().decode(partes[3]);
        byte[] esperada = Base64.getDecoder().decode(partes[4]);
        byte[] calculada = null;
        try {
            calculada = derivar(clave, sal, Integer.parseInt(partes[2]));
            return MessageDigest.isEqual(esperada, calculada);
        } finally {
            Arrays.fill(sal, (byte) 0);
            Arrays.fill(esperada, (byte) 0);
            if (calculada != null) {
                Arrays.fill(calculada, (byte) 0);
            }
        }
    }

    /** Verifica que el texto sea una codificación Base64 canonica de la longitud esperada. */
    private static boolean esBase64Canonico(String texto, int longitud) {
        byte[] bytes = null;
        try {
            bytes = Base64.getDecoder().decode(texto);
            return bytes.length == longitud && Base64.getEncoder().encodeToString(bytes).equals(texto);
        } catch (IllegalArgumentException excepcion) {
            return false;
        } finally {
            if (bytes != null) {
                Arrays.fill(bytes, (byte) 0);
            }
        }
    }

    /** Deriva una clave segura usando PBKDF2 con SHA-256. */
    private static byte[] derivar(char[] clave, byte[] sal, int iteraciones) {
        PBEKeySpec especificacion = new PBEKeySpec(clave, sal, iteraciones, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(especificacion).getEncoded();
        } catch (GeneralSecurityException excepcion) {
            throw new IllegalStateException("El JDK no pudo realizar la verificación criptográfica.", excepcion);
        } finally {
            especificacion.clearPassword();
        }
    }
}