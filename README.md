# AmiDog

AmiDog es una aplicación web de agenda y gestión de clientes para **una clínica
veterinaria, un profesional y una única cuenta administradora**. Este repositorio
contiene la aplicación cohesiva: frontend React/Vite, backend Spring Boot,
persistencia PostgreSQL con Flyway, pruebas automatizadas, aceptación en
Chromium, colección Bruno y documentación operativa.

La funcionalidad descrita aquí fue verificada localmente. El paquete **no**
incluye ni afirma haber desplegado dominio, hosting, base de datos administrada,
proxy HTTPS, proveedor SMTP ni monitoreo de producción; esos recursos y sus
credenciales deben ser suministrados y operados externamente.

## Alcance del producto

### Capacidades implementadas para clientes

- registro con correo y contraseña, verificación del correo, reenvío de
  verificación, recuperación y restablecimiento de contraseña;
- sesión persistida en servidor, perfil del cliente y mascotas activas o
  archivadas;
- catálogo de servicios y disponibilidad real de la clínica;
- reserva autenticada de una o varias mascotas, eligiendo un servicio por cada
  mascota;
- consulta, reprogramación y cancelación de reservas con historial conservado;
- notificaciones dentro de la aplicación, incluidos recordatorios idempotentes
  de citas confirmadas;
- página de contacto con formulario de correo validado y protegido contra abuso,
  más enlace directo `wa.me` al WhatsApp configurado de la clínica.

El formulario envía por SMTP una consulta a la clínica y una confirmación al
remitente. WhatsApp abre el canal externo; AmiDog no recibe ni sincroniza esa
conversación.

### Capacidades implementadas para la administración única

- panel con agenda, indicadores y búsquedas paginadas;
- gestión de clientes y mascotas sin borrar su historial;
- alta, edición y archivado protegido de servicios;
- horarios semanales flexibles y bloqueos completos o parciales;
- consulta y gestión del ciclo de vida de las reservas;
- notificaciones administrativas y administración del estado activo de cuentas.

### Exclusiones explícitas

AmiDog no implementa historias o fichas clínicas, registros médicos, recetas,
diagnósticos, ecommerce, chat o mensajería interna, relay/API/webhook de
WhatsApp, jerarquías de múltiples administradores, veterinarios, recepcionistas
o personal, ni autenticación con Google. Tampoco elimina físicamente el
historial de reservas.

Google OAuth es una opción futura intencional: el modelo de cuentas ya separa
identidad local e identidades externas para facilitar una integración posterior,
pero **no existe un botón, endpoint ni flujo Google OAuth implementado** en esta
entrega.

## Arquitectura

El sistema es un monolito modular con un frontend independiente en desarrollo:

```text
Navegador React 19 + Vite 8
        │  /api/v1, cookies de sesión y CSRF
        ▼
Spring MVC/REST + Spring Security + Spring Session JDBC
        │  controladores → servicios/dominio → repositorios
        ▼
PostgreSQL 17 + Flyway V1–V9

Spring Mail ──SMTP──► Mailpit local o proveedor SMTP de producción
```

- **React/Vite** sirve las vistas públicas, de cuenta, cliente y administración.
- **Spring MVC/REST** aplica validación, autorización, reglas de negocio,
  límites de uso, sesiones y protección CSRF.
- **PostgreSQL 17** es la autoridad de datos y de exclusión de horarios
  solapados; Spring Session también persiste allí.
- **Flyway** es la única autoridad del esquema. Hibernate usa
  `ddl-auto=validate` y no crea tablas.
- **Mailpit** captura localmente los correos SMTP de verificación, recuperación y
  contacto; no es infraestructura de producción.

El contrato de rutas, cargas, estados y errores está en
[docs/api.md](docs/api.md), y las decisiones de dominio en el
[diseño de la plataforma](docs/superpowers/specs/2026-07-28-amidog-booking-platform-design.md).

## Requisitos

- Windows PowerShell 5.1 o PowerShell 7 para los scripts de entrega;
- Java 21;
- Node.js en una rama admitida por el toolchain bloqueado: 22.22.2 o posterior
  dentro de 22.x, 24.15.0 o posterior dentro de 24.x, o 26.0.0 o posterior, y
  npm. Node.js 23.x y 25.x no son compatibles;
- Docker Desktop u otro daemon compatible con Docker Compose;
- puertos locales libres `5432`, `1025`, `8025`, `8080` y `5173` para el flujo
  completo y E2E.

El Maven Wrapper viene incluido; no hace falta instalar Maven por separado. El
daemon Docker también es obligatorio para `mvnw.cmd test`, porque las pruebas
de integración autoritativas usan PostgreSQL 17 mediante Testcontainers.

## Configuración local desde `.env.example`

Desde la raíz del proyecto, copie la plantilla:

