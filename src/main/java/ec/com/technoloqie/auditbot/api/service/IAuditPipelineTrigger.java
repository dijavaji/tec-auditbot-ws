package ec.com.technoloqie.auditbot.api.service;

import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;

/**
 * Puerto de extensión para activar el pipeline de análisis de auditoría.
 * <p>
 * US-02 registra la implementación no-op {@link ec.com.technoloqie.auditbot.api.service.impl.NoOpAuditPipelineTrigger}.
 * US-03 y siguientes sustituyen esa implementación por la que orqueste los analyzers reales.
 * <p>
 * No importa tipos Spring AMQP, JPA ni Spring AI: es un puerto de dominio puro.
 */
public interface IAuditPipelineTrigger {

    /**
     * Activa el pipeline de análisis para la conversación identificada por {@code chatId}.
     *
     * @param chatId       identificador de la conversación a analizar
     * @param conversation contrato con los mensajes de la conversación
     */
    void trigger(String chatId, AuditConversationDto conversation);
}
