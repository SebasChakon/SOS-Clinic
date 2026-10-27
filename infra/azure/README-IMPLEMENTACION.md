# Implementación del despliegue Azure — SOS Clinic

> Registro de la implementación realizada el **25 de octubre de 2026** para el prototipo académico SOS Clinic.
> Este documento describe **qué se hizo, por qué se tomó cada decisión y cómo se validó**. La guía operativa del día a día está en [`README.md`](README.md).

---

## 1. Resultado final

| Elemento | Valor |
|---|---|
| URL pública | https://sos-clinic-t76e5.eastus2.cloudapp.azure.com |
| Estado | Desplegado y validado; la VM se apaga a las 22:00 y se enciende con `start.ps1` antes de cada demo |
| Grupo de recursos | `sos-clinic-rg` |
| Región | East US 2 |
| VM | `sos-clinic-vm`, `Standard_D2as_v7` (2 vCPU / 8 GB), Ubuntu 24.04, disco de 64 GB |
| IP pública | `20.96.10.152` (estática) |
| ACR | `sosclinicd1t7vn.azurecr.io` (Basic, admin deshabilitado) |
| Storage de backups | `sosclinicworgm0`, contenedor privado `sos-clinic-backups` |
| Tag de imágenes | `20261025-154434-80e6889` |
| Auto-shutdown | 22:00 hora Bogotá (`SA Pacific Standard Time`) |
| Presupuesto | `sos-clinic-presupuesto`, US$60/mes, alertas al 50/80/100 % |
| Trial | Hasta el 24 de noviembre de 2026 |

El stack quedó corriendo con **7 microservicios Spring Boot**, **Caddy** con HTTPS, **PostgreSQL 16** y **MySQL 8** en contenedores, descubrimiento con Eureka y el agente de Groq.

---

## 2. Objetivo y alcance

### Objetivo

Publicar el prototipo completo en Azure para una demo académica, con HTTPS, base de datos sembrada, backups y scripts de operación, **sin romper el flujo local de Windows**.

### Alcance de esta fase

- Una sola VM Linux con Docker Compose.
- PostgreSQL y MySQL en contenedores, no bases gestionadas de Azure.
- DNS de Azure (`*.cloudapp.azure.com`) y certificado automático de Let's Encrypt con Caddy.
- Backups a Azure Blob Storage con identidad administrada.
- Scripts de despliegue, arranque, apagado, actualización, destrucción y smoke test.
- Sin CI/CD en esta versión; el despliegue es manual y reproducible.
- Uso exclusivamente académico/demo; no datos clínicos reales.

### Fuera de alcance

- Alta disponibilidad, balanceador, WAF o múltiples zonas.
- Key Vault, rotación automática de secretos o bases gestionadas.
- Observabilidad centralizada, APM o alertas de aplicación.
- Pipeline de CI/CD.

---

## 3. Decisiones de arquitectura

| Decisión | Motivo |
|---|---|
| **Una sola VM con Docker Compose** | Menor costo y complejidad para una demo; mantiene los 7 servicios y las dos bases en un único host. |
| **`network_mode: host` para las 7 apps y Caddy** | Conserva los bindings `127.0.0.1` de `application.properties` y el registro de Eureka sin modificar el código Java. |
| **PostgreSQL y MySQL en bridge** | Se publican solo en `127.0.0.1:5433` y `127.0.0.1:3306`; no quedan expuestos a Internet. |
| **Caddy como única entrada pública** | Sirve el frontend estático, proxya `/ms-*` al gateway y emite el certificado TLS automáticamente. |
| **DNS label de Azure** | Evita comprar un dominio y permite HTTPS con un nombre válido de Let's Encrypt. |
| **ACR con identidad administrada** | La VM descarga imágenes sin usuario/contraseña de registro. |
| **Blob Storage con identidad administrada** | Los backups se suben sin claves de Storage en el `.env`. |
| **Secretos en `.env` con permisos 600** | Key Vault se dejó para una fase posterior; el archivo vive solo en la VM y en el equipo local. |
| **Seed de PostgreSQL con `reset_seed.py`** | Reutiliza el seed canónico del proyecto y mantiene las 9 cuentas de prueba. |
| **Seed de MySQL con perfil Compose temporal** | Carga las 50.000 llegadas sintéticas sin agregar un servicio permanente. |
| **Auto-shutdown a las 22:00** | Evita cobrar cómputo fuera de las demos; `stop.ps1` permite apagar antes. |
| **Sin CI/CD** | El alcance pedido era despliegue manual reproducible; el pipeline queda como mejora futura. |

