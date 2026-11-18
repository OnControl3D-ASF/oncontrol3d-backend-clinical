# language: es
Característica: HU11 - Clasificación de Toxicidad y Umbral de Alerta
  Como médico oncólogo tratante
  Quiero que los reportes de toxicidad grado 3 o 4 generen una alerta
  Para intervenir oportunamente

  Regla: Grado CTCAE 3 o 4 dispara alerta; grados 1 y 2 no

    Esquema del escenario: Clasificación del grado CTCAE
      Cuando se clasifica un evento de toxicidad de grado <grado>
      Entonces la severidad resultante es "<severidad>"
      Y el evento <alerta> alerta al médico

      Ejemplos:
        | grado | severidad | alerta     |
        | 1     | LEVE      | no genera  |
        | 2     | MODERADA  | no genera  |
        | 3     | SEVERA    | genera     |
        | 4     | CRITICA   | genera     |

    Esquema del escenario: Grados fuera del rango reportable
      Cuando se clasifica un evento de toxicidad de grado <grado>
      Entonces la clasificación es rechazada

      Ejemplos:
        | grado |
        | 0     |
        | 5     |

  Regla: El reporte grave de un paciente alerta a su oncólogo tratante

    Esquema del escenario: Un reporte grave notifica al médico asignado
      Dado que existe el médico "Carlos Garcia" con el paciente asignado "Juan Perez"
      Cuando "Juan Perez" reporta el síntoma "<síntoma>" con severidad "<severidad>"
      Entonces la respuesta tiene estado 201
      Y "Carlos Garcia" recibe una alerta de toxicidad "<severidad>" por el síntoma "<síntoma>"

      Ejemplos:
        | síntoma             | severidad |
        | Fiebre neutropénica | CRITICA   |
        | Mucositis           | SEVERA    |

    Escenario: Un reporte leve no genera alertas
      Dado que existe el médico "Carlos Garcia" con el paciente asignado "Juan Perez"
      Cuando "Juan Perez" reporta el síntoma "Náuseas" con severidad "LEVE"
      Entonces la respuesta tiene estado 201
      Y "Carlos Garcia" no recibe alertas de toxicidad

    Escenario: La alerta llega solo al médico tratante
      Dado que existe el médico "Carlos Garcia" con el paciente asignado "Juan Perez"
      Y que existe el médico "Ana Rojas" sin pacientes asignados
      Cuando "Juan Perez" reporta el síntoma "Fiebre neutropénica" con severidad "CRITICA"
      Entonces "Carlos Garcia" recibe una alerta de toxicidad "CRITICA" por el síntoma "Fiebre neutropénica"
      Y "Ana Rojas" no recibe alertas de toxicidad
