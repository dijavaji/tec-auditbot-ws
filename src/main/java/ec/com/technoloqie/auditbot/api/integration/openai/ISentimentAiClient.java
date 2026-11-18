package ec.com.technoloqie.auditbot.api.integration.openai;

import ec.com.technoloqie.auditbot.api.dto.AuditMessageDto;
import ec.com.technoloqie.auditbot.api.dto.SentimentAnalysisResponseDto;

import java.util.List;

/**
 * Puerto anti-corrupción para invocar la clasificación de sentimientos de mensajes
 * de usuario con un modelo de IA compatible con OpenAI (US-04).
 */
public interface ISentimentAiClient {

    /**
     * Clasifica el sentimiento de los mensajes de usuario de una conversación en un único lote.
     *
     * @param userMessages lista cronológica de mensajes con rol USER
     * @return respuesta estructurada con la clasificación de cada mensaje
     */
    SentimentAnalysisResponseDto classifySentiment(List<AuditMessageDto> userMessages);
}
