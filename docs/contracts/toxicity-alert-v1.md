# Contrato: ToxicityAlertMessage v1

Productor: Clinical Service (`ServiceBusToxicityAlertPublisher`).
Consumidor: Azure Function `toxicityAlert` (`functions/toxicity-alerts`).
Transporte: Service Bus, cola `toxicity-alerts`.

## Mensaje de Service Bus

| Propiedad | Valor |
|---|---|
| `MessageId` | `eventId` (permite detección de duplicados si el namespace la soporta) |
| `ContentType` | `application/json` |
| `Subject` | `ToxicityAlert` |
| Application property `schemaVersion` | `1` |
| Application property `severity` | `SEVERA` \| `CRITICA` |

## Cuerpo (JSON)

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `schemaVersion` | int | sí | Siempre `1` en esta versión |
| `eventId` | string (UUID) | sí | Identificador único del evento (idempotencia) |
| `reportedAt` | string ISO-8601 instante (UTC) | sí | Momento del reporte; base de la medición del SLA de 60 s |
| `symptomId` | long | sí | Síntoma reportado |
| `symptomName` | string | no | Nombre del síntoma |
| `severity` | string | sí | `SEVERA` o `CRITICA` (CTCAE grado 3–4, RN-01) |
| `occurrenceDate` | string `yyyy-MM-dd` | no | Fecha del síntoma |
| `occurrenceTime` | string `HH:mm:ss` | no | Hora del síntoma |
| `patientProfileId` | long | sí | Paciente (id usado en las rutas de la API) |
| `doctorProfileId` | long | no | Oncólogo tratante |
| `doctorName` | string | no | Nombre del oncólogo |
| `doctorEmail` | string | sí | Destinatario de la alerta |
| `caseUrl` | string \| null | no | Enlace al expediente (si `ALERT_CASE_URL_TEMPLATE` está configurado) |

Ejemplo: `functions/toxicity-alerts/src/test/resources/toxicity-alert-v1.json`.

## Reglas

- **Minimización de datos:** no viaja el nombre del paciente ni texto clínico libre; el médico abre el caso con `caseUrl`/`patientProfileId`.
- **Compatibilidad:** el consumidor ignora campos desconocidos. Agregar campos opcionales no cambia la versión; quitar o cambiar el tipo de un campo obligatorio exige `schemaVersion: 2`.
- **Mensajes inválidos:** el consumidor los rechaza (excepción) → reintentos (`maxDeliveryCount: 5`) → *dead-letter queue*.
- **Pruebas que fijan el contrato:** `ServiceBusToxicityAlertPublisherTest` (productor) y `ToxicityAlertProcessorTest` (consumidor, con el JSON de ejemplo).
