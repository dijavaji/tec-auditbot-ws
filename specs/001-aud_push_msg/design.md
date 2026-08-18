# Design — US-01 Publicar solicitud de auditoría genérica

## 1. Resumen

Se implementará un puerto de publicación síncrono respecto del broker, pero asíncrono respecto del procesamiento de auditoría. El llamador entrega un DTO inmutable; el adaptador valida, serializa una sola vez, publica un mensaje persistente y espera únicamente el publisher confirm correlacionado de RabbitMQ.

No se añade endpoint HTTP, consumidor, analyzer ni persistencia JPA. La feature termina al recibir confirm positivo y comprobar que el mensaje no fue devuelto como no enrutable.

## 2. Flujo

```text
Sistema cliente
    |
    v
IConversationAuditPublisher.publish(request)
    |
    v
ConversationAuditRequestValidator
    | válido
    v
ObjectMapper.writeValueAsBytes(request)  [una vez]
    | tamaño permitido
    v
Message + MessageProperties + CorrelationData(eventId)
    |
    v
RabbitTemplate.send(exchange, routingKey, message, correlationData)
    |
    +--> confirm ACK y sin returned message --> éxito
    +--> NACK / returned / timeout / excepción --> AuditPublicationException
```

La declaración de `DirectExchange`, `Queue` y `Binding` ocurre al levantar el contexto Spring mediante beans administrados por `RabbitAdmin`.

## 3. Decisiones de arquitectura

### 3.1 Puerto y adaptador

- El puerto vive en `api.service` y no importa Spring AMQP.
- El adaptador vive en `api.integration.rabbitmq` y concentra todas las APIs RabbitMQ.
- Los DTOs viven en `api.dto` y no dependen de RabbitMQ.
- La validación de reglas del contrato se mantiene separada del transporte para poder probarla sin contexto Spring.
- No se modifica `AuditChatRestController`.

### 3.2 Confirmación y routing

Se usará `RabbitTemplate.send(String, String, Message, CorrelationData)` con publisher confirms correlacionados. El adaptador esperará el future de `CorrelationData` hasta `confirmTimeout` y tratará como fallo:

1. `ack = false`;
2. timeout;
3. interrupción, restaurando el interrupt flag;
4. excepción de serialización o envío;
5. `CorrelationData.getReturned()` no nulo con publicación mandatory.

La configuración habilitará:

```properties
spring.rabbitmq.publisher-confirm-type=correlated
spring.rabbitmq.publisher-returns=true
spring.rabbitmq.template.mandatory=true
```

No se configura retry automático para esta operación.

### 3.3 Durabilidad

La garantía de permanencia usa las cuatro piezas necesarias:

- `DirectExchange("auditbot.exchange", true, false)`;
- `Queue("conversation.audit.queue", true, false, false)`;
- binding con `conversation.audit.requested`;
- `MessageDeliveryMode.PERSISTENT`.

Publisher confirm demuestra aceptación por el broker, no consumo. La prueba de integración verifica además que el mensaje queda en la cola cuando no hay consumidor.

### 3.4 Serialización y límite

El adaptador usa el `ObjectMapper` administrado por Spring para producir `byte[]` UTF-8. Esos mismos bytes se usan para medir el límite y construir el mensaje; no se serializa una segunda vez.

Valor por defecto: `1MB` (1 048 576 bytes). El límite se representa con `DataSize` y se compara mediante `toBytes()`.

### 3.5 Tiempo

Los DTOs usan `Instant`, serializado en ISO-8601 UTC. Un bean `Clock` UTC permite fijar de manera determinista el `timestamp` AMQP durante pruebas. `request.occurredAt` representa la creación lógica del evento; el timestamp AMQP representa el intento de publicación.

### 3.6 Seguridad y observabilidad

