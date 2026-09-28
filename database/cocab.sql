-- =============================================================================
-- PROYECTO INTEGRADOR: Centro Odontológico COCAB
-- AUTOR: Federico Bencini
-- ARCHIVO: cocab.sql
-- =============================================================================

-- =============================================================================
-- SECCIÓN CREACIÓN
-- =============================================================================

-- Base de Datos: cocab_db
CREATE DATABASE IF NOT EXISTS cocab_db;
USE cocab_db;

-- Tabla Base: personas
CREATE TABLE IF NOT EXISTS personas (
    id_persona INT AUTO_INCREMENT PRIMARY KEY,
    dni VARCHAR(10) NOT NULL,
    nombre VARCHAR(50) NOT NULL,
    apellido VARCHAR(50) NOT NULL,
    telefono VARCHAR(20) NULL,
    email VARCHAR(100) NULL,
    direccion VARCHAR(150) NULL,
    fecha_registro TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_personas_dni UNIQUE (dni)
);

-- Tabla: usuarios
CREATE TABLE IF NOT EXISTS usuarios (
    id_usuario INT AUTO_INCREMENT PRIMARY KEY,
    id_persona INT NOT NULL,
    nombre_usuario VARCHAR(50) NOT NULL,
    clave VARCHAR(255) NOT NULL,
    rol ENUM('Secretaria', 'Odontologo') NOT NULL,
    estado_activo TINYINT(1) DEFAULT 1 NOT NULL,
    CONSTRAINT uq_usuarios_persona UNIQUE (id_persona),
    CONSTRAINT uq_usuarios_nombre UNIQUE (nombre_usuario),
    CONSTRAINT fk_usuarios_persona FOREIGN KEY (id_persona)
        REFERENCES personas(id_persona)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

-- Tabla: secretarias
CREATE TABLE IF NOT EXISTS secretarias (
    id_secretaria INT AUTO_INCREMENT PRIMARY KEY,
    id_persona INT NOT NULL,
    legajo_empleado VARCHAR(20) NOT NULL,
    CONSTRAINT uq_secretarias_persona UNIQUE (id_persona),
    CONSTRAINT uq_secretarias_legajo UNIQUE (legajo_empleado),
    CONSTRAINT fk_secretarias_persona FOREIGN KEY (id_persona)
        REFERENCES personas(id_persona)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

-- Tabla: odontologos
CREATE TABLE IF NOT EXISTS odontologos (
    id_odontologo INT AUTO_INCREMENT PRIMARY KEY,
    id_persona INT NOT NULL,
    matricula VARCHAR(20) NOT NULL,
    especialidad VARCHAR(100) NOT NULL,
    estado_activo TINYINT(1) DEFAULT 1 NOT NULL,
    CONSTRAINT uq_odontologos_persona UNIQUE (id_persona),
    CONSTRAINT uq_odontologos_matricula UNIQUE (matricula),
    CONSTRAINT fk_odontologos_persona FOREIGN KEY (id_persona)
        REFERENCES personas(id_persona)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

-- Tabla: pacientes
CREATE TABLE IF NOT EXISTS pacientes (
    id_paciente INT AUTO_INCREMENT PRIMARY KEY,
    id_persona INT NOT NULL,
    fecha_nacimiento DATE NOT NULL,
    obra_social VARCHAR(50) DEFAULT 'Particular',
    estado_activo TINYINT(1) DEFAULT 1 NOT NULL,
    CONSTRAINT uq_pacientes_persona UNIQUE (id_persona),
    CONSTRAINT fk_pacientes_persona FOREIGN KEY (id_persona)
        REFERENCES personas(id_persona)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

-- Tabla: historias_clinicas
CREATE TABLE IF NOT EXISTS historias_clinicas (
    id_historia_clinica INT AUTO_INCREMENT PRIMARY KEY,
    id_paciente INT NOT NULL,
    numero_legajo VARCHAR(30) NOT NULL,
    fecha_apertura DATE NOT NULL,
    grupo_sanguineo VARCHAR(10) NULL,
    alergias TEXT NULL,
    antecedentes_medicos TEXT NULL,
    estado_activo TINYINT(1) DEFAULT 1 NOT NULL,
    CONSTRAINT uq_historias_paciente UNIQUE (id_paciente),
    CONSTRAINT uq_historias_legajo UNIQUE (numero_legajo),
    CONSTRAINT fk_historias_paciente FOREIGN KEY (id_paciente)
        REFERENCES pacientes(id_paciente)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

-- Tabla: evoluciones_clinicas
CREATE TABLE IF NOT EXISTS evoluciones_clinicas (
    id_evolucion INT AUTO_INCREMENT PRIMARY KEY,
    id_historia_clinica INT NOT NULL,
    id_odontologo INT NOT NULL,
    fecha_hora DATETIME NOT NULL,
    pieza_dental VARCHAR(20) NULL,
    diagnostico TEXT NOT NULL,
    tratamiento_realizado TEXT NOT NULL,
    observaciones TEXT NULL,
    CONSTRAINT fk_evoluciones_historia FOREIGN KEY (id_historia_clinica)
        REFERENCES historias_clinicas(id_historia_clinica)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_evoluciones_odontologo FOREIGN KEY (id_odontologo)
        REFERENCES odontologos(id_odontologo)
        ON UPDATE CASCADE ON DELETE RESTRICT
);

-- Tabla: turnos
CREATE TABLE IF NOT EXISTS turnos (
    id_turno INT AUTO_INCREMENT PRIMARY KEY,
    id_paciente INT NOT NULL,
    id_odontologo INT NOT NULL,
    fecha DATE NOT NULL,
    hora_inicio TIME NOT NULL,
    hora_fin TIME NOT NULL,
    motivo_consulta VARCHAR(255) NOT NULL,
    estado ENUM('Asignado', 'Reprogramado', 'Cancelado', 'Atendido') DEFAULT 'Asignado' NOT NULL,
    CONSTRAINT fk_turnos_paciente FOREIGN KEY (id_paciente)
        REFERENCES pacientes(id_paciente)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_turnos_odontologo FOREIGN KEY (id_odontologo)
        REFERENCES odontologos(id_odontologo)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    INDEX idx_turnos_odontologo_fecha (id_odontologo, fecha),
    INDEX idx_turnos_paciente (id_paciente)
);

-- =============================================================================
-- SECCIÓN INSERCIÓN
-- =============================================================================

USE cocab_db;

-- Inserción de Personas
INSERT INTO personas (id_persona, dni, nombre, apellido, telefono, email, direccion) VALUES
(1, '32111222', 'Ana', 'Martínez', '2214112233', 'ana.martinez@cocab.com', 'Calle 7 N° 850, La Plata'),
(2, '18456789', 'Carlos Alberto', 'Bencini', '2214998877', 'dr.bencini@cocab.com', 'Calle 50 N° 1240, La Plata'),
(3, '27333444', 'Mariana', 'Gómez', '2214556677', 'dra.gomez@cocab.com', 'Diag. 74 N° 320, La Plata'),
(4, '30123456', 'Juan', 'Pérez', '2214567890', 'juan.perez@email.com', 'Calle 12 N° 450, La Plata'),
(5, '28654987', 'María', 'Gómez', '2215678901', 'maria.gomez@email.com', 'Calle 60 N° 110, La Plata'),
(6, '35789123', 'Lucas', 'Rodríguez', '2216789012', 'lucas.rod@email.com', 'Calle 13 N° 920, La Plata'),
(7, '38999888', 'Florencia', 'Díaz', '2217890123', 'flor.diaz@email.com', 'Calle 45 N° 340, La Plata');

-- Inserción de Usuarios
-- (Claves hash simuladas para constraseñas 'admin123' y 'bencini2026')
INSERT INTO usuarios (id_persona, nombre_usuario, clave, rol, estado_activo) VALUES
(1, 'admin_secretaria', '$2a$12$e8Yf.uW8i5Y9Z...', 'Secretaria', 1),
(2, 'dr_bencini', '$2a$12$k1Lm.oP4q7R2S...', 'Odontologo', 1);

-- Inserción de Secretaria
INSERT INTO secretarias (id_persona, legajo_empleado) VALUES
(1, 'SEC-2026-01');

-- Inserción de Odontólogos
INSERT INTO odontologos (id_odontologo, id_persona, matricula, especialidad, estado_activo) VALUES
(1, 2, 'MAT-14258', 'Cirugía e Implantes', 1),
(2, 3, 'MAT-18965', 'Ortodoncia y Endodoncia', 1);

-- Inserción de Pacientes
INSERT INTO pacientes (id_paciente, id_persona, fecha_nacimiento, obra_social, estado_activo) VALUES
(1, 4, '1984-05-15', 'OSDE', 1),
(2, 5, '1981-11-20', 'Swiss Medical', 1),
(3, 6, '1991-03-08', 'IOMA', 1),
-- Paciente recién registrado (sin Historia Clínica)
(4, 7, '1995-07-22', 'Particular', 1);

-- Inserción de Historias Clínicas (Pacientes 1, 2 y 3)
INSERT INTO historias_clinicas (id_historia_clinica, id_paciente, numero_legajo, fecha_apertura, grupo_sanguineo, alergias, antecedentes_medicos, estado_activo) VALUES
(1, 1, 'HC-2026-0001', '2026-03-10', '0+', 'Penicilina', 'Hipertensión arterial controlada', 1),
(2, 2, 'HC-2026-0002', '2026-04-15', 'A+', 'Ninguna', 'Sin antecedentes de relevancia', 1),
(3, 3, 'HC-2026-0003', '2026-06-01', 'B+', 'Ibuprofeno', 'Asma bronquial leve', 1);

-- Inserción de Evoluciones Clínicas Cronológicas (Inmutables)
INSERT INTO evoluciones_clinicas (id_historia_clinica, id_odontologo, fecha_hora, pieza_dental, diagnostico, tratamiento_realizado, observaciones) VALUES
-- Evoluciones Paciente 1 (Dr. Bencini)
(1, 1, '2026-03-15 10:15:00', '1.8', 'Caries profunda con compromiso pulpar', 'Apertura de cámara y colocación de cura provisional', 'Se indica analgésico cada 8 hs.'),
(1, 1, '2026-03-22 11:00:00', '1.8', 'Tratamiento de conducto concluido', 'Obturación de conductos con gutapercha y sellado coronal', 'Control clínico a los 15 días.'),
-- Evoluciones Paciente 2 (Dr. Bencini y Dra. Gómez)
(2, 1, '2026-04-20 09:30:00', '4.6', 'Fractura de cúspide lingual', 'Reconstrucción con resina de fotocurado y pulido oclusal', 'Buena adaptación marginal.'),
(2, 2, '2026-05-18 16:00:00', 'General', 'Gingivitis marginal generalizada', 'Profilaxis mecánica y tartrectomía supragingival', 'Instrucción de técnica de cepillado.'),
-- Evolución Paciente 3 (Dra. Gómez)
(3, 2, '2026-06-10 14:30:00', 'General', 'Maloclusión Clase II', 'Instalación de aparatología fija multibrackets arco superior', 'Control de activación en 30 días.');

-- Inserción de 8 Turnos de Agenda (Simulación de Turnos Asignados, Reprogramados y Cancelados)
INSERT INTO turnos (id_turno, id_paciente, id_odontologo, fecha, hora_inicio, hora_fin, motivo_consulta, estado) VALUES
-- Agenda 28/09/2026 - Dr. Bencini (id_odontologo: 1)
(1, 1, 1, '2026-09-28', '09:00:00', '09:30:00', 'Limpieza y control post-operatorio', 'Asignado'),
(2, 2, 1, '2026-09-28', '10:00:00', '10:30:00', 'Evaluación por dolor en molar', 'Asignado'),
(3, 3, 1, '2026-09-28', '10:30:00', '11:00:00', 'Extracción de resto radicular', 'Asignado'),
(4, 4, 1, '2026-09-28', '11:00:00', '11:30:00', 'Primera consulta y diagnóstico general', 'Asignado'),
-- Agenda 28/09/2026 - Dra. Gómez (id_odontologo: 2)
(5, 1, 2, '2026-09-28', '09:30:00', '10:00:00', 'Control de ortodoncia', 'Asignado'),
(6, 2, 2, '2026-09-28', '10:00:00', '10:30:00', 'Ajuste de brackets', 'Cancelado'),
(7, 3, 2, '2026-09-28', '11:00:00', '11:30:00', 'Consulta por dolor articular', 'Reprogramado'),
-- Agenda 29/09/2026 - Dr. Bencini (id_odontologo: 1)
(8, 4, 1, '2026-09-29', '09:00:00', '09:30:00', 'Apertura de historia clínica y plan de tratamiento', 'Asignado');

-- =============================================================================
-- SECCIÓN CONSULTAS
-- =============================================================================

-- Consulta 1: Consulta de Agenda Diaria de un Profesional
SELECT 
    t.id_turno,
    t.hora_inicio,
    t.hora_fin,
    CONCAT(per_pac.apellido, ', ', per_pac.nombre) AS paciente,
    per_pac.dni AS dni_paciente,
    pac.obra_social,
    t.motivo_consulta,
    t.estado
FROM turnos t
INNER JOIN pacientes pac ON t.id_paciente = pac.id_paciente
INNER JOIN personas per_pac ON pac.id_persona = per_pac.id_persona
WHERE t.id_odontologo = 1 
  AND t.fecha = '2026-09-28'
ORDER BY t.hora_inicio ASC;


--Consulta 2: Verificación de Superposición Horaria en Agenda
SELECT COUNT(*) AS turnos_en_conflicto
FROM turnos
WHERE id_odontologo = 1
  AND fecha = '2026-09-28'
  AND estado != 'Cancelado'
  AND (
  -- Intervalo en superposición
      (hora_inicio < '10:30:00' AND hora_fin > '10:00:00')
  );

-- Consulta 3: Recuperación Cronológica del Historial Clínico Completo
SELECT 
    hc.numero_legajo,
    CONCAT(p_pac.apellido, ', ', p_pac.nombre) AS paciente,
    hc.grupo_sanguineo,
    hc.alergias,
    hc.antecedentes_medicos,
    ev.fecha_hora,
    ev.pieza_dental,
    ev.diagnostico,
    ev.tratamiento_realizado,
    ev.observaciones,
    CONCAT(p_odo.apellido, ', ', p_odo.nombre) AS odontologo_actuante,
    odo.matricula
FROM historias_clinicas hc
INNER JOIN pacientes pac ON hc.id_paciente = pac.id_paciente
INNER JOIN personas p_pac ON pac.id_persona = p_pac.id_persona
LEFT JOIN evoluciones_clinicas ev ON hc.id_historia_clinica = ev.id_historia_clinica
LEFT JOIN odontologos odo ON ev.id_odontologo = odo.id_odontologo
LEFT JOIN personas p_odo ON odo.id_persona = p_odo.id_persona
WHERE pac.id_paciente = 1
ORDER BY ev.fecha_hora ASC;

-- Consulta 4: Reprogramación de Turno Existente
UPDATE turnos 
SET fecha = '2026-09-30',
    hora_inicio = '15:00:00',
    hora_fin = '15:30:00',
    estado = 'Reprogramado'
WHERE id_turno = 7;

-- Consulta 5: Baja Lógica de Paciente
UPDATE pacientes 
SET estado_activo = 0 
WHERE id_paciente = 4;

-- Consulta 6: Baja Física de Turno por Anulación / Error de Carga
DELETE FROM turnos 
WHERE id_turno = 8;