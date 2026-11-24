package ec.com.technoloqie.auditbot.api.service.impl;

import ec.com.technoloqie.auditbot.api.analyzer.LeadAnalyzer;
import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;
import ec.com.technoloqie.auditbot.api.dto.AuditMessageDto;
import ec.com.technoloqie.auditbot.api.dto.LeadAnalysisResponseDto;
import ec.com.technoloqie.auditbot.api.dto.LeadEvaluationResultDto;
import ec.com.technoloqie.auditbot.api.integration.openai.ILeadDetectionAiClient;
import ec.com.technoloqie.auditbot.api.model.AuditResult;
import ec.com.technoloqie.auditbot.api.model.AuditStatus;
import ec.com.technoloqie.auditbot.api.repository.IAuditResultRepository;
import ec.com.technoloqie.auditbot.api.service.ILeadDetectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Implementación transaccional del servicio para detección de oportunidades comerciales
 * y leads no calificados (US-05).
 */
@Slf4j
@Service
@Transactional
public class LeadDetectionServiceImpl implements ILeadDetectionService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;
    private static final String AI_ERROR_PREFIX = "[AI_ERROR] ";
    private static final BigDecimal ZERO_SCORE = BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);

    private final ILeadDetectionAiClient aiClient;
    private final LeadAnalyzer analyzer;
    private final IAuditResultRepository auditResultRepository;

    public LeadDetectionServiceImpl(
            ILeadDetectionAiClient aiClient,
            LeadAnalyzer analyzer,
            IAuditResultRepository auditResultRepository) {
        this.aiClient = aiClient;
        this.analyzer = analyzer;
        this.auditResultRepository = auditResultRepository;
    }

    @Override
    public void analyzeAndPersistLeads(String chatId, AuditConversationDto conversation) {
        long startTime = System.currentTimeMillis();
        String assistantId = conversation != null ? conversation.assistantId() : "unknown";

        Optional<AuditResult> auditOpt = auditResultRepository.findByChatId(chatId);
        if (auditOpt.isEmpty()) {
            log.warn("chatId={} no encontrado en audit_result — omitiendo detección de leads", chatId);
            return;
        }

        AuditResult auditRecord = auditOpt.get();
        if (auditRecord.getStatus() == AuditStatus.FAILED) {
            log.warn("chatId={} se encuentra en estado FAILED — omitiendo detección de leads", chatId);
            return;
        }

        // 1. Verificar si existen mensajes emitidos por el usuario (R3)
        boolean hasUserMessages = hasUserMessages(conversation);
        if (!hasUserMessages) {
            auditRecord.setLostLeadDetected(false);
            auditRecord.setLeadScore(ZERO_SCORE);
            auditRecord.setLostLeadReason(null);
            auditRecord.setUpdatedDate(Instant.now());
            auditRecord.setStatus(AuditStatus.PROCESSING);
            auditResultRepository.saveAndFlush(auditRecord);

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("etapa=LEAD_DETECTION resultado=OK chatId={} assistantId={} mensajesEvaluados=0 lostLeadDetected=false leadScore=0.000 duracionMs={}",
                    chatId, assistantId, durationMs);
            return;
        }

        // 2. Filtrar mensajes descartando SYSTEM y reteniendo USER y ASSISTANT cronológicamente (R1, R2)
        List<AuditMessageDto> evaluableMessages = extractEvaluableMessages(conversation);

        // 3. Invocar evaluación con IA protegida contra fallos (R4, R7, R21)
        LeadAnalysisResponseDto aiResponse;
        try {
            aiResponse = aiClient.evaluateCommercialOpportunity(evaluableMessages);
        } catch (Exception ex) {
            long durationMs = System.currentTimeMillis() - startTime;
            handleAiFailure(auditRecord, ex, durationMs);
            return;
        }

        // 4. Evaluar reglas de negocio mediante el analizador puro (R8-R13)
        LeadEvaluationResultDto evaluation = analyzer.evaluate(aiResponse);

        // 5. Persistir atómicamente en AuditResult preservando PROCESSING (R16, R17, R18)
        Instant now = Instant.now();
        auditRecord.setLostLeadDetected(evaluation.lostLeadDetected());
        auditRecord.setLeadScore(evaluation.leadScore());
        auditRecord.setLostLeadReason(evaluation.lostLeadReason());
        auditRecord.setUpdatedDate(now);
        auditRecord.setStatus(AuditStatus.PROCESSING);
        auditResultRepository.saveAndFlush(auditRecord);

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("etapa=LEAD_DETECTION resultado=OK chatId={} assistantId={} mensajesEvaluados={} lostLeadDetected={} leadScore={} duracionMs={}",
                chatId, assistantId, evaluableMessages.size(), evaluation.lostLeadDetected(), evaluation.leadScore(), durationMs);
    }

    /**
     * Verifica si la conversación incluye al menos un mensaje con rol USER (R3).
     */
    boolean hasUserMessages(AuditConversationDto conversation) {
        if (conversation == null || conversation.messages() == null) {
            return false;
        }
        return conversation.messages().stream()
                .anyMatch(m -> m != null && m.role() != null && m.role().equalsIgnoreCase("USER"));
    }

    /**
     * Extrae cronológicamente los mensajes evaluables excluyendo el rol SYSTEM (R1, R2).
     */
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

        log.error("etapa=LEAD_DETECTION resultado=FAILED chatId={} assistantId={} duracionMs={} error={}",
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
