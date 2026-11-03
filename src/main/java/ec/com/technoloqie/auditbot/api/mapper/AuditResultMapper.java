package ec.com.technoloqie.auditbot.api.mapper;

import ec.com.technoloqie.auditbot.api.dto.ConversationAuditRequestDto;
import ec.com.technoloqie.auditbot.api.model.AuditResult;
import ec.com.technoloqie.auditbot.api.model.AuditStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Mapper manual de DTO de solicitud de auditoría hacia la entidad de control.
 * <p>
 * No copia {@code messages[].content} ni ningún dato sensible de la conversación
 * en la entidad persistida.
 */
@Component
public class AuditResultMapper {

    /**
     * Construye un registro de auditoría en estado {@code PENDING} a partir de la
     * solicitud admitida.
     *
     * @param request solicitud de auditoría deserializada
     * @param now     instante de creación del registro
     * @return entidad lista para persistir; sin id de auditoría asignado por la base de datos
     */
    public AuditResult toClaim(ConversationAuditRequestDto request, Instant now) {
        AuditResult entity = new AuditResult();
        entity.setAuditId(UUID.randomUUID().toString());
        entity.setChatId(request.conversation().chatId());
        entity.setAssistantId(request.conversation().assistantId());
        entity.setStatus(AuditStatus.PENDING);
        entity.setEventId(request.eventId().toString());
        entity.setAttemptCount(1);
        entity.setCreatedDate(now);
        entity.setUpdatedDate(now);
        return entity;
    }
}
