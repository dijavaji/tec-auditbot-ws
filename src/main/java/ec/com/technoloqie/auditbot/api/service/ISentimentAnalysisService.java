package ec.com.technoloqie.auditbot.api.service;

import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;

/**
 * Servicio de aplicación para el análisis y persistencia del sentimiento
 * en conversaciones auditadas (US-04).
 */
public interface ISentimentAnalysisService {

    /**
     * Ejecuta la etapa de clasificación de sentimiento y detección de frustración.
     *
     * @param chatId       identificador de la conversación auditada
     * @param conversation DTO de la conversación con sus mensajes
     */
    void analyzeAndPersistSentiment(String chatId, AuditConversationDto conversation);
}
