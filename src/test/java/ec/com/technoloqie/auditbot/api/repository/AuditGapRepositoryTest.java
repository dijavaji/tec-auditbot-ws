package ec.com.technoloqie.auditbot.api.repository;

import ec.com.technoloqie.auditbot.api.model.AuditGap;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de integración del repositorio JPA {@link IAuditGapRepository} usando H2 en memoria.
 * Levanta el contexto con H2 (ddl-auto=create-drop) y Flyway deshabilitado.
 * Cubre R19, R20, R21.
 */
@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:auditgaprepotest;DB_CLOSE_DELAY=-1;MODE=MySQL",
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
class AuditGapRepositoryTest {

    @Autowired
    private IAuditResultRepository auditResultRepository;

    @Autowired
    private IAuditGapRepository auditGapRepository;

    private AuditResult buildAuditResult(String chatId) {
        AuditResult r = new AuditResult();
        r.setAuditId(UUID.randomUUID().toString());
        r.setChatId(chatId);
        r.setAssistantId("assistant-1");
        r.setStatus(AuditStatus.PROCESSING);
        r.setEventId(UUID.randomUUID().toString());
        r.setAttemptCount(1);
        Instant now = Instant.now();
        r.setCreatedDate(now);
        r.setUpdatedDate(now);
        return r;
    }

    private AuditGap buildAuditGap(AuditResult auditResult, String messageId, String question, BigDecimal score) {
        AuditGap gap = new AuditGap();
        gap.setGapId(UUID.randomUUID().toString());
        gap.setAuditResult(auditResult);
        gap.setMessageId(messageId);
        gap.setQuestionText(question);
        gap.setConfidenceScore(score);
        gap.setCreatedDate(Instant.now());
        return gap;
    }

    // R20 — Persiste y recupera entidad AuditGap
    @Test
    void shouldPersistAndRetrieveAuditGap() {
        AuditResult auditResult = auditResultRepository.saveAndFlush(buildAuditResult("chat-gap-1"));

        AuditGap gap = buildAuditGap(auditResult, "msg-001", "¿Tienen entrega a domicilio?", new BigDecimal("0.950"));
        AuditGap saved = auditGapRepository.saveAndFlush(gap);

        Optional<AuditGap> found = auditGapRepository.findById(saved.getGapId());

        assertThat(found).isPresent();
        assertThat(found.get().getGapId()).isEqualTo(saved.getGapId());
        assertThat(found.get().getMessageId()).isEqualTo("msg-001");
        assertThat(found.get().getQuestionText()).isEqualTo("¿Tienen entrega a domicilio?");
        assertThat(found.get().getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.950"));
        assertThat(found.get().getAuditResult().getAuditId()).isEqualTo(auditResult.getAuditId());
    }

    // R21 — Búsqueda y eliminación por auditId
    @Test
    void shouldFindByAuditIdAndDeleteByAuditId() {
        AuditResult auditResult = auditResultRepository.saveAndFlush(buildAuditResult("chat-gap-2"));

        AuditGap gap1 = buildAuditGap(auditResult, "msg-001", "¿Tienen cobertura en Quito?", new BigDecimal("0.850"));
        AuditGap gap2 = buildAuditGap(auditResult, "msg-002", "¿Aceptan tarjeta de crédito?", new BigDecimal("0.900"));
        auditGapRepository.saveAllAndFlush(List.of(gap1, gap2));

        List<AuditGap> foundGaps = auditGapRepository.findByAuditResult_AuditId(auditResult.getAuditId());
        assertThat(foundGaps).hasSize(2);

        auditGapRepository.deleteByAuditResult_AuditId(auditResult.getAuditId());
        auditGapRepository.flush();

        List<AuditGap> afterDelete = auditGapRepository.findByAuditResult_AuditId(auditResult.getAuditId());
        assertThat(afterDelete).isEmpty();
    }

    // R19 — Eliminación en cascada cuando se elimina el AuditResult
    @Test
    void shouldCascadeDeleteGapsWhenAuditResultIsDeleted() {
        AuditResult auditResult = buildAuditResult("chat-gap-3");
        AuditGap gap = buildAuditGap(auditResult, "msg-003", "¿Cuál es el horario?", new BigDecimal("0.750"));
        auditResult.getGaps().add(gap);
        AuditResult savedResult = auditResultRepository.saveAndFlush(auditResult);

        String gapId = savedResult.getGaps().get(0).getGapId();
        assertThat(auditGapRepository.findById(gapId)).isPresent();

        auditResultRepository.delete(savedResult);
        auditResultRepository.flush();

        Optional<AuditGap> found = auditGapRepository.findById(gapId);
        assertThat(found).isEmpty();
    }
}
