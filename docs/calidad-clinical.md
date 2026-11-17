# Calidad — Clinical Service

## Estrategia de pruebas

| Capa | Herramienta | Alcance |
|---|---|---|
| Servicios de aplicación | JUnit 5 + Mockito | Reglas de negocio, validaciones y autorización (IDOR) con repositorios simulados |
| Controladores REST | MockMvc *standalone* | Códigos HTTP y forma del JSON que consume el frontend (contrato), sin levantar Spring ni BD |
| Seguridad | JUnit 5 | Emisión/validación de JWT, filtro JWT, rate limiting de login |
| Aceptación (BDD) | Cucumber 7 + Spring Boot + MockMvc | HU05, HU06, HU07, HU11 en Gherkin (español), con contexto completo, cadena de seguridad real y JWT |
| Integración | Testcontainers (PostgreSQL 16) | Queries JPQL propias y los escenarios BDD sobre una base de datos real |

- Unitarias (`*Test`, surefire): sin base de datos ni red.
- BDD e integración (`*IT`, failsafe): requieren Docker; se ejecutan en `./mvnw verify`
  (omitir localmente con `-DskipITs`).
- Reportes: `target/cucumber-reports/cucumber.html` y `cucumber.json`; en CI se publican como artifact
  `cucumber-reports` y el resumen aparece en el *job summary*.

## BDD

| Feature | Escenarios | Técnicas |
|---|---|---|
| `HU05_historial_clinico.feature` | 3 | Antecedentes + Data Table (entradas y orden esperado) |
| `HU06_tratamientos.feature` | 6 | Scenario Outline (tipo × ciclos × avance) |
| `HU07_registro_epro.feature` | 6 | Scenario Outline (severidad enviada → registrada, alias en inglés) |
| `HU11_alerta_toxicidad.feature` | 6 | Regla + Scenario Outline (grado CTCAE → severidad → alerta) |

Cada escenario parte de una base vacía (`TRUNCATE ... RESTART IDENTITY`) y lee el estado
persistido de vuelta por la API, no la respuesta de creación.

## Gate de cobertura (JaCoCo)

- `mvn verify` **falla** si la cobertura de líneas es menor a **70 %** (`jacoco.line.minimum` en `pom.xml`).
- Código generado por Lombok excluido vía `lombok.config` (`@lombok.Generated`).
- Excluidos del cálculo, por ser arranque o cableado sin lógica de negocio:
  `OncontrolBackendApplication`, `DataSeeder`, `SecurityConfig`, `WebConfig`, `OpenApiConfiguration`.
  Sonar usa exactamente las mismas exclusiones.
- Verificado: con `-Djacoco.line.minimum=0.80` el build termina en `BUILD FAILURE`.

## Métricas

| Métrica | Inicial | feat/clinical-tests | feat/bdd |
|---|---|---|---|
| Pruebas unitarias | 0 | 182 | 191 |
| Escenarios BDD aprobados | 0 | — | 21/21 (87 steps) |
| Pruebas de integración (Testcontainers) | 0 | — | 6 + 21 escenarios |
| Cobertura de líneas (unit + IT) | 0 % | 74.7 % | 76.2 % (1834/2408) |
| Duración `mvn verify` local | — | ~15 s | ~1 min 15 s (incluye 2 contenedores PostgreSQL) |

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
| 6 | `Profile.generateProfileId` | `profileId = "DOC-"/"PAT-" + System.currentTimeMillis()` con restricción `unique`: dos altas en el mismo milisegundo colisionan | Error 500 intermitente al registrar perfiles concurrentemente |

Las pruebas de `ReportService` solo verifican los valores que realmente se calculan
(totales, distribución por tipo, series mensuales); no se fijan las cifras constantes.

El defecto #1 está confirmado contra PostgreSQL real en `ClinicalRepositoriesIT.countSevereSymptoms_currentlyNeverMatches_defect1`.
