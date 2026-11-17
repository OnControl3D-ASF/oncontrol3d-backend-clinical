# Reglas de negocio — Clinical Service

## RN-01 · Clasificación de toxicidad CTCAE → severidad

**Implementación:** `symptoms/domain/model/CtcaeGrade.java` (value object de dominio, sin dependencias de framework).
**Pruebas:** `CtcaeGradeTest` (unitarias) y `HU11_alerta_toxicidad.feature` (BDD).

Los reportes ePRO de toxicidad se expresan en grados CTCAE (*Common Terminology Criteria
for Adverse Events*). El sistema los traduce a la escala de severidad que ya almacena y
expone la API (`SymptomSeverity`):

| Grado CTCAE | Descripción CTCAE | Severidad | ¿Alerta al oncólogo? (HU11) |
|---|---|---|---|
| 1 | Leve; asintomático o síntomas leves | `LEVE` | No |
| 2 | Moderado; limita actividades instrumentales | `MODERADA` | No |
| 3 | Severo o médicamente significativo | `SEVERA` | **Sí** |
| 4 | Consecuencias potencialmente mortales | `CRITICA` | **Sí** |
| 5 | Muerte relacionada al evento adverso | — (rechazado) | — |

**Decisiones:**
- El grado 5 se rechaza porque no es un síntoma que el paciente pueda autoreportar.
- El umbral de alerta (grado ≥ 3, es decir `SEVERA` o `CRITICA`) coincide con el criterio
  que el dashboard del médico ya usa para "síntomas críticos" (`DashboardService`).
- La API pública no cambia: sigue recibiendo y devolviendo `LEVE`/`MODERADA`/`SEVERA`/`CRITICA`
  (y los alias `MILD`/`MODERATE`/`SEVERE`/`CRITICAL`). `CtcaeGrade.isAlertSeverity` permite
  aplicar el umbral a reportes que llegan con severidad.

## Brechas frente al informe (sección 2.6)

Criterios del informe que **no** están implementados y por eso no se automatizan:

| HU | Criterio del informe | Estado |
|---|---|---|
| HU05 | Búsqueda por documento de identidad | No existe campo de documento |
| HU05 | Auditoría del intento de acceso fallido | No implementado (la API responde 403) |
| HU06 | Cálculo de fechas estimadas por ciclo | No implementado |
| HU06 | "Ciclos Totales" obligatorio | Opcional; se mantiene para no cambiar el contrato con el frontend |
| HU07 | Escala de dolor 1–10 / grado CTCAE en el formulario | La API recibe severidad |
| HU11 | Envío de notificación push/correo | Bloque 6 (Azure Function + Service Bus) |
