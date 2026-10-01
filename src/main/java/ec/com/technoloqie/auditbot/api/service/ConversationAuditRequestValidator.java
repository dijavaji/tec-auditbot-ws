package ec.com.technoloqie.auditbot.api.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ec.com.technoloqie.auditbot.api.commons.exception.InvalidAuditRequestException;
import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;
import ec.com.technoloqie.auditbot.api.dto.AuditMessageDto;
import ec.com.technoloqie.auditbot.api.dto.ConversationAuditRequestDto;

/**
 * Valida que una {@link ConversationAuditRequestDto} cumpla el contrato {@code 1.0}
 * y las reglas de negocio de {@code US-01} antes de intentar cualquier publicación.
 * <p>
 * Esta clase es lógica pura: no importa Spring ni el cliente de RabbitMQ, para poder
 * probarse sin levantar contexto. Acumula todas las rutas de campos inválidos y las
 * reporta en una única {@link InvalidAuditRequestException}, sin incluir en ningún
 * momento valores de contenido sensible.
 */
public final class ConversationAuditRequestValidator {

    private static final String SUPPORTED_SCHEMA_VERSION = "1.0";
    private static final Set<String> ALLOWED_ROLES = Set.of("USER", "ASSISTANT", "SYSTEM");

    /**
     * Valida la solicitud y lanza {@link InvalidAuditRequestException} si encuentra
     * al menos una regla incumplida.
     *
     * @param request solicitud a validar, puede ser nula
     */
    public void validate(ConversationAuditRequestDto request) {
        List<String> invalidFields = new ArrayList<>();

        if (request == null) {
            invalidFields.add("request");
            throw new InvalidAuditRequestException(invalidFields);
        }

        validateTopLevelFields(request, invalidFields);
        validateConversation(request.conversation(), invalidFields);

        if (!invalidFields.isEmpty()) {
            throw new InvalidAuditRequestException(invalidFields);
        }
    }

    private void validateTopLevelFields(ConversationAuditRequestDto request, List<String> invalidFields) {
        if (request.eventId() == null) {
            invalidFields.add("eventId");
        }

        if (isBlank(request.schemaVersion())) {
            invalidFields.add("schemaVersion");
        } else if (!SUPPORTED_SCHEMA_VERSION.equals(request.schemaVersion())) {
            invalidFields.add("schemaVersion");
        }

        if (request.occurredAt() == null) {
            invalidFields.add("occurredAt");
        }

        if (isBlank(request.producer())) {
            invalidFields.add("producer");
        }

        if (request.conversation() == null) {
            invalidFields.add("conversation");
        }
    }

    private void validateConversation(AuditConversationDto conversation, List<String> invalidFields) {
        if (conversation == null) {
            return;
        }

        if (isBlank(conversation.chatId())) {
            invalidFields.add("conversation.chatId");
        }

        if (isBlank(conversation.assistantId())) {
            invalidFields.add("conversation.assistantId");
        }

        if (conversation.startedAt() == null) {
            invalidFields.add("conversation.startedAt");
        }

        if (conversation.endedAt() != null && conversation.startedAt() != null
                && conversation.endedAt().isBefore(conversation.startedAt())) {
            invalidFields.add("conversation.endedAt");
        }

        validateMessages(conversation.messages(), invalidFields);
    }

    private void validateMessages(List<AuditMessageDto> messages, List<String> invalidFields) {
        if (messages == null) {
            invalidFields.add("conversation.messages");
            return;
        }

        if (messages.isEmpty()) {
            invalidFields.add("conversation.messages");
            return;
        }

        Set<String> seenMessageIds = new HashSet<>();
        boolean duplicateFound = false;
        boolean outOfOrderFound = false;
        Instant previousOccurredAt = null;

        for (int i = 0; i < messages.size(); i++) {
            AuditMessageDto message = messages.get(i);
            String prefix = "conversation.messages[" + i + "]";

            if (message == null) {
                invalidFields.add(prefix);
                continue;
            }

            if (isBlank(message.messageId())) {
                invalidFields.add(prefix + ".messageId");
            } else if (!duplicateFound && !seenMessageIds.add(message.messageId())) {
                invalidFields.add("conversation.messages");
                duplicateFound = true;
            }

            if (isBlank(message.role())) {
                invalidFields.add(prefix + ".role");
            } else if (!ALLOWED_ROLES.contains(message.role())) {
                invalidFields.add(prefix + ".role");
            }

            if (isBlank(message.content())) {
                invalidFields.add(prefix + ".content");
            }

            if (message.occurredAt() == null) {
                invalidFields.add(prefix + ".occurredAt");
            } else {
                if (!outOfOrderFound && previousOccurredAt != null
                        && message.occurredAt().isBefore(previousOccurredAt)) {
                    invalidFields.add("conversation.messages");
                    outOfOrderFound = true;
                }
                previousOccurredAt = message.occurredAt();
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
