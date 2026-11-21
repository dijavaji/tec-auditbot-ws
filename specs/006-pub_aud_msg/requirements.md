# Requirements — US-06 Publicación de Auditoría de Chat vía REST (`pub_aud_msg`)

## Propósito

Definir la exposición de un endpoint REST seguro (`POST /api/v1/audits/conversations`) en el microservicio `tec-auditbot-ws` para recibir conversaciones completas de chatbots, autenticar y validar la petición, y publicar el evento de auditoría directamente en el broker RabbitMQ (`auditbot.exchange` con routing key `conversation.audit.requested`) de forma asíncrona, durable y protegida contra fallos.

Esta especificación elimina la necesidad de exponer credenciales de RabbitMQ en el cliente frontend (browser), centraliza las políticas de CORS, y garantiza un desacoplamiento seguro entre los canales de chat y el procesamiento de auditoría.

---

## Glosario

- **BFF / Ingestion Gateway:** compuerta de entrada HTTP del microservicio que recibe peticiones del frontend o de sistemas cliente y las encola hacia el broker interno.
- **Endpoint de Ingesta:** ruta HTTP `POST /api/v1/audits/conversations` expuesta para recibir el payload de una conversación terminada.
- **Token de Autenticación (Bearer JWT):** credencial provista en la cabecera HTTP `Authorization: Bearer <token>` requerida para autorizar la ingesta.
- **Conversación Válida:** payload JSON que cumple con la estructura `AuditConversationDto` (incluye `chatId`, `assistantId`, `startedAt`, `endedAt` coherente y lista ordenada no vacía de `messages`).
- **Envelope del Evento:** objeto `ConversationAuditRequestDto` que encapsula la conversación agregando metadatos (`eventId`, `schemaVersion="1.0"`, `occurredAt`, `producer`).
- **Publisher Confirm:** mecanismo de confirmación asíncrona de RabbitMQ donde el broker notifica recepción exitosa (`ack`) o fallo (`nack`).
- **Respuesta Asíncrona (202 Accepted):** código de estado HTTP que certifica que la conversación fue recibida, validada y encolada para su procesamiento posterior.
- **Contenido Sensible:** cuerpo íntegro del diálogo, tokens de autenticación o credenciales de conexión del broker.

---

## Requisitos funcionales

### A. Endpoint REST y Recepción de Peticiones

## R1 — Exposición del endpoint de ingesta
El sistema DEBE exponer el endpoint HTTP `POST ${ec.com.technoloqie.auditbot.api.prefix}/audits/conversations` aceptando y retornando el tipo de contenido `application/json`.

**Verificación:** `AuditChatRestControllerTest#shouldExposePostConversationsEndpoint`.

## R2 — Estructura del cuerpo de la petición
CUANDO se envía una petición al endpoint de ingesta, el sistema DEBE recibir como cuerpo un JSON deserializable a `AuditConversationDto` que contenga `chatId`, `assistantId`, `startedAt`, opcionalmente `endedAt`, y la lista `messages`.

**Verificación:** `AuditChatRestControllerTest#shouldAcceptValidConversationPayload`.

## R3 — Generación de metadatos del evento
CUANDO se recibe una conversación válida, el sistema DEBE generar un identificador único `eventId` (UUID v4), asignar `schemaVersion = "1.0"`, registrar `occurredAt` con la hora UTC actual, y asignar `producer` configurado para conformar el `ConversationAuditRequestDto`.

**Verificación:** `ConversationAuditSubmissionServiceImplTest#shouldConstructEnvelopeWithRequiredMetadata`.

---

### B. Validación de Petición y Manejo de Errores

## R4 — Validación exhaustiva del contrato de conversación
CUANDO se procesa la conversación recibida, el sistema DEBE validar que `chatId`, `assistantId`, `startedAt` y `messages` no sean nulos ni vacíos, que los roles pertenezcan al catálogo permitido (`USER`, `ASSISTANT`, `SYSTEM`), y que los mensajes mantengan estricto orden cronológico por `occurredAt`.

**Verificación:** `ConversationAuditSubmissionServiceImplTest#shouldValidateConversationUsingContractValidator`.

