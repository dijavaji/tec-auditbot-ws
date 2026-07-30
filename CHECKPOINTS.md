# CHECKPOINTS — Evaluación del estado final

> En sistemas multi-agente no se evalúa el camino, se evalúa el destino.
> Estos son los checkpoints objetivos que un juez (humano o IA) puede usar
> para decidir si el proyecto está sano.

## C1 — El arnés está completo

- [ ] Existen los archivos base: `AGENTS.md`, `feature_list.json`,
      `progress/current.md`.
- [ ] Existen los docs: `docs/structure.md`, `docs/backend-standards.md`,
      `docs/verification.md`, `docs/product.md`, `docs/tech.md`.
- [ ] Existe `ai-specs/doc/spec.md` con la definición del proceso SDD.
- [ ] `./mvnw clean install -Dmaven.test.skip=true` termina con exit code 0.

## C2 — El estado es coherente

- [ ] Como mucho una feature en `in_progress` en `feature_list.json`.
- [ ] Toda feature `done` tiene tests asociados que pasan con `./mvnw test`.
- [ ] `progress/current.md` está vacío (plantilla limpia) o describe la sesión activa
      (no contiene basura de sesiones anteriores).
- [ ] Los estados en `feature_list.json` son válidos:
      `pending`, `history_ready`, `in_progress`, `done`, `blocked`.

## C3 — El código respeta la arquitectura

- [ ] `src/main/java/ec/com/technoloqie/auditbot/api/` solo contiene los
      paquetes previstos en `docs/structure.md`:
      `controller`, `service`, `analyzer`, `integration`, `repository`,
      `model`, `dto`, `mapper`, `config`, `commons`.
- [ ] El flujo respeta Controller → Service → Analyzer / Repository. No se saltan capas.
- [ ] Los analyzers no importan Spring, JPA ni RabbitMQ — son lógica pura.
- [ ] Los controllers son delgados: no tienen lógica de negocio ni acceso directo a repositorios.
- [ ] `@Transactional` solo aparece en la capa `service`.
- [ ] Se usa constructor injection exclusivamente — no hay `@Autowired` en fields.
- [ ] No hay `System.out.println()` sueltos para debug — se usa `@Slf4j`.
- [ ] No hay `e.printStackTrace()` — se usa `log.error("mensaje", e)`.
- [ ] No hay TODOs sin contexto (cada TODO incluye quién y por qué).
- [ ] No hay dependencias en `pom.xml` sin uso real en el código.

## C4 — La verificación es real

- [ ] `src/test/java/` tiene al menos un test por cada service y analyzer
      en `src/main/java/`.
- [ ] Los tests de repositorio usan `@DataJpaTest` con H2 embebido,
      no mocks del `EntityManager`.
- [ ] Los tests de RabbitMQ usan `@Testcontainers`.
- [ ] Los analyzers se testean con JUnit 5 + Mockito puro (sin contexto Spring).
- [ ] `./mvnw clean verify` muestra > 0 tests y todos verdes.

## C5 — La sesión se cerró bien

- [ ] No hay archivos sin trackear sospechosos (`*.class`, `*.tmp`,
      `target/` fuera del `.gitignore`).
- [ ] `progress/history.md` tiene una entrada por la última sesión completada.
- [ ] La última feature trabajada está reflejada en su estado correcto
      en `feature_list.json`.
- [ ] `progress/current.md` está limpio (plantilla vacía).

## C6 — Spec Driven Development

- [ ] Toda feature con `"sdd": true` en estado `history_ready`, `in_progress`
      o `done` tiene su carpeta `specs/<name>/` con los 3 archivos:
      `requirements.md`, `design.md`, `tasks.md`.
- [ ] `requirements.md` usa EARS estricto (ver `ai-specs/doc/spec.md`).
- [ ] Toda feature `done` con `"sdd": true` tiene todas sus tasks marcadas
      `[x]` en `tasks.md`.
- [ ] Cada `R<n>` de `requirements.md` está cubierto por al menos un test
      concreto en `src/test/java/`.
- [ ] El mapa de trazabilidad existe en `progress/impl_<name>.md`.

---

**Cómo usar este archivo:** un agente revisor (`ai-specs/agents/reviewer.md`)
recorre cada checkbox, marca `[x]` o `[ ]`, y rechaza el cierre de sesión
si quedan boxes vacíos en C1-C6.