- La conexión reutiliza las propiedades estándar `spring.rabbitmq.*` de Spring Boot.
- Producción obtiene host, puerto, usuario, contraseña y SSL desde variables de entorno.
- Ningún log incluye el DTO, JSON, `content` o credenciales.
- Los logs estructurados incluyen resultado, `eventId`, `chatId`, exchange y routing key.
- TLS se habilita por configuración; no se altera trust management desde código.

## 4. Modelo y firmas

### 4.1 DTOs

`ConversationAuditRequestDto.java`:

```java
public record ConversationAuditRequestDto(
        UUID eventId,
        String schemaVersion,
        Instant occurredAt,
        String producer,
        AuditConversationDto conversation) {
}
```

`AuditConversationDto.java`:

```java
public record AuditConversationDto(
        String chatId,
        String assistantId,
        Instant startedAt,
        Instant endedAt,
        List<AuditMessageDto> messages) {
}
```

`AuditMessageDto.java`:

```java
public record AuditMessageDto(
        String messageId,
        String role,
        String content,
        Instant occurredAt) {
}
```

`role` permanece como `String` en la frontera para poder rechazar explícitamente valores distintos de `USER`, `ASSISTANT` y `SYSTEM`. Los records realizan copia defensiva de `messages` mediante `List.copyOf` cuando la lista no es nula; la validación sigue siendo responsable de reportar nulos y vacíos.

### 4.2 Puerto

`IConversationAuditPublisher.java`:

```java
public interface IConversationAuditPublisher {
    void publish(ConversationAuditRequestDto request);
}
```

El método retorna solo después de confirm positivo. Puede lanzar `InvalidAuditRequestException` o `AuditPublicationException`.

### 4.3 Validador

`ConversationAuditRequestValidator.java`:

```java
public final class ConversationAuditRequestValidator {
    public void validate(ConversationAuditRequestDto request);
}
```

Valida, acumulando rutas de campos inválidos sin incluir valores sensibles:

- request y campos obligatorios;
- versión exacta `1.0`;
- strings no blank;
- al menos un mensaje;
- rol permitido;
- `messageId` único;
- orden ascendente no decreciente de `occurredAt`;
- `endedAt >= startedAt` cuando existe.

Lanza una sola `InvalidAuditRequestException` con las rutas inválidas. No importa Spring ni RabbitMQ.

### 4.4 Propiedades

`AuditMessagingProperties.java`:

```java
@ConfigurationProperties(prefix = "auditbot.messaging")
public class AuditMessagingProperties {
    private String exchange = "auditbot.exchange";
    private String routingKey = "conversation.audit.requested";
    private String queue = "conversation.audit.queue";
    private Duration confirmTimeout = Duration.ofSeconds(5);
    private DataSize maxPayloadSize = DataSize.ofMegabytes(1);
    // getters y setters
}
```

Se valida que nombres no estén blank, timeout sea positivo y tamaño sea mayor que cero.

### 4.5 Configuración RabbitMQ

`RabbitMqConfiguration.java` define por constructor/métodos `@Bean`:

```java
DirectExchange auditExchange(AuditMessagingProperties properties);
Queue auditQueue(AuditMessagingProperties properties);
Binding auditRequestBinding(Queue auditQueue, DirectExchange auditExchange,
        AuditMessagingProperties properties);
ConversationAuditRequestValidator conversationAuditRequestValidator();
Clock auditPublicationClock();
```

La clase habilita `AuditMessagingProperties`. Spring Boot proporciona `RabbitTemplate`, `ConnectionFactory`, `RabbitAdmin` y `ObjectMapper`.

### 4.6 Adaptador

`RabbitMqConversationAuditPublisher.java`:

```java
@Component
public final class RabbitMqConversationAuditPublisher
        implements IConversationAuditPublisher {

    public RabbitMqConversationAuditPublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            ConversationAuditRequestValidator validator,
            AuditMessagingProperties properties,
            Clock clock);

    @Override
    public void publish(ConversationAuditRequestDto request);
}
```

Responsabilidades internas:

