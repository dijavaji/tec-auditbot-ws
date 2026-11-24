package ec.com.technoloqie.auditbot.api.service;

import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;

/**
 * Servicio de aplicación para la ejecución del análisis de brechas de conocimiento (knowledge gaps)
 * y la persistencia de sus hallazgos en la base de datos de auditoría.
 */
public interface IKnowledgeGapService {

    /**
     * Analiza las consultas de la conversación con el modelo de IA, agrupa brechas por similitud
     * y persiste los resultados actualizando el registro de auditoría.
     *
     * @param chatId       identificador de la conversación
     * @param conversation objeto de conversación con sus turnos de mensajes
     */
    void analyzeAndPersistGaps(String chatId, AuditConversationDto conversation);
}
