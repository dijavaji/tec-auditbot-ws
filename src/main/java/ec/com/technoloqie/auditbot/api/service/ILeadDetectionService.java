package ec.com.technoloqie.auditbot.api.service;

import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;

/**
 * Servicio de aplicación para la detección y persistencia de oportunidades comerciales
 * y leads no calificados en conversaciones auditadas (US-05).
 */
public interface ILeadDetectionService {

    /**
     * Ejecuta la etapa de detección de leads y oportunidades de venta perdidas.
     *
     * @param chatId       identificador de la conversación auditada
     * @param conversation DTO de la conversación con sus mensajes
     */
    void analyzeAndPersistLeads(String chatId, AuditConversationDto conversation);
}
