package ec.com.technoloqie.auditbot.api.integration.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.connection.CorrelationData.Confirm;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.util.unit.DataSize;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import ec.com.technoloqie.auditbot.api.commons.exception.AuditPublicationException;
import ec.com.technoloqie.auditbot.api.commons.exception.InvalidAuditRequestException;
import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;
import ec.com.technoloqie.auditbot.api.dto.AuditMessageDto;
import ec.com.technoloqie.auditbot.api.dto.ConversationAuditRequestDto;
import ec.com.technoloqie.auditbot.api.service.ConversationAuditRequestValidator;

/**
 * Pruebas unitarias de {@link RabbitMqConversationAuditPublisher}.
 * <p>
 * Usa un {@link ObjectMapper} real para el camino feliz de serialización y mocks
 * únicamente en los límites externos ({@link RabbitTemplate}) o para forzar fallos
 * controlados. Cubre R2, R4, R15-R18, R20, R24, R28, R29.
 */
class RabbitMqConversationAuditPublisherTest {

    private static final String CONTENT_MARKER = "no-debe-aparecer-en-logs-ni-errores";

    private RabbitTemplate rabbitTemplate;
    private ConversationAuditRequestValidator validator;
    private AuditMessagingProperties properties;
    private Clock clock;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        validator = new ConversationAuditRequestValidator();
        properties = new AuditMessagingProperties();
        properties.setExchange("auditbot.exchange");
        properties.setRoutingKey("conversation.audit.requested");
        properties.setQueue("conversation.audit.queue");
        properties.setConfirmTimeout(Duration.ofSeconds(5));
        properties.setMaxPayloadSize(DataSize.ofMegabytes(1));
        clock = Clock.fixed(Instant.parse("2026-08-31T10:00:00Z"), ZoneOffset.UTC);

        Logger logger = (Logger) LoggerFactory.getLogger(RabbitMqConversationAuditPublisher.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(RabbitMqConversationAuditPublisher.class);
        logger.detachAppender(logAppender);
    }

    private RabbitMqConversationAuditPublisher publisherWith(ObjectMapper objectMapper) {
        return new RabbitMqConversationAuditPublisher(rabbitTemplate, objectMapper, validator, properties, clock);
    }

    private ObjectMapper newObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private ConversationAuditRequestDto validRequest() {
        Instant now = Instant.parse("2026-08-31T09:00:00Z");
        AuditMessageDto message = new AuditMessageDto("msg-1", "USER", CONTENT_MARKER, now);
        AuditConversationDto conversation = new AuditConversationDto("chat-123", "assistant-1", now,
                now.plusSeconds(30), List.of(message));
        return new ConversationAuditRequestDto(UUID.randomUUID(), "1.0", now, "smart-chatbot", conversation);
    }

    private void completeSendWithConfirm(boolean ack, String reason) {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new Confirm(ack, reason));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    private void leaveSendPending() {
        doAnswer(invocation -> null)
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void shouldSerializeVersionOneContractExactlyOnce() throws JsonProcessingException {
        ObjectMapper realMapper = newObjectMapper();
        ObjectMapper spyMapper = spy(realMapper);
        completeSendWithConfirm(true, null);

        ConversationAuditRequestDto request = validRequest();
        publisherWith(spyMapper).publish(request);

        verify(spyMapper, times(1)).writeValueAsBytes(request);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(anyString(), anyString(), messageCaptor.capture(), any(CorrelationData.class));
        String json = new String(messageCaptor.getValue().getBody(), StandardCharsets.UTF_8);

        assertThat(json).contains("\"eventId\"", "\"schemaVersion\"", "\"occurredAt\"", "\"producer\"",
                "\"conversation\"", "\"chatId\"", "\"assistantId\"", "\"startedAt\"", "\"endedAt\"", "\"messages\"",
                "\"messageId\"", "\"role\"", "\"content\"");
    }

    @Test
    void shouldSetRequiredAmqpProperties() {
        completeSendWithConfirm(true, null);
        ConversationAuditRequestDto request = validRequest();

        publisherWith(newObjectMapper()).publish(request);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(anyString(), anyString(), messageCaptor.capture(), any(CorrelationData.class));
        Message message = messageCaptor.getValue();

        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getMessageProperties().getContentEncoding()).isEqualTo("UTF-8");
        assertThat(message.getMessageProperties().getDeliveryMode().name()).isEqualTo("PERSISTENT");
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(request.eventId().toString());
        assertThat(message.getMessageProperties().getTimestamp()).isEqualTo(java.util.Date.from(clock.instant()));
        assertThat(message.getMessageProperties().getHeaders().get("schemaVersion")).isEqualTo("1.0");
        assertThat(message.getMessageProperties().getHeaders().get("producer")).isEqualTo("smart-chatbot");
    }