---

## 4. Recursos creados en Azure

| Recurso | Nombre / detalle |
|---|---|
| Grupo de recursos | `sos-clinic-rg` |
| VNet | `10.42.0.0/16` |
| Subnet | `10.42.1.0/24` |
| NSG | `sos-clinic-nsg` |
| Reglas NSG | `AllowSSH` solo desde la IP administrativa, `AllowHTTP` 80, `AllowHTTPS` 443, `DenyAllInbound` |
| IP pública | Estática, DNS `sos-clinic-t76e5.eastus2.cloudapp.azure.com` |
| VM | `sos-clinic-vm`, Ubuntu 24.04, `Standard_D2as_v7` |
| Disco | 64 GB SSD |
| ACR | `sosclinicd1t7vn.azurecr.io`, SKU Basic, admin deshabilitado |
| Storage Account | `sosclinicworgm0`, Standard LRS, TLS 1.2, blob público deshabilitado, shared key deshabilitada |
| Contenedor de blobs | `sos-clinic-backups`, acceso privado |
| Identidad de la VM | System-assigned |
| Roles de la identidad | `AcrPull` sobre el ACR y `Storage Blob Data Contributor` sobre la Storage Account |
| Auto-shutdown | `shutdown-computevm-sos-clinic-vm`, 22:00, `SA Pacific Standard Time` |
| Presupuesto | `sos-clinic-presupuesto`, US$60/mes, avisos al 50/80/100 % |

### Zona horaria y arranque

- La VM y los contenedores usan `America/Bogota`.
- `sos-clinic.service` (systemd) ejecuta `docker compose up -d` al arrancar.
- `sos-clinic-backup.timer` ejecuta el backup diario a las **21:30**.
- Se agregó una swap de 2 GB para absorber picos de los servicios Java.

---

## 5. Archivos creados y modificados

### Nuevos en `infra/azure/`

| Archivo | Función |
|---|---|
| `main.bicep` | Infraestructura: VM, red, NSG, IP, ACR, Storage, identidad, roles, auto-shutdown y presupuesto. |
| `cloud-init.yaml` | Bootstrap de Ubuntu: Docker, Compose, Azure CLI, swap, timezone, unidades systemd y timer de backup. |
| `Dockerfile.service` | Dockerfile multi-stage común para los 7 microservicios (Maven 17 → JRE 17). |
| `docker-compose.azure.yml` | Stack completo: 7 apps, Caddy, PostgreSQL, MySQL y perfil temporal de seed. |
| `Caddyfile` | HTTPS, frontend estático, proxy `/ms-*`, cabeceras de seguridad y cache. |
| `env.example` | Plantilla de variables del despliegue. |
| `common.ps1` | Utilidades compartidas: Azure CLI, SSH/SCP con reintentos, `.env`, estado y espera de servicios. |
| `deploy.ps1` | Orquestador: preflight, Bicep, build/push, subida de código, `.env`, seeds, arranque, verificación y smoke test. |
| `start.ps1` | Enciende la VM, actualiza la regla SSH, arranca el stack y valida HTTPS. |
| `stop.ps1` | Backup opcional, apagado ordenado y `deallocate`. |
| `update.ps1` | Nuevo tag de imágenes, subida del último commit y reinicio sin tocar datos. |
| `destroy.ps1` | Elimina el grupo de recursos con confirmación. |
| `smoke-test.ps1` | Pruebas públicas: frontend, login, redirección, HSTS, CSP, gateway, 401 y certificado. |
| `backup.sh` | Backup de PostgreSQL, MySQL y configuración; copia local y subida a Blob. |
| `README.md` | Runbook operativo. |
| `README-IMPLEMENTACION.md` | Este documento. |

