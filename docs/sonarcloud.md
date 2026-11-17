# SonarCloud — configuración del Clinical Service

El workflow `.github/workflows/ci.yml` ejecuta el análisis con `sonar:sonar` y
`-Dsonar.qualitygate.wait=true`: si el quality gate queda en rojo, **el job falla**.
Mientras `SONAR_TOKEN` no exista, el paso se omite y el run muestra un warning.

La configuración del proyecto está en `pom.xml` (`sonar.organization`,
`sonar.projectKey`, ruta del reporte JaCoCo y exclusiones de cobertura).

## Pasos manuales (una sola vez)

1. Entrar a <https://sonarcloud.io> con GitHub y verificar que existe la organización
   `oncontrol3d-asf` vinculada a la organización de GitHub `OnControl3D-ASF`.
2. **Analyze new project** → elegir `oncontrol3d-backend-clinical`.
   - La key debe quedar `OnControl3D-ASF_oncontrol3d-backend-clinical` (igual que en `pom.xml`).
3. En el proyecto: **Administration → Analysis Method** → desactivar
   **Automatic Analysis** (si queda activo, el análisis desde CI falla).
4. **My Account → Security** → generar un token y guardarlo en GitHub:
   `Settings → Secrets and variables → Actions → New repository secret`
   - Nombre: `SONAR_TOKEN`
   - Con gh CLI: `gh secret set SONAR_TOKEN -R OnControl3D-ASF/oncontrol3d-backend-clinical`
5. (Opcional) **Quality Gates**: el gate por defecto *Sonar way* exige, sobre código nuevo,
   cobertura ≥ 80 %, duplicación ≤ 3 % y ratings A. Si el equipo define otro gate,
   asignarlo al proyecto en **Administration → Quality Gate**.

## Verificación

Volver a ejecutar el workflow del PR (`gh run rerun <id>`). El step
*SonarCloud analysis (waits for quality gate)* debe terminar con
`QUALITY GATE STATUS: PASSED`.
