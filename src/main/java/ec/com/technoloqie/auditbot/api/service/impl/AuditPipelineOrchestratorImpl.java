package ec.com.technoloqie.auditbot.api.service.impl;

import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;
import ec.com.technoloqie.auditbot.api.model.AuditStatus;
import ec.com.technoloqie.auditbot.api.repository.IAuditResultRepository;
import ec.com.technoloqie.auditbot.api.service.IAuditPipelineTrigger;
import ec.com.technoloqie.auditbot.api.service.IKnowledgeGapService;
import ec.com.technoloqie.auditbot.api.service.ILeadDetectionService;
import ec.com.technoloqie.auditbot.api.service.IRecommendationService;
import ec.com.technoloqie.auditbot.api.service.ISentimentAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Orquestador principal del pipeline de auditoría de conversaciones.
 * <p>
 * Implementa {@link IAuditPipelineTrigger} como implementación primaria,
 * coordinando secuencialmente las etapas de análisis de IA (US-03, US-04, US-05, US-07).
 */
@Slf4j
@Component
@Primary
public class AuditPipelineOrchestratorImpl implements IAuditPipelineTrigger {

    private final IKnowledgeGapService knowledgeGapService;
    private final ISentimentAnalysisService sentimentAnalysisService;
    private final ILeadDetectionService leadDetectionService;
    private final IRecommendationService recommendationService;
    private final IAuditResultRepository auditResultRepository;

    public AuditPipelineOrchestratorImpl(
            IKnowledgeGapService knowledgeGapService,
            ISentimentAnalysisService sentimentAnalysisService,
            ILeadDetectionService leadDetectionService,
            IRecommendationService recommendationService,
            IAuditResultRepository auditResultRepository) {
        this.knowledgeGapService = knowledgeGapService;
        this.sentimentAnalysisService = sentimentAnalysisService;
        this.leadDetectionService = leadDetectionService;
        this.recommendationService = recommendationService;
        this.auditResultRepository = auditResultRepository;
    }

    @Override
    public void trigger(String chatId, AuditConversationDto conversation) {
        log.info("Iniciando pipeline de auditoría para chatId={}", chatId);

        // 1. Etapa de detección de brechas de conocimiento (US-03)
        knowledgeGapService.analyzeAndPersistGaps(chatId, conversation);

        if (isAuditFailed(chatId)) {
            log.warn("chatId={} en estado FAILED tras detección de gaps — omitiendo etapas posteriores", chatId);
            return;
        }

        // 2. Etapa de análisis de sentimiento y frustración (US-04)
        sentimentAnalysisService.analyzeAndPersistSentiment(chatId, conversation);

        if (isAuditFailed(chatId)) {
            log.warn("chatId={} en estado FAILED tras análisis de sentimiento — omitiendo detección de leads", chatId);
            return;
        }

        // 3. Etapa de detección de leads y oportunidades de venta perdidas (US-05)
        leadDetectionService.analyzeAndPersistLeads(chatId, conversation);

        if (isAuditFailed(chatId)) {
            log.warn("chatId={} en estado FAILED tras detección de leads — omitiendo recomendaciones", chatId);
            return;
        }

        // 4. Etapa de recomendaciones de mejora y culminación de auditoría (US-07)
        recommendationService.generateAndPersistRecommendations(chatId, conversation);
    }

    private boolean isAuditFailed(String chatId) {
        return auditResultRepository.findByChatId(chatId)
                .map(r -> r.getStatus() == AuditStatus.FAILED)
                .orElse(false);
    }
}
