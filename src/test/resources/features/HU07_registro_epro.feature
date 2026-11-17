# language: es
Característica: HU07 - Reporte ePRO de Síntomas y Toxicidad Ambulatoria
  Como paciente oncológico
  Quiero registrar mis síntomas y su toxicidad
  Para que mi médico haga seguimiento proactivo

  Antecedentes:
    Dado que existe el médico "Carlos Garcia" con el paciente asignado "Juan Perez"

  Esquema del escenario: El paciente reporta un síntoma con su severidad
    Cuando "Juan Perez" reporta el síntoma "<síntoma>" con severidad "<severidad enviada>"
    Entonces la respuesta tiene estado 201
    Y el síntoma queda registrado con severidad "<severidad registrada>"
    Y "Carlos Garcia" ve el síntoma "<síntoma>" con fecha y hora en el expediente de "Juan Perez"

    Ejemplos:
      | síntoma  | severidad enviada | severidad registrada |
      | Náuseas  | LEVE              | LEVE                 |
      | Fatiga   | MODERATE          | MODERADA             |
      | Mucositis| SEVERA            | SEVERA               |
      | Fiebre   | CRITICAL          | CRITICA              |

  Escenario: No se aceptan reportes con fecha futura
    Cuando "Juan Perez" reporta el síntoma "Dolor" con fecha de mañana
    Entonces la respuesta tiene estado 400

  Escenario: Otro paciente no puede reportar en un expediente ajeno
    Dado que existe el paciente "Luis Soto" del médico "Carlos Garcia"
    Cuando "Luis Soto" reporta el síntoma "Dolor" con severidad "LEVE" en el expediente de "Juan Perez"
    Entonces la respuesta tiene estado 403
