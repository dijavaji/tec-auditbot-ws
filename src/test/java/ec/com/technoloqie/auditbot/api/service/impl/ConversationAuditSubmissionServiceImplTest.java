package ec.com.technoloqie.auditbot.api.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ec.com.technoloqie.auditbot.api.commons.exception.AuditPublicationException;
import ec.com.technoloqie.auditbot.api.commons.exception.InvalidAuditRequestException;
import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;
import ec.com.technoloqie.auditbot.api.dto.AuditMessageDto;
import ec.com.technoloqie.auditbot.api.dto.ConversationAuditRequestDto;
import ec.com.technoloqie.auditbot.api.dto.ConversationAuditSubmissionResponseDto;
import ec.com.technoloqie.auditbot.api.service.ConversationAuditRequestValidator;
import ec.com.technoloqie.auditbot.api.service.IConversationAuditPublisher;

@ExtendWith(MockitoExtension.class)
class ConversationAuditSubmissionServiceImplTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-11-25T10:00:00Z");
    private static final String PRODUCER = "smart-chatbot-rest";

    @Mock
    private IConversationAuditPublisher publisher;

    @Mock
    private ConversationAuditRequestValidator validator;

    private Clock clock;
    private ConversationAuditSubmissionServiceImpl submissionService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        submissionService = new ConversationAuditSubmissionServiceImpl(publisher, validator, clock, PRODUCER);
    }

    @Test
    void shouldConstructEnvelopeAndDelegateToPublisher() {
        AuditMessageDto message = new AuditMessageDto(
                "msg-1",
                "USER",
                "Hola, necesito ayuda",
                FIXED_INSTANT
        );
        AuditConversationDto conversation = new AuditConversationDto(
                "chat-123",
                "asst-1",
                FIXED_INSTANT,
                FIXED_INSTANT.plusSeconds(30),
                List.of(message)
        );

        ConversationAuditSubmissionResponseDto response = submissionService.submitConversation(conversation);

        assertNotNull(response);
        assertNotNull(response.eventId());
        assertEquals("chat-123", response.chatId());
        assertEquals("ACCEPTED", response.status());
        assertEquals(FIXED_INSTANT, response.submittedAt());

        ArgumentCaptor<ConversationAuditRequestDto> captor = ArgumentCaptor.forClass(ConversationAuditRequestDto.class);
        verify(validator).validate(captor.capture());
        verify(publisher).publish(captor.getValue());

        ConversationAuditRequestDto captured = captor.getValue();
        assertEquals(response.eventId(), captured.eventId());
        assertEquals("1.0", captured.schemaVersion());
        assertEquals(FIXED_INSTANT, captured.occurredAt());
        assertEquals(PRODUCER, captured.producer());
        assertEquals(conversation, captured.conversation());
    }

    @Test
    void shouldPropagateValidationExceptionAndNotPublish() {
        AuditConversationDto conversation = new AuditConversationDto(
                "",
                "asst-1",
                FIXED_INSTANT,
                null,
                List.of()
        );

        doThrow(new InvalidAuditRequestException(List.of("conversation.chatId", "conversation.messages")))
                .when(validator).validate(any());

        InvalidAuditRequestException ex = assertThrows(InvalidAuditRequestException.class,
                () -> submissionService.submitConversation(conversation));

        assertEquals(List.of("conversation.chatId", "conversation.messages"), ex.getInvalidFields());
        verify(publisher, never()).publish(any());
    }

    @Test
    void shouldPropagateBrokerPublicationException() {
        AuditMessageDto message = new AuditMessageDto("msg-1", "USER", "¿Precios?", FIXED_INSTANT);
        AuditConversationDto conversation = new AuditConversationDto("chat-999", "asst-1", FIXED_INSTANT, null, List.of(message));

        doThrow(new AuditPublicationException(UUID.randomUUID(), "broker no disponible"))
                .when(publisher).publish(any());

        assertThrows(AuditPublicationException.class, () -> submissionService.submitConversation(conversation));
    }
}