### Modificados de forma compatible

| Archivo | Cambio |
|---|---|
| `.dockerignore` | Excluye `.env`, credenciales, estado local y artefactos del contexto de build. |
| `.gitignore` | Ignora `.env.azure`, `.deployment.json`, `.credenciales.azure.md`, credenciales y archivos temporales. |
| `.gitattributes` | Fuerza LF en `.sh`, `.yaml`, `.yml`, `.bicep`, `Caddyfile`, `Dockerfile*` y `.dockerignore`. |
| `frontend/assets/api.js` | En Azure usa el mismo origen (`''`) en vez de `http://localhost:8080`; en local conserva el gateway. |
| `llenar_hospital.py` | Permite host, puerto y nombre de MySQL por variables de entorno. |
| `infra/seed/reset_seed.py` | Admite `SOS_COMPOSE_FILE` y `DB_CONTAINER` para el stack Azure. |
| `README.md` (raíz) | Sección de despliegue Azure y enlace al runbook. |

> No se modificaron `infra/docker-compose.yml`, los `application.properties`, los scripts `00`–`05`, el esquema, las migraciones ni el seed SQL canónico.

---

## 6. Flujo de despliegue ejecutado

1. **Preflight de Azure**
   - `az login` con la cuenta de la suscripción.
   - Registro de proveedores: `Microsoft.Compute`, `Microsoft.Network`, `Microsoft.Storage`, `Microsoft.ContainerRegistry`, `Microsoft.DevTestLab` y `Microsoft.Consumption`.
   - Verificación de cuota y disponibilidad de SKU en East US 2.

2. **Selección de VM**
   - `Standard_B2ms` y `Standard_D2as_v5` no estaban disponibles en East US 2 para esta suscripción.
   - Se usó el fallback `Standard_D2as_v7` (2 vCPU / 8 GB), con cuota de 4 vCPU.

3. **Infraestructura con Bicep**
   - Grupo de recursos, VNet, subnet, NSG, IP estática, VM, ACR, Storage, identidad y roles.
   - Auto-shutdown y presupuesto.
   - Salida guardada en `infra/azure/.deployment.json`.

4. **cloud-init**
   - Instalación de Docker Engine, Compose v2, Azure CLI, `jq`, `python3`, fuentes y utilidades.
   - Configuración de timezone, swap, `daemon.json` y unidades systemd.
   - Habilitación de `sos-clinic.service` y `sos-clinic-backup.timer`.

5. **Imágenes**
   - Build de los 7 servicios para `linux/amd64` con `Dockerfile.service`.
   - Push a ACR con el tag `20261025-154434-80e6889`.
   - Login de Docker contra ACR mediante `az acr login`.

6. **Código y configuración**
   - `git archive HEAD` → `tar.gz` → `scp` → `/opt/sos-clinic`.
   - Generación de `infra/azure/.env.azure` con LF.
   - Instalación en la VM como `/opt/sos-clinic/.env`, propietario `root`, permisos `600`.

7. **Bases de datos**
   - `docker compose up -d --wait postgres mysql`.
   - PostgreSQL: migraciones + `reset_seed.py --yes --skip-backup`.
   - MySQL: recreación de `urgencias_hospital` + `bd_local.sql`.
   - Perfil temporal `analitica-seed` para cargar las 50.000 llegadas.

