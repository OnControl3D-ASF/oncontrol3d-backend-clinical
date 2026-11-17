# Plan de migración a Spring Boot 4

## Motivo

CVE-2026-47884 y CVE-2026-47890 (CRITICAL, `spring-webmvc`) solo están corregidas en Spring Framework
**7.0.9+**, que requiere **Spring Boot 4**. La línea 6.2 (Boot 3.5) no tiene parche: Boot 3.5.16 / Spring 6.2.19
es la última versión disponible y es la que usa el servicio hoy. Mientras tanto, ambas CVE están aceptadas
en `.trivyignore` hasta **2027-02-01** porque la aplicación no usa `XsltView` ni Server-Sent Events.

## Evidencia: prueba real con Boot 4.0.9 (2026-11-17)

Se cambió el *parent* a `spring-boot-starter-parent:4.0.9` en una copia descartable y se ejecutó `mvn verify`:

| Paso | Resultado |
|---|---|
| Compilar código de producción | **OK** (0 errores) |
| Resolver dependencias de test | **Falla**: Testcontainers 2 renombró artefactos (`postgresql` → `testcontainers-postgresql`, `junit-jupiter` → `testcontainers-junit-jupiter`) |
| Compilar tests | **Falla** (14 errores en 2 archivos): `@AutoConfigureMockMvc`, `@DataJpaTest` y `@AutoConfigureTestDatabase` cambiaron de paquete y requieren `spring-boot-starter-webmvc-test` / `spring-boot-starter-data-jpa-test` |
| 191 pruebas unitarias | **OK** tras corregir lo anterior |
| 6 pruebas de repositorio (Testcontainers) | **OK** |
| 21 escenarios BDD (contexto completo) | **Falla: la aplicación no arranca**: `No qualifying bean of type 'com.fasterxml.jackson.databind.ObjectMapper'`, requerido por `MedicalHistoryService` y `TreatmentService` |

**Conclusión:** la migración rompe la compatibilidad. Boot 4 usa **Jackson 3** (`tools.jackson.*`) y ya no
registra el `ObjectMapper` de Jackson 2. Además, Jackson 3 cambia valores por defecto de serialización, lo que
puede alterar el JSON que consume el frontend. Por eso no se migra en este avance.

## Plan

| Fase | Trabajo | Riesgo | Verificación |
|---|---|---|---|
| 0. Red de seguridad | Agregar pruebas de contrato del JSON (snapshot de las respuestas que usa el frontend: auth, pacientes, tratamientos, síntomas, historial) | — | Las pruebas pasan en Boot 3.5 |
| 1. Build y tests | Parent 4.0.x; Testcontainers 2 (artefactos `testcontainers-*`); starters `*-test` modulares y nuevos paquetes; quitar overrides de Tomcat/Jackson/Log4j2/JUnit/Testcontainers | Bajo | `mvn verify` compila |
| 2. Jackson 3 | Migrar `MedicalHistoryService` y `TreatmentService` a `tools.jackson.databind.json.JsonMapper` (o, como paso intermedio, el módulo de compatibilidad Jackson 2 de Boot 4). Revisar `@JsonCreator`/`@JsonValue` de los enums (`SymptomSeverity`, etc.) | **Alto** (contrato JSON) | Pruebas de contrato de la fase 0 sin cambios |
| 3. Dependencias externas | `springdoc-openapi` 2.6 → línea 3.x (compatible con Boot 4); `jjwt`, `spring-dotenv` y `commons-lang3` a versiones compatibles | Medio | `/swagger-ui.html` y `/api-docs` responden |
| 4. Seguridad y configuración | Revisar cambios de Spring Security 7 (DSL de `SecurityConfig`) y propiedades renombradas (usar `spring-boot-properties-migrator` temporalmente) | Medio | BDD completo con JWT real |
| 5. Cierre | Eliminar CVE-2026-47884/47890 de `.trivyignore`; Trivy sin CRITICAL; desplegar en `dev` y validar con el frontend | — | Gates `sca` y `container` en verde sin excepciones |

**Estimación:** 1 sprint (fases 0–2 son el grueso). **Fecha límite:** antes de que venza la excepción (2027-02-01).
Si no se llega, renovar la aceptación de riesgo con nueva justificación y fecha.
