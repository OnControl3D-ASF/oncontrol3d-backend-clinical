# Calidad — Clinical Service

## Estrategia de pruebas

| Capa | Herramienta | Alcance |
|---|---|---|
| Servicios de aplicación | JUnit 5 + Mockito | Reglas de negocio, validaciones y autorización (IDOR) con repositorios simulados |
| Controladores REST | MockMvc *standalone* | Códigos HTTP y forma del JSON que consume el frontend (contrato), sin levantar Spring ni BD |
| Seguridad | JUnit 5 | Emisión/validación de JWT, filtro JWT, rate limiting de login |

Las pruebas no requieren base de datos ni red: `./mvnw verify` corre en ~15 s.

## Gate de cobertura (JaCoCo)

- `mvn verify` **falla** si la cobertura de líneas es menor a **70 %** (`jacoco.line.minimum` en `pom.xml`).
- Código generado por Lombok excluido vía `lombok.config` (`@lombok.Generated`).
- Excluidos del cálculo, por ser arranque o cableado sin lógica de negocio:
  `OncontrolBackendApplication`, `DataSeeder`, `SecurityConfig`, `WebConfig`, `OpenApiConfiguration`.
  Sonar usa exactamente las mismas exclusiones.
- Verificado: con `-Djacoco.line.minimum=0.80` el build termina en `BUILD FAILURE`.

## Métricas (feat/clinical-tests)

| Métrica | Antes | Después |
|---|---|---|
| Pruebas | 0 | 182 (0 fallos) |
| Cobertura de líneas | 0 % | 74.7 % (1791/2396) |
| Duración `mvn verify` | — | ~15 s local |

## Defectos detectados al escribir las pruebas

No se corrigieron en este bloque porque cambian valores que muestra el frontend; quedan
registrados para decidir su corrección.

| # | Ubicación | Defecto | Impacto |
|---|---|---|---|
| 1 | `SymptomRepository.countSevereSymptomsByProfile` | JPQL compara con `'SEVERO'`, pero el enum es `SEVERA` | `severeSymptoms` en `/api/symptoms/patient/{id}/stats` siempre 0 |
| 2 | `SymptomService.getSymptomStats` | `criticalSymptoms` reutiliza el conteo total | El valor "críticos" muestra el total de síntomas |
| 3 | `TreatmentService.getTreatmentStats` | `paused` y `suspended` cuentan ambos `SUSPENDED` | Estadística de pausados duplicada |
| 4 | `MedicationService.markDoseAsTaken` | Suma 24 h a un `LocalTime` (vuelve a la misma hora) | Marcar dosis tomada no mueve la próxima dosis |
| 5 | `ReportService.getDoctorReports` | Varias cifras son constantes o estimaciones (satisfacción 4.8, crecimiento 12 %, efectividad 87 %, completadas = total/3…) y consulta citas con el id de `DoctorProfile` en lugar del de `Profile` | Reportes del médico no reflejan datos reales |

Las pruebas de `ReportService` solo verifican los valores que realmente se calculan
(totales, distribución por tipo, series mensuales); no se fijan las cifras constantes.
