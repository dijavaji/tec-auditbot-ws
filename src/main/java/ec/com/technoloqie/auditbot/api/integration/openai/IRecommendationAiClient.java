package ec.com.technoloqie.auditbot.api.integration.openai;

import ec.com.technoloqie.auditbot.api.dto.AuditFindingsContextDto;
import ec.com.technoloqie.auditbot.api.dto.AuditMessageDto;
import ec.com.technoloqie.auditbot.api.dto.RecommendationAnalysisResponseDto;

import java.util.List;

/**
 * Puerto anti-corrupción para generar recomendaciones de mejora (US-07)
 * con un modelo de IA compatible con OpenAI / Ollama.
 */
public interface IRecommendationAiClient {

    /**
     * Genera recomendaciones de mejora estructuradas (NEW_FAQ, ALTERNATIVE_FLOW)
     * a partir de la conversación y de los hallazgos previos consolidados.
     *
     * @param conversationMessages lista cronológica de mensajes evaluables de la conversación
     * @param findings             contexto de hallazgos previos (gaps, sentimiento, leads)
     * @return respuesta estructurada conteniendo las recomendaciones candidatas
     */
    RecommendationAnalysisResponseDto analyzeRecommendations(
            List<AuditMessageDto> conversationMessages,
            AuditFindingsContextDto findings);
}
