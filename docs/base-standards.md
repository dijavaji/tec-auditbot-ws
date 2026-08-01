---
description: Este documento contiene todas las reglas y directrices de desarrollo para este proyecto, aplicables a todos los agentes de IA (Claude, Cursor, Codex, Gemini, etc.).
alwaysApply: true
---

# Mapa de navegación para agentes de IA

> Este archivo es el **punto de entrada** para cualquier agente que trabaje en este
> repositorio. NO es una biblia de reglas: es un **mapa**. Lee solo lo que
> necesites cuando lo necesites (divulgación progresiva).

---

## 1. Antes de empezar (obligatorio)

1. Ejecuta `./mvnw clean install -Dmaven.test.skip=true` y verifica que termina sin errores. Si falla, **para**
   y resuelve el entorno antes de tocar código.
2. Lee `progress/current.md` para entender en qué estado quedó la última sesión.
3. Lee `feature_list.json`. Toda feature nueva (`"sdd": true`) pasa por
   **Spec Driven Development** — ver `ai-specs/doc/spec.md` y §4 de este archivo.
4. Lee `ai-specs/doc/spec.md` antes de tocar cualquier spec o feature `sdd: true`.

## 2. Mapa del repositorio

| Archivo / carpeta            | Qué contiene                                                                | Cuándo leerlo |
|------------------------------|-----------------------------------------------------------------------------|---------------|
| `feature_list.json`          | Lista de tareas con estado (`pending` / `history_ready` / `in_progress` / `done` / `blocked`) | Siempre, al empezar |
| `progress/current.md`        | Estado de la sesión actual                                                  | Siempre, al empezar |
| `progress/history.md`        | Bitácora append-only de sesiones anteriores                                 | Si necesitas contexto histórico |
| `specs/<feature>/`           | `requirements.md` + `design.md` + `tasks.md` (Kiro-style)                   | Antes de implementar cualquier feature con `"sdd": true` |
| `tmp/<id>-enriched-us.md`    | Historia de usuario enriquecida (output del skill `refine-stories`)          | Antes de generar el spec Kiro |
| `docs/architecture.md`       | Qué significa "hacer un buen trabajo" en este proyecto                      | Antes de implementar |
| `docs/conventions.md`        | Reglas de estilo, nombres, estructura                                       | Antes de escribir código |
| `ai-specs/doc/spec.md`       | Proceso SDD: EARS notation, los 3 archivos, puerta de aprobación humana     | Antes de redactar o leer un spec |
| `docs/verification.md`       | Cómo verificar que tu trabajo funciona (incluye trazabilidad requirements)  | Antes de declarar una tarea como `done` |
| `CHECKPOINTS.md`             | Criterios objetivos de "estado final correcto"                              | Para auto-evaluarte |
| `ai-specs/agents/`           | Definiciones de subagentes (`leader`, `implementer`, `reviewer`)            | Si orquestas trabajo |
| `.kiro/skills/`              | Skills de Kiro (`refine-stories`)                                           | Para refinar historias de usuario |
| `src/`                       | Código de la aplicación                                                     | Para implementar |
| `tests/`                     | Tests automáticos                                                           | Para verificar |

## 3. Reglas duras (no negociables)

- **Una sola feature a la vez.** No mezcles cambios de varias tareas en la misma sesión.
- **No declares una tarea `done` sin pruebas verdes.** Ejecuta `./mvn` y
  asegúrate de que el bloque de tests pasa al 100%.
- **No saltes la fase de refinamiento.** Toda feature con `"sdd": true` debe
  pasar primero por el skill `refine-stories` antes de generar el spec Kiro.
- **No saltes la puerta de aprobación humana.** El leader detiene el flujo
  en `history_ready` y espera a que el humano ejecute y apruebe el spec.
- **Documenta lo que haces** en `progress/current.md` mientras trabajas, no al final.
- **Deja el repositorio limpio** antes de cerrar la sesión (ver §5).
- **Si no sabes algo, busca en `docs/`** antes de inventarlo.

## 4. Flujo de trabajo (SDD)

```
pending → [refine_stories] → history_ready → ⏸ HUMANO (ejecuta spec Kiro) → in_progress → [implementer → reviewer] → done
```

1. El leader detecta la primera feature `pending` con `"sdd": true`.
2. El leader (o el humano) invoca el skill `/refine-stories <id>`, 
   que enriquece la historia y genera `tmp/<id>-enriched-us.md`.
3. El skill marca el status como `history_ready`.
4. **Pausa.** El humano revisa la historia enriquecida y ejecuta el flujo
   de spec de Kiro (sesión Spec) utilizando la historia enriquecida `tmp/<id>-enriched-us.md` para generar
   `specs/<name>/{requirements,design,tasks}.md`.
5. Una vez aprobado el spec, el humano indica "aprobado" y el leader cambia
   el status a `in_progress` y lanza `implementer`.
6. El implementer ejecuta `tasks.md` una a una, marcándolas `[x]`.
7. El reviewer verifica trazabilidad `R<n>` ↔ test y tasks completas;
   aprueba o rechaza.
8. Si aprueba, el implementer marca `done` y mueve el resumen a
   `progress/history.md`.

## 5. Cierre de sesión (lifecycle)

Antes de terminar:

1. Ejecuta `./init.sh` — todo verde.
2. Si la tarea está acabada: marca `status: "done"` en `feature_list.json`.
3. Mueve el resumen de `progress/current.md` al final de `progress/history.md`.
4. Vacía `progress/current.md` dejando solo la plantilla.
5. No dejes archivos temporales, ni `print()` de debug, ni TODOs sin contexto.

## 6. Si te bloqueas

- Relee la sección relevante de `docs/`.
- Si la herramienta no hace lo que esperas, **no inventes un workaround**:
  documenta el bloqueo en `progress/current.md` y para la sesión.