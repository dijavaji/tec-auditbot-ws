# Spec Driven Development (SDD)

> Este proyecto sigue un flujo de dos fases: refinamiento de historia →
> spec Kiro (requirements → design → tasks) → code.
> El código no se escribe hasta que el spec está aprobado por un humano.

## Estructura

Cada feature nueva (`"sdd": true` en `feature_list.json`) pasa primero por
un refinamiento de su historia de usuario (skill `refine-stories`) y luego
por la generación de spec Kiro en una carpeta dedicada:

```
tmp/<id>-enriched-us.md           # Historia enriquecida (output de refine-stories)
specs/<feature-name>/
├── requirements.md               # QUÉ se necesita (EARS notation)
├── design.md                     # CÓMO se construirá (decisiones técnicas)
└── tasks.md                      # PASOS concretos a implementar
```

El `feature-name` coincide con el campo `name` de `feature_list.json`.

## Estados de una feature

| Estado           | Significado                                                                  |
|------------------|------------------------------------------------------------------------------|
| `pending`        | Historia de usuario sin refinar. Primer paso: ejecutar `refine-stories`.     |
| `history_ready`  | Historia refinada en `tmp/<id>-enriched-us.md`. Esperando que el humano lance el spec Kiro. |
| `in_progress`    | Spec aprobado. `implementer` trabajando.                                     |
| `done`           | Código verde, `reviewer` aprobó, sesión cerrada.                             |
| `blocked`        | Atascado. Razón en `progress/current.md`.                                    |

## Flujo completo

```
pending → [refine_stories] → history_ready → ⏸ HUMANO (ejecuta spec Kiro) → in_progress → [implementer → reviewer] → done
```

### Fase 1 — Refinamiento de historia (`pending → history_ready`)

1. El humano (o el leader) invoca el skill `/refine-stories <id>`.
2. El skill analiza la historia en `feature_list.json`, la enriquece con
   detalles técnicos (endpoints, archivos, criterios de aceptación
   exhaustivos, requisitos no funcionales).
3. El skill guarda el resultado en `tmp/<id>-enriched-us.md`.
4. El skill cambia el status de la feature a `history_ready`.
5. **Pausa.** El flujo automático se detiene aquí.

### Fase 2 — Spec Kiro (`history_ready → in_progress`)

1. El humano revisa `tmp/<id>-enriched-us.md`.
2. El humano ejecuta el flujo de spec de Kiro (sesión Spec) que genera
   `specs/<name>/{requirements.md, design.md, tasks.md}` a partir de la
   historia enriquecida.
3. Una vez satisfecho con el spec, el humano aprueba y se transiciona el
   status a `in_progress`.

### Fase 3 — Implementación (`in_progress → done`)

1. El `implementer` trabaja a partir de `specs/<name>/tasks.md`.
2. El `reviewer` verifica trazabilidad `R<n>` ↔ test y tasks completas.
3. Si aprueba, se marca `done`.

## requirements.md — EARS estricto

Las requirements se redactan en **EARS** (Easy Approach to Requirements
Syntax). Cada requirement es un párrafo numerado con uno de estos cinco
patrones:

| Patrón         | Plantilla                                                   |
|----------------|-------------------------------------------------------------|
| **Ubicuo**     | `El sistema DEBE <acción>.`                                 |
| **Evento**     | `CUANDO <disparador>, el sistema DEBE <acción>.`            |
| **Estado**     | `MIENTRAS <estado>, el sistema DEBE <acción>.`              |
| **Opcional**   | `DONDE <feature opcional>, el sistema DEBE <acción>.`       |
| **No deseado** | `SI <evento no deseado> ENTONCES el sistema DEBE <acción>.` |

Reglas duras:

- Cada requirement tiene un id estable: `R1`, `R2`, ...
- Cada requirement DEBE ser verificable por al menos un test concreto.
- No mezcles varios `DEBE` en un mismo requirement. Si hay más de uno, parte.
- No uses verbos blandos ("podría", "puede", "soporta"). Solo `DEBE` / `NO DEBE`.

Ejemplo:

```markdown
## R1
CUANDO el usuario ejecuta `python -m src.cli recent`, el sistema DEBE
imprimir hasta 5 notas ordenadas por `created_at` descendente.

## R2
SI el flag `--limit` recibe un valor <= 0 ENTONCES el sistema DEBE
imprimir un mensaje de error en stderr y salir con código != 0.
```

## design.md — decisiones técnicas

Captura **antes** de tocar código:

- Qué archivos se crean / modifican.
- Qué firmas nuevas aparecen (funciones, clases, comandos).
- Qué excepciones se reutilizan o se añaden.
- Qué alternativa se descartó y por qué (mínimo una).

NO es ingeniería desde primeros principios — apóyate en
`docs/architecture.md` y `docs/conventions.md`. El `design.md` documenta los
puntos donde tu feature roza la frontera de esas reglas.

## tasks.md — checklist ejecutable

Pasos discretos en orden, cada uno con checkbox. Cada task referencia al
menos un `R<n>` que cubre.

Ejemplo:

```markdown
- [ ] T1 — Añadir `cmd_recent` en `src/cli.py`. Cubre: R1, R3.
- [ ] T2 — Registrar subparser `recent` con flag `--limit`. Cubre: R1, R2.
- [ ] T3 — Añadir `test_recent_default_limit` en `tests/test_cli.py`. Cubre: R1.
- [ ] T4 — Añadir `test_recent_invalid_limit` en `tests/test_cli.py`. Cubre: R2.
```

El `implementer` marca `[x]` cada task al completarla. El `reviewer`
rechaza si queda alguna `[ ]` sin justificación documentada.

## Trazabilidad (regla dura)

- Cada test en `tests/` debe poder mapearse a un `R<n>` de su spec.
- Cada `R<n>` debe tener al menos un test concreto.
- El `reviewer` comprueba esta correspondencia explícitamente y rechaza
  si falta.

El `implementer` documenta el mapa en `progress/impl_<name>.md`:

```markdown
## Trazabilidad
- R1 → `test_recent_default_limit`
- R2 → `test_recent_invalid_limit`
- R3 → `test_recent_custom_limit`
```

## Cuándo NO aplica SDD

Las features con `"sdd": false` o sin el campo `sdd` (las legacy 1–6) NO
tienen spec. SDD solo se aplica hacia adelante.