```powershell
Copy-Item .env.example .env
```

La copia directa es suficiente para el PostgreSQL y Mailpit incluidos después de
`docker compose up -d --wait`; no requiere reemplazos manuales para el perfil
local. `.env` está excluido de la entrega y nunca debe contener valores que se
copien a fuentes, documentación, frontend o ZIP. Todos los valores mostrados,
incluidos la cuenta administradora y las contraseñas, son identidades de ejemplo
exclusivamente locales: reemplácelos y use almacenamiento de secretos antes de
cualquier despliegue de producción.

```properties
DB_URL=jdbc:postgresql://localhost:5432/amidog
DB_USERNAME=amidog
DB_PASSWORD=amidog-local

ADMIN_EMAIL=admin.local@example.test
ADMIN_PASSWORD=amidog-local-admin-only
ADMIN_NAME=Administradora Local AmiDog
ADMIN_PHONE=+56900000000

FRONTEND_BASE_URL=http://localhost:5173
FRONTEND_ORIGIN=http://localhost:5173
SESSION_COOKIE_SECURE=false
CLINIC_TIMEZONE=America/Santiago

BOOKING_AUTO_CONFIRM=false
BOOKING_DURATION_MINUTES=30
BOOKING_MIN_NOTICE_HOURS=2
BOOKING_HORIZON_DAYS=90

NOTIFICATION_REMINDERS_ENABLED=true
NOTIFICATION_REMINDER_WINDOW_START_HOURS=23
NOTIFICATION_REMINDER_WINDOW_END_HOURS=25
NOTIFICATION_REMINDER_BATCH_SIZE=100
NOTIFICATION_REMINDER_CRON=0 5 * * * *
TOKEN_CLEANUP_CRON=0 30 3 * * *

EMAIL_DELIVERY=smtp
EMAIL_RECONCILIATION_DELAY_MS=30000
SMTP_HOST=localhost
SMTP_PORT=1025
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_AUTH=false
SMTP_STARTTLS=false
SMTP_STARTTLS_REQUIRED=false
SMTP_SSL=false
SMTP_ALLOW_PLAINTEXT=true
CLINIC_EMAIL=contacto.local@example.test

WHATSAPP_NUMBER=56900000000
VITE_WHATSAPP_NUMBER=56900000000
```

`VITE_WHATSAPP_NUMBER` es configuración pública incorporada durante el build,
no un secreto. Debe usar solo dígitos en formato internacional, sin `+`, espacios
ni puntuación. `WHATSAPP_NUMBER` queda reservado en la configuración backend; el
enlace React actual utiliza `VITE_WHATSAPP_NUMBER`.

La matriz completa de variables, validaciones y defaults está en
[docs/configuration.md](docs/configuration.md). La plantilla trae un perfil
local ejecutable, no credenciales reales ni valores aptos para producción.

## Inicio local y URLs

1. Inicie PostgreSQL y Mailpit, esperando que estén disponibles:

   ```powershell
   docker compose up -d --wait
   ```

2. Inicie el backend desde una terminal con las variables de `.env` disponibles
   en el entorno del proceso:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

3. En otra terminal, instale exactamente el lockfile e inicie Vite:

   ```powershell
   npm ci
   npm run dev
   ```

URLs locales:

| Servicio | URL/puerto |
| --- | --- |
| Frontend | `http://localhost:5173` |
| Backend API | `http://localhost:8080/api/v1` |
| Mailpit web | `http://localhost:8025` |
| Mailpit SMTP | `localhost:1025` |
| PostgreSQL | `localhost:5432` |

Vite reenvía `/api` al backend sin reescribir la ruta. Los adaptadores del
frontend usan URLs relativas, cookies y `credentials: include`; el backend debe
estar activo para registro, contacto, reservas y administración. La base local
vive en el volumen nombrado `amidog-postgres`. No ejecute
`docker compose down -v` durante el uso o las pruebas normales.

## Cuentas, correo y seguridad

### Verificación y contraseñas

El registro crea una cuenta cliente no verificada y envía un enlace de
verificación. Los enlaces de verificación y recuperación se construyen con
`FRONTEND_BASE_URL`; los tokens son breves, de un solo uso y solo sus hashes se
persisten. El envío usa una outbox durable y no registra tokens, enlaces,
contraseñas ni credenciales SMTP.

Con Mailpit local, abra `http://localhost:8025`, seleccione el mensaje más
reciente para la dirección creada y siga el enlace entregado. El mismo flujo se
usa para copiar el token de restablecimiento cuando se ejecuta la colección
Bruno.

### Sesión y CSRF

Spring Security guarda la sesión en PostgreSQL y la representa con la cookie
HttpOnly `SESSION`. Las mutaciones `POST`, `PUT`, `PATCH` y `DELETE`, incluidos
login y logout, deben:

