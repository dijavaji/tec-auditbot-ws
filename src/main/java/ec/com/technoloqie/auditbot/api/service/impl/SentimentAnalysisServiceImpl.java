package ec.com.technoloqie.auditbot.api.service.impl;

import ec.com.technoloqie.auditbot.api.analyzer.SentimentAnalyzer;
import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;
import ec.com.technoloqie.auditbot.api.dto.AuditMessageDto;
import ec.com.technoloqie.auditbot.api.dto.ProcessedSentimentDto;
import ec.com.technoloqie.auditbot.api.dto.SentimentAnalysisResponseDto;
import ec.com.technoloqie.auditbot.api.dto.SentimentEvaluationResultDto;
import ec.com.technoloqie.auditbot.api.integration.openai.ISentimentAiClient;
import ec.com.technoloqie.auditbot.api.model.AuditResult;
import ec.com.technoloqie.auditbot.api.model.AuditSentiment;
import ec.com.technoloqie.auditbot.api.model.AuditStatus;
import ec.com.technoloqie.auditbot.api.model.SentimentTrend;
import ec.com.technoloqie.auditbot.api.repository.IAuditResultRepository;
import ec.com.technoloqie.auditbot.api.repository.IAuditSentimentRepository;
import ec.com.technoloqie.auditbot.api.service.ISentimentAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementación transaccional del servicio para análisis de sentimiento,
 * cálculo de tendencia emocional y detección de frustración (US-04).
 */
