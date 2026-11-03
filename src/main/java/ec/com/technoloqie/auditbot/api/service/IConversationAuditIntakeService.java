package ec.com.technoloqie.auditbot.api.service;

import ec.com.technoloqie.auditbot.api.dto.AuditIntakeResultDto;
import ec.com.technoloqie.auditbot.api.dto.ConversationAuditRequestDto;

/**
 * Puerto de admisión de mensajes de auditoría.
 * <p>
 * Recibe la solicitud ya deserializada, la valida, reserva el {@code chatId} y deja
 * el registro en estado inicial. No importa tipos Spring AMQP ni del cliente RabbitMQ.
 */
public interface IConversationAuditIntakeService {

    /**
     * Admite un mensaje de auditoría.
     *
     * @param request        solicitud de auditoría deserializada y no nula
     * @param retryRequested {@code true} si el mensaje incluye el header {@code x-audit-retry=true}
     * @return resultado de la admisión con el desenlace, {@code chatId} y {@code auditId}
     */
    AuditIntakeResultDto admit(ConversationAuditRequestDto request, boolean retryRequested);
}
