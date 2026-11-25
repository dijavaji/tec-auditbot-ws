package ec.com.technoloqie.auditbot.api.service;

import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;

/**
 * Servicio de orquestación y persistencia para la generación automática
 * de recomendaciones de mejora (US-07).
 */
public interface IRecommendationService {

    /**
     * Genera y persiste recomendaciones de mejora a partir de los hallazgos y el diálogo,
     * culminando el ciclo de auditoría a estado COMPLETED.
     *
     * @param chatId       identificador único de la conversación
     * @param conversation datos de la conversación
     */
    void generateAndPersistRecommendations(String chatId, AuditConversationDto conversation);
}
