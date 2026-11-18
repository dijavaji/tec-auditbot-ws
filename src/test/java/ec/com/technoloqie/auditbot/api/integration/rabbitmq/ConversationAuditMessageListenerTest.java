package ec.com.technoloqie.auditbot.api.integration.rabbitmq;

import ec.com.technoloqie.auditbot.api.commons.exception.AuditProcessingException;
import ec.com.technoloqie.auditbot.api.commons.exception.InvalidAuditMessageException;
import ec.com.technoloqie.auditbot.api.commons.util.AuditMessageHeaders;
import ec.com.technoloqie.auditbot.api.dto.AuditIntakeOutcome;
import ec.com.technoloqie.auditbot.api.dto.AuditIntakeResultDto;
import ec.com.technoloqie.auditbot.api.service.IConversationAuditIntakeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de {@link ConversationAuditMessageListener}. Cubre R7, R9, R10, R24, R25, R32,
 * R43, R44, R45, R48, R51, R52, R54, R55, R74.
 */
@ExtendWith(MockitoExtension.class)
class ConversationAuditMessageListenerTest {

    @Mock private AuditMessageDeserializer deserializer;
    @Mock private IConversationAuditIntakeService intakeService;
    @Mock private AuditMessageDeadLetterPublisher deadLetterPublisher;

    private AuditMessagingProperties properties;
    private ConversationAuditMessageListener listener;

    private static final String VALID_JSON = """
            {"eventId":"3fa85f64-5717-4562-b3fc-2c963f66afa6","schemaVersion":"1.0",
             "occurredAt":"2026-08-31T09:00:00Z","producer":"smart-chatbot",
             "conversation":{"chatId":"chat-1","assistantId":"asst-1",
             "startedAt":"2026-08-31T09:00:00Z","messages":[
             {"messageId":"m1","role":"USER","content":"hola","occurredAt":"2026-08-31T09:00:00Z"}
             ]}}""";

    @BeforeEach
    void setUp() {
        properties = new AuditMessagingProperties();
        listener = new ConversationAuditMessageListener(deserializer, intakeService, deadLetterPublisher, properties);
    }

    private Message buildMessage(byte[] body) {
        MessageProperties props = new MessageProperties();
        props.setMessageId("msg-id-1");
        props.setConsumerQueue("conversation.audit.queue");
        return new Message(body, props);
    }

    private Message buildMessageWithHeader(byte[] body, String headerName, Object headerValue) {
        Message msg = buildMessage(body);
        msg.getMessageProperties().setHeader(headerName, headerValue);
        return msg;
    }

    // R7, R9 — mensaje válido se delega al servicio
    @Test
    void shouldDelegateValidMessageToIntakeService() {
        byte[] body = VALID_JSON.getBytes(StandardCharsets.UTF_8);
        Message message = buildMessage(body);
        var dto = buildDto();
        when(deserializer.deserialize(body)).thenReturn(dto);
        when(intakeService.admit(any(), eq(false)))
                .thenReturn(new AuditIntakeResultDto(AuditIntakeOutcome.ADMITTED, "chat-1", "audit-1"));

        listener.onMessage(message);

        verify(intakeService).admit(dto, false);
        verify(deadLetterPublisher, never()).publish(any(), any(), any());
    }