8. **Arranque y HTTPS**
   - `systemctl restart sos-clinic.service`.
   - Caddy obtiene el certificado de Let's Encrypt para el FQDN.
   - `verify_seed.py` valida las 9 cuentas y el flujo 2FA.
   - `smoke-test.ps1` valida frontend, cabeceras, gateway y TLS.

9. **Validaciones posteriores**
   - Pruebas de PDF, analítica y agente Groq.
   - Backup manual, backup vía systemd y restauración en bases temporales.
   - Reinicio de la VM y ciclo `stop.ps1` / `start.ps1`.

---

## 7. Datos sembrados

### PostgreSQL (`sosclinic`)

| Tabla | Filas |
|---|---:|
| `personal` | 9 |
| `paciente` | 24 |
| `registro_triage` | 30 |
| `actividades` | 8 |
| `categoria_triage` | 5 |
| `perfil` | 8 |
| `perfil_actividad` | 22 |
| `estado_triage` | 3 |
| `turno` | 9 |

Las 9 cuentas del seed son:

| Usuario | Perfil |
|---|---|
| `admin.test` | administrador |
| `seguridad.sos` | seguridad |
| `coordinacion.sos` | coordinador_turnos |
| `medico.dia` | doctor_triage |
| `medico.noche` | doctor_triage |
| `recepcion.sos` | recepcionista |
| `triage.sos` | triage |
| `listas.sos` | lista_espera |
| `auditoria.sos` | auditor |

El correo del administrador es **`pruebasosclinic+admin@gmail.com`**; por el alias `+admin`, los códigos llegan a `pruebasosclinic@gmail.com`.

### MySQL (`urgencias_hospital`)

| Tabla | Filas |
|---|---:|
| `llegadas` | 50.000 |
| `contexto_zona` | 5 |
| `contexto_diario` | 365 |
| `capacidad_turno` | 1.095 |

Los datos de analítica son sintéticos y están separados del flujo clínico de PostgreSQL.

---

## 8. Seguridad implementada

