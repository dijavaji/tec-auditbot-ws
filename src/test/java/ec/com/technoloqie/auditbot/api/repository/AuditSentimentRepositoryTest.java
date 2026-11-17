package ec.com.technoloqie.auditbot.api.repository;

import ec.com.technoloqie.auditbot.api.model.AuditResult;
import ec.com.technoloqie.auditbot.api.model.AuditSentiment;
import ec.com.technoloqie.auditbot.api.model.AuditStatus;
import ec.com.technoloqie.auditbot.api.model.SentimentLabel;
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
 * Pruebas de integración del repositorio JPA {@link IAuditSentimentRepository} usando H2 en memoria.
 * Levanta el contexto con H2 (ddl-auto=create-drop) y Flyway deshabilitado.
 * Cubre R20, R21, R25.
 */
@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:auditsentimentrepotest;DB_CLOSE_DELAY=-1;MODE=MySQL",
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
class AuditSentimentRepositoryTest {

    @Autowired
    private IAuditResultRepository auditResultRepository;

    @Autowired
    private IAuditSentimentRepository auditSentimentRepository;

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

    private AuditSentiment buildAuditSentiment(
            AuditResult auditResult,
            String messageId,
            SentimentLabel label,
            BigDecimal score,
            int sequenceOrder) {
        AuditSentiment sentiment = new AuditSentiment();
        sentiment.setSentimentId(UUID.randomUUID().toString());
        sentiment.setAuditResult(auditResult);
        sentiment.setMessageId(messageId);
        sentiment.setLabel(label);
        sentiment.setScore(score);
        sentiment.setSequenceOrder(sequenceOrder);
        sentiment.setCreatedDate(Instant.now());
        return sentiment;
    }

    // R21 — Persiste y recupera entidad AuditSentiment
    @Test
    void shouldPersistAndRetrieveAuditSentiment() {
        AuditResult auditResult = auditResultRepository.saveAndFlush(buildAuditResult("chat-sentiment-1"));

        AuditSentiment sentiment = buildAuditSentiment(
                auditResult,
                "msg-001",
                SentimentLabel.POSITIVE,
                new BigDecimal("0.950"),
                1
        );
        AuditSentiment saved = auditSentimentRepository.saveAndFlush(sentiment);

        Optional<AuditSentiment> found = auditSentimentRepository.findById(saved.getSentimentId());

        assertThat(found).isPresent();
        assertThat(found.get().getSentimentId()).isEqualTo(saved.getSentimentId());
        assertThat(found.get().getMessageId()).isEqualTo("msg-001");
        assertThat(found.get().getLabel()).isEqualTo(SentimentLabel.POSITIVE);
        assertThat(found.get().getScore()).isEqualByComparingTo(new BigDecimal("0.950"));
        assertThat(found.get().getSequenceOrder()).isEqualTo(1);
        assertThat(found.get().getAuditResult().getAuditId()).isEqualTo(auditResult.getAuditId());
    }

    // R25 — Búsqueda y eliminación por auditId (idempotencia en reintentos)
    @Test
    void shouldFindByAuditIdAndDeleteByAuditId() {
        AuditResult auditResult = auditResultRepository.saveAndFlush(buildAuditResult("chat-sentiment-2"));

        AuditSentiment s1 = buildAuditSentiment(auditResult, "msg-001", SentimentLabel.NEUTRAL, new BigDecimal("0.800"), 1);
        AuditSentiment s2 = buildAuditSentiment(auditResult, "msg-002", SentimentLabel.FRUSTRATED, new BigDecimal("0.920"), 2);
        auditSentimentRepository.saveAllAndFlush(List.of(s1, s2));

        List<AuditSentiment> found = auditSentimentRepository.findByAuditResult_AuditId(auditResult.getAuditId());
        assertThat(found).hasSize(2);

        auditSentimentRepository.deleteByAuditResult_AuditId(auditResult.getAuditId());
        auditSentimentRepository.flush();

        List<AuditSentiment> afterDelete = auditSentimentRepository.findByAuditResult_AuditId(auditResult.getAuditId());
        assertThat(afterDelete).isEmpty();
    }

    // R20 — Eliminación en cascada cuando se elimina el AuditResult
    @Test
    void shouldCascadeDeleteSentimentsWhenAuditResultIsDeleted() {
        AuditResult auditResult = buildAuditResult("chat-sentiment-3");
        AuditSentiment sentiment = buildAuditSentiment(auditResult, "msg-003", SentimentLabel.NEGATIVE, new BigDecimal("0.750"), 1);
        auditResult.getSentiments().add(sentiment);
        AuditResult savedResult = auditResultRepository.saveAndFlush(auditResult);

        String sentimentId = savedResult.getSentiments().get(0).getSentimentId();
        assertThat(auditSentimentRepository.findById(sentimentId)).isPresent();

        auditResultRepository.delete(savedResult);
        auditResultRepository.flush();

        Optional<AuditSentiment> found = auditSentimentRepository.findById(sentimentId);
        assertThat(found).isEmpty();
    }
}
