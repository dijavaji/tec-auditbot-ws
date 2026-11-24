package ec.com.technoloqie.auditbot.api.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;
import ec.com.technoloqie.auditbot.api.dto.ConversationAuditRequestDto;
import ec.com.technoloqie.auditbot.api.dto.ConversationAuditSubmissionResponseDto;
import ec.com.technoloqie.auditbot.api.service.ConversationAuditRequestValidator;
import ec.com.technoloqie.auditbot.api.service.IConversationAuditPublisher;
import ec.com.technoloqie.auditbot.api.service.IConversationAuditSubmissionService;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementación del servicio de admisión y publicación de conversaciones de auditoría vía REST.
 */
@Service
@Slf4j
public class ConversationAuditSubmissionServiceImpl implements IConversationAuditSubmissionService {

    private static final String SCHEMA_VERSION_1_0 = "1.0";
    private static final String DEFAULT_STATUS = "ACCEPTED";
    private static final String DEFAULT_SUCCESS_MESSAGE = "Solicitud de auditoría recibida y enviada a procesamiento asíncrono";

    private final IConversationAuditPublisher publisher;
    private final ConversationAuditRequestValidator validator;
    private final Clock clock;
    private final String producer;

    public ConversationAuditSubmissionServiceImpl(
            IConversationAuditPublisher publisher,
            ConversationAuditRequestValidator validator,
            Clock clock,
            @Value("${auditbot.messaging.producer:smart-chatbot-rest}") String producer) {
        this.publisher = publisher;
        this.validator = validator;
        this.clock = clock;
        this.producer = producer;
    }

    @Override
    public ConversationAuditSubmissionResponseDto submitConversation(AuditConversationDto conversation) {
        UUID eventId = UUID.randomUUID();
        Instant now = clock.instant();

        ConversationAuditRequestDto request = new ConversationAuditRequestDto(
                eventId,
                SCHEMA_VERSION_1_0,
                now,
                producer,
                conversation
        );

        validator.validate(request);
        publisher.publish(request);

        String chatId = conversation != null ? conversation.chatId() : "unknown";
        log.info("etapa=REST_AUDIT_SUBMISSION chatId={} eventId={} status=OK", chatId, eventId);

        return new ConversationAuditSubmissionResponseDto(
                eventId,
                chatId,
                DEFAULT_STATUS,
                DEFAULT_SUCCESS_MESSAGE,
                now
        );
    }
}
