package ec.com.technoloqie.auditbot.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ec.com.technoloqie.auditbot.api.commons.exception.InvalidAuditRequestException;
import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;
import ec.com.technoloqie.auditbot.api.dto.AuditMessageDto;
import ec.com.technoloqie.auditbot.api.dto.ConversationAuditRequestDto;

/**
 * Pruebas exhaustivas de {@link ConversationAuditRequestValidator}.
 * <p>
 * Verifica R3, R9-R14: rechazo de versión no soportada, campos obligatorios
 * faltantes, roles no permitidos, IDs de mensaje duplicados, mensajes fuera de
 * orden cronológico, intervalo de conversación inválido y lista de mensajes
 * vacía. Ningún caso de error debe reportar {@code content} en el mensaje.
 */
class ConversationAuditRequestValidatorTest {

    private final ConversationAuditRequestValidator validator = new ConversationAuditRequestValidator();

    private ConversationAuditRequestDto validRequest() {
        Instant now = Instant.parse("2026-08-31T10:00:00Z");
        AuditMessageDto message1 = new AuditMessageDto("msg-1", "USER", "hola", now);
        AuditMessageDto message2 = new AuditMessageDto("msg-2", "ASSISTANT", "hola, ¿en qué te ayudo?",
                now.plusSeconds(5));
        AuditConversationDto conversation = new AuditConversationDto("chat-123", "assistant-1", now,
                now.plusSeconds(30), List.of(message1, message2));
        return new ConversationAuditRequestDto(UUID.randomUUID(), "1.0", now, "smart-chatbot", conversation);
    }

    @Test
    void shouldNotThrowForValidRequest() {
        ConversationAuditRequestDto request = validRequest();
        validator.validate(request);
    }

    @Test
    void shouldRejectUnsupportedSchemaVersion() {
        ConversationAuditRequestDto valid = validRequest();
        ConversationAuditRequestDto invalid = new ConversationAuditRequestDto(valid.eventId(), "2.0",
                valid.occurredAt(), valid.producer(), valid.conversation());

        InvalidAuditRequestException exception = catchInvalid(() -> validator.validate(invalid));

        assertThat(exception.getInvalidFields()).contains("schemaVersion");
        assertThat(exception.getMessage()).doesNotContain("hola");
    }

    @Test
    void shouldRejectEveryMissingRequiredField() {
        InvalidAuditRequestException exception = catchInvalid(() -> validator.validate(
                new ConversationAuditRequestDto(null, null, null, null, null)));

        assertThat(exception.getInvalidFields()).contains(
                "eventId", "schemaVersion", "occurredAt", "producer", "conversation");
    }

    @Test
    void shouldRejectMissingConversationRequiredFields() {
        Instant now = Instant.parse("2026-08-31T10:00:00Z");
        AuditConversationDto conversation = new AuditConversationDto(null, null, null, null, null);
        ConversationAuditRequestDto request = new ConversationAuditRequestDto(UUID.randomUUID(), "1.0", now,
                "smart-chatbot", conversation);

        InvalidAuditRequestException exception = catchInvalid(() -> validator.validate(request));

        assertThat(exception.getInvalidFields()).contains(
                "conversation.chatId", "conversation.assistantId", "conversation.startedAt",
                "conversation.messages");
    }

    @Test
    void shouldRejectUnsupportedMessageRole() {
        ConversationAuditRequestDto valid = validRequest();
        Instant now = valid.conversation().startedAt();
        AuditMessageDto badRoleMessage = new AuditMessageDto("msg-1", "MODERATOR", "hola", now);
        AuditConversationDto conversation = new AuditConversationDto(valid.conversation().chatId(),
                valid.conversation().assistantId(), now, now.plusSeconds(30), List.of(badRoleMessage));
        ConversationAuditRequestDto request = new ConversationAuditRequestDto(valid.eventId(),
                valid.schemaVersion(), valid.occurredAt(), valid.producer(), conversation);

        InvalidAuditRequestException exception = catchInvalid(() -> validator.validate(request));

        assertThat(exception.getInvalidFields()).contains("conversation.messages[0].role");
        assertThat(exception.getMessage()).doesNotContain("hola");
    }

