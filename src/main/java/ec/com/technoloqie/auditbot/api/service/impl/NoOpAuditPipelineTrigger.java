package ec.com.technoloqie.auditbot.api.service.impl;

import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;
import ec.com.technoloqie.auditbot.api.service.IAuditPipelineTrigger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Implementación no-op del puerto {@link IAuditPipelineTrigger}.
 * <p>
 * Registra a nivel {@code DEBUG} que el pipeline aún no está implementado.
 * Actúa como fallback cuando no hay otro componente {@link IAuditPipelineTrigger} registrado.
 * <p>
 * No importa Spring AMQP, JPA, Spring AI ni ningún analyzer.
 */
@Slf4j
@Component
@ConditionalOnMissingBean(IAuditPipelineTrigger.class)
public class NoOpAuditPipelineTrigger implements IAuditPipelineTrigger {

    @Override
    public void trigger(String chatId, AuditConversationDto conversation) {
        log.debug("Pipeline trigger no-op: chatId={} — pipeline no implementado hasta US-03", chatId);
    }
}