## R5 — Rechazo por cuerpo inválido o incompleto
SI el payload de la conversación carece de campos obligatorios, contiene mensajes fuera de orden o roles no permitidos ENTONCES el sistema DEBE rechazar la petición con código `HTTP 400 Bad Request` y un cuerpo estructurado con la lista de campos inválidos en `invalidFields`.

**Verificación:** `AuditChatRestControllerTest#shouldReturn400WhenPayloadIsInvalid`.

## R6 — Rechazo por cuerpo JSON malformado
SI el cuerpo de la petición HTTP no es un JSON válido o no puede deserializarse ENTONCES el sistema DEBE retornar código `HTTP 400 Bad Request` con un mensaje descriptivo sin exponer trazas internas.

**Verificación:** `AuditChatRestControllerTest#shouldReturn400WhenJsonIsMalformed`.

---

### C. Autenticación y Autorización

## R7 — Exigencia de cabecera `Authorization`
SI una petición a `POST /api/v1/audits/conversations` no incluye la cabecera `Authorization` con esquema `Bearer` ENTONCES el sistema DEBE retornar código `HTTP 401 Unauthorized`.

**Verificación:** `AuditChatRestControllerTest#shouldReturn401WhenAuthorizationHeaderIsMissing`.

## R8 — Validación del token Bearer
SI el token provisto en la cabecera `Authorization` está expirado, malformado o su firma es inválida ENTONCES el sistema DEBE rechazar la petición con código `HTTP 401 Unauthorized`.

**Verificación:** `AuditChatRestControllerTest#shouldReturn401WhenTokenIsInvalid`.

## R9 — Aceptación con token válido
CUANDO una petición incluye un token Bearer válido, el sistema DEBE permitir el acceso al endpoint y procesar la solicitud.

**Verificación:** `AuditChatRestControllerTest#shouldAllowAccessWhenTokenIsValid`.

---

### D. Publicación en RabbitMQ y Confirmación

## R10 — Delegación al puerto de publicación
CUANDO la conversación es válida y autenticada, el sistema DEBE delegar la publicación del evento `ConversationAuditRequestDto` al puerto `IConversationAuditPublisher`.

**Verificación:** `ConversationAuditSubmissionServiceImplTest#shouldDelegateToAuditPublisher`.

## R11 — Enrutamiento y persistencia en RabbitMQ
CUANDO el sistema publica el evento en RabbitMQ, el sistema DEBE enviarlo al exchange `auditbot.exchange` con routing key `conversation.audit.requested`, delivery mode `2` (PERSISTENTE) y `content_type=application/json`.

**Verificación:** `ConversationAuditSubmissionServiceImplTest#shouldPublishWithCorrectExchangeAndRoutingKey`.

## R12 — Respuesta de confirmación exitosa (HTTP 202)
CUANDO la publicación en RabbitMQ se confirma exitosamente con publisher confirm positivo, el sistema DEBE retornar código `HTTP 202 Accepted` con un cuerpo JSON que contenga `eventId`, `chatId`, `status = "ACCEPTED"`, mensaje descriptivo y marca temporal `submittedAt`.

**Verificación:** `AuditChatRestControllerTest#shouldReturn202AcceptedWithSubmissionDetails`.

## R13 — Manejo de fallo en broker (HTTP 502 Bad Gateway)
SI la publicación hacia RabbitMQ falla por broker inaccesible, confirm negativo, timeout o mensaje devuelto ENTONCES el sistema DEBE capturar la excepción `AuditPublicationException`, registrar el error en logs del servidor y responder al cliente con código `HTTP 502 Bad Gateway` y un mensaje de error controlado sin exponer detalles de infraestructura.

**Verificación:** `AuditChatRestControllerTest#shouldReturn502WhenRabbitMqFails`.

---

### E. Configuración y Políticas de CORS

## R14 — Habilitación de CORS para el cliente chatbot
El sistema DEBE permitir peticiones de origen cruzado (CORS) provenientes de la URL configurada en `${ec.com.technoloqie.chatbot.app.url}` para los métodos `GET`, `POST`, `OPTIONS` y cabeceras `Authorization` y `Content-Type`.