1. validar el DTO;
2. serializar una vez;
3. validar bytes contra `maxPayloadSize`;
4. construir propiedades AMQP;
5. enviar con `CorrelationData(eventId.toString())`;
6. esperar y evaluar confirm/returned message;
7. registrar metadata segura.

No muta el request y es thread-safe porque todas sus dependencias son inmutables o thread-safe.

### 4.7 Excepciones

`InvalidAuditRequestException.java`:

```java
public final class InvalidAuditRequestException extends RuntimeException {
    public InvalidAuditRequestException(List<String> invalidFields);
    public List<String> getInvalidFields();
}
```

`AuditPublicationException.java`:

```java
public final class AuditPublicationException extends RuntimeException {
    public AuditPublicationException(UUID eventId, String reason);
    public AuditPublicationException(UUID eventId, String reason, Throwable cause);
    public UUID getEventId();
}
```

Los mensajes contienen solo razón técnica y `eventId`; nunca el payload.

## 5. Configuración

### `application.properties`

```properties
auditbot.messaging.exchange=auditbot.exchange
auditbot.messaging.routing-key=conversation.audit.requested
auditbot.messaging.queue=conversation.audit.queue
auditbot.messaging.confirm-timeout=5s
auditbot.messaging.max-payload-size=1MB
spring.rabbitmq.publisher-confirm-type=correlated
spring.rabbitmq.publisher-returns=true
spring.rabbitmq.template.mandatory=true
spring.rabbitmq.template.retry.enabled=false
```

### `application-prod.properties`

```properties
spring.rabbitmq.host=${RABBITMQ_HOST}
spring.rabbitmq.port=${RABBITMQ_PORT:5672}
spring.rabbitmq.username=${RABBITMQ_USERNAME}
spring.rabbitmq.password=${RABBITMQ_PASSWORD}
spring.rabbitmq.ssl.enabled=${RABBITMQ_SSL_ENABLED:false}
```

No se versionan valores reales de credenciales.

## 6. Archivos

### Crear

- `src/main/java/ec/com/technoloqie/auditbot/api/dto/ConversationAuditRequestDto.java`
- `src/main/java/ec/com/technoloqie/auditbot/api/dto/AuditConversationDto.java`
- `src/main/java/ec/com/technoloqie/auditbot/api/dto/AuditMessageDto.java`
- `src/main/java/ec/com/technoloqie/auditbot/api/service/IConversationAuditPublisher.java`
- `src/main/java/ec/com/technoloqie/auditbot/api/service/ConversationAuditRequestValidator.java`
- `src/main/java/ec/com/technoloqie/auditbot/api/integration/rabbitmq/AuditMessagingProperties.java`
- `src/main/java/ec/com/technoloqie/auditbot/api/integration/rabbitmq/RabbitMqConfiguration.java`
- `src/main/java/ec/com/technoloqie/auditbot/api/integration/rabbitmq/RabbitMqConversationAuditPublisher.java`
- `src/main/java/ec/com/technoloqie/auditbot/api/commons/exception/InvalidAuditRequestException.java`
- `src/main/java/ec/com/technoloqie/auditbot/api/commons/exception/AuditPublicationException.java`
- pruebas listadas en la sección 8.

### Modificar

- `pom.xml`: añadir dependencias test fijadas para Testcontainers RabbitMQ y ArchUnit.
- `src/main/resources/application.properties`: propiedades funcionales y publisher confirms.
- `src/main/resources/application-prod.properties`: conexión externa y TLS.
- `docs/data-model.md`: añadir contrato de mensaje `conversation.audit.requested` versión `1.0`.

### No modificar

- `controller/`, `analyzer/`, `model/`, `repository/` y lógica JPA.

## 7. Dependencias

`spring-boot-starter-amqp` y Jackson ya existen. Añadir únicamente para pruebas:

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-rabbitmq</artifactId>
    <version>2.0.5</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>1.4.1</version>
    <scope>test</scope>