@Slf4j
@Service
@Transactional
public class SentimentAnalysisServiceImpl implements ISentimentAnalysisService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;
    private static final String AI_ERROR_PREFIX = "[AI_ERROR] ";

    private final ISentimentAiClient aiClient;
    private final SentimentAnalyzer analyzer;
    private final IAuditResultRepository auditResultRepository;
    private final IAuditSentimentRepository auditSentimentRepository;
    private final BigDecimal frustrationScoreThreshold;
    private final double negativeRatioThreshold;
    private final int consecutiveNegativeThreshold;

    public SentimentAnalysisServiceImpl(
            ISentimentAiClient aiClient,
            SentimentAnalyzer analyzer,
            IAuditResultRepository auditResultRepository,
            IAuditSentimentRepository auditSentimentRepository,
            @Value("${auditbot.ai.sentiment.frustration-score-threshold:0.70}") BigDecimal frustrationScoreThreshold,
            @Value("${auditbot.ai.sentiment.negative-ratio-threshold:0.40}") double negativeRatioThreshold,
            @Value("${auditbot.ai.sentiment.consecutive-negative-threshold:2}") int consecutiveNegativeThreshold) {
        this.aiClient = aiClient;
        this.analyzer = analyzer;
        this.auditResultRepository = auditResultRepository;
        this.auditSentimentRepository = auditSentimentRepository;
        this.frustrationScoreThreshold = frustrationScoreThreshold;
        this.negativeRatioThreshold = negativeRatioThreshold;
        this.consecutiveNegativeThreshold = consecutiveNegativeThreshold;
    }

    @Override
    public void analyzeAndPersistSentiment(String chatId, AuditConversationDto conversation) {
        long startTime = System.currentTimeMillis();
        String assistantId = conversation != null ? conversation.assistantId() : "unknown";

        Optional<AuditResult> auditOpt = auditResultRepository.findByChatId(chatId);
        if (auditOpt.isEmpty()) {
            log.warn("chatId={} no encontrado en audit_result — omitiendo análisis de sentimiento", chatId);
            return;
        }

        AuditResult auditRecord = auditOpt.get();
        if (auditRecord.getStatus() == AuditStatus.FAILED) {
            log.warn("chatId={} se encuentra en estado FAILED — omitiendo análisis de sentimiento", chatId);
            return;
        }

        // 1. Extraer y ordenar cronológicamente los mensajes de rol USER
        List<AuditMessageDto> userMessages = extractUserMessages(conversation);

        // Caso borde: sin mensajes de usuario (R5)
        if (userMessages.isEmpty()) {
            auditRecord.setFrustrationDetected(false);
            auditRecord.setUpdatedDate(Instant.now());
            auditRecord.setStatus(AuditStatus.PROCESSING);
            auditResultRepository.saveAndFlush(auditRecord);

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("etapa=SENTIMENT_ANALYSIS resultado=OK chatId={} assistantId={} mensajesEvaluados=0 frustrationDetected=false tendencia={} duracionMs={}",
                    chatId, assistantId, SentimentTrend.STABLE, durationMs);
            return;
        }

        // 2. Invocar clasificación con IA protegida contra fallos
        SentimentAnalysisResponseDto aiResponse;
        try {
            aiResponse = aiClient.classifySentiment(userMessages);
        } catch (Exception ex) {
            long durationMs = System.currentTimeMillis() - startTime;
            handleAiFailure(auditRecord, ex, durationMs);
            return;
        }

        // 3. Evaluar métricas y reglas con el analizador puro
        SentimentEvaluationResultDto evaluation = analyzer.evaluate(
                aiResponse != null ? aiResponse.classifications() : List.of(),
                frustrationScoreThreshold,
                negativeRatioThreshold,
                consecutiveNegativeThreshold
        );

        Instant now = Instant.now();

        // 4. Limpieza defensiva en caso de reintento
        auditSentimentRepository.deleteByAuditResult_AuditId(auditRecord.getAuditId());
        auditSentimentRepository.flush();

        // 5. Persistencia de registros de sentimiento
        if (!evaluation.items().isEmpty()) {
            List<AuditSentiment> sentimentsToSave = evaluation.items().stream()
                    .map(item -> {
                        AuditSentiment entity = new AuditSentiment();
                        entity.setSentimentId(UUID.randomUUID().toString());
                        entity.setAuditResult(auditRecord);
                        entity.setMessageId(item.messageId());
                        entity.setLabel(item.label());
                        entity.setScore(item.score());
                        entity.setSequenceOrder(item.sequenceOrder());
                        entity.setCreatedDate(now);
                        return entity;
                    })
                    .toList();
            auditSentimentRepository.saveAll(sentimentsToSave);
        }

        // 6. Actualizar AuditResult manteniendo estado en PROCESSING
        auditRecord.setFrustrationDetected(evaluation.frustrationDetected());
        auditRecord.setUpdatedDate(now);
        auditRecord.setStatus(AuditStatus.PROCESSING);
        auditResultRepository.saveAndFlush(auditRecord);

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("etapa=SENTIMENT_ANALYSIS resultado=OK chatId={} assistantId={} mensajesEvaluados={} frustrationDetected={} tendencia={} duracionMs={}",
                chatId, assistantId, evaluation.items().size(), evaluation.frustrationDetected(), evaluation.trend(), durationMs);
    }

    /**
     * Extrae exclusivamente los mensajes de rol USER ordenados cronológicamente.
     * Descarta mensajes de rol SYSTEM o ASSISTANT (R1, R2, R3).
     */
    List<AuditMessageDto> extractUserMessages(AuditConversationDto conversation) {
        if (conversation == null || conversation.messages() == null) {
            return List.of();
        }

        return conversation.messages().stream()
                .filter(m -> m != null && m.role() != null && m.role().equalsIgnoreCase("USER"))
                .sorted(Comparator.comparing(AuditMessageDto::occurredAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private void handleAiFailure(AuditResult auditRecord, Exception ex, long durationMs) {
        String rawMessage = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        String formattedError = truncateErrorMessage(AI_ERROR_PREFIX + rawMessage);

        auditRecord.setStatus(AuditStatus.FAILED);
        auditRecord.setErrorMessage(formattedError);
        auditRecord.setUpdatedDate(Instant.now());
        auditResultRepository.saveAndFlush(auditRecord);

        log.error("etapa=SENTIMENT_ANALYSIS resultado=FAILED chatId={} assistantId={} duracionMs={} error={}",
                auditRecord.getChatId(), auditRecord.getAssistantId(), durationMs, formattedError);
    }

    private String truncateErrorMessage(String message) {
        if (message == null) {
            return null;
        }
        if (message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_MESSAGE_LENGTH - 3) + "...";
    }
}
