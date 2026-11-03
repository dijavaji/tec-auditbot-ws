package ec.com.technoloqie.auditbot.api.repository;

import ec.com.technoloqie.auditbot.api.model.AuditResult;
import ec.com.technoloqie.auditbot.api.model.AuditStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de integración del repositorio JPA usando H2 en memoria.
 * Levanta el contexto completo con H2 (ddl-auto=create-drop) y Flyway deshabilitado.
 * Cubre R26, R27.
 */
@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:auditrepotest;DB_CLOSE_DELAY=-1;MODE=MySQL",
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
        "spring.ai.ollama.base-url=http://localhost:11434"
})
@Transactional
class AuditResultRepositoryTest {

    @Autowired
    private IAuditResultRepository repository;

    private AuditResult buildRecord(String chatId) {
        AuditResult r = new AuditResult();
        r.setAuditId(UUID.randomUUID().toString());
        r.setChatId(chatId);
        r.setAssistantId("assistant-1");
        r.setStatus(AuditStatus.PENDING);
        r.setEventId(UUID.randomUUID().toString());
        r.setAttemptCount(1);
        Instant now = Instant.now();
        r.setCreatedDate(now);
        r.setUpdatedDate(now);
        return r;
    }

    // R26 — persiste y recupera por chatId
    @Test
    void shouldPersistAndFindAuditByChatId() {
        AuditResult saved = repository.save(buildRecord("chat-persist-1"));

        Optional<AuditResult> found = repository.findByChatId("chat-persist-1");

        assertThat(found).isPresent();
        assertThat(found.get().getAuditId()).isEqualTo(saved.getAuditId());
        assertThat(found.get().getStatus()).isEqualTo(AuditStatus.PENDING);
    }

    // R27 — restricción única uk_audit_result_chat_id rechaza duplicado
    @Test
    void shouldRejectDuplicatedChatId() {
        repository.saveAndFlush(buildRecord("chat-dup-repo-1"));

        AuditResult duplicate = buildRecord("chat-dup-repo-1");

        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
