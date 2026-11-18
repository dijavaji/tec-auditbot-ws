package ec.com.technoloqie.auditbot.api.integration.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.com.technoloqie.auditbot.api.commons.exception.InvalidAuditMessageException;
import ec.com.technoloqie.auditbot.api.dto.ConversationAuditRequestDto;
import org.springframework.stereotype.Component;

/**
 * Deserializa el cuerpo crudo de un mensaje AMQP al contrato
 * {@link ConversationAuditRequestDto} usando el bean {@code auditObjectMapper} de Jackson 2.
 * <p>
 * Cualquier fallo de deserialización (JSON malformado, tipo incompatible) se traduce
 * en {@link InvalidAuditMessageException} con razón {@code MALFORMED_JSON}, indicando
 * un error permanente: el mismo mensaje nunca será deserializable.
 * <p>
 * Los instantes se preservan en UTC gracias al {@code JavaTimeModule} registrado en
 * {@code auditObjectMapper}.
 */
@Component
public class AuditMessageDeserializer {

    private final ObjectMapper objectMapper;

    public AuditMessageDeserializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Deserializa el cuerpo del mensaje.
     *
     * @param body bytes del cuerpo AMQP
     * @return DTO deserializado
     * @throws InvalidAuditMessageException si el cuerpo no es JSON válido o no se puede
     *                                      mapear al contrato
     */
    public ConversationAuditRequestDto deserialize(byte[] body) {
        try {
            return objectMapper.readValue(body, ConversationAuditRequestDto.class);
        } catch (Exception ex) {
            throw new InvalidAuditMessageException("MALFORMED_JSON", null, ex);
        }
    }
}
