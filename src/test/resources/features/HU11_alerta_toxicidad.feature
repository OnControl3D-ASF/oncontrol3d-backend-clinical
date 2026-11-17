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
