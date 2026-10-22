# Pruebas Maven por módulo — resumen

**Fecha:** 2026-10-22 · **Rama:** `pruebas-seguridad-3`

## Qué se hizo

Se ejecutó `mvnw -B test` en los siete módulos Maven del repositorio, con
PostgreSQL y `.env` disponibles para las pruebas de contexto. La salida se
resumió a partir de Surefire y se guardó un consolidado.

## Resultado

| Módulo | Estado | Tests |
|---|---|---|
| eureka-server | PASS | 1 |
| ms-gateway | PASS | 1 |
| ms-seguridad | PASS | 35 |
| ms-pacientes | PASS | 6 |
| ms-triage | PASS | 1 |
| ms-analitica | PASS | 4 |
| ms-ia-agente | PASS | 1 |
| **Total** | **7/7** | **49** |

Sin fallos ni errores. No se omitió ninguna prueba.

## Observaciones

- Las pruebas de contexto requieren `.env` y PostgreSQL.
- Compiló con JDK 26 usando release 17, sin incidencias.
- No hay cobertura JaCoCo ni Testcontainers; son mejoras para la CI.

## Evidencia

- `seguridad/resultados/maven/resumen.md`
- `seguridad/resultados/maven/resumen.csv`
- `seguridad/pruebas/ejecutar-tests-maven.ps1`

## Conclusión

Batería Maven en verde. No sustituye las pruebas E2E, pero confirma que las
unidades y los contextos de los siete servicios arrancan correctamente.
