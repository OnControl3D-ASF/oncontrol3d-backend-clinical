# language: es
Característica: HU05 - Consulta de Historial Clínico Oncológico
  Como médico oncólogo
  Quiero consultar el historial clínico del paciente
  Para decidir con datos actualizados

  Antecedentes:
    Dado que existe el médico "Carlos Garcia" con el paciente asignado "Juan Perez"
    Y el historial de "Juan Perez" contiene las entradas:
      | tipo        | fecha      | título                       |
      | DIAGNOSIS   | 2026-03-01 | Adenocarcinoma pulmonar      |
      | TREATMENT   | 2026-04-15 | Inicio de quimioterapia      |
      | TEST_RESULT | 2026-06-01 | TAC de control sin progresión|

  Escenario: El médico asignado consulta la cronología del historial
    Cuando "Carlos Garcia" consulta el historial de "Juan Perez"
    Entonces la respuesta tiene estado 200
    Y el historial se muestra del más reciente al más antiguo:
      | título                        |
      | TAC de control sin progresión |
      | Inicio de quimioterapia       |
      | Adenocarcinoma pulmonar       |

  Escenario: Un médico sin asignación no puede ver el expediente
    Dado que existe el médico "Ana Rojas" sin pacientes asignados
    Cuando "Ana Rojas" consulta el historial de "Juan Perez"
    Entonces la respuesta tiene estado 403

  Escenario: El paciente consulta su propio historial
    Cuando "Juan Perez" consulta el historial de "Juan Perez"
    Entonces la respuesta tiene estado 200
    Y el historial tiene 3 entradas
