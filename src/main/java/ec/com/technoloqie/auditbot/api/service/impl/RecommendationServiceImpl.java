package ec.com.technoloqie.auditbot.api.service.impl;

import ec.com.technoloqie.auditbot.api.analyzer.RecommendationAnalyzer;
import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;
import ec.com.technoloqie.auditbot.api.dto.AuditFindingsContextDto;
import ec.com.technoloqie.auditbot.api.dto.AuditMessageDto;
import ec.com.technoloqie.auditbot.api.dto.RecommendationAnalysisResponseDto;
import ec.com.technoloqie.auditbot.api.dto.RecommendationEvaluationResultDto;
import ec.com.technoloqie.auditbot.api.dto.RecommendationItemDto;
import ec.com.technoloqie.auditbot.api.integration.openai.IRecommendationAiClient;
import ec.com.technoloqie.auditbot.api.model.AuditGap;
import ec.com.technoloqie.auditbot.api.model.AuditRecommendation;
import ec.com.technoloqie.auditbot.api.model.AuditResult;
import ec.com.technoloqie.auditbot.api.model.AuditStatus;
import ec.com.technoloqie.auditbot.api.repository.IAuditRecommendationRepository;
import ec.com.technoloqie.auditbot.api.repository.IAuditResultRepository;
import ec.com.technoloqie.auditbot.api.service.IRecommendationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementación transaccional del servicio para generar recomendaciones de mejora (US-07).
 * Culmina oficialmente el pipeline de auditoría promoviendo el agregado raíz a COMPLETED.
 */
@Slf4j
@Service
@Transactional
public class RecommendationServiceImpl implements IRecommendationService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;
    private static final String AI_ERROR_PREFIX = "[AI_ERROR] ";

    private final IAuditResultRepository auditResultRepository;
    private final IAuditRecommendationRepository auditRecommendationRepository;
    private final IRecommendationAiClient aiClient;
    private final RecommendationAnalyzer analyzer;

    public RecommendationServiceImpl(
            IAuditResultRepository auditResultRepository,
            IAuditRecommendationRepository auditRecommendationRepository,
            IRecommendationAiClient aiClient,
            RecommendationAnalyzer analyzer) {
        this.auditResultRepository = auditResultRepository;
        this.auditRecommendationRepository = auditRecommendationRepository;
        this.aiClient = aiClient;
        this.analyzer = analyzer;
    }

    @Override
    public void generateAndPersistRecommendations(String chatId, AuditConversationDto conversation) {
        long startTime = System.currentTimeMillis();
        String assistantId = conversation != null ? conversation.assistantId() : "unknown";

        Optional<AuditResult> auditOpt = auditResultRepository.findByChatId(chatId);
        if (auditOpt.isEmpty()) {
            log.warn("chatId={} no encontrado en audit_result — omitiendo recomendaciones", chatId);
            return;
        }

        AuditResult auditRecord = auditOpt.get();
        if (auditRecord.getStatus() == AuditStatus.FAILED) {
            log.warn("chatId={} se encuentra en estado FAILED — omitiendo recomendaciones", chatId);
            return;
        }

        // 1. Limpieza previa por idempotencia en reintentos (R19)
        auditRecommendationRepository.deleteByAuditResult_AuditId(auditRecord.getAuditId());
        auditRecommendationRepository.flush();

        // 2. Consolidación de hallazgos previos (R3)
        AuditFindingsContextDto findings = buildFindingsContext(auditRecord);

        // 3. Conversación limpia sin hallazgos adversos (R5)
        boolean hasAdverseFindings = findings.gapsCount() > 0
                || findings.frustrationDetected()
                || findings.lostLeadDetected();

        if (!hasAdverseFindings) {
            Instant now = Instant.now();
            auditRecord.setStatus(AuditStatus.COMPLETED);
            auditRecord.setAuditedDate(now);
            auditRecord.setUpdatedDate(now);
            auditResultRepository.saveAndFlush(auditRecord);

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("etapa=RECOMMENDATIONS resultado=OK chatId={} assistantId={} recomendacionesGeneradas=0 duracionMs={}",
                    chatId, assistantId, durationMs);
            return;
        }

        // 4. Filtrar mensajes excluyendo SYSTEM (R4)
        List<AuditMessageDto> evaluableMessages = extractEvaluableMessages(conversation);

        // 5. Invocación de IA protegida contra fallos (R6, R9, R20)
        RecommendationAnalysisResponseDto aiResponse;
        try {
            aiResponse = aiClient.analyzeRecommendations(evaluableMessages, findings);
        } catch (Exception ex) {
            long durationMs = System.currentTimeMillis() - startTime;
            handleAiFailure(auditRecord, ex, durationMs);
            return;
        }

        // 6. Evaluación y saneamiento mediante el analizador puro (R10, R11, R12, R13)
        RecommendationEvaluationResultDto evaluation = analyzer.evaluate(aiResponse, evaluableMessages);

        // 7. Persistir recomendaciones generadas (R17)
        Instant now = Instant.now();
        List<AuditRecommendation> entities = new ArrayList<>();
        for (RecommendationItemDto item : evaluation.items()) {
            AuditRecommendation rec = new AuditRecommendation();
            rec.setRecommendationId(UUID.randomUUID().toString());
            rec.setAuditResult(auditRecord);
            rec.setType(item.type());
            rec.setSuggestedText(item.suggestedText());
            rec.setOriginMessageId(item.originMessageId());
            rec.setCreatedDate(now);
            entities.add(rec);
        }

        if (!entities.isEmpty()) {
            auditRecommendationRepository.saveAll(entities);
        }

        // 8. Culminación de auditoría: status COMPLETED y auditedDate (R18)
        auditRecord.setStatus(AuditStatus.COMPLETED);
        auditRecord.setAuditedDate(now);
        auditRecord.setUpdatedDate(now);
        auditResultRepository.saveAndFlush(auditRecord);

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("etapa=RECOMMENDATIONS resultado=OK chatId={} assistantId={} recomendacionesGeneradas={} duracionMs={}",
                chatId, assistantId, entities.size(), durationMs);
    }

    AuditFindingsContextDto buildFindingsContext(AuditResult auditRecord) {
        int gapsCount = auditRecord.getGapsCount() != null ? auditRecord.getGapsCount() : 0;
        List<String> gapQuestions = List.of();
        if (auditRecord.getGaps() != null && !auditRecord.getGaps().isEmpty()) {
            gapQuestions = auditRecord.getGaps().stream()
                    .map(AuditGap::getQuestionText)
                    .filter(Objects::nonNull)
                    .toList();
        }

        boolean frustrationDetected = Boolean.TRUE.equals(auditRecord.getFrustrationDetected());
        boolean lostLeadDetected = Boolean.TRUE.equals(auditRecord.getLostLeadDetected());
        BigDecimal leadScore = auditRecord.getLeadScore() != null ? auditRecord.getLeadScore() : BigDecimal.ZERO;
        String lostLeadReason = auditRecord.getLostLeadReason();

        return new AuditFindingsContextDto(
                gapsCount,
                gapQuestions,
                frustrationDetected,
                lostLeadDetected,
                leadScore,
                lostLeadReason
        );
    }

    List<AuditMessageDto> extractEvaluableMessages(AuditConversationDto conversation) {
        if (conversation == null || conversation.messages() == null) {
            return List.of();
        }

        return conversation.messages().stream()
                .filter(m -> m != null && m.role() != null && !m.role().equalsIgnoreCase("SYSTEM"))
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

        log.error("etapa=RECOMMENDATIONS resultado=FAILED chatId={} assistantId={} duracionMs={} error={}",
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
