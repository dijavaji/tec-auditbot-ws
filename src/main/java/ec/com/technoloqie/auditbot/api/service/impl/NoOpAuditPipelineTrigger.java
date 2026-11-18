package ec.com.technoloqie.auditbot.api.service.impl;

import ec.com.technoloqie.auditbot.api.service.IAuditPipelineTrigger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Implementación no-op del puerto {@link IAuditPipelineTrigger}.
 * <p>
 * Registra a nivel {@code DEBUG} que el pipeline aún no está implementado.
 * US-03 sustituirá este componente por la implementación que orqueste los analyzers reales.
 * <p>
 * No importa Spring AMQP, JPA, Spring AI ni ningún analyzer.
 */
@Slf4j
@Component
public class NoOpAuditPipelineTrigger implements IAuditPipelineTrigger {

    @Override
    public void trigger(String chatId) {
        log.debug("Pipeline trigger no-op: chatId={} — pipeline no implementado hasta US-03", chatId);
    }
}