</dependency>
```

La imagen de integración se fija como `rabbitmq:4.1.0-management`; Docker debe estar disponible al ejecutar esos tests.

## 8. Estrategia de pruebas

### Unitarias

- `ConversationAuditRequestValidatorTest`: R3, R9–R14.
- `RabbitMqConversationAuditPublisherTest`: R2, R4, R15–R18, R20, R24, R28, R29.
- `AuditMessagingPropertiesTest`: R21, R22.
- `ApplicationRabbitMqConfigurationTest`: R23.
- `ArchitectureRulesTest` con ArchUnit: R25–R27.

Mocks permitidos: `RabbitTemplate`, `ObjectMapper` solo para fallos controlados y future de confirm. El camino feliz de serialización usa `ObjectMapper` real para comprobar bytes concretos.

### Integración

- `RabbitMqTopologyIntegrationTest`: R5–R7.
- `RabbitMqConversationAuditPublisherIntegrationTest`: R1, R3, R8, R16 y R19.

Ambas usan un `RabbitMQContainer` fijado, `@DynamicPropertySource` y ninguna infraestructura compartida externa. Cada prueba purga o usa recursos aislados. La persistencia se demuestra publicando sin listener, consultando el conteo de cola, consumiendo después y verificando cuerpo y `deliveryMode=2`.

### Evidencia final

```bash
./mvnw test
./mvnw clean verify
```

El implementer documentará la matriz final Rn → método de test en `progress/impl_aud_push_msg.md`.

## 9. Manejo de errores

| Condición | Resultado |
|---|---|
| Contrato inválido | `InvalidAuditRequestException`; cero llamadas a RabbitMQ |
| Payload demasiado grande | `InvalidAuditRequestException`; cero llamadas a RabbitMQ |
| Serialización falla | `AuditPublicationException` con causa |
| Envío falla | `AuditPublicationException` con causa |
| Confirm NACK | `AuditPublicationException` con reason del broker saneado |
| Confirm timeout | `AuditPublicationException` |
| Mensaje returned | `AuditPublicationException` con reply code/text saneados |
| Thread interrumpido | restaurar interrupt flag y lanzar `AuditPublicationException` |

## 10. Alternativas descartadas

### HTTP síncrono

Descartado porque acopla al productor con la disponibilidad y latencia de AuditBot AI, contradiciendo el objetivo de producto.

### Fire-and-forget sin confirms

Descartado porque un retorno exitoso de `send` no demuestra que el broker aceptó el mensaje; no satisface los fallos explícitos ni la durabilidad observable.

### Transacciones RabbitMQ

Descartadas porque publisher confirms proporcionan la garantía requerida con menor costo y sin combinar esta historia con una transacción de base de datos.

### Declarar solo el exchange

Descartado porque un exchange no almacena mensajes. La cola durable y el binding son necesarios para conservar solicitudes mientras el consumidor está fuera de servicio.

### Reintentos automáticos

Descartados en US-01 para evitar duplicados y latencias ocultas. El llamador decide una política explícita; US-02 resolverá idempotencia de consumo.

## 11. Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Duplicados por reintento del llamador | `eventId` estable y futura idempotencia por `chatId` en US-02; no afirmar exactly-once. |
| Payload excesivo | límite previo al envío y configurable. |
| Topología incompatible ya existente | fallo visible al declarar; nombres y atributos documentados como contrato operativo. |
| Mensaje confirmado pero no enrutable | mandatory + publisher returns + prueba de binding. |
| Fuga de conversación en logs | logs por whitelist de metadata y prueba de captura. |
| Tests dependientes de entorno | Testcontainers con imagen fijada; fallo explícito si Docker no está disponible. |

## 12. Fuentes técnicas

- El módulo oficial de RabbitMQ para Testcontainers documenta la dependencia `org.testcontainers:testcontainers-rabbitmq:2.0.5`: [Testcontainers for Java — RabbitMQ Module](https://java.testcontainers.org/modules/rabbitmq/).

Contenido técnico externo reformulado para cumplir restricciones de licencia.