    @Test
    void shouldRejectDuplicatedMessageIds() {
        ConversationAuditRequestDto valid = validRequest();
        Instant now = valid.conversation().startedAt();
        AuditMessageDto message1 = new AuditMessageDto("msg-1", "USER", "hola", now);
        AuditMessageDto message2 = new AuditMessageDto("msg-1", "ASSISTANT", "respuesta", now.plusSeconds(5));
        AuditConversationDto conversation = new AuditConversationDto(valid.conversation().chatId(),
                valid.conversation().assistantId(), now, now.plusSeconds(30), List.of(message1, message2));
        ConversationAuditRequestDto request = new ConversationAuditRequestDto(valid.eventId(),
                valid.schemaVersion(), valid.occurredAt(), valid.producer(), conversation);

        InvalidAuditRequestException exception = catchInvalid(() -> validator.validate(request));

        assertThat(exception.getInvalidFields()).contains("conversation.messages");
        assertThat(exception.getMessage()).doesNotContain("respuesta");
    }

    @Test
    void shouldRejectMessagesOutOfOrder() {
        ConversationAuditRequestDto valid = validRequest();
        Instant now = valid.conversation().startedAt();
        AuditMessageDto message1 = new AuditMessageDto("msg-1", "USER", "hola", now.plusSeconds(10));
        AuditMessageDto message2 = new AuditMessageDto("msg-2", "ASSISTANT", "respuesta", now);
        AuditConversationDto conversation = new AuditConversationDto(valid.conversation().chatId(),
                valid.conversation().assistantId(), now, now.plusSeconds(30), List.of(message1, message2));
        ConversationAuditRequestDto request = new ConversationAuditRequestDto(valid.eventId(),
                valid.schemaVersion(), valid.occurredAt(), valid.producer(), conversation);

        InvalidAuditRequestException exception = catchInvalid(() -> validator.validate(request));

        assertThat(exception.getInvalidFields()).contains("conversation.messages");
    }

    @Test
    void shouldRejectEndBeforeStart() {
        ConversationAuditRequestDto valid = validRequest();
        Instant startedAt = valid.conversation().startedAt();
        Instant endedAt = startedAt.minusSeconds(10);
        AuditConversationDto conversation = new AuditConversationDto(valid.conversation().chatId(),
                valid.conversation().assistantId(), startedAt, endedAt, valid.conversation().messages());
        ConversationAuditRequestDto request = new ConversationAuditRequestDto(valid.eventId(),
                valid.schemaVersion(), valid.occurredAt(), valid.producer(), conversation);

        InvalidAuditRequestException exception = catchInvalid(() -> validator.validate(request));

        assertThat(exception.getInvalidFields()).contains("conversation.endedAt");
    }

    @Test
    void shouldRejectConversationWithoutMessages() {
        ConversationAuditRequestDto valid = validRequest();
        AuditConversationDto conversation = new AuditConversationDto(valid.conversation().chatId(),
                valid.conversation().assistantId(), valid.conversation().startedAt(),
                valid.conversation().endedAt(), List.of());
        ConversationAuditRequestDto request = new ConversationAuditRequestDto(valid.eventId(),
                valid.schemaVersion(), valid.occurredAt(), valid.producer(), conversation);

        InvalidAuditRequestException exception = catchInvalid(() -> validator.validate(request));

        assertThat(exception.getInvalidFields()).contains("conversation.messages");
    }

    private InvalidAuditRequestException catchInvalid(Runnable runnable) {
        try {
            runnable.run();
        } catch (InvalidAuditRequestException e) {
            return e;
        }
        throw new AssertionError("Se esperaba InvalidAuditRequestException y no fue lanzada");
    }
}