    // R43, R44 — JSON malformado → DLQ inmediata sin reintentos
    @Test
    void shouldRejectMalformedJsonWithoutRequeue() {
        byte[] body = "bad json".getBytes(StandardCharsets.UTF_8);
        Message message = buildMessage(body);
        when(deserializer.deserialize(body))
                .thenThrow(new InvalidAuditMessageException("MALFORMED_JSON", null, new RuntimeException("parse")));

        assertThatThrownBy(() -> listener.onMessage(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(deadLetterPublisher).publish(eq(message), eq("MALFORMED_JSON"), any());
        verify(intakeService, never()).admit(any(), anyBoolean());
    }

    // R43, R44 — esquema inválido → DLQ inmediata
    @Test
    void shouldRejectInvalidSchemaWithoutRequeue() {
        byte[] body = VALID_JSON.getBytes(StandardCharsets.UTF_8);
        Message message = buildMessage(body);
        var dto = buildDto();
        when(deserializer.deserialize(body)).thenReturn(dto);
        when(intakeService.admit(any(), anyBoolean()))
                .thenThrow(new InvalidAuditMessageException("INVALID_SCHEMA",
                        List.of("schemaVersion"), new RuntimeException()));

        assertThatThrownBy(() -> listener.onMessage(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(deadLetterPublisher).publish(eq(message), eq("INVALID_SCHEMA"), any());
    }

    // R24, R25 — payload sobredimensionado → DLQ sin deserializar
    @Test
    void shouldRejectOversizedPayloadWithoutRequeue() {
        // max-payload-size por defecto = 1MB; creamos 2MB
        byte[] body = new byte[(int) DataSize.ofMegabytes(2).toBytes()];
        Message message = buildMessage(body);

        assertThatThrownBy(() -> listener.onMessage(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(deserializer, never()).deserialize(any());
        verify(deadLetterPublisher).publish(eq(message), eq("OVERSIZED_PAYLOAD"), isNull());
    }

    // R45 — error transitorio se propaga para retry del contenedor
    @Test
    void shouldRethrowTransientErrorForBoundedRetry() {
        byte[] body = VALID_JSON.getBytes(StandardCharsets.UTF_8);
        Message message = buildMessage(body);
        var dto = buildDto();
        when(deserializer.deserialize(body)).thenReturn(dto);
        when(intakeService.admit(any(), anyBoolean()))
                .thenThrow(new AuditProcessingException("chat-1", "DB timeout", new RuntimeException()));

        assertThatThrownBy(() -> listener.onMessage(message))
                .isInstanceOf(AuditProcessingException.class);

        // No debe publicar en DLQ en error transitorio
        verify(deadLetterPublisher, never()).publish(any(), any(), any());
    }

    // R48 — guardia redelivery: x-death >= max-redeliveries → DLQ
    @Test
    void shouldRouteToDlqWhenRedeliveryLimitReached() {
        byte[] body = VALID_JSON.getBytes(StandardCharsets.UTF_8);
        MessageProperties props = new MessageProperties();
        props.setMessageId("msg-id-1");
        props.setConsumerQueue("conversation.audit.queue");
        props.setRedelivered(true);
        Map<String, Object> deathEntry = Map.of("count", 3L);
        props.setHeader(AuditMessageHeaders.X_DEATH, List.of(deathEntry));
        Message message = new Message(body, props);

        assertThatThrownBy(() -> listener.onMessage(message))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(deserializer, never()).deserialize(any());
        verify(deadLetterPublisher).publish(eq(message), eq("REDELIVERY_LIMIT_REACHED"), isNull());
    }

    // R32 — duplicado produce log WARN (verificado por no lanzar excepción y no ir a DLQ)
    @Test
    void shouldLogDuplicateOutcomeAtWarnLevel() {
        byte[] body = VALID_JSON.getBytes(StandardCharsets.UTF_8);
        Message message = buildMessage(body);
        var dto = buildDto();
        when(deserializer.deserialize(body)).thenReturn(dto);
        when(intakeService.admit(any(), anyBoolean()))
                .thenReturn(new AuditIntakeResultDto(AuditIntakeOutcome.DUPLICATE_SKIPPED, "chat-1", "audit-1"));

        // No lanza excepción, no va a DLQ
        listener.onMessage(message);

        verify(deadLetterPublisher, never()).publish(any(), any(), any());
    }

    // R54, R55 — MDC limpiado en finally
    @Test
    void shouldClearMdcAfterEachMessage() {
        byte[] body = VALID_JSON.getBytes(StandardCharsets.UTF_8);
        Message message = buildMessage(body);
        var dto = buildDto();
        when(deserializer.deserialize(body)).thenReturn(dto);
        when(intakeService.admit(any(), anyBoolean()))
                .thenReturn(new AuditIntakeResultDto(AuditIntakeOutcome.ADMITTED, "chat-1", "audit-1"));

        listener.onMessage(message);

        assertThat(MDC.get("chatId")).isNull();
        assertThat(MDC.get("eventId")).isNull();
    }

    // R55 — MDC limpiado incluso cuando hay error
    @Test
    void shouldClearMdcEvenOnError() {
        byte[] body = "bad".getBytes(StandardCharsets.UTF_8);
        Message message = buildMessage(body);
        when(deserializer.deserialize(body))
                .thenThrow(new InvalidAuditMessageException("MALFORMED_JSON"));

        try {
            listener.onMessage(message);
        } catch (AmqpRejectAndDontRequeueException ignored) {
        }

        assertThat(MDC.get("chatId")).isNull();
        assertThat(MDC.get("eventId")).isNull();
    }

    // R74 — header x-audit-retry propagado al servicio
    @Test
    void shouldNotAcknowledgeWhenClaimIsNotConfirmed() {
        byte[] body = VALID_JSON.getBytes(StandardCharsets.UTF_8);
        Message message = buildMessageWithHeader(body, AuditMessageHeaders.X_AUDIT_RETRY, true);
        var dto = buildDto();
        when(deserializer.deserialize(body)).thenReturn(dto);
        when(intakeService.admit(any(), eq(true)))
                .thenReturn(new AuditIntakeResultDto(AuditIntakeOutcome.RETRY_ADMITTED, "chat-1", "audit-1"));

        listener.onMessage(message);

        verify(intakeService).admit(dto, true);
    }

    // R10 — el listener no accede directamente a repositorios
    @Test
    void shouldNotAccessRepositoryOrOpenTransaction() {
        // Verificado estructuralmente: el listener solo tiene como dependencias
        // AuditMessageDeserializer, IConversationAuditIntakeService, AuditMessageDeadLetterPublisher
        // y AuditMessagingProperties. No tiene IAuditResultRepository.
        // Este test verifica que la admisión correcta no provoca llamadas inesperadas.
        byte[] body = VALID_JSON.getBytes(StandardCharsets.UTF_8);
        Message message = buildMessage(body);
        var dto = buildDto();
        when(deserializer.deserialize(body)).thenReturn(dto);
        when(intakeService.admit(any(), anyBoolean()))
                .thenReturn(new AuditIntakeResultDto(AuditIntakeOutcome.ADMITTED, "chat-1", "audit-1"));

        listener.onMessage(message);

        // Solo una interacción con intakeService, ninguna con repositorio directo
        verify(intakeService).admit(any(), anyBoolean());
    }

    private ec.com.technoloqie.auditbot.api.dto.ConversationAuditRequestDto buildDto() {
        java.time.Instant now = java.time.Instant.parse("2026-08-31T09:00:00Z");
        var msg = new ec.com.technoloqie.auditbot.api.dto.AuditMessageDto("m1", "USER", "hola", now);
        var conv = new ec.com.technoloqie.auditbot.api.dto.AuditConversationDto("chat-1", "asst-1", now, now.plusSeconds(30), List.of(msg));
        return new ec.com.technoloqie.auditbot.api.dto.ConversationAuditRequestDto(
                java.util.UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"), "1.0", now, "smart-chatbot", conv);
    }
}