- **Superficie pública mínima:** solo Caddy escucha en `0.0.0.0:80/443`. PostgreSQL, MySQL, gateway, Eureka y los microservicios quedan en loopback.
- **SSH restringido:** la regla `AllowSSH` solo permite la IP administrativa actual (`/32`). `start.ps1` la actualiza al encender.
- **ACR sin admin:** la autenticación es por identidad administrada de la VM con rol `AcrPull`.
- **Storage privado:** blob público deshabilitado, shared key deshabilitada, TLS 1.2 y HTTPS obligatorio.
- **Backups protegidos:** contenedor privado, identidad con `Storage Blob Data Contributor`, lifecycle de 30 días y soft delete de 7 días.
- **Secretos fuera de Git:** `.env`, `.env.azure`, `.deployment.json` y credenciales del seed están ignorados.
- **Permisos del `.env`:** `600 root:root` en la VM.
- **Cabeceras HTTP:** HSTS, CSP con `connect-src 'self'`, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy` y `Permissions-Policy`.
- **Sesiones:** JWT de acceso de 15 minutos y refresh token opaco en PostgreSQL (8 h de inactividad, 12 h absolutas); sin gestión de dispositivos ni purga automática (limitación conocida).

---

## 9. Validación ejecutada

### Smoke test público (`smoke-test.ps1`)

| Prueba | Resultado |
|---|---|
| Frontend `/` responde 200 | PASS |
| `login.html` responde 200 | PASS |
| `landing.html` redirige a `/index.html` | PASS |
| Cabecera HSTS presente | PASS |
| CSP same-origin presente | PASS |
| Gateway/`ms-seguridad` responde por Caddy | PASS |
| Gateway rechaza sin JWT (401) | PASS |
| Certificado TLS de confianza | PASS |

### Pruebas funcionales

| Prueba | Resultado / evidencia |
|---|---|
| Login + 2FA + roles de las 9 cuentas | **9/9 PASS** (código 2FA inyectado en BD para la prueba) |
| Envío SMTP de códigos 2FA | **9 correos enviados** según `ms-seguridad`; no se leyó el buzón |
| Analítica `/dashboard/resumen` | **200**, respuesta con datos |
| Analítica `/dashboard/llegadas` | **200**, respuesta con datos |
| PDF de historia clínica | **24.887 bytes**, `application/pdf`, encabezado `%PDF` |
| Agente Groq | **200**, respuesta `{"texto":"OK","tokens":893}` |
| Backup manual | 3 archivos locales y 3 blobs subidos |
| Backup vía systemd | Servicio terminado correctamente; timer activo |
| Restauración PostgreSQL | Base temporal con **9 personas** |
| Restauración MySQL | Base temporal con **50.000 llegadas** |
| Reinicio de VM | Stack vuelve solo, 10 contenedores healthy, HTTPS 200, datos intactos |
| Ciclo `stop.ps1` / `start.ps1` | Deallocate + start + smoke test correctos |
| Auto-shutdown | Habilitado a las 22:00 Bogotá |
| Presupuesto | Configurado con alertas 50/80/100 % |

### Evidencia de backup

Backups realizados durante la validación:

- `sos-clinic/20261025-160403/`
- `sos-clinic/20261025-161252/`

Cada uno contiene:

- `postgres-<timestamp>.dump` (`pg_dump -Fc`)
- `mysql-<timestamp>.sql.gz` (`mysqldump --single-transaction`)
- `config-<timestamp>.tar.gz` (`.env` y credenciales del seed)

Los archivos locales quedan en `/var/backups/sos-clinic` con permisos `600`.

---

## 10. Problemas encontrados y correcciones

| Problema | Causa | Corrección |
|---|---|---|
| Proveedores de Azure en `NotRegistered` | Suscripción nueva | Registro explícito y espera a `Registered`. |
| `Standard_B2ms` y `Standard_D2as_v5` no disponibles en East US 2 | Restricción de la suscripción/región | Fallback a `Standard_D2as_v7`; Bicep y scripts actualizados. |
| `.env` generado con CRLF | `WriteAllLines` en Windows | `WriteAllText` con `\n` explícito; verificación de 0 bytes `CR` en local y VM. |
| `./.env: Permission denied` al sembrar MySQL | `.env` es `600 root` y el comando hacía `source` como `azureuser` | Lectura con `sudo grep` y `MYSQL_PWD`; sin `source` del archivo. |
| `unexpected EOF` en el comando MySQL | PowerShell 5.1 elimina comillas dobles embebidas al pasar el comando a SSH | Comando remoto sin comillas dobles y contraseña hexadecimal sin necesidad de quoting. |
| Timeout SSH transitorio durante `.env` | Corte de red puntual | Reintentos automáticos (3 intentos) en `Invoke-Remote` y `Copy-Remote` solo para fallos de transporte. |
| Backup no copiaba al directorio local | Faltaba la copia explícita | `install -m 600` de cada archivo a `/var/backups/sos-clinic`. |
| Primer despliegue detenido en el seed MySQL | Combinación de permisos y CRLF | Se corrigió y se reanudó con `-SkipInfra -SkipBuild -ReseedAnalytics`. |

---

## 11. Operación

### Comandos principales

```powershell
.\infra\azure\start.ps1
.\infra\azure\stop.ps1
.\infra\azure\update.ps1
.\infra\azure\destroy.ps1
.\infra\azure\smoke-test.ps1 -Domain sos-clinic-t76e5.eastus2.cloudapp.azure.com
```

### Ciclo diario recomendado

1. Antes de la demo: `.\infra\azure\start.ps1`.
2. Durante la demo: usar la URL HTTPS.
3. Al terminar: `.\infra\azure\stop.ps1` (hace backup y desaloja la VM).

### `stop.ps1`

- Actualiza la regla SSH con la IP actual.
- Ejecuta `backup.sh` (salvo `-SkipBackup`).
- Detiene el stack con `systemctl stop sos-clinic.service`.
- Desaloja la VM con `az vm deallocate`.
- Conserva discos, IP, ACR, Storage y volúmenes.

### `update.ps1`

- Construye un tag nuevo de las 7 imágenes y las publica en ACR.
- Sube el último commit y regenera `.env.azure`.
- Reinicia el stack y ejecuta el smoke test.
- No toca datos salvo `-Reseed` o `-ReseedAnalytics`.

### Acceso SSH

```powershell
ssh -i "$env:USERPROFILE\.ssh\sos_clinic_azure" azureuser@sos-clinic-t76e5.eastus2.cloudapp.azure.com
```

### Logs útiles

```bash
sudo systemctl status sos-clinic
sudo docker compose -f /opt/sos-clinic/infra/azure/docker-compose.azure.yml ps
sudo docker compose -f /opt/sos-clinic/infra/azure/docker-compose.azure.yml logs -f --tail=100
sudo docker logs --tail=150 sos-caddy
sudo journalctl -u sos-clinic -n 100 --no-pager
```

---

## 12. Backups y restauración

### Backup automático

- Timer: `sos-clinic-backup.timer`.
- Hora: **21:30 America/Bogota**.
- `Persistent=true`: si la VM estaba apagada, se recupera en el siguiente arranque.
- Destino remoto: `sos-clinic/<timestamp>/` en `sos-clinic-backups`.
- Retención local: 7 días.
- Lifecycle de Azure: 30 días.

### Backup manual

```bash
sudo /opt/sos-clinic/infra/azure/backup.sh
systemctl list-timers sos-clinic-backup.timer
```

### Restauración validada

PostgreSQL:

```bash
sudo systemctl stop sos-clinic
sudo docker compose -f /opt/sos-clinic/infra/azure/docker-compose.azure.yml up -d postgres
sudo cat /ruta/postgres-<timestamp>.dump | \
  sudo docker exec -i sos-postgres pg_restore -U sos_admin -d sosclinic --clean --if-exists
