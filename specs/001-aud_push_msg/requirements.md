# Requirements — US-01 Publicar solicitud de auditoría genérica

## Propósito

Definir la publicación asíncrona, durable y verificable de una conversación completa en RabbitMQ. Esta especificación cubre exclusivamente el contrato de salida y su publicación; el consumo y procesamiento corresponden a US-02.

## Glosario

- **Solicitud válida:** evento que cumple el contrato `1.0` y todas las reglas de validación de este documento.
- **Confirm positivo:** publisher confirm de RabbitMQ con `ack = true` y sin devolución del mensaje.
- **Confirm negativo:** publisher confirm de RabbitMQ con `ack = false`.
- **Mensaje devuelto:** publicación que el exchange no pudo enrutar cuando `mandatory` está habilitado.
- **Contenido sensible:** cuerpo JSON completo o valor de `messages[].content`.

## Requisitos funcionales

## R1 — Destino AMQP
CUANDO el llamador publica una solicitud válida, el sistema DEBE enviar el mensaje al exchange `auditbot.exchange` con routing key `conversation.audit.requested`.

**Verificación:** `RabbitMqConversationAuditPublisherIntegrationTest#shouldRouteValidRequestToAuditQueue`.

## R2 — Contrato JSON 1.0
CUANDO el sistema serializa una solicitud válida, el sistema DEBE producir JSON UTF-8 con `eventId`, `schemaVersion`, `occurredAt`, `producer` y `conversation`, incluyendo dentro de la conversación `chatId`, `assistantId`, `startedAt`, `endedAt` cuando exista y `messages` con `messageId`, `role`, `content` y `occurredAt`.

**Verificación:** `RabbitMqConversationAuditPublisherTest#shouldSerializeVersionOneContractExactlyOnce`.

## R3 — Versión del contrato
CUANDO el sistema publica una solicitud, el sistema DEBE usar el valor exacto `1.0` en `schemaVersion`.

**Verificación:** `ConversationAuditRequestValidatorTest#shouldRejectUnsupportedSchemaVersion` y `RabbitMqConversationAuditPublisherIntegrationTest#shouldPublishVersionOneHeadersAndBody`.

## R4 — Propiedades AMQP
CUANDO el sistema crea el mensaje AMQP, el sistema DEBE establecer `contentType=application/json`, `contentEncoding=UTF-8`, `deliveryMode=2`, `messageId=eventId`, `timestamp`, y los headers `schemaVersion` y `producer`.

**Verificación:** `RabbitMqConversationAuditPublisherTest#shouldSetRequiredAmqpProperties`.

## R5 — Exchange durable
CUANDO la aplicación inicia con RabbitMQ disponible, el sistema DEBE declarar `auditbot.exchange` como exchange directo, durable y no auto-delete.

**Verificación:** `RabbitMqTopologyIntegrationTest#shouldDeclareDurableDirectExchange`.

## R6 — Cola durable
CUANDO la aplicación inicia con RabbitMQ disponible, el sistema DEBE declarar `conversation.audit.queue` como cola durable, no exclusiva y no auto-delete.

**Verificación:** `RabbitMqTopologyIntegrationTest#shouldDeclareDurableAuditQueue`.

## R7 — Binding
CUANDO la aplicación inicia con RabbitMQ disponible, el sistema DEBE enlazar `conversation.audit.queue` a `auditbot.exchange` mediante `conversation.audit.requested`.

**Verificación:** `RabbitMqTopologyIntegrationTest#shouldBindAuditQueueWithRequestedRoutingKey`.

## R8 — Persistencia sin consumidor
MIENTRAS no exista un consumidor activo, el sistema DEBE conservar en `conversation.audit.queue` todo mensaje que el broker haya confirmado positivamente.

**Verificación:** `RabbitMqConversationAuditPublisherIntegrationTest#shouldKeepPersistentMessageUntilConsumed`.

