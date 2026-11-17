# language: es
Característica: HU06 - Prescripción y Seguimiento de Tratamientos Oncológicos
  Como médico oncólogo
  Quiero registrar y dar seguimiento a los esquemas de tratamiento
  Para asegurar la continuidad del cuidado

  Antecedentes:
    Dado que existe el médico "Carlos Garcia" con el paciente asignado "Juan Perez"

  Esquema del escenario: Registro de un nuevo esquema terapéutico
    Cuando "Carlos Garcia" registra un tratamiento "<tipo>" de <ciclos> ciclos para "Juan Perez"
    Entonces la respuesta tiene estado 201
    Y el tratamiento queda en estado "ACTIVE" en el ciclo 1
    Y el avance del tratamiento es <avance>%

    Ejemplos:
      | tipo           | ciclos | avance |
      | CHEMOTHERAPY   | 6      | 16     |
      | RADIOTHERAPY   | 4      | 25     |
      | IMMUNOTHERAPY  | 10     | 10     |

  Escenario: Completar el último ciclo finaliza el tratamiento
    Dado que "Juan Perez" tiene un tratamiento "CHEMOTHERAPY" de 2 ciclos
    Cuando "Carlos Garcia" registra la sesión del ciclo 2
    Entonces el tratamiento queda en estado "COMPLETED"
    Y el avance del tratamiento es 100%

  Escenario: Suspender un tratamiento deja registrado el motivo
    Dado que "Juan Perez" tiene un tratamiento "CHEMOTHERAPY" de 6 ciclos
    Cuando "Carlos Garcia" suspende el tratamiento por "Neutropenia febril"
    Entonces el tratamiento queda en estado "SUSPENDED"
    Y las notas del tratamiento incluyen "Neutropenia febril"

  Escenario: No se puede registrar un tratamiento sin tipo
    Cuando "Carlos Garcia" registra un tratamiento sin tipo para "Juan Perez"
    Entonces la respuesta tiene estado 400
    Y "Juan Perez" no tiene tratamientos registrados