1. obtener `GET /api/v1/auth/csrf`;
2. conservar las cookies `XSRF-TOKEN` y `SESSION`;
3. devolver el token en el nombre de header informado por el servidor.

El login rota el token CSRF, por lo que hay que solicitarlo otra vez después de
autenticarse. En producción se requieren HTTPS, origen exacto revisado,
`SESSION_COOKIE_SECURE=true` y tratamiento confiable de headers del proxy.

### Bootstrap de administración única

No existe registro público de administración. En el primer arranque, una pareja
completa `ADMIN_EMAIL`/`ADMIN_PASSWORD` crea idempotentemente la única cuenta
administradora; valores vacíos hacen que el bootstrap no opere. Arranques
posteriores no duplican la cuenta y cambiar `ADMIN_PASSWORD` no rota el hash ya
persistido. Después del alta verificada, retire el secreto de bootstrap y rote
la contraseña mediante el flujo soportado de recuperación.

## Reglas de agenda y reservas

- Se necesita una cuenta cliente verificada y autenticada para reservar.
- Cada reserva contiene entre 1 y 10 mascotas y exactamente un servicio por
  mascota; todas comparten el mismo horario.
- Toda reserva ocupa **exactamente 30 minutos**. Otro valor de
  `BOOKING_DURATION_MINUTES` hace fallar la configuración.
- `PENDING` y `CONFIRMED` ocupan la agenda. La exclusión PostgreSQL impide la
  doble reserva incluso bajo concurrencia; una colisión se devuelve de forma
  controlada.
- Con `BOOKING_AUTO_CONFIRM=false`, recomendado y predeterminado, las reservas
  nuevas y las reprogramadas por el cliente quedan `PENDING`. Con `true` quedan
  `CONFIRMED`. La reprogramación administrativa conserva el estado ocupado.
- La zona es `America/Santiago`, con reglas IANA y `timestamptz`; nunca se fija
  manualmente UTC-3 o UTC-4.
- La administración define horarios semanales flexibles y bloqueos completos o
  parciales. El aviso mínimo y horizonte son configurables.
- Confirmación, reprogramación, cancelación, finalización y no-asistencia dejan
  eventos de historial. Cancelar conserva la reserva como `CANCELLED` y libera
  el horario.
- Solo citas actualmente `CONFIRMED` dentro de la ventana configurada reciben
  recordatorios internos; la clave de deduplicación hace idempotente el barrido.

## Verificación y pruebas

El comando de entrega predeterminado ejecuta secuencialmente, con detención en
el primer error: backend, instalación npm limpia, frontend, lint, build frontend
y package backend.

```powershell
.\scripts\verify.ps1
```

Para incluir el gate real de navegador, PostgreSQL y Mailpit deben poder usar
los puertos locales indicados:

```powershell
.\scripts\verify.ps1 -IncludeE2E
```

`-IncludeE2E` ejecuta **todos** los gates predeterminados, valida Docker y
Compose, arranca `postgres` y `mailpit` con `up -d --wait` sin borrar su volumen,
asegura el Chromium requerido por Playwright y finalmente ejecuta
`npm run test:e2e`.

Línea base autoritativa aceptada al cerrar Task 6:

| Gate | Resultado |
| --- | --- |
| Backend Maven | 404/404, 0 fallos, 0 errores, 0 omitidos |
| Frontend Vitest | 388/388 en 38 archivos |
| Playwright Chromium | 5/5 flujos reales |
| ESLint | exit 0 |
| Vite build | 168 módulos, exit 0 |
| Maven package | `BUILD SUCCESS` |

Los gates individuales siguen disponibles:

```powershell
.\mvnw.cmd test
npm test
npm run lint
npm run build
npm run test:e2e
.\mvnw.cmd -DskipTests package
```

Las pruebas focalizadas de seguridad, rutas y fail-fast de los scripts son:

```powershell
.\scripts\tests\release-scripts.Tests.ps1
```

Consulte [docs/testing.md](docs/testing.md) para requisitos, pruebas
PostgreSQL/Testcontainers, artefactos Playwright y diagnóstico.

## Colección Bruno y flujo Mailpit

Abra `bruno/amidog/` como colección en Bruno, elija el environment `local` y
mantenga habilitadas las cookies. El workflow tiene 62 requests ordenadas y
stateful; ejecute sus números de secuencia, no **Run all**.

Hay dos pausas deliberadas:

1. después de registro/reenvío, abra Mailpit, copie solo el token opaco del
   enlace del correo más reciente y reemplace `verificationToken`;
2. después de solicitar recuperación, copie el token del nuevo correo y
   reemplace `resetToken`.

