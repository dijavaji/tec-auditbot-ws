package ec.com.technoloqie.auditbot.api.service.impl;

import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;
import ec.com.technoloqie.auditbot.api.model.AuditStatus;
import ec.com.technoloqie.auditbot.api.repository.IAuditResultRepository;
import ec.com.technoloqie.auditbot.api.service.IAuditPipelineTrigger;
import ec.com.technoloqie.auditbot.api.service.IKnowledgeGapService;
import ec.com.technoloqie.auditbot.api.service.ISentimentAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Orquestador principal del pipeline de auditoría de conversaciones.
 * <p>
 * Implementa {@link IAuditPipelineTrigger} como implementación primaria,
 * coordinando secuencialmente las etapas de análisis de IA (US-03, US-04).
 */
@Slf4j
@Component
@Primary
public class AuditPipelineOrchestratorImpl implements IAuditPipelineTrigger {

    private final IKnowledgeGapService knowledgeGapService;
    private final ISentimentAnalysisService sentimentAnalysisService;
    private final IAuditResultRepository auditResultRepository;

    public AuditPipelineOrchestratorImpl(
            IKnowledgeGapService knowledgeGapService,
            ISentimentAnalysisService sentimentAnalysisService,
            IAuditResultRepository auditResultRepository) {
        this.knowledgeGapService = knowledgeGapService;
        this.sentimentAnalysisService = sentimentAnalysisService;
        this.auditResultRepository = auditResultRepository;
    }

    @Override
    public void trigger(String chatId, AuditConversationDto conversation) {
        log.info("Iniciando pipeline de auditoría para chatId={}", chatId);

        // 1. Etapa de detección de brechas de conocimiento (US-03)
        knowledgeGapService.analyzeAndPersistGaps(chatId, conversation);

        // Verificar si la auditoría falló en la etapa previa
        boolean hasFailed = auditResultRepository.findByChatId(chatId)
                .map(r -> r.getStatus() == AuditStatus.FAILED)
                .orElse(false);

        if (hasFailed) {
            log.warn("chatId={} en estado FAILED tras detección de gaps — omitiendo análisis de sentimiento", chatId);
            return;
        }

        // 2. Etapa de análisis de sentimiento y frustración (US-04)
        sentimentAnalysisService.analyzeAndPersistSentiment(chatId, conversation);
    }
}
