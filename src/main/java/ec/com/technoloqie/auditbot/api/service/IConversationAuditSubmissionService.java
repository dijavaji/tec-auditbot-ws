package ec.com.technoloqie.auditbot.api.service;

import ec.com.technoloqie.auditbot.api.commons.exception.AuditPublicationException;
import ec.com.technoloqie.auditbot.api.commons.exception.InvalidAuditRequestException;
import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;
import ec.com.technoloqie.auditbot.api.dto.ConversationAuditSubmissionResponseDto;

/**
 * Puerto de servicio para la admisión de conversaciones vía REST y su publicación hacia RabbitMQ.
 */
public interface IConversationAuditSubmissionService {

    /**
     * Valida la conversación, ensambla el evento del contrato 1.0 y lo publica en RabbitMQ.
     *
     * @param conversation datos de la conversación a auditar
     * @return confirmación de la admisión con los metadatos generados
     * @throws InvalidAuditRequestException si la conversación no cumple las reglas del contrato
     * @throws AuditPublicationException    si la publicación en RabbitMQ falla
     */
    ConversationAuditSubmissionResponseDto submitConversation(AuditConversationDto conversation);
}
