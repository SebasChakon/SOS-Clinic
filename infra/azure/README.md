# Despliegue de SOS Clinic en Azure

Esta carpeta contiene el despliegue de demo para Azure. **No reemplaza el flujo local de Windows**: `infra/docker-compose.yml`, los `application.properties`, los scripts `00` a `05`, el esquema, las migraciones y el seed local siguen igual.

> Prototipo academico. Una sola VM, sin alta disponibilidad, sin WAF, sin revocacion centralizada de JWT y dependiente de Gmail/Groq. No lo uses con datos clinicos reales.

## Arquitectura

```text
Internet :443
  └── Caddy (red host, TLS Let's Encrypt, frontend + proxy /ms-*)
        └── ms-gateway 127.0.0.1:8080
              ├── eureka-server 127.0.0.1:8761
              ├── ms-seguridad 127.0.0.1:8081 ──┐
              ├── ms-pacientes 127.0.0.1:8082 ──┤── PostgreSQL 16
              ├── ms-triage    127.0.0.1:8083 ──┘   (127.0.0.1:5433)
              ├── ms-analitica 127.0.0.1:8084 ───── MySQL 8
              └── ms-ia-agente 127.0.0.1:8088        (127.0.0.1:3306)
                                                    └── Groq API
```

Decisiones clave:

- **Red host para las 7 aplicaciones y Caddy.** Los servicios siguen escuchando en `127.0.0.1`, Eureka sigue anunciando `127.0.0.1` y el gateway encuentra los servicios sin cambiar propiedades ni discovery.
- **PostgreSQL y MySQL en bridge**, publicados solo en loopback. Las aplicaciones en red host los ven en `localhost:5433` y `localhost:3306`.
- **Solo Caddy escucha en 0.0.0.0**: puertos 80/443. SSH (22) solo desde la IP administrativa. No se exponen 5433, 3306, 8080 ni 8761.
- **Caddy sirve el frontend y proxya `/ms-*`** al gateway. El navegador usa el mismo origen; por eso `connect-src 'self'` funciona y no hay CORS entre dominios.
- **Imagenes en ACR Basic**, descargadas por la VM con identidad administrada (`AcrPull`). ACR no usa usuario admin.
- **Backups a Blob Storage privado** con identidad administrada (`Storage Blob Data Contributor`).

Recursos que crea `main.bicep`:

| Recurso | Detalle |
|---|---|
| Grupo de recursos | `sos-clinic-rg` por defecto |
| VNet / NSG | `10.42.0.0/16`, subnet `10.42.1.0/24` |
| IP publica | Estatica, DNS `<label>.<region>.cloudapp.azure.com` |
| VM | Ubuntu 24.04, 2 vCPU/8 GB (`Standard_B2ms` o `Standard_D2as_v7`), disco 64 GB SSD |
| ACR | Basic, admin deshabilitado |
| Storage | Standard LRS, contenedor privado `sos-clinic-backups` |
| Identidad | System-assigned, roles AcrPull y Blob Data Contributor |
| Auto-shutdown | 22:00 hora Bogota (`SA Pacific Standard Time`) |
| Presupuesto | Alerta mensual (US$60 por defecto) |

## Requisitos

En el equipo desde el que despliegas:

