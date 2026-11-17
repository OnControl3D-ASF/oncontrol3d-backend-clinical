# 🏥 OnControl Backend v2.0
> **Sistema de Gestión Oncológica Integral**

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.x-brightgreen)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://www.oracle.com/java/)
[![Security](https://img.shields.io/badge/Security-JWT-blue)](https://jwt.io/)
[![Deployment](https://img.shields.io/badge/Deployed%20on-Render-000000)](https://oncontrol-backend-api.onrender.com)

OnControl es una plataforma desarrollada en **Java 21** con **Spring Boot**, siguiendo principios de **Domain-Driven Design (DDD)**. Proporciona una API RESTful robusta para conectar Organizaciones Médicas, Doctores y Pacientes en un ecosistema de salud digital.

---

## 🚀 Conectividad del Ecosistema
*   **Backend API:** [https://oncontrol-backend-api.onrender.com](https://oncontrol-backend-api.onrender.com)
*   **Frontend Web:** [https://oncontrol-front-sem.vercel.app](https://oncontrol-front-sem.vercel.app)
*   **Documentación Interactiva:** [Swagger UI](https://oncontrol-backend-api.onrender.com/swagger-ui.html)

---

## ✨ Funcionalidades por Rol

### 🏢 Módulo de Organización (Administrativo)
*   **Dashboard Institucional:** Vista global de médicos activos, pacientes totales y citas programadas.
*   **Gestión de Cuerpo Médico:** Registro de especialistas y asignación de credenciales médicas.
*   **Control de Capacidad:** Monitorización de límites operativos del centro de salud.

### 🩺 Módulo Médico (Clínico)
*   **Seguimiento Oncológico:** Monitoreo de síntomas reportados y evolución de pacientes.
*   **Gestión de Tratamientos:** Creación de protocolos (Quimio, Radio, Inmunoterapia) y control de ciclos.
*   **Agenda Digital:** Programación, confirmación y reprogramación de citas médicas.
*   **Prescripción Farmacéutica:** Registro detallado de medicación y dosis.

### 👤 Módulo del Paciente (Autogestión)
*   **Diario de Salud:** Registro de síntomas diarios con niveles de severidad (Leve a Crítico).
*   **Adherencia Terapéutica:** Marcado de dosis tomadas y recordatorios automáticos.
*   **Expediente Personal:** Acceso a historial clínico, alergias y progreso del tratamiento.

---

## 📊 Módulos Detallados (Bounded Contexts)

### 1. 🔐 Identity & Access Management (IAM)
**Responsabilidad:** Autenticación multi-rol y gestión de organizaciones.
- `POST /api/auth/register/organization` - Registro de organizaciones.
- `POST /api/auth/login` - Login unificado con JWT.

### 2. 👥 Profiles
**Responsabilidad:** Jerarquía de perfiles (Org -> Doc -> Pat).
- `POST /api/organizations/{id}/doctors` - Crear doctor.
- `POST /api/doctors/{id}/patients` - Crear paciente.

### 3. 📅 Appointments
**Responsabilidad:** Gestión de citas y estados (SCHEDULED, CONFIRMED, COMPLETED, CANCELLED).
- `POST /api/appointments/doctor/{docId}/patient/{patId}` - Agendar cita.
- `PATCH /api/appointments/{id}/status` - Cambiar estado.

### 4. 🩺 Symptoms & Monitoring
**Responsabilidad:** Reporte de síntomas y alertas críticas.
- `POST /api/symptoms/patient/{id}` - Reportar síntoma.
- `GET /api/symptoms/patient/{id}/stats` - Estadísticas de severidad.

### 5. 📊 Dashboard & Reports
**Responsabilidad:** Vistas agregadas con filtros dinámicos (Organización por Doctor, Doctor por Paciente).

---

## 🎯 Inicio Rápido (Ejemplos API)

### 1. Registrar Organización
```bash
POST /api/auth/register/organization
{
  "email": "admin@hospital.com",
  "password": "<tu-contraseña>",
  "organizationName": "Hospital Central",
  "country": "México",
  "city": "Ciudad de México"
}
```

### 2. Crear Doctor (Desde Organización)
```bash
POST /api/organizations/1/doctors
{
  "email": "dr.garcia@hospital.com",
  "password": "<tu-contraseña>",
  "firstName": "Carlos",
  "lastName": "García",
  "specialization": "Oncología",
  "licenseNumber": "MED-001"
}
```

### 3. Crear Paciente (Desde Doctor)
```bash
POST /api/doctors/1/patients
{
  "email": "juan.perez@email.com",
  "password": "<tu-contraseña>",
  "firstName": "Juan",
  "lastName": "Pérez",
  "bloodType": "O+",
  "cancerType": "Pulmón"
}
```

---

## 🔐 Cuentas de Demostración

Solo existen en desarrollo local, cuando `APP_DATA_SEEDING_ENABLED=true`, con una base de datos vacía. El seeder está desactivado por defecto y requiere `SEED_USER_PASSWORD`.

### 🏢 Organización (Administrador)
- **Email**: `admin@hospital.com`
- **Contraseña**: la definida en `SEED_USER_PASSWORD`

### 🩺 Médicos
- **Doctor 1**: `dr.garcia@hospital.com`
- **Doctor 2**: `dr.rodriguez@hospital.com`
- **Contraseña**: la definida en `SEED_USER_PASSWORD`

### 👤 Pacientes
- **Paciente 1**: `juan.perez@email.com`
- **Paciente 2**: `ana.martinez@email.com`
- **Paciente 3**: `pedro.lopez@email.com`
- **Paciente 4**: `laura.sanchez@email.com`
- **Contraseña**: la definida en `SEED_USER_PASSWORD`

---

## 🚦 Instalación Local

1. **Base de Datos:**
   ```sql
   CREATE DATABASE OnControlSem;
   ```
2. **Variables de entorno obligatorias:**
   ```bash
   export DB_USERNAME=tu_usuario
   export DB_PASSWORD=<tu-contraseña>
   export JWT_SECRET="$(openssl rand -hex 32)"
   ```
   El servicio no arranca si falta `DB_PASSWORD` o `JWT_SECRET`. No guardes estos valores en el repositorio.
3. **Ejecución:**
   ```bash
   ./mvnw spring-boot:run
   ```

---

## 🔐 Roles y Permisos

| Rol | Descripción | Capacidades Clave |
|-----|-------------|-------------------|
| **ORGANIZATION** | Clínicas/Hospitales | Crear doctores, ver dashboard global |
| **DOCTOR** | Médicos Oncólogos | Crear pacientes, gestionar citas, ver síntomas |
| **PATIENT** | Pacientes | Ver citas, reportar síntomas, marcar medicación |
| **ADMIN** | Super Administrador | Gestión técnica completa |

---

## 🗄️ Estructura de Base de Datos
1. `users`: Datos de acceso de Organizaciones.
2. `profiles`: Datos maestros de personas (Nombre, Email).
3. `doctor_profiles`: Especialidad y licencias.
4. `patient_profiles`: Historial oncológico.
5. `appointments`: Registro de citas médicas.
6. `symptoms`: Log de síntomas reportados.
7. `medical_records`: Historiales clínicos.
8. `medications`: Tratamientos farmacológicos.

---

## 🛠️ Stack Tecnológico
- **Java 21** & **Spring Boot 3.3.x**
- **Spring Security + JWT**
- **Spring Data JPA + MySQL 8.0**
- **Swagger/OpenAPI 3.0**
- **Lombok** & **BCrypt**
- **Maven** (Gestor de dependencias)

---
**Versión:** 2.0.2  
**Estado:** ✅ Operativo y Conectado (Producción Ready)
