# Informe de pruebas de seguridad — SOS Clinic

> **Estado:** borrador en construcción. Las secciones se completan a medida que
> se ejecutan los casos del [plan](../PLAN.md). No incluir datos personales,
> tokens ni secretos en este documento ni en sus anexos.

## 1. Portada

| Campo | Valor |
|---|---|
| Proyecto | SOS Clinic |
| Tipo de evaluación | Pruebas de seguridad académicas sobre entorno local |
| Rama | `pruebas-seguridad-2` |
| Commit base | `50f8bea0969f0824d511d7cbc24de413ff74931d` |
| Fecha de inicio | 2026-10-19 |
| Integrantes | _(completar)_ |
| Autorización | _(completar: responsable y alcance)_ |

## 2. Resumen ejecutivo

_(Completar al final: cantidad de hallazgos por severidad, riesgo global y
recomendaciones principales.)_

| Severidad | Cantidad |
|---|---|
| Crítica | — |
| Alta | — |
| Media | — |
| Baja | — |

## 3. Alcance

_(Describir componentes evaluados, límites y exclusiones. Referencia:
`PLAN.md`.)_

## 4. Metodología

_(PTES, OWASP WSTG/ASVS/API Top 10, ISO/IEC 27002:2022 y CVSS 4.0.)_

## 5. Arquitectura y superficie de ataque

_(Incluir el diagrama de arquitectura del README y las rutas del gateway.)_

## 6. Hallazgos

Plantilla por hallazgo:

### H-XX — Título

| Campo | Valor |
|---|---|
| Severidad | — |
| CVSS 4.0 | — |
| CWE | — |
| OWASP | — |
| ISO 27002 | — |
| Componente | — |
| Evidencia | `evidencias/XX_descripcion.png` |
| Estado | Confirmado / Pendiente / Corregido |

**Descripción.** _(Qué se observó.)_

**Reproducción.** _(Petición o pasos mínimos.)_

**Impacto.** _(Qué permite un atacante y sobre qué activo.)_

**Causa raíz.** _(Código o configuración responsable.)_

**Remediación.** _(Cambio concreto y verificable.)_

**Verificación.** _(Cómo se comprueba que quedó corregido.)_

## 7. Matriz STRIDE/DREAD

_(Completar por componente.)_

## 8. Pruebas sin hallazgo

| Prueba | Resultado |
|---|---|
| Inyección SQL | Pendiente |
| Path traversal | Pendiente |
| _..._ | |

## 9. Plan de remediación priorizado

| Prioridad | Hallazgo | Acción | Esfuerzo |
|---|---|---|---|
| — | — | — | — |

## 10. Conclusiones

_(Completar al final.)_

## 11. Anexos

- Matriz de pruebas (Excel).
- Resultados crudos de Trivy, Semgrep, gitleaks y ZAP.
- Comandos y payloads utilizados.
