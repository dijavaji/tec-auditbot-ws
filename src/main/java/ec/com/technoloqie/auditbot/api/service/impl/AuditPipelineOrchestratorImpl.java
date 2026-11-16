package ec.com.technoloqie.auditbot.api.service.impl;

import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;
import ec.com.technoloqie.auditbot.api.service.IAuditPipelineTrigger;
import ec.com.technoloqie.auditbot.api.service.IKnowledgeGapService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Orquestador principal del pipeline de auditoría de conversaciones.
 * <p>
 * Implementa {@link IAuditPipelineTrigger} como implementación primaria,
 * coordinando secuencialmente las etapas de análisis de IA (comenzando por US-03).
 */
@Slf4j
@Component
@Primary
public class AuditPipelineOrchestratorImpl implements IAuditPipelineTrigger {

    private final IKnowledgeGapService knowledgeGapService;

    public AuditPipelineOrchestratorImpl(IKnowledgeGapService knowledgeGapService) {
        this.knowledgeGapService = knowledgeGapService;
    }

    @Override
    public void trigger(String chatId, AuditConversationDto conversation) {
        log.info("Iniciando pipeline de auditoría para chatId={}", chatId);
        knowledgeGapService.analyzeAndPersistGaps(chatId, conversation);
    }
}
