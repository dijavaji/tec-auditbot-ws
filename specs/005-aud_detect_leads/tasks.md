# Tasks — US-05 Identificar leads no calificados (`aud_detect_leads`)

> Ejecutar en orden de dependencia. Cada tarea se marca `[x]` solo después de completar su implementación y sus pruebas asociadas.  
> Exportar `PROFILE=local` y utilizar Java 17 en todos los comandos Maven.

---

## Fase 1 — Modelo de datos y persistencia

- [x] **T1 — Crear la migración Flyway `V4__add_lost_lead_reason.sql`.**
  - Crear `src/main/resources/db/migration/V4__add_lost_lead_reason.sql` añadiendo la columna `lost_lead_reason VARCHAR(500) NULL` a la tabla `audit_result`.
  - **Cubre:** R14.

- [x] **T2 — Actualizar entidad `AuditResult` con campo `lostLeadReason`.**
  - Modificar `model/AuditResult.java` para añadir el atributo `lostLeadReason` mapeado a la columna `lost_lead_reason` con longitud 500.
  - **Cubre:** R15.

- [x] **T3 — Crear pruebas de integración para persistencia de campos de lead.**
  - Crear `src/test/java/ec/com/technoloqie/auditbot/api/repository/AuditResultRepositoryLeadTest.java` con `@DataJpaTest` y perfil H2.
  - Verificar que la migración V4 se aplica limpiamente y que `lostLeadDetected`, `leadScore` y `lostLeadReason` se guardan y leen correctamente.
  - **Cubre:** R14, R15.

---

## Fase 2 — DTOs y Analizador de Negocio Puro

- [x] **T4 — Crear DTOs para evaluación de oportunidades de venta.**
  - Crear `dto/LeadAnalysisResponseDto.java` (record: `commercialIntentDetected`, `leadScore`, `closureAchieved`, `reason`).
  - Crear `dto/LeadEvaluationResultDto.java` (record: `lostLeadDetected`, `leadScore`, `lostLeadReason`).
  - **Cubre:** R6.

- [x] **T5 — Implementar `LeadAnalyzer` (lógica de dominio pura).**
  - Crear `analyzer/LeadAnalyzer.java` libre de dependencias de Spring, JPA, AMQP o Spring AI.
  - Implementar regla de detección de venta perdida: `lostLeadDetected = commercialIntent && !closure`.
  - Implementar caso sin intención comercial (`commercialIntent == false` $\implies$ score 0, reason null).
  - Implementar caso con cierre (`closure == true` $\implies$ `lostLeadDetected = false`, reason null, preserva score).
  - Implementar clamping de `leadScore` en `[0.000, 1.000]` con redondeo a 3 decimales.
  - Implementar saneamiento de espacios y truncado de `lostLeadReason` a 500 caracteres.
  - **Cubre:** R8, R9, R10, R11, R12, R13, R23.

- [x] **T6 — Crear pruebas unitarias exhaustivas en `LeadAnalyzerTest`.**
  - Crear `src/test/java/ec/com/technoloqie/auditbot/api/analyzer/LeadAnalyzerTest.java` (JUnit 5 puro sin contexto Spring).
  - Verificar casos de intención sin cierre, intención con cierre, sin intención comercial, scores fuera de rango (< 0 y > 1), motivos mayores a 500 caracteres, y respuesta nula/vacía.
  - **Cubre:** R8, R9, R10, R11, R12, R13, R23.

---

## Fase 3 — Integración OpenAI con Spring AI (Ollama compatible)

- [x] **T7 — Crear puerto `ILeadDetectionAiClient` y adaptador `OpenAiLeadDetectionAdapter`.**
  - Crear `integration/openai/ILeadDetectionAiClient.java`.
  - Crear `integration/openai/OpenAiLeadDetectionAdapter.java` inyectando `ChatClient` y timeout configurable (`auditbot.ai.openai.timeout-seconds`).
  - Formular prompt de sistema comercial y procesar la conversación en una sola llamada estructurada retornando `LeadAnalysisResponseDto`.
  - Confinar el adaptador y puerto estrictamente a `integration.openai`.
  - **Cubre:** R4, R5, R6, R7, R24.

- [x] **T8 — Crear pruebas unitarias para `OpenAiLeadDetectionAdapterTest`.**
  - Crear `src/test/java/ec/com/technoloqie/auditbot/api/integration/openai/OpenAiLeadDetectionAdapterTest.java`.
  - Probar llamada estructurada exitosa con mock de `ChatClient`.
  - Probar manejo de timeout y fallos de comunicación con propagación controlada de excepción.
  - **Cubre:** R4, R5, R6, R7, R24.

---

## Fase 4 — Servicio de Aplicación, Orquestación y Arquitectura

- [x] **T9 — Implementar `ILeadDetectionService` y `LeadDetectionServiceImpl`.**
  - Crear interfaz `service/ILeadDetectionService.java`.
  - Crear implementación `service/impl/LeadDetectionServiceImpl.java` con `@Service` y `@Transactional`.
  - Implementar filtrado de mensajes `SYSTEM`, caso borde sin mensajes de usuario (persistencia neutra sin llamar a IA).
  - Integrar llamada a `ILeadDetectionAiClient`, evaluación en `LeadAnalyzer`, y actualización atómica en `AuditResult` preservando `status = PROCESSING`.
  - Manejar excepciones de IA marcando `status = FAILED`, registrando `error_message` con prefijo `[AI_ERROR]` y emitiendo log estructurado.
  - **Cubre:** R1, R2, R3, R16, R17, R18, R21, R22.

- [x] **T10 — Extender `AuditPipelineOrchestratorImpl` para incorporar la etapa de leads.**
  - Modificar `AuditPipelineOrchestratorImpl` inyectando `ILeadDetectionService`.
  - Invocar `leadDetectionService.analyzeAndPersistLeads(chatId, conversation)` inmediatamente después del análisis de sentimiento, previa verificación de estado no fallido.
  - **Cubre:** R19, R20.

- [x] **T11 — Crear pruebas unitarias de servicio y orquestación.**
  - Crear `src/test/java/ec/com/technoloqie/auditbot/api/service/impl/LeadDetectionServiceImplTest.java` verificando flujo exitoso, idempotencia, caso sin mensajes de usuario y manejo de error de IA.
  - Actualizar pruebas de orquestación en `AuditPipelineOrchestratorTest` verificando invocación secuencial y omisión si una etapa previa falló.
  - **Cubre:** R1, R2, R3, R16, R17, R18, R19, R20, R21, R22.

- [x] **T12 — Actualizar y verificar reglas de arquitectura en `ArchitectureRulesTest`.**
  - Añadir regla en `ArchitectureRulesTest` asegurando que `LeadAnalyzer` sea lógica pura y que los componentes de IA residan en `integration.openai`.
  - Ejecutar `ArchitectureRulesTest` y asegurar que todas las reglas pasen en verde.
  - **Cubre:** R23, R24.

---

## Fase 5 — Verificación Integral

- [x] **T13 — Ejecutar verificación completa `./mvnw clean verify`.**
  - Ejecutar `./mvnw clean verify` con Java 17 y verificar que el 100% de las pruebas pasen en verde sin regresiones en US-01, US-02, US-03 ni US-04.
  - **Cubre:** R1–R24.
