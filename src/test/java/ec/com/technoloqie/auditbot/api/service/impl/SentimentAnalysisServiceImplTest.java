package ec.com.technoloqie.auditbot.api.service.impl;

import ec.com.technoloqie.auditbot.api.analyzer.SentimentAnalyzer;
import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;
import ec.com.technoloqie.auditbot.api.dto.AuditMessageDto;
import ec.com.technoloqie.auditbot.api.dto.ProcessedSentimentDto;
import ec.com.technoloqie.auditbot.api.dto.SentimentAnalysisResponseDto;
import ec.com.technoloqie.auditbot.api.dto.SentimentEvaluationResultDto;
import ec.com.technoloqie.auditbot.api.dto.SentimentItemClassificationDto;
import ec.com.technoloqie.auditbot.api.integration.openai.ISentimentAiClient;
import ec.com.technoloqie.auditbot.api.model.AuditResult;
import ec.com.technoloqie.auditbot.api.model.AuditSentiment;
import ec.com.technoloqie.auditbot.api.model.AuditStatus;
import ec.com.technoloqie.auditbot.api.model.SentimentLabel;
import ec.com.technoloqie.auditbot.api.model.SentimentTrend;
import ec.com.technoloqie.auditbot.api.repository.IAuditResultRepository;
import ec.com.technoloqie.auditbot.api.repository.IAuditSentimentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para {@link SentimentAnalysisServiceImpl}.
 * Cubre R1, R2, R3, R4, R5, R22, R23, R24, R25, R26, R30, R31, R32, R33.
 */
@ExtendWith(MockitoExtension.class)
class SentimentAnalysisServiceImplTest {

    @Mock
    private ISentimentAiClient aiClient;

    @Mock
    private SentimentAnalyzer analyzer;

    @Mock
    private IAuditResultRepository auditResultRepository;

    @Mock
    private IAuditSentimentRepository auditSentimentRepository;