## R9 — Campos obligatorios
SI falta `eventId`, `schemaVersion`, `occurredAt`, `producer`, `conversation.chatId`, `conversation.assistantId`, `conversation.startedAt`, `conversation.messages` o cualquier campo obligatorio de un mensaje ENTONCES el sistema DEBE rechazar la solicitud antes de invocar RabbitMQ.

**Verificación:** `ConversationAuditRequestValidatorTest#shouldRejectEveryMissingRequiredField`.

## R10 — Roles permitidos
SI `messages[].role` no es `USER`, `ASSISTANT` o `SYSTEM` ENTONCES el sistema DEBE rechazar la solicitud antes de invocar RabbitMQ.

**Verificación:** `ConversationAuditRequestValidatorTest#shouldRejectUnsupportedMessageRole`.

## R11 — Identificadores de mensaje únicos
SI dos elementos de `conversation.messages` tienen el mismo `messageId` ENTONCES el sistema DEBE rechazar la solicitud antes de invocar RabbitMQ.

**Verificación:** `ConversationAuditRequestValidatorTest#shouldRejectDuplicatedMessageIds`.

## R12 — Orden cronológico
SI `conversation.messages` no está ordenado ascendentemente por `occurredAt` ENTONCES el sistema DEBE rechazar la solicitud antes de invocar RabbitMQ.

**Verificación:** `ConversationAuditRequestValidatorTest#shouldRejectMessagesOutOfOrder`.

## R13 — Intervalo de conversación
SI `conversation.endedAt` es anterior a `conversation.startedAt` ENTONCES el sistema DEBE rechazar la solicitud antes de invocar RabbitMQ.

**Verificación:** `ConversationAuditRequestValidatorTest#shouldRejectEndBeforeStart`.

## R14 — Lista no vacía
SI `conversation.messages` está vacía ENTONCES el sistema DEBE rechazar la solicitud antes de invocar RabbitMQ.

**Verificación:** `ConversationAuditRequestValidatorTest#shouldRejectConversationWithoutMessages`.

## R15 — Tamaño máximo
SI el JSON UTF-8 excede el tamaño máximo configurado ENTONCES el sistema DEBE rechazar la solicitud antes de invocar RabbitMQ.

**Verificación:** `RabbitMqConversationAuditPublisherTest#shouldRejectPayloadLargerThanConfiguredLimit`.

## R16 — Confirmación positiva
CUANDO RabbitMQ devuelve un confirm positivo sin devolución, el sistema DEBE finalizar la publicación sin error.

**Verificación:** `RabbitMqConversationAuditPublisherTest#shouldCompleteAfterPositiveConfirm`.

## R17 — Confirmación negativa
SI RabbitMQ devuelve un confirm negativo ENTONCES el sistema DEBE lanzar `AuditPublicationException` con `eventId` y sin contenido sensible.

**Verificación:** `RabbitMqConversationAuditPublisherTest#shouldFailOnNegativeConfirm`.

## R18 — Timeout de confirmación
SI RabbitMQ no confirma dentro del timeout configurado ENTONCES el sistema DEBE lanzar `AuditPublicationException` con `eventId` y sin contenido sensible.

**Verificación:** `RabbitMqConversationAuditPublisherTest#shouldFailWhenConfirmTimesOut`.

## R19 — Mensaje no enrutable
SI RabbitMQ devuelve el mensaje por no poder enrutarlo ENTONCES el sistema DEBE lanzar `AuditPublicationException` con `eventId` y sin contenido sensible.

**Verificación:** `RabbitMqConversationAuditPublisherIntegrationTest#shouldFailWhenMandatoryMessageIsUnroutable`.

## R20 — Fallo técnico
SI la serialización o el envío a RabbitMQ falla ENTONCES el sistema DEBE lanzar `AuditPublicationException` preservando la causa y sin contenido sensible en el mensaje de error.

**Verificación:** `RabbitMqConversationAuditPublisherTest#shouldWrapSerializationFailure` y `RabbitMqConversationAuditPublisherTest#shouldWrapBrokerSendFailure`.