sudo systemctl start sos-clinic
```

MySQL:

```bash
sudo zcat /ruta/mysql-<timestamp>.sql.gz | \
  sudo docker exec -i -e MYSQL_PWD="$(sudo grep '^ANALITICA_DB_PASSWORD=' /opt/sos-clinic/.env | cut -d= -f2- | tr -d "'" | tr -d '\r')" \
  sos-mysql mysql -uroot
```

> Si el backup se descarga a `/tmp` como `azureuser`, la redirección `< /tmp/...` funciona; si se lee desde `/var/backups/sos-clinic` (root, 600), usa `sudo cat`/`sudo zcat` como en los ejemplos.

---

## 13. Costos y trial

| Estado | Orden de magnitud |
|---|---|
| VM encendida 24/7 | US$60–90/mes (VM + disco + IP + ACR + Storage) |
| VM desalocada | US$15–25/mes (disco, IP, ACR, Storage) |
| Bases gestionadas | +US$25–40/mes y adaptación del seed |

- El trial de 30 días termina el **24 de noviembre de 2026**.
- El presupuesto de US$60/mes avisa al 50 %, 80 % y 100 %.
- La VM se auto-apaga a las 22:00; `stop.ps1` permite apagarla antes.
- Antes del día 28 del trial: exportar backups y credenciales, y decidir si se actualiza la suscripción o se ejecuta `destroy.ps1`.

---

## 14. Limitaciones y riesgos

- **Una sola VM:** no hay alta disponibilidad; si la VM o el disco fallan, el servicio se cae.
- **Sin WAF ni balanceador:** Caddy es la única entrada y no hay protección perimetral avanzada.
- **Sin Key Vault:** los secretos viven en `.env` con permisos restringidos. Key Vault quedó para una fase posterior.
- **Sin CI/CD:** el despliegue es manual; las imágenes se construyen desde el equipo local.
- **Dependencia de Gmail:** el 2FA usa SMTP de Gmail y el buzón `pruebasosclinic@gmail.com`.
- **Dependencia de Groq:** el agente conversacional requiere una API key válida y cuota disponible.
- **Sesiones sin gestión de dispositivos ni purga automática:** el JWT de acceso dura 15 minutos y las sesiones vencen por inactividad o por el tope absoluto.
- **MySQL sintético:** las 50.000 llegadas no forman parte del flujo clínico real.
- **Claves no rotadas:** por decisión del equipo se usaron las claves existentes de Gmail/Groq; el riesgo residual fue aceptado.
- **Pruebas pendientes:** no se leyó el buzón para un código 2FA real ni se probó el flujo completo de cambio/recuperación de clave.
- **Prototipo académico:** no debe usarse con datos clínicos reales.

---

## 15. Pendientes y próximos pasos

1. Hacer un login real desde `login.html` con un código recibido en `pruebasosclinic@gmail.com`.
2. Probar cambio de clave y recuperación con una cuenta de prueba.
3. Decidir si se fusiona/pushea la rama `azure-deploy` a `main`/`origin`.
4. Exportar backups, `.env.azure` y credenciales antes del día 28 del trial.
5. Definir si se mantiene la suscripción, se actualiza o se ejecuta `destroy.ps1`.
6. Opcional: rotar `MAIL_PASSWORD` y `GROQ_API_KEY` y ejecutar `update.ps1 -SyncLocalSecrets`.
7. Opcional fase 2: Key Vault, bases gestionadas, CI/CD, monitoreo y alta disponibilidad.

---

## 16. Reproducción desde cero

### Requisitos

- Windows 10/11 con PowerShell 5.1 o 7.
- Azure CLI y sesión activa (`az login`).
- Docker Desktop iniciado.
- `ssh`, `scp`, `git` y `tar`.
- `.env` local con `MAIL_USERNAME`, `MAIL_PASSWORD` y `GROQ_API_KEY`.
- Acceso al buzón `pruebasosclinic@gmail.com`.
- Cuota para 2 vCPU en la región elegida.

### Pasos

```powershell
git checkout azure-deploy
az login
.\infra\azure\deploy.ps1
```

Si el despliegue se interrumpe después de crear la infraestructura:

```powershell
.\infra\azure\deploy.ps1 -SkipInfra -SkipBuild -ReseedAnalytics
```

Para forzar una región o tamaño:

```powershell
.\infra\azure\deploy.ps1 -Location brazilsouth -VmSize Standard_D2as_v7
```

La guía detallada de parámetros, backups y soluciones a problemas está en [`README.md`](README.md).

---

## 17. Commits de la implementación

| Commit | Descripción |
|---|---|
| `80e6889` | Add Azure single-VM deployment for SOS Clinic |
| `748c468` | Fix Azure env line endings and MySQL seed credentials |
| `ec58fbc` | Avoid PowerShell quote mangling in MySQL seed command |
| `3d025e1` | Retry transient SSH and SCP failures during Azure deployment |
| `8873ba9` | Polish Azure deployment output and credential notes |

Rama: `azure-deploy`. El despliegue en la VM se hizo desde `3d025e1`; los commits posteriores solo ajustan documentación y salida de consola, no el código Java ni las imágenes.

---

## 18. Archivos locales que no se versionan

| Archivo | Contenido |
|---|---|
| `infra/azure/.env.azure` | Secretos del despliegue (JWT, bases, SMTP, Groq, dominio). |
| `infra/azure/.deployment.json` | Estado de Azure: recursos, IP, FQDN, tag de imágenes y `seeded`. |
| `infra/azure/.credenciales.azure.md` | Credenciales de las 9 cuentas del seed. |
| `C:\Users\santi\.ssh\sos_clinic_azure` | Llave privada SSH de la VM. |

Estos archivos están cubiertos por `.gitignore` y no deben subirse al repositorio.