Los tokens expiran, son de un uso y nunca deben guardarse en el repositorio. La
colección captura IDs y timestamps de respuestas reales, refresca CSRF después
de cada login y limpia/archiva los datos que admite el flujo. Instrucciones
completas: [bruno/amidog/README.md](bruno/amidog/README.md).

## Empaquetado de entrega

```powershell
.\scripts\package.ps1
```

El script resuelve rutas desde su propia ubicación, copia solo fuentes,
configuración, migraciones, pruebas, E2E, Bruno, documentos, wrappers y assets,
audita el ZIP y reemplaza exclusivamente
`..\..\outputs\amidog-complete.zip`. Si ya existe una entrega, instala la nueva
con reemplazo atómico en el mismo volumen y un backup temporal validado; ante
una falla conserva o restaura la entrega anterior antes de limpiar solamente
sus candidatos propios. El archivo contiene un único directorio
superior `amidog-complete/` y excluye `.env`, `.superpowers`, Git,
`node_modules`, `target`, `dist`, `output`, reportes, logs, bases temporales,
secretos por nombre y ZIP previos. Otros archivos del directorio `outputs`
permanecen intactos.

## Checklist para producción

Antes de publicar, un operador debe:

- contratar/provisionar hosting, dominio, DNS, PostgreSQL 17 administrado o
  equivalente, almacenamiento de backups y gestión de secretos;
- suministrar credenciales externas de base, bootstrap inicial y SMTP; no están
  incluidas en la entrega;
- contratar y costear un proveedor SMTP real, validar dominio remitente, SPF,
  DKIM y DMARC, monitorear rebotes y probar entrega. Mailpit no reemplaza ese
  servicio y los costos/cuotas dependen del proveedor;
- desplegar bajo HTTPS y un proxy confiable, usar el mismo origen cuando sea
  posible, configurar `FRONTEND_BASE_URL`, `FRONTEND_ORIGIN` y
  `SESSION_COOKIE_SECURE=true`, y descartar forwarding headers aportados por el
  cliente;
- restringir PostgreSQL a red privada, mantener Flyway como única autoridad y
  detener el despliegue ante checksum, permisos o validación JPA fallidos;
- tomar un backup consistente, registrar checksum y demostrar una restauración
  aislada antes de migrar. Las migraciones V1–V9 son inmutables;
- configurar logs sin secretos, métricas/alertas, disponibilidad, uso de disco,
  errores de correo, backups, expiración TLS y respuesta operativa;
- ejecutar verificación, smoke tests de sesión/CSRF/roles, enlaces de correo,
  contacto, agenda y rollback antes de habilitar tráfico.

No existe ni se ejecutó una migración destructiva V10. Las tablas heredadas se
conservan como evidencia de recuperación; un V10 futuro requiere backup,
exportación separada, restauración verificada, ensayo en staging, aprobación del
propietario y revisión de un segundo operador. Consulte el
[runbook de despliegue](docs/deployment.md).

## Estructura final y documentación

```text
.
├── .mvn/                         Maven Wrapper
├── bruno/amidog/                 colección API y environment local
├── docs/
│   ├── api.md                    contrato REST/errores/rate limits
│   ├── configuration.md          matriz de variables
│   ├── deployment.md             producción, backup, restore y rollback
│   ├── testing.md                estrategia y operación de pruebas
│   └── superpowers/              diseño y planes de implementación
├── e2e/                          aceptación Playwright/Chromium
├── public/                       assets públicos
├── scripts/
│   ├── verify.ps1                verificación secuencial de una orden
│   ├── package.ps1               ZIP allowlisted y autoauditado
│   ├── lib/package-archive.ps1   auditoría y publicación atómica del ZIP
│   └── tests/                    pruebas focalizadas de los scripts
├── src/
│   ├── main/java/                backend por capas y módulos
│   ├── main/resources/           configuración y migraciones Flyway
│   ├── test/                     frontend y soporte Vitest
│   ├── test/java/                pruebas backend
│   └── …                         frontend React, estilos y assets
├── .env.example                 plantilla sin secretos reales
├── compose.yaml                 PostgreSQL 17 + Mailpit local
├── package.json / package-lock.json
├── playwright.config.js
└── pom.xml / mvnw / mvnw.cmd
```

Documentos de referencia:

- [API v1](docs/api.md)
- [Configuración](docs/configuration.md)
- [Despliegue y operaciones](docs/deployment.md)
- [Pruebas](docs/testing.md)
- [Diseño de booking y administración](docs/superpowers/specs/2026-07-28-amidog-booking-platform-design.md)
- [Plan de contact, hardening y delivery](docs/superpowers/plans/2026-07-28-amidog-contact-hardening-delivery.md)
- [Roadmap de entrega](docs/superpowers/plans/2026-07-28-amidog-delivery-roadmap.md)
