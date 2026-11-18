package ec.com.technoloqie.auditbot.api.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Prueba unitaria de {@link NoOpAuditPipelineTrigger}. Cubre R59.
 */
class NoOpAuditPipelineTriggerTest {

    private final NoOpAuditPipelineTrigger trigger = new NoOpAuditPipelineTrigger();

    @Test
    void shouldDoNothingOnTrigger() {
        // No debe lanzar ninguna excepción ni producir efectos secundarios
        assertThatCode(() -> trigger.trigger("chat-123"))
                .doesNotThrowAnyException();
    }
}
