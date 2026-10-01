package ec.com.technoloqie.auditbot.api.service;

import ec.com.technoloqie.auditbot.api.commons.exception.AuditPublicationException;
import ec.com.technoloqie.auditbot.api.commons.exception.InvalidAuditRequestException;
import ec.com.technoloqie.auditbot.api.dto.ConversationAuditRequestDto;

/**
 * Puerto de publicación de solicitudes de auditoría de conversaciones.
 * <p>
 * El método retorna solo después de recibir un publisher confirm positivo del
 * broker y de comprobar que el mensaje no fue devuelto como no enrutable. No
 * consume, analiza ni persiste resultados de auditoría; esa responsabilidad
 * corresponde a {@code US-02} en adelante.
 */
public interface IConversationAuditPublisher {

    /**
     * Valida y publica la solicitud de auditoría de una conversación.
     *
     * @param request solicitud a publicar
     * @throws InvalidAuditRequestException si la solicitud no cumple el contrato o las reglas de negocio
     * @throws AuditPublicationException    si la publicación hacia RabbitMQ falla
     */
    void publish(ConversationAuditRequestDto request);
}
