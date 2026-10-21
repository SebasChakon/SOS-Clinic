# Informe de ejecución — claves, alta aprobada, recuperación y 2FA

- **Fecha:** 2026-10-21
- **Rama:** `feature/claves-seguras`
- **Alcance:** política de claves, alta con aprobación, activación/recuperación por enlace, 2FA con *challenge*, cambio obligatorio, cuenta propia, cambio de correo, migración y frontend asociado.
- **Entorno de ejecución:** PostgreSQL 16 local; Eureka `8761`; `ms-seguridad` `8081`; gateway `8080`; frontend `5500`; JDK del proyecto; Maven Wrapper.
- **Datos:** cuentas temporales con prefijo `e2e.` creadas y eliminadas por el script. No se enviaron correos a direcciones reales.

## Resultados

### Pruebas unitarias: 33/33 correctas

Comando:

```powershell
cd ms-seguridad
.\mvnw.cmd -Dtest=PoliticaClavesTest,JwtServiceTest,LoginAttemptServiceTest,PlantillasCorreoTest,RecuperacionClaveServiceTest,AuthServiceTest test
```

| Clase | Pruebas | Resultado |
|---|---:|---|
| `PoliticaClavesTest` | 9 | ✅ |
| `JwtServiceTest` | 3 | ✅ |
| `LoginAttemptServiceTest` | 2 | ✅ |
| `PlantillasCorreoTest` | 4 | ✅ |
| `RecuperacionClaveServiceTest` | 7 | ✅ |
| `AuthServiceTest` | 8 | ✅ |
| **Total** | **33** | **✅** |

`MsSeguridadApplicationTests` y `EnvResolucionTest` no forman parte de esta ejecución porque requieren `.env` y una base de datos completa; el contexto se validó al arrancar `ms-seguridad` contra PostgreSQL real.

### Pruebas E2E por gateway: 47/47 correctas

Script: `seguridad/pruebas/e2e_claves.py`

Comando:

```powershell
python seguridad/pruebas/e2e_claves.py
```

Cobertura ejecutada:

| Bloque | Comprobaciones | Resultado |
|---|---:|---|
| Perfiles requeridos | 1 | ✅ |
| Política pública por gateway | 1 | ✅ |
| Login admin/seguridad/normal + perfil + mi cuenta | 5 | ✅ |
| Cambio de clave voluntario y política | 4 | ✅ |
| Clave temporal y bloqueo `ccp` en gateway | 4 | ✅ |
| 2FA con *challenge*: válido, reutilizado, inválido y propósito | 4 | ✅ |
| Recuperación: válida, expirada, usada y reutilizada | 7 | ✅ |
| Activación: política y clave válida | 4 | ✅ |
| Cambio de correo confirmado en BD | 2 | ✅ |
| Alta pendiente y doble control | 5 | ✅ |
| Clave temporal generada por admin | 4 | ✅ |
| Rechazo de cuenta | 3 | ✅ |
| Autorización sin token y auditoría | 2 | ✅ |
| Login final de cuenta aprobada | 1 | ✅ |
| **Total** | **47** | **✅** |

El script limpia las cuentas `e2e.%` al inicio y al final. La base quedó con **15 usuarios**: los 14 originales más la cuenta de bootstrap `seguridad.inicial`; no quedan cuentas `e2e.%`.

### Migración y base de datos

- `schema-postgres.sql` actualizado y probado en PostgreSQL 16 limpio: la aplicación arranca con `ddl-auto=validate`.
- `infra/migraciones/2026-10-21-claves.sql` aplicada sobre una copia de la base existente y sobre la base local:
  - columnas de aprobación, cambio obligatorio y correo pendiente;
  - tabla `token_recuperacion` con índices únicos;
  - `challenge_hash` y `proposito` en `codigo_verificacion`;
  - perfil `seguridad` creado si faltaba y actividad `aprobar_cuentas` asignada;
  - usuarios existentes marcados como activados y 2FA forzado para perfiles privilegiados.
- Migración reejecutada sin errores ni duplicados.
- Backup previo a la migración conservado fuera del repositorio, en el directorio temporal del entorno.

### Frontend

Verificación manual en navegador:

- `login.html`: enlace de recuperación y formulario de login correctos.
- `recuperar-clave.html`: mensaje genérico y layout correctos.
- `restablecer-clave.html`: estado de enlace inválido y mensaje de error correctos.
- Login real contra el gateway: redirección a `aprobaciones.html` y carga de datos de la cuenta.
- `cuenta.html`: datos de la cuenta y formularios de clave/2FA/correo renderizados.
- Sin errores de JavaScript en las páginas revisadas.

## Limitaciones de esta ejecución

1. **Correo real:** las pruebas E2E usaron tokens y códigos sembrados en la base y direcciones `example.test`; no se verificó la entrega real por Gmail. Las plantillas se validaron con `PlantillasCorreoTest` y el servicio arrancó con el SMTP configurado. Para CI se recomienda Mailpit.
2. **2FA de login completo:** se validó `verificar-2fa` con un código sembrado. El envío del código durante el login está cubierto por `AuthServiceTest`; falta una prueba de integración con buzón real/Mailpit.
3. **UI automatizada:** no se añadió Playwright; la verificación de interfaz fue manual y con el navegador del entorno.
4. **Carga, estrés y penetración:** fuera del alcance.
5. **Rate limit en memoria:** se reinicia con el servicio; está documentado como limitación del prototipo.

## Observaciones

- Las cuentas `esc8656` y `tester.admin` tienen correo `example.local` y quedaron con 2FA obligatorio; no pueden recibir códigos. `juan.ocampo` tiene un correo Gmail válido. Para operar, usa `juan.ocampo` o aplica un cambio temporal controlado de `requiere_2fa` a una cuenta de prueba y reactívalo después.
- La cuenta de bootstrap `seguridad.inicial` no se usó en las pruebas automatizadas; su clave temporal está en un archivo fuera del repositorio y debe cambiarse y borrarse.
- No se encontraron defectos bloqueantes en los casos ejecutados.

## Conclusión

Los flujos P0/P1 del alcance pasan: política, alta con doble control, activación, recuperación, 2FA con *challenge*, cambio obligatorio, cambio de correo, autorización por rol y migración. Se agregó el job `test:ms-seguridad` en `.gitlab-ci.yml` con las 33 pruebas unitarias. Para el E2E se recomienda un job nocturno con PostgreSQL y Mailpit; Playwright queda como mejora opcional.
