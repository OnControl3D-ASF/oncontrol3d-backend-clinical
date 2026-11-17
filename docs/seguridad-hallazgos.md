# Hallazgos de seguridad — Clinical Service

Fecha: 2026-11-17 · Rama: `feat/devsecops` · Herramientas: Trivy 0.76 (imagen, SCA, Dockerfile),
Semgrep 1.182 (`p/java`), gitleaks 8.30 (reglas por defecto + `.gitleaks.toml`) y revisión manual.

## Política de bloqueo en CI

| Workflow | Herramienta | Bloquea el PR | Solo reporta |
|---|---|---|---|
| `container.yml` | Trivy (imagen) | **Cualquier CRITICAL** (con o sin parche); HIGH con parche; Dockerfile HIGH/CRITICAL | Resto en `trivy-image.json` |
| `sca.yml` | Trivy (dependencias Maven) | **Cualquier CRITICAL**; HIGH con parche | Resto en `trivy.json` |
| `sast.yml` | Semgrep `p/java` | Severidad **ERROR** (máxima de Semgrep, equivale a HIGH/CRITICAL) | WARNING/INFO en `semgrep.json` |
| `secrets.yml` | gitleaks | **Cualquier secreto en el árbol actual** | Historial completo en `gitleaks-history.json` |

Excepciones: `.trivyignore`, con justificación y fecha de vencimiento obligatorias. Al vencer, el gate vuelve a bloquear.

## Hallazgos

| # | Hallazgo | Severidad | Antes | Corrección | Después |
|---|---|---|---|---|---|
| 1 | `JWT_SECRET` con valor por defecto hardcodeado (`${JWT_SECRET:5f61…}`): cualquiera que lea el repo puede firmar tokens válidos (bypass total de autenticación) | CRITICAL | Default en `application.properties` y en 2 commits del historial | Default eliminado; arranque bloqueado si falta, está vacío o tiene < 32 bytes (`RequiredSecretsEnvironmentPostProcessor`, `JwtService.validateSecret`) | 0 en el árbol. **Rotar `JWT_SECRET`** en todo ambiente que haya usado el default (el historial no se puede limpiar sin reescribirlo) |
| 2 | `DB_PASSWORD` con default `postgres` | HIGH | `${DB_PASSWORD:postgres}` | Default eliminado; arranque bloqueado si falta | 0 |
| 3 | Placeholder sin resolver usado como contraseña literal: sin `DB_PASSWORD`, Spring Boot conectaba con el texto `${DB_PASSWORD}` | HIGH | La app intentaba conectar (falla solo si la BD rechaza esa clave) | Validación previa a la creación de beans: `Missing required secrets [DB_PASSWORD]` | Verificado con contenedor: sale con código 1 y mensaje claro |
| 4 | Seeder activo por defecto con 7 cuentas demo con contraseña conocida (`password123`) | HIGH | `app.data.seeding.enabled=true` | Desactivado por defecto (`APP_DATA_SEEDING_ENABLED`); si se activa exige `SEED_USER_PASSWORD` | 0 cuentas con contraseña conocida |
| 5 | Contraseñas publicadas en el README | MEDIUM | `password123` ×6 | Reemplazadas por instrucciones con variables de entorno | 0 |
| 6 | CVE-2026-47884 `spring-webmvc` (RCE vía `XsltView`) | CRITICAL | 6.2.11 (Boot 3.5.6) | Boot 3.5.16 → 6.2.19 (última 6.2, **sin parche**). El fix (7.0.9) exige Spring Boot 4 | **Riesgo aceptado temporal** hasta 2027-02-01: la app no usa `XsltView`. Plan: `docs/plan-migracion-spring-boot-4.md` |
| 7 | CVE-2026-47890 `spring-webmvc` (corrupción de streams SSE con *view fragments*) | CRITICAL | 6.2.11 | Igual que #6 | **Riesgo aceptado temporal**: la app no usa SSE |
| 8 | 6 CVE CRITICAL en `tomcat-embed-core` | CRITICAL | 10.1.4x | Override `tomcat.version=10.1.61` | 0 |
| 9 | CVE-2026-22732 `spring-security-web` | CRITICAL | Boot 3.5.6 | Boot 3.5.16 | 0 |
| 10 | 24 CVE HIGH en dependencias (Jackson, PostgreSQL JDBC, Log4j2, Tomcat…) | HIGH | 24 | Boot 3.5.16 + overrides de `jackson-bom`, `postgresql`, `log4j2` | 0 |
| 11 | Imagen base con JDK completo y paquetes de SO vulnerables | HIGH | `eclipse-temurin:21-jdk`: 78 CVE de SO, 845 MB | Runtime `eclipse-temurin:21-jre-alpine` (multi-stage) | 1 CVE de SO (MEDIUM, sin parche), 408 MB |
| 12 | Contenedor ejecutado como root y sin healthcheck | MEDIUM | `root` | Usuario `app` (uid 10001) y `HEALTHCHECK` sobre `/health` | Verificado `healthy` |
| 13 | gitleaks no detecta defaults de Spring (`${X_PASSWORD:valor}`), por baja entropía | MEDIUM (brecha de detección) | 0 detecciones sobre `main` | Regla propia `spring-secret-placeholder-default` en `.gitleaks.toml` | Detecta las 2 líneas en `main`; 0 en la rama |
| 14 | El gate de imagen solo bloqueaba HIGH/CRITICAL **con parche**: una CRITICAL sin fix pasaba | MEDIUM (política) | `--ignore-unfixed` en el único gate | Gate adicional `--severity CRITICAL` sin `--ignore-unfixed` | Toda CRITICAL bloquea salvo excepción documentada y con vencimiento |

### Resultados de las herramientas

| Herramienta | Antes (`main`) | Después (`feat/devsecops`) |
|---|---|---|
| Trivy SCA (dependencias) | 72 (10 CRITICAL, 24 HIGH) | 2 (2 CRITICAL aceptadas, 0 HIGH) |
| Trivy imagen | 151 (10 CRITICAL, 26 HIGH), 78 de SO | 3 (2 CRITICAL aceptadas, 0 HIGH), 1 de SO |
| Semgrep `p/java` | 0 hallazgos (121 archivos Java) | 0 hallazgos |
| gitleaks (árbol actual) | 2 (con regla propia) | 0 |
| gitleaks (historial) | — | 3 (defaults antiguos; requieren rotación, no bloquean) |

Falso positivo descartado: gitleaks marcó como `generic-api-key` una clave de firma **ficticia** usada solo en
`JwtServiceTest`. Está en el allowlist de `.gitleaks.toml` por valor exacto, no por ruta.

## Acciones manuales pendientes

1. **Rotar `JWT_SECRET`** en todo ambiente desplegado antes de este cambio (Render, AKS). Al rotar se invalidan las sesiones activas.
2. Definir `DB_PASSWORD` y `JWT_SECRET` en cada ambiente (GitHub Environments / Key Vault, Bloques 5 y 7) antes de desplegar esta versión: **sin ellas el servicio no arranca**.
3. Antes del 2027-02-01: ejecutar `docs/plan-migracion-spring-boot-4.md` o renovar la aceptación de riesgo de #6 y #7 con una nueva justificación.