    private SentimentAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SentimentAnalysisServiceImpl(
                aiClient,
                analyzer,
                auditResultRepository,
                auditSentimentRepository,
                new BigDecimal("0.70"),
                0.40,
                2
        );
    }

    private AuditResult buildAuditRecord(String chatId) {
        AuditResult r = new AuditResult();
        r.setAuditId(UUID.randomUUID().toString());
        r.setChatId(chatId);
        r.setAssistantId("asst-1");
        r.setStatus(AuditStatus.PROCESSING);
        r.setEventId(UUID.randomUUID().toString());
        r.setAttemptCount(1);
        r.setCreatedDate(Instant.now());
        r.setUpdatedDate(Instant.now());
        return r;
    }

    // R1, R2 — Filtrado exclusivo de mensajes de rol USER; exclusión de SYSTEM y ASSISTANT
    @Test
    void shouldFilterOnlyUserMessages() {
        AuditConversationDto conv = new AuditConversationDto(
                "chat-1", "asst-1", Instant.now(), null,
                List.of(
                        new AuditMessageDto("m-1", "SYSTEM", "Session started", Instant.now().minusSeconds(30)),
                        new AuditMessageDto("m-2", "USER", "Hola", Instant.now().minusSeconds(20)),
                        new AuditMessageDto("m-3", "ASSISTANT", "Hola, ¿en qué te ayudo?", Instant.now().minusSeconds(10)),
                        new AuditMessageDto("m-4", "USER", "¿Qué precio tiene?", Instant.now())
                )
        );

        List<AuditMessageDto> userMessages = service.extractUserMessages(conv);

        assertThat(userMessages).hasSize(2);
        assertThat(userMessages.get(0).messageId()).isEqualTo("m-2");
        assertThat(userMessages.get(1).messageId()).isEqualTo("m-4");
    }

    @Test
    void shouldExcludeSystemAndAssistantRoles() {
        AuditConversationDto conv = new AuditConversationDto(
                "chat-1", "asst-1", Instant.now(), null,
                List.of(
                        new AuditMessageDto("m-1", "SYSTEM", "sys", Instant.now()),
                        new AuditMessageDto("m-2", "ASSISTANT", "bot", Instant.now())
                )
        );

        List<AuditMessageDto> userMessages = service.extractUserMessages(conv);
        assertThat(userMessages).isEmpty();
    }

    // R3 — Ordenación cronológica ascendente de mensajes de usuario
    @Test
    void shouldOrderUserMessagesChronologically() {
        Instant t1 = Instant.parse("2026-11-17T10:00:00Z");
        Instant t2 = Instant.parse("2026-11-17T10:05:00Z");
        Instant t3 = Instant.parse("2026-11-17T10:10:00Z");

        AuditConversationDto conv = new AuditConversationDto(
                "chat-1", "asst-1", t1, null,
                List.of(
                        new AuditMessageDto("m-3", "USER", "Tercero", t3),
                        new AuditMessageDto("m-1", "USER", "Primero", t1),
                        new AuditMessageDto("m-2", "USER", "Segundo", t2)
                )
        );

        List<AuditMessageDto> userMessages = service.extractUserMessages(conv);

        assertThat(userMessages).extracting(AuditMessageDto::messageId)
                .containsExactly("m-1", "m-2", "m-3");
    }

    // R4, R22, R23, R24 — Asignación secuencial, persistencia de entidades, actualización y preservación de PROCESSING
    @Test
    void shouldPersistAllSentimentRecords() {
        AuditResult record = buildAuditRecord("chat-full-1");
        when(auditResultRepository.findByChatId("chat-full-1")).thenReturn(Optional.of(record));

        AuditConversationDto conv = new AuditConversationDto(
                "chat-full-1", "asst-1", Instant.now(), null,
                List.of(
                        new AuditMessageDto("m-1", "USER", "Hola", Instant.now().minusSeconds(10)),
                        new AuditMessageDto("m-2", "USER", "Necesito ayuda urgente", Instant.now())
                )
        );

        SentimentAnalysisResponseDto aiResponse = new SentimentAnalysisResponseDto(List.of(
                new SentimentItemClassificationDto("m-1", SentimentLabel.NEUTRAL, new BigDecimal("0.900")),
                new SentimentItemClassificationDto("m-2", SentimentLabel.FRUSTRATED, new BigDecimal("0.850"))
        ));
        when(aiClient.classifySentiment(anyList())).thenReturn(aiResponse);

        SentimentEvaluationResultDto evaluation = new SentimentEvaluationResultDto(
                List.of(
                        new ProcessedSentimentDto("m-1", SentimentLabel.NEUTRAL, new BigDecimal("0.900"), 1),
                        new ProcessedSentimentDto("m-2", SentimentLabel.FRUSTRATED, new BigDecimal("0.850"), 2)
                ),
                true,
                SentimentTrend.WORSENING
        );
        when(analyzer.evaluate(anyList(), any(BigDecimal.class), anyDouble(), anyInt())).thenReturn(evaluation);

        service.analyzeAndPersistSentiment("chat-full-1", conv);

        // Verifica borrado idempotente
        verify(auditSentimentRepository).deleteByAuditResult_AuditId(record.getAuditId());

        // Verifica guardado de entidades
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AuditSentiment>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditSentimentRepository).saveAll(captor.capture());

        List<AuditSentiment> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getMessageId()).isEqualTo("m-1");
        assertThat(saved.get(0).getSequenceOrder()).isEqualTo(1);
        assertThat(saved.get(1).getMessageId()).isEqualTo("m-2");
        assertThat(saved.get(1).getSequenceOrder()).isEqualTo(2);

        // Verifica actualización de AuditResult
        assertThat(record.getFrustrationDetected()).isTrue();
        assertThat(record.getStatus()).isEqualTo(AuditStatus.PROCESSING);
        verify(auditResultRepository).saveAndFlush(record);
    }

    // R5 — Manejo de conversación sin mensajes de usuario
    @Test
    void shouldHandleConversationWithoutUserMessages() {
        AuditResult record = buildAuditRecord("chat-no-user");
        when(auditResultRepository.findByChatId("chat-no-user")).thenReturn(Optional.of(record));

        AuditConversationDto conv = new AuditConversationDto(
                "chat-no-user", "asst-1", Instant.now(), null,
                List.of(new AuditMessageDto("m-1", "SYSTEM", "Welcome", Instant.now()))
        );

        service.analyzeAndPersistSentiment("chat-no-user", conv);

        verify(aiClient, never()).classifySentiment(any());
        verify(auditSentimentRepository, never()).saveAll(any());
        assertThat(record.getFrustrationDetected()).isFalse();
        assertThat(record.getStatus()).isEqualTo(AuditStatus.PROCESSING);
        verify(auditResultRepository).saveAndFlush(record);
    }

    // R25 — Idempotencia en reintentos
    @Test
    void shouldDeletePreviousSentimentsOnRetry() {
        AuditResult record = buildAuditRecord("chat-retry-1");
        when(auditResultRepository.findByChatId("chat-retry-1")).thenReturn(Optional.of(record));

        AuditConversationDto conv = new AuditConversationDto(
                "chat-retry-1", "asst-1", Instant.now(), null,
                List.of(new AuditMessageDto("m-1", "USER", "Hola", Instant.now()))
        );

        when(aiClient.classifySentiment(anyList())).thenReturn(new SentimentAnalysisResponseDto(List.of()));
        when(analyzer.evaluate(anyList(), any(), anyDouble(), anyInt()))
                .thenReturn(new SentimentEvaluationResultDto(List.of(), false, SentimentTrend.STABLE));

        service.analyzeAndPersistSentiment("chat-retry-1", conv);

        verify(auditSentimentRepository).deleteByAuditResult_AuditId(record.getAuditId());
        verify(auditSentimentRepository).flush();
    }

    // R30, R31 — Marcación en FAILED y truncado de error si el cliente de IA lanza excepción
    @Test
    void shouldMarkAuditResultAsFailedOnAiError() {
        AuditResult record = buildAuditRecord("chat-error-1");
        when(auditResultRepository.findByChatId("chat-error-1")).thenReturn(Optional.of(record));

        AuditConversationDto conv = new AuditConversationDto(
                "chat-error-1", "asst-1", Instant.now(), null,
                List.of(new AuditMessageDto("m-1", "USER", "¿Hola?", Instant.now()))
        );

        when(aiClient.classifySentiment(anyList())).thenThrow(new RuntimeException("Connection timeout after 30s"));

        service.analyzeAndPersistSentiment("chat-error-1", conv);

        assertThat(record.getStatus()).isEqualTo(AuditStatus.FAILED);
        assertThat(record.getErrorMessage()).startsWith("[AI_ERROR] Connection timeout after 30s");
        verify(auditSentimentRepository, never()).saveAll(any());
        verify(auditResultRepository).saveAndFlush(record);
    }

    @Test
    void shouldTruncateErrorMessageTo500Characters() {
        AuditResult record = buildAuditRecord("chat-long-error");
        when(auditResultRepository.findByChatId("chat-long-error")).thenReturn(Optional.of(record));

        AuditConversationDto conv = new AuditConversationDto(
                "chat-long-error", "asst-1", Instant.now(), null,
                List.of(new AuditMessageDto("m-1", "USER", "Ayuda", Instant.now()))
        );

        String hugeError = "E".repeat(600);
        when(aiClient.classifySentiment(anyList())).thenThrow(new RuntimeException(hugeError));

        service.analyzeAndPersistSentiment("chat-long-error", conv);

        assertThat(record.getStatus()).isEqualTo(AuditStatus.FAILED);
        assertThat(record.getErrorMessage()).hasSize(500);
        assertThat(record.getErrorMessage()).endsWith("...");
    }

    // Omisión si chatId no existe o si ya está en FAILED
    @Test
    void shouldSkipWhenRecordNotFoundOrAlreadyFailed() {
        when(auditResultRepository.findByChatId("chat-missing")).thenReturn(Optional.empty());
        service.analyzeAndPersistSentiment("chat-missing", null);
        verify(aiClient, never()).classifySentiment(any());

        AuditResult failedRecord = buildAuditRecord("chat-failed");
        failedRecord.setStatus(AuditStatus.FAILED);
        when(auditResultRepository.findByChatId("chat-failed")).thenReturn(Optional.of(failedRecord));
        service.analyzeAndPersistSentiment("chat-failed", null);
        verify(aiClient, never()).classifySentiment(any());
    }
}
