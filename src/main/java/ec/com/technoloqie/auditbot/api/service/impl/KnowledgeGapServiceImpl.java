package ec.com.technoloqie.auditbot.api.service.impl;

import ec.com.technoloqie.auditbot.api.analyzer.KnowledgeGapAnalyzer;
import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;
import ec.com.technoloqie.auditbot.api.dto.AuditMessageDto;
import ec.com.technoloqie.auditbot.api.dto.ConversationTurnDto;
import ec.com.technoloqie.auditbot.api.dto.GapAnalysisResponseDto;
import ec.com.technoloqie.auditbot.api.dto.KnowledgeGapItemDto;
import ec.com.technoloqie.auditbot.api.integration.openai.IKnowledgeGapAiClient;
import ec.com.technoloqie.auditbot.api.model.AuditGap;
import ec.com.technoloqie.auditbot.api.model.AuditResult;
import ec.com.technoloqie.auditbot.api.model.AuditStatus;
import ec.com.technoloqie.auditbot.api.repository.IAuditGapRepository;
import ec.com.technoloqie.auditbot.api.repository.IAuditResultRepository;
import ec.com.technoloqie.auditbot.api.service.IKnowledgeGapService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementación transaccional del servicio para detección y persistencia
 * de brechas de conocimiento (knowledge gaps) en conversaciones auditadas.
 */
@Slf4j
@Service
@Transactional
public class KnowledgeGapServiceImpl implements IKnowledgeGapService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;
    private static final String AI_ERROR_PREFIX = "[AI_ERROR] ";

    private final IKnowledgeGapAiClient aiClient;
    private final KnowledgeGapAnalyzer analyzer;
    private final IAuditResultRepository auditResultRepository;
    private final IAuditGapRepository auditGapRepository;
    private final BigDecimal minConfidence;
    private final double similarityThreshold;

    public KnowledgeGapServiceImpl(
            IKnowledgeGapAiClient aiClient,
            KnowledgeGapAnalyzer analyzer,
            IAuditResultRepository auditResultRepository,
            IAuditGapRepository auditGapRepository,
            @Value("${auditbot.ai.gaps.min-confidence:0.60}") BigDecimal minConfidence,
            @Value("${auditbot.ai.gaps.similarity-threshold:0.70}") double similarityThreshold) {
        this.aiClient = aiClient;
        this.analyzer = analyzer;
        this.auditResultRepository = auditResultRepository;
        this.auditGapRepository = auditGapRepository;
        this.minConfidence = minConfidence;
        this.similarityThreshold = similarityThreshold;
    }

    @Override
    public void analyzeAndPersistGaps(String chatId, AuditConversationDto conversation) {
        long startTime = System.currentTimeMillis();
        String assistantId = conversation != null ? conversation.assistantId() : "unknown";
        int totalMessages = (conversation != null && conversation.messages() != null) ? conversation.messages().size() : 0;

        Optional<AuditResult> auditOpt = auditResultRepository.findByChatId(chatId);
        if (auditOpt.isEmpty()) {
            log.warn("chatId={} no encontrado en audit_result — omitiendo análisis de gaps", chatId);
            return;
        }

        AuditResult auditRecord = auditOpt.get();

        // 1. Extraer turnos cronológicos usuario-asistente
        List<ConversationTurnDto> turns = extractTurns(conversation);

        // 2. Invocar clasificación con IA protegida contra fallos
        GapAnalysisResponseDto aiResponse;
        try {
            aiResponse = aiClient.classifyTurns(turns);
        } catch (Exception ex) {
            long durationMs = System.currentTimeMillis() - startTime;
            handleAiFailure(auditRecord, ex, durationMs);
            return;
        }

        // 3. Agrupación y deduplicación con el analizador puro
        List<KnowledgeGapItemDto> consolidatedGaps = analyzer.groupAndConsolidate(
                aiResponse != null ? aiResponse.classifications() : List.of(),
                minConfidence,
                similarityThreshold
        );

        Instant now = Instant.now();

        // 4. Limpieza defensiva en caso de reintento
        auditGapRepository.deleteByAuditResult_AuditId(auditRecord.getAuditId());
        auditGapRepository.flush();

        // 5. Persistencia de gaps consolidados
        if (!consolidatedGaps.isEmpty()) {
            List<AuditGap> gapsToSave = consolidatedGaps.stream()
                    .map(dto -> {
                        AuditGap gap = new AuditGap();
                        gap.setGapId(UUID.randomUUID().toString());
                        gap.setAuditResult(auditRecord);
                        gap.setMessageId(dto.messageId());
                        gap.setQuestionText(dto.questionText());
                        gap.setConfidenceScore(dto.confidenceScore());
                        gap.setCreatedDate(now);
                        return gap;
                    })
                    .toList();
            auditGapRepository.saveAll(gapsToSave);
        }

        // 6. Actualización de audit_result manteniendo estado en PROCESSING
        auditRecord.setGapsCount(consolidatedGaps.size());
        auditRecord.setUpdatedDate(now);
        auditRecord.setStatus(AuditStatus.PROCESSING);
        auditResultRepository.saveAndFlush(auditRecord);

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("etapa=GAP_DETECTION resultado=OK chatId={} assistantId={} gapsDetectados={} mensajesAnalizados={} duracionMs={}",
                chatId, assistantId, consolidatedGaps.size(), totalMessages, durationMs);
    }

    /**
     * Reconstruye los turnos de diálogo respetando el orden cronológico.
     * Descarta mensajes de rol SYSTEM. Cada mensaje USER se empareja con la siguiente respuesta ASSISTANT.
     */
    List<ConversationTurnDto> extractTurns(AuditConversationDto conversation) {
        if (conversation == null || conversation.messages() == null || conversation.messages().isEmpty()) {
            return List.of();
        }

        // Ordenar mensajes cronológicamente
        List<AuditMessageDto> sorted = conversation.messages().stream()
                .filter(m -> m != null && m.role() != null && !m.role().equalsIgnoreCase("SYSTEM"))
                .sorted(Comparator.comparing(AuditMessageDto::occurredAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<ConversationTurnDto> turns = new ArrayList<>();

        for (int i = 0; i < sorted.size(); i++) {
            AuditMessageDto current = sorted.get(i);
            if (current.role().equalsIgnoreCase("USER")) {
                // Buscar siguiente respuesta del asistente
                AuditMessageDto nextAssistant = null;
                for (int j = i + 1; j < sorted.size(); j++) {
                    if (sorted.get(j).role().equalsIgnoreCase("ASSISTANT")) {
                        nextAssistant = sorted.get(j);
                        break;
                    }
                }

                String asstMsgId = nextAssistant != null ? nextAssistant.messageId() : null;
                String asstResponse = nextAssistant != null ? nextAssistant.content() : null;

                turns.add(new ConversationTurnDto(
                        current.messageId(),
                        current.content(),
                        asstMsgId,
                        asstResponse
                ));
            }
        }

        return turns;
    }

    private void handleAiFailure(AuditResult auditRecord, Exception ex, long durationMs) {
        String rawMessage = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        String formattedError = truncateErrorMessage(AI_ERROR_PREFIX + rawMessage);

        auditRecord.setStatus(AuditStatus.FAILED);
        auditRecord.setErrorMessage(formattedError);
        auditRecord.setUpdatedDate(Instant.now());
        auditResultRepository.saveAndFlush(auditRecord);

        log.error("etapa=GAP_DETECTION resultado=FAILED chatId={} assistantId={} duracionMs={} error={}",
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