## Requisitos no funcionales

## R21 — Configuración del timeout
El sistema DEBE permitir configurar el timeout de publisher confirms mediante `auditbot.messaging.confirm-timeout` con valor por defecto `5s`.

**Verificación:** `AuditMessagingPropertiesTest#shouldUseFiveSecondDefaultConfirmTimeout` y `AuditMessagingPropertiesTest#shouldBindCustomConfirmTimeout`.

## R22 — Configuración del límite
El sistema DEBE permitir configurar el tamaño máximo mediante `auditbot.messaging.max-payload-size` con valor por defecto `1MB`.

**Verificación:** `AuditMessagingPropertiesTest#shouldUseOneMegabyteDefaultPayloadLimit` y `AuditMessagingPropertiesTest#shouldBindCustomPayloadLimit`.

## R23 — Configuración externa del broker
El sistema DEBE obtener host, puerto, usuario, contraseña y TLS de las propiedades estándar `spring.rabbitmq.*` sin credenciales hardcodeadas.

**Verificación:** `ApplicationRabbitMqConfigurationTest#shouldBindBrokerConnectionFromEnvironment` y revisión estática de `application*.properties`.

## R24 — Observabilidad segura
CUANDO termina un intento de publicación, el sistema DEBE registrar nivel, resultado, `eventId`, `chatId`, exchange y routing key sin contenido sensible.

**Verificación:** `RabbitMqConversationAuditPublisherTest#shouldLogMetadataWithoutConversationContent`.

## R25 — Sin endpoint síncrono
El sistema NO DEBE crear ni invocar un endpoint HTTP para solicitar la auditoría.

**Verificación:** `ArchitectureRulesTest#shouldNotExposeAuditRequestHttpEndpoint` y revisión de cambios en `controller`.

## R26 — Separación arquitectónica
El código fuera de `integration.rabbitmq` y `config` NO DEBE importar tipos de Spring AMQP o del cliente RabbitMQ.

**Verificación:** `ArchitectureRulesTest#shouldKeepRabbitMqTypesInsideAdapterBoundary`.

## R27 — Sin procesamiento anticipado
CUANDO publica una solicitud, el sistema NO DEBE consumirla, ejecutar analyzers ni persistir resultados de auditoría como parte de esta operación.

**Verificación:** `ArchitectureRulesTest#shouldKeepPublicationIndependentFromAuditProcessing` y revisión del flujo de publicación.

## R28 — Reintentos acotados
SI una publicación falla ENTONCES el sistema NO DEBE iniciar reintentos automáticos infinitos u ocultos.

**Verificación:** `RabbitMqConversationAuditPublisherTest#shouldPropagateFailureWithoutHiddenRetry`.

## R29 — Serialización única por intento
CUANDO se realiza un intento de publicación, el sistema DEBE serializar el cuerpo exactamente una vez.

**Verificación:** `RabbitMqConversationAuditPublisherTest#shouldSerializeVersionOneContractExactlyOnce`.

## Matriz de cobertura de aceptación

| Criterio de la historia enriquecida | Requirements |
|---|---|
| Destino y publicación confirmada | R1, R16, R17, R18, R19, R20 |
| Topología durable | R5, R6, R7 |
| Persistencia sin consumidor | R4, R8 |
| Contrato completo | R2, R3, R4, R9, R10, R11, R12, R13, R14 |
| Validación previa | R9–R15 |
| Fallos explícitos | R17–R20, R28 |
| Sin acoplamiento HTTP | R25, R27 |
| Observabilidad segura | R24 |
| Configuración, seguridad y rendimiento | R15, R21, R22, R23, R26, R29 |

## Fuera de alcance

- Consumo, idempotencia de consumo, reintentos del consumidor y DLQ.
- Ejecución de analyzers o persistencia de resultados.
- Cifrado de campos dentro del payload.
- Garantía exactly-once, alta disponibilidad o escalamiento formal.
