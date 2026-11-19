package ec.com.technoloqie.auditbot.api.repository;

import ec.com.technoloqie.auditbot.api.model.AuditResult;
import ec.com.technoloqie.auditbot.api.model.AuditStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de integración para persistencia de campos de lead en {@link AuditResult} (US-05).
 * Verifica mapeo JPA y compatibilidad de esquema con H2.
 * Cubre R14, R15.
 */
@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:auditleadtest;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "spring.rabbitmq.host=127.0.0.1",
        "spring.rabbitmq.port=5672",
        "spring.rabbitmq.username=rabbitmq",
        "spring.rabbitmq.password=Password.1",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.ai.openai.api-key=ollama",
        "spring.ai.openai.chat.base-url=http://localhost:11434/v1"
})
@Transactional
class AuditResultRepositoryLeadTest {

    @Autowired
    private IAuditResultRepository auditResultRepository;

    private AuditResult buildBaseAuditResult(String chatId) {
        AuditResult r = new AuditResult();
        r.setAuditId(UUID.randomUUID().toString());
        r.setChatId(chatId);
        r.setAssistantId("assistant-lead-test");
        r.setStatus(AuditStatus.PROCESSING);
        r.setEventId(UUID.randomUUID().toString());
        r.setAttemptCount(1);
        Instant now = Instant.now();
        r.setCreatedDate(now);
        r.setUpdatedDate(now);
        return r;
    }

    // R14, R15 — Persiste y recupera campos de oportunidad de venta perdida
    @Test
    void shouldPersistAndRetrieveLostLeadFields() {
        AuditResult record = buildBaseAuditResult("chat-lead-1");
        record.setLostLeadDetected(true);
        record.setLeadScore(new BigDecimal("0.850"));
        record.setLostLeadReason("Usuario solicitó plan Enterprise pero el bot no capturó datos de contacto.");

        AuditResult saved = auditResultRepository.saveAndFlush(record);

        Optional<AuditResult> found = auditResultRepository.findByChatId("chat-lead-1");
        assertThat(found).isPresent();
        assertThat(found.get().getAuditId()).isEqualTo(saved.getAuditId());
        assertThat(found.get().getLostLeadDetected()).isTrue();
        assertThat(found.get().getLeadScore()).isEqualByComparingTo(new BigDecimal("0.850"));
        assertThat(found.get().getLostLeadReason())
                .isEqualTo("Usuario solicitó plan Enterprise pero el bot no capturó datos de contacto.");
    }

    // R14, R15 — Persiste correctamente valores de lead no detectado
    @Test
    void shouldPersistWhenLostLeadNotDetected() {
        AuditResult record = buildBaseAuditResult("chat-lead-2");
        record.setLostLeadDetected(false);
        record.setLeadScore(new BigDecimal("0.000"));
        record.setLostLeadReason(null);

        auditResultRepository.saveAndFlush(record);

        Optional<AuditResult> found = auditResultRepository.findByChatId("chat-lead-2");
        assertThat(found).isPresent();
        assertThat(found.get().getLostLeadDetected()).isFalse();
        assertThat(found.get().getLeadScore()).isEqualByComparingTo(new BigDecimal("0.000"));
        assertThat(found.get().getLostLeadReason()).isNull();
    }
}