**Verificación:** `AuditChatRestControllerTest#shouldSupportCorsForConfiguredOrigin`.

## R15 — Respuestas preflight OPTIONS
CUANDO el cliente envía una petición preflight `OPTIONS` a `/api/v1/audits/conversations`, el sistema DEBE responder con código `HTTP 200 OK` (o `204 No Content`) e incluir las cabeceras `Access-Control-Allow-Origin`, `Access-Control-Allow-Methods` y `Access-Control-Allow-Headers`.

**Verificación:** `AuditChatRestControllerTest#shouldHandlePreflightOptionsRequest`.

---

### F. Observabilidad y Privacidad

## R16 — Registro estructurado de la ingesta REST
CUANDO concluye una petición de ingesta (sea exitosa o con error), el sistema DEBE emitir un log estructurado con `etapa=REST_AUDIT_SUBMISSION`, `chatId`, `eventId`, código de estado HTTP y resultado (`OK` o `ERROR`).

**Verificación:** `ConversationAuditSubmissionServiceImplTest#shouldLogStructuredEventWithoutPayload`.

## R17 — Protección de contenido sensible en logs y respuestas
El sistema NO DEBE imprimir en logs de servidor ni en respuestas de error HTTP el contenido de los mensajes de la conversación (`messages[].content`) ni tokens de autorización.

**Verificación:** `ConversationAuditSubmissionServiceImplTest#shouldNotExposePrivateChatContentInLogs`.

---

## Requisitos no funcionales

## R18 — Arquitectura en capas limpia
El controlador REST `AuditChatRestController` NO DEBE depender de clases del paquete `integration.rabbitmq` ni importar tipos de Spring AMQP; DEBE comunicarse exclusivamente mediante la interfaz de servicio `IConversationAuditSubmissionService`.

**Verificación:** `ArchitectureRulesTest#shouldEnforceControllerLayerBoundaries`.

## R19 — Inyección exclusiva por constructor
Todas las nuevas clases de servicio, controladores y configuración DEBEN utilizar inyección de dependencias por constructor y NO DEBEN utilizar la anotación `@Autowired` en atributos.

**Verificación:** `ArchitectureRulesTest#shouldEnforceConstructorInjection`.

## R20 — API Stateless sin persistencia de sesión HTTP
El sistema DEBE configurar la seguridad web con política de creación de sesiones `SessionCreationPolicy.STATELESS`.

**Verificación:** `SecurityConfigurationTest#shouldConfigureStatelessSessionManagement`.

## R21 — Desactivación de CSRF en endpoints API
El sistema DEBE deshabilitar la protección CSRF para las rutas de la API bajo `${ec.com.technoloqie.auditbot.api.prefix}/**`.

**Verificación:** `SecurityConfigurationTest#shouldDisableCsrfForApiRoutes`.

## R22 — Build y verificación integral verde
CUANDO se ejecuta `./mvnw clean verify` con Java 17, el sistema DEBE compilar exitosamente y completar el 100% de las pruebas unitarias, de integración y de arquitectura sin fallos ni regresiones.

**Verificación:** Ejecución integral del pipeline de build Maven.

---

## Matriz de trazabilidad

| Criterio de la historia enriquecida | Requirements cubiertos |
|---|---|
| Recepción segura del payload (`POST /api/v1/audits/conversations`) | R1, R2, R3, R4, R5, R6 |
| Validación de autenticación y token | R7, R8, R9, R20, R21 |
| Publicación y enrutamiento en RabbitMQ | R10, R11 |
| Confirmación al cliente (HTTP 202) y manejo de errores (HTTP 400, 401, 502) | R5, R6, R7, R8, R12, R13 |
| Prevención de problemas de CORS | R14, R15 |
| Observabilidad segura y límites arquitectónicos | R16, R17, R18, R19, R22 |

---

## Fuera de alcance

- Consumo del mensaje y procesamiento de analyzers (cubierto por US-02 a US-05).
- Servidor de autenticación OAuth2 / emisión de tokens (los tokens son emitidos externamente).
- Persistencia síncrona en base de datos antes de RabbitMQ.