    @Test
    void shouldRejectPayloadLargerThanConfiguredLimit() {
        properties.setMaxPayloadSize(DataSize.ofBytes(5));
        ConversationAuditRequestDto request = validRequest();

        assertThatThrownBy(() -> publisherWith(newObjectMapper()).publish(request))
                .isInstanceOf(InvalidAuditRequestException.class);

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void shouldCompleteAfterPositiveConfirm() {
        completeSendWithConfirm(true, null);
        ConversationAuditRequestDto request = validRequest();

        publisherWith(newObjectMapper()).publish(request);

        verify(rabbitTemplate, times(1))
                .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void shouldFailOnNegativeConfirm() {
        completeSendWithConfirm(false, "NACK del broker");
        ConversationAuditRequestDto request = validRequest();

        assertThatThrownBy(() -> publisherWith(newObjectMapper()).publish(request))
                .isInstanceOf(AuditPublicationException.class)
                .satisfies(e -> assertThat(((AuditPublicationException) e).getEventId()).isEqualTo(request.eventId()))
                .hasMessageNotContaining(CONTENT_MARKER);
    }

    @Test
    void shouldFailWhenConfirmTimesOut() {
        properties.setConfirmTimeout(Duration.ofMillis(150));
        leaveSendPending();
        ConversationAuditRequestDto request = validRequest();

        assertThatThrownBy(() -> publisherWith(newObjectMapper()).publish(request))
                .isInstanceOf(AuditPublicationException.class)
                .hasMessageNotContaining(CONTENT_MARKER);
    }

    @Test
    void shouldWrapSerializationFailure() throws JsonProcessingException {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        JsonProcessingException serializationError = mock(JsonProcessingException.class);
        when(failingMapper.writeValueAsBytes(any())).thenThrow(serializationError);
        ConversationAuditRequestDto request = validRequest();

        assertThatThrownBy(() -> publisherWith(failingMapper).publish(request))
                .isInstanceOf(AuditPublicationException.class)
                .hasCause(serializationError);

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void shouldWrapBrokerSendFailure() {
        doThrow(new AmqpException("fallo de conexión"))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        ConversationAuditRequestDto request = validRequest();

        assertThatThrownBy(() -> publisherWith(newObjectMapper()).publish(request))
                .isInstanceOf(AuditPublicationException.class)
                .hasMessageNotContaining(CONTENT_MARKER);
    }

    @Test
    void shouldLogMetadataWithoutConversationContent() {
        completeSendWithConfirm(true, null);
        ConversationAuditRequestDto request = validRequest();

        publisherWith(newObjectMapper()).publish(request);

        List<String> messages = logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).isNotEmpty();
        assertThat(messages).noneMatch(m -> m.contains(CONTENT_MARKER));
        assertThat(messages).anyMatch(m -> m.contains(request.eventId().toString())
                && m.contains(request.conversation().chatId())
                && m.contains(properties.getExchange())
                && m.contains(properties.getRoutingKey()));
    }

    @Test
    void shouldPropagateFailureWithoutHiddenRetry() {
        completeSendWithConfirm(false, "NACK del broker");
        ConversationAuditRequestDto request = validRequest();

        assertThatThrownBy(() -> publisherWith(newObjectMapper()).publish(request))
                .isInstanceOf(AuditPublicationException.class);

        verify(rabbitTemplate, times(1))
                .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void shouldRestoreInterruptFlagOnInterruption() throws InterruptedException {
        properties.setConfirmTimeout(Duration.ofSeconds(30));
        leaveSendPending();
        ConversationAuditRequestDto request = validRequest();
        RabbitMqConversationAuditPublisher publisher = publisherWith(newObjectMapper());

        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean interruptedFlagObserved = new AtomicBoolean(false);

        Thread worker = new Thread(() -> {
            try {
                publisher.publish(request);
            } catch (AuditPublicationException e) {
                thrown.set(e);
                interruptedFlagObserved.set(Thread.currentThread().isInterrupted());
            }
        });
        worker.start();
        Thread.sleep(200);
        worker.interrupt();
        worker.join(5000);

        assertThat(thrown.get()).isInstanceOf(AuditPublicationException.class);
        assertThat(interruptedFlagObserved.get()).isTrue();
    }
}
