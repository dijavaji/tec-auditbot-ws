package ec.com.technoloqie.auditbot.api.integration.openai;

import ec.com.technoloqie.auditbot.api.dto.AuditMessageDto;
import ec.com.technoloqie.auditbot.api.dto.LeadAnalysisResponseDto;

import java.util.List;

/**
 * Puerto anti-corrupción para evaluar oportunidades comerciales y leads en una conversación
 * con un modelo de IA compatible con OpenAI (US-05).
 */
public interface ILeadDetectionAiClient {

    /**
     * Evalúa la conversación completa para identificar intención de compra, calidad de lead y cierre.
     *
     * @param conversationMessages lista cronológica de mensajes de diálogo (USER y ASSISTANT)
     * @return respuesta estructurada con la evaluación comercial
     */
    LeadAnalysisResponseDto evaluateCommercialOpportunity(List<AuditMessageDto> conversationMessages);
}
