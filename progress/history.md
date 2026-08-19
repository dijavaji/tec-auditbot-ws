# Historial de sesiones

> Bitácora append-only. Cada sesión agrega un bloque al final.

---

## Sesión: US-01 (`aud_push_msg`) — Publicar solicitud de auditoría genérica

- **Feature:** US-01
- **Estado final:** `done`
- **Resultado:** implementación completa de T1–T13 de `specs/001-aud_push_msg/tasks.md`. `./mvnw test` y `./mvnw clean verify` finalizan con `BUILD SUCCESS`, 37/37 tests en verde.

### Resumen de lo implementado

- DTOs inmutables del contrato `conversation.audit.requested` v1.0 (`ConversationAuditRequestDto`, `AuditConversationDto`, `AuditMessageDto`).
- `ConversationAuditRequestValidator` (lógica pura, sin Spring/RabbitMQ) con 9 reglas de validación.
- Excepciones `InvalidAuditRequestException` y `AuditPublicationException` sin fuga de contenido sensible.
- Topología durable de RabbitMQ (`RabbitMqConfiguration`) y propiedades tipadas (`AuditMessagingProperties`).
- Adaptador de publicación `RabbitMqConversationAuditPublisher` con publisher confirms correlacionados, publisher returns, manejo de NACK/timeout/interrupción/fallos de serialización y envío.
- 37 tests en 8 clases: unitarios, contexto Spring, ArchUnit, e integración contra RabbitMQ real.
- Documentación del contrato operativo en `docs/data-model.md`.

### Desviaciones respecto al diseño original (autorizadas explícitamente por el usuario)

1. **Testcontainers → RabbitMQ real:** Docker no accesible en el entorno de ejecución (`permission denied` en `docker.sock`). Los tests de integración (`RabbitMqTopologyIntegrationTest`, `RabbitMqConversationAuditPublisherIntegrationTest`) usan un broker RabbitMQ real ya disponible en `127.0.0.1:5672`, con nombres de exchange/cola/routing-key únicos por ejecución y limpieza verificada vía management API.
2. **`spring-boot-starter-data-jpa` comentada en `pom.xml`:** el proyecto tenía esta dependencia sin ningún datasource configurado, lo que rompía `TecAuditbotWsApplicationTests.contextLoads` (fallo preexistente, confirmado con `git stash` temporal, no relacionado con US-01). El usuario decidió comentarla ya que ninguna feature implementada la usa aún.
3. **Bean `ObjectMapper` (Jackson 2) agregado en `RabbitMqConfiguration`:** al resolver el punto anterior se destapó que Spring Boot 4 auto-configura por defecto un `ObjectMapper` de Jackson 3, incompatible con el Jackson 2 usado por el contrato de esta feature. Se agregó un bean explícito con `@ConditionalOnMissingBean` para no romper otros contextos (como los de test) que ya provean uno.

### Archivos modificados/creados

Ver matriz completa de trazabilidad R1–R29 → test y detalle de archivos en `progress/impl_aud_push_msg.md`.

### Estado de `feature_list.json`

`US-01` marcada como `"status": "done"`.