- Windows 10/11 con PowerShell 5.1 o 7.
- [Azure CLI](https://learn.microsoft.com/cli/azure/install-azure-cli-windows): `winget install Microsoft.AzureCLI`.
- Docker Desktop iniciado.
- `ssh`, `scp`, `git` y `tar` (Windows 10/11 los incluye; `git` viene con Git for Windows).
- Sesion activa: `az login`.
- Acceso a la cuenta SMTP y al buzon `pruebasosclinic@gmail.com` para los codigos 2FA.
- Una API key de Groq vigente.

En Azure:

- Suscripcion con cuota para 2 vCPU en la region elegida. El script prueba `eastus2` y luego `brazilsouth`, con `Standard_B2ms`, `Standard_D2as_v5` o `Standard_D2as_v7`.
- Permiso para crear el grupo de recursos y asignar roles.

## Antes del primer despliegue

1. **Rota los secretos comprometidos.** El README principal explica como cambiar `MAIL_PASSWORD` y `GROQ_API_KEY`. Actualiza el `.env` local; el despliegue copia esos valores la primera vez.
2. **Haz commit de todo.** `deploy.ps1` usa `git archive HEAD`: la VM recibe el ultimo commit, no archivos sin commitear. Si acabas de crear esta carpeta:

   ```powershell
   git add .
   git commit -m "Infraestructura Azure para SOS Clinic"
   ```

3. **Confirma el acceso al buzon 2FA.** Sin `pruebasosclinic@gmail.com` no podras entrar como administrador ni como seguridad.
4. **Decide el DNS label.** Si no pasas `-DnsLabel`, el script genera uno unico. Si quieres uno fijo:

   ```powershell
   .\infra\azure\deploy.ps1 -DnsLabel sos-clinic-santi
   ```

5. **Opcional: define correo de alerta y fin del trial.**

   ```powershell
   .\infra\azure\deploy.ps1 `
     -AlertEmail tu-correo@ejemplo.com `
     -TrialEndDate 2026-11-24
   ```

## Despliegue

```powershell
az login
az account show
.\infra\azure\deploy.ps1
```

Si Windows bloquea el script:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\infra\azure\deploy.ps1
```

El script hace, en orden:

1. Valida `az`, Docker, SSH y la cuota de la VM.
2. Genera una llave SSH en `%USERPROFILE%\.ssh\sos_clinic_azure` si no existe.
3. Crea el grupo de recursos y despliega `main.bicep`.
4. Espera a cloud-init (Docker, Compose, Azure CLI, swap, systemd).
5. Construye las 7 imagenes con `infra/azure/Dockerfile.service` y las publica en ACR con un tag nuevo.
6. Genera `infra/azure/.env.azure` con secretos aleatorios para PostgreSQL, MySQL y JWT.
7. Sube el commit actual y el `.env` a `/opt/sos-clinic` (`.env` con permisos `600`, propietario `root`).
8. Levanta PostgreSQL/MySQL, aplica migraciones y seed canónico, y carga las 50.000 llegadas sinteticas.
9. Arranca el stack con `systemd`, verifica el seed por API y prueba HTTPS.
10. Descarga las credenciales del seed a `infra/azure/.credenciales.azure.md` (ignorado por Git).

La primera ejecucion tarda bastante: cloud-init instala Docker/Azure CLI, Maven descarga dependencias y MySQL carga 50.000 filas. Es normal que tarde 15-30 minutos.

### Opciones utiles

```powershell
# Reintentar build + subida + arranque sin recrear infraestructura
.\infra\azure\deploy.ps1 -SkipInfra

# Reutilizar las imagenes ya publicadas
.\infra\azure\deploy.ps1 -SkipInfra -SkipBuild

# Volver a sembrar PostgreSQL (borra datos de aplicacion) y MySQL
.\infra\azure\update.ps1 -Reseed -ReseedAnalytics

# Cambiar solo una imagen: actualiza el codigo, publica y reinicia
.\infra\azure\update.ps1

# Ver la URL, la VM y el estado sin arrancar nada
Get-Content .\infra\azure\.deployment.json
```

`-SkipInfra` exige que exista `infra/azure/.deployment.json`. Si quieres crear otro despliegue, mueve o elimina ese archivo y el `.env.azure`.

## Variables y secretos

`deploy.ps1` genera `infra/azure/.env.azure`, que **no se versiona** y queda con ACL restringida en Windows. En la VM se instala como `/opt/sos-clinic/.env` con `600 root:root`.

| Clave | Origen |
|---|---|
| `APP_JWT_SECRET` | 64 bytes aleatorios (128 hex) |
| `DB_PASSWORD`, `POSTGRES_PASSWORD` | 24 bytes aleatorios, mismo valor |
| `ANALITICA_DB_PASSWORD` | 24 bytes aleatorios |
| `MAIL_*` | `.env` local en el primer despliegue |
| `GROQ_API_KEY` | `.env` local en el primer despliegue |
| `SOS_DOMAIN`, `ACME_EMAIL` | DNS de Azure y correo para Let's Encrypt |
| `ACR_LOGIN_SERVER`, `IMAGE_TAG` | salidas de Bicep y build |

Para rotar despues:

```powershell
# Copia MAIL_* y GROQ_API_KEY actuales del .env local al .env.azure
.\infra\azure\update.ps1 -SyncLocalSecrets
```

No cambies `DB_PASSWORD` en `.env.azure` sin recrear el volumen/rol de PostgreSQL; la aplicacion dejaria de conectar. Para una rotacion completa de base de datos, haz backup, destruye el volumen conscientemente y vuelve a sembrar.

Las credenciales del seed se generan una sola vez en la VM. El resumen del despliegue imprime la ruta local de `.credenciales.azure.md`; pasa ese archivo a un gestor de contrasenas y no lo subas a Git.

## Verificacion

El script ejecuta:

```powershell
.\infra\azure\smoke-test.ps1 -Domain <label>.<region>.cloudapp.azure.com
```

Comprueba frontend, `login.html`, redireccion de `landing.html`, HSTS, CSP, el endpoint publico de `ms-seguridad` a traves de Caddy y que el gateway rechace peticiones sin JWT.

Pruebas manuales recomendadas antes de la demo:

1. Login con `admin.test` y codigo 2FA real del buzon.
2. Cambio de clave y recuperacion.
3. Alta de paciente, triage, PDF y correo.
4. Dashboard de analitica con las 50.000 llegadas.
5. Agente IA con Groq.
6. Reinicio de la VM: los contenedores vuelven y los datos persisten.
7. Backup manual y restauracion en una base temporal.

Logs utiles:

```bash
sudo systemctl status sos-clinic
sudo docker compose -f /opt/sos-clinic/infra/azure/docker-compose.azure.yml ps
sudo docker compose -f /opt/sos-clinic/infra/azure/docker-compose.azure.yml logs -f --tail=100
sudo docker logs --tail=150 sos-caddy
sudo journalctl -u sos-clinic -n 100 --no-pager
```

## Operacion

```powershell
.\infra\azure\start.ps1
.\infra\azure\stop.ps1
.\infra\azure\update.ps1
.\infra\azure\destroy.ps1
```

- `start.ps1` actualiza la regla SSH con tu IP actual, enciende la VM, arranca el stack y prueba HTTPS.
- `stop.ps1` crea un backup, detiene los contenedores de forma ordenada y desaloca la VM. Los volumenes, discos, IP, ACR y backups se conservan. Usa `-SkipBackup` si necesitas apagar rapido.
- `update.ps1` publica un tag nuevo, sube el ultimo commit y reinicia. No toca datos salvo `-Reseed`/`-ReseedAnalytics`.
- `destroy.ps1` elimina el grupo de recursos completo. Pide confirmacion; usa `-Force` para automatizar y `-KeepLocalSecrets` para conservar `.env.azure` y las credenciales del seed.

### Auto-shutdown

La VM se apaga automaticamente a las 22:00 hora Bogota. Si la demo es de noche, cambia `-AutoShutdownTime` al desplegar o desactiva el schedule desde Azure. `stop.ps1` sigue siendo la forma recomendada de apagarla porque hace el apagado ordenado.

## Backups

`sos-clinic-backup.timer` ejecuta `backup.sh` todos los dias a las 21:30 hora Bogota, antes del auto-shutdown de las 22:00. `stop.ps1` tambien lanza un backup antes de apagar. El timer usa `Persistent=true`: si la VM estaba apagada a esa hora, el backup se recupera en el siguiente arranque.

- dump PostgreSQL en formato custom (`pg_dump -Fc`);
- dump MySQL comprimido (`mysqldump --single-transaction`);
- `.env` y credenciales del seed;
- subida a `sos-clinic/<timestamp>/` en el contenedor privado;
- copias locales en `/var/backups/sos-clinic` durante 7 dias;
- lifecycle de Azure borra los blobs a los 30 dias.

El backup de configuracion contiene secretos. El contenedor es privado y la Storage Account exige identidad/TLS, pero no esta cifrado a nivel de aplicacion. Trata esos blobs como material sensible.

Backup manual:

```bash
sudo /opt/sos-clinic/infra/azure/backup.sh
systemctl list-timers sos-clinic-backup.timer
```

Restauracion (con el stack detenido):

```bash
sudo systemctl stop sos-clinic
sudo docker compose -f /opt/sos-clinic/infra/azure/docker-compose.azure.yml up -d postgres mysql
# Descarga el dump con:
# az storage blob download --account-name <storage> --container-name sos-clinic-backups \
#   --name sos-clinic/<timestamp>/postgres-<timestamp>.dump --file /tmp/pg.dump --auth-mode login
sudo docker exec -i sos-postgres pg_restore -U sos_admin -d sosclinic --clean --if-exists < /tmp/pg.dump
# MySQL:
# az storage blob download ... --name sos-clinic/<timestamp>/mysql-<timestamp>.sql.gz --file /tmp/mysql.sql.gz
gunzip -c /tmp/mysql.sql.gz | sudo docker exec -i -e MYSQL_PWD="$(sudo grep '^ANALITICA_DB_PASSWORD=' /opt/sos-clinic/.env | cut -d= -f2- | tr -d "'")" sos-mysql mysql -uroot
sudo systemctl start sos-clinic
```

Antes del dia 28 del trial exporta el dump de PostgreSQL, el `.env`, `credenciales.local.md` y los backups que necesites. Si no vas a actualizar la suscripcion, ejecuta `destroy.ps1` despues de respaldar.

## Costos y trial

Orden de magnitud con una VM de 2 vCPU/8 GB (`Standard_B2ms` o `Standard_D2as_v7`):

| Estado | Costo aproximado |
|---|---|
| VM encendida 24/7 | US$60-90/mes (VM + disco + IP + ACR + Storage) |
| VM desalocada | US$15-25/mes (disco, IP, ACR, Storage) |
| Bases gestionadas | +US$25-40/mes y adaptacion del seed |

El trial de 30 dias incluye credito y limite de gasto. La alerta de presupuesto avisa al 50%, 80% y 100% del monto configurado. Aun asi, apaga la VM fuera de la demo.

## Problemas frecuentes

| Sintoma | Revision |
|---|---|
| `az` no se reconoce | `winget install Microsoft.AzureCLI` y abre una consola nueva |
| `docker buildx` falla | Abre Docker Desktop y verifica `docker buildx version` |
| No hay cuota en eastus2 | `deploy.ps1 -Location brazilsouth` |
| `Bicep` falla por nombre global | Borra `.deployment.json` y deja que el script regenere nombres |
| SSH no conecta | `start.ps1` actualiza la regla con tu IP; revisa NSG `AllowSSH` |
| HTTPS no responde | DNS aun no propaga o Caddy no emitio el certificado: `sudo docker logs --tail 150 sos-caddy` |
| Login sin 2FA | Revisa acceso a `pruebasosclinic@gmail.com` y `MAIL_*` en `/opt/sos-clinic/.env` |
| Analitica vacia | `.\infra\azure\update.ps1 -ReseedAnalytics` |
| Seed falla | `sudo journalctl -u sos-clinic -n 200` y `sudo docker logs sos-postgres` |
| Trial por vencer | Exporta backups y ejecuta `destroy.ps1` o actualiza la suscripcion |

## Limites conocidos

- Una sola VM y un solo nodo de cada base: no hay alta disponibilidad.
- El JWT dura 2 horas y no hay revocacion centralizada.
- El seed usa correos plus-addressed a un Gmail; el 2FA depende de ese buzon.
- MySQL de analitica es sintetico y no forma parte del flujo clinico.
- No hay CI/CD en esta version: el despliegue es manual y reproducible con los scripts.
- `azure-deploy` debe estar commiteado; `git archive` no incluye cambios sin commit.
- Key Vault no se usa en esta fase; los secretos viven en `.env` con permisos restringidos.
