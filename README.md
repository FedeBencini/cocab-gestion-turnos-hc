# COCAB — Gestión de turnos e historias clínicas

Aplicación de escritorio para el Centro Odontológico COCAB, desarrollada como proyecto integrador de Seminario de Práctica de Informática (Universidad Siglo 21). Permite administrar pacientes, odontólogos, la agenda de turnos y las historias clínicas.

## Estado del prototipo

- Prototipo operacional en Java con interfaz JavaFX, arquitectura MVC y patrón DAO.
- **Persistencia en memoria** (`com.cocab.mock.MockDatabase`): la aplicación se ejecuta sin instalar ninguna base de datos y carga datos ficticios al iniciar. Los cambios se pierden al cerrarla.
- La conexión real a MySQL se implementará en la próxima entrega.

## Funcionalidades por rol

| Rol | Acceso |
| :--- | :--- |
| Secretaria | Alta, modificación y baja lógica de pacientes y odontólogos; agenda de turnos (alta, reprogramación y cancelación). Sin acceso a historias clínicas. |
| Odontólogo | Agenda propia en solo lectura; historias clínicas compartidas (consulta, apertura, actualización de la ficha y evoluciones inmutables firmadas por el autor de la sesión). |

Reglas principales:

- No se superponen turnos de un mismo odontólogo (franjas de 30 minutos, de 08:00 a 18:00).
- Las historias clínicas y sus evoluciones nunca se borran; las bajas son lógicas.
- El DNI se valida (7 u 8 dígitos) y los permisos se verifican al abrir cada vista y al ejecutar cada acción.
- Las contraseñas se guardan con PBKDF2 (JDK), nunca en texto plano.

## Cuentas de prueba

| Usuario | Contraseña | Rol |
| :--- | :--- | :--- |
| `admin` | `admin` | Secretaria |
| `odontologo` | `admin` | Odontólogo |

Son cuentas ficticias del modo en memoria; no deben usarse con datos reales.

## Arquitectura

```
Vista (FXML) -> Controlador JavaFX -> DAO (interfaz) -> implementación -> MockDatabase / MySQL (JDBC)
                                          ^
                                      Modelo (POO)
```

### Código fuente (`src/main/java/com/cocab`)

| Carpeta / paquete | Contenido |
| :--- | :--- |
| `com.cocab` (raíz) | `Launcher` (punto de entrada que invoca a `MainApp`) y `MainApp` (aplicación JavaFX que muestra `LoginView.fxml`). |
| `controller/` | Controladores JavaFX, uno por vista: `LoginController`, `MainShellController` (menú y navegación por rol), `PacientesController`, `OdontologosController`, `AgendaTurnosController`, `NuevoTurnoController` y `HistoriaClinicaController`. Incluye `EjecutorFx` (ejecuta las operaciones de datos fuera del hilo de la interfaz) y `AyudaVista` (fechas, máscaras y utilidades comunes de las vistas). |
| `model/` | Entidades del dominio, Java puro sin JavaFX, organizadas en subpaquetes (ver abajo). |
| `dao/` | Interfaces de acceso a datos: `PacienteDAO`, `OdontologoDAO`, `TurnoDAO`, `HistoriaClinicaDAO` y `UsuarioDAO`. |
| `dao/impl/` | Implementaciones de los DAO, respaldadas por `MockDatabase` en esta entrega. |
| `mock/` | `MockDatabase`: almacenamiento en memoria con datos ficticios (se reemplazará por conexión a base de datos en la siguiente entrega). |
| `exception/` | Excepciones propias: `ValidationException` (reglas de negocio y validaciones) y `DatabaseException` (fallos de acceso a datos). |
| `util/` | `UserSession` (sesión del usuario activo), `SeguridadClaves` (PBKDF2), `Validador` (validaciones reutilizables), `AlgoritmosClinica` (ordenamiento por inserción y búsqueda binaria), `DatosClinica` (catálogos fijos: franjas horarias, obras sociales, cabeceras de tablas) y `AlertHelper` (diálogos de aviso). |

Subpaquetes de `model/`:

| Subpaquete | Clases |
| :--- | :--- |
| `model/base` | `Persona` (clase abstracta con los datos comunes). |
| `model/paciente` | `Paciente`. |
| `model/odontologo` | `Odontologo`. |
| `model/administracion` | `Secretaria`. |
| `model/seguridad` | `Usuario` y `RolUsuario`. |
| `model/turno` | `Turno` y `EstadoTurno`. |
| `model/historia` | `HistoriaClinica` y `EvolucionClinica`. |

### Recursos (`src/main/resources`)

| Carpeta | Contenido |
| :--- | :--- |
| `views/` | Vistas FXML, cada una vinculada a su controlador: `LoginView`, `MainShellView` (contenedor con la barra de menús), `PacientesView`, `OdontologosView`, `AgendaTurnosView`, `NuevoTurnoView` y `HistoriaClinicaView`. |
| `css/` | `styles.css`, hoja de estilos común de la aplicación. |

Se usan `ArrayList` para las listas dinámicas y arreglos para los valores fijos del dominio (franjas horarias, obras sociales, cabeceras de tablas).

## Tecnologías y restricciones

- Java SE 17 (se puede construir con JDK 17 o 21).
- OpenJFX 17. MySQL Connector/J 8.0.33 se incorporará en la siguiente entrega.
- Sin frameworks (Spring, Hibernate, JPA, Lombok) ni pruebas automatizadas; solo Java estándar (y JDBC nativo en siguiente entrega).
- Maven se usa únicamente para construir.

## Compilar y ejecutar

Con JDK 17 o 21 y Maven 3.9 en el PATH, desde la raíz del proyecto:

```
homvn javafx:run
```

Durante el desarrollo también se puede iniciar ejecutando `com.cocab.Launcher` desde el IDE.

## Estructura del repositorio

```
src/main/java      Código fuente (paquetes descritos en Arquitectura)
src/main/resources Vistas FXML (views/) y estilos (css/)
```

## Antes de usar datos reales

El prototipo no está habilitado para datos reales: hasta la siguiente entrega, los datos viven solo en memoria y no hay persistencia ni controles de seguridad propios de una base de datos. La autoría de una evolución clínica no equivale a una firma digital certificada.
