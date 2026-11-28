package ec.com.technoloqie.auditbot.api.repository;

import ec.com.technoloqie.auditbot.api.model.AuditRecommendation;
import ec.com.technoloqie.auditbot.api.model.AuditResult;
import ec.com.technoloqie.auditbot.api.model.AuditStatus;
import ec.com.technoloqie.auditbot.api.model.RecommendationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de integración del repositorio JPA {@link IAuditRecommendationRepository} usando H2 en memoria.
 * Cubre R14, R15, R16, R19.
 */
@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:auditrecommendationrepotest;DB_CLOSE_DELAY=-1;MODE=MySQL",
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
class AuditRecommendationRepositoryTest {

    @Autowired
    private IAuditResultRepository auditResultRepository;

    @Autowired
    private IAuditRecommendationRepository auditRecommendationRepository;

    private AuditResult buildAuditResult(String chatId) {
        AuditResult r = new AuditResult();
        r.setAuditId(UUID.randomUUID().toString());
        r.setChatId(chatId);
        r.setAssistantId("assistant-rec-test");
        r.setStatus(AuditStatus.PROCESSING);
        r.setEventId(UUID.randomUUID().toString());
        r.setAttemptCount(1);
        Instant now = Instant.now();
        r.setCreatedDate(now);
        r.setUpdatedDate(now);
        return r;
    }

    private AuditRecommendation buildAuditRecommendation(
            AuditResult auditResult,
            RecommendationType type,
            String suggestedText,
            String originMessageId) {
        AuditRecommendation rec = new AuditRecommendation();
        rec.setRecommendationId(UUID.randomUUID().toString());
        rec.setAuditResult(auditResult);
        rec.setType(type);
        rec.setSuggestedText(suggestedText);
        rec.setOriginMessageId(originMessageId);
        rec.setCreatedDate(Instant.now());
        return rec;
    }

    // R14, R15 — Persiste y recupera entidad AuditRecommendation
    @Test
    void shouldPersistAndRetrieveRecommendationEntity() {
        AuditResult auditResult = auditResultRepository.saveAndFlush(buildAuditResult("chat-rec-1"));

        AuditRecommendation rec = buildAuditRecommendation(
                auditResult,
                RecommendationType.NEW_FAQ,
                "Agregar FAQ sobre políticas de reembolso en cancelaciones.",
                "msg-101"
        );
        AuditRecommendation saved = auditRecommendationRepository.saveAndFlush(rec);

        Optional<AuditRecommendation> found = auditRecommendationRepository.findById(saved.getRecommendationId());

        assertThat(found).isPresent();
        assertThat(found.get().getRecommendationId()).isEqualTo(saved.getRecommendationId());
        assertThat(found.get().getType()).isEqualTo(RecommendationType.NEW_FAQ);
        assertThat(found.get().getSuggestedText()).isEqualTo("Agregar FAQ sobre políticas de reembolso en cancelaciones.");
        assertThat(found.get().getOriginMessageId()).isEqualTo("msg-101");
        assertThat(found.get().getAuditResult().getAuditId()).isEqualTo(auditResult.getAuditId());
    }

    // R19 — Búsqueda y eliminación por auditId (idempotencia en reintentos)
    @Test
    void shouldFindByAuditIdAndDeleteByAuditId() {
        AuditResult auditResult = auditResultRepository.saveAndFlush(buildAuditResult("chat-rec-2"));

        AuditRecommendation r1 = buildAuditRecommendation(auditResult, RecommendationType.NEW_FAQ, "FAQ nueva", "msg-1");
        AuditRecommendation r2 = buildAuditRecommendation(auditResult, RecommendationType.ALTERNATIVE_FLOW, "Flujo alternativo", "msg-2");
        auditRecommendationRepository.saveAllAndFlush(List.of(r1, r2));

        List<AuditRecommendation> found = auditRecommendationRepository.findByAuditResult_AuditId(auditResult.getAuditId());
        assertThat(found).hasSize(2);

        auditRecommendationRepository.deleteByAuditResult_AuditId(auditResult.getAuditId());
        auditRecommendationRepository.flush();

        List<AuditRecommendation> afterDelete = auditRecommendationRepository.findByAuditResult_AuditId(auditResult.getAuditId());
        assertThat(afterDelete).isEmpty();
    }

    // R16 — Cascada y relación OneToMany desde AuditResult
    @Test
    void shouldCascadeRecommendationsFromAuditResult() {
        AuditResult auditResult = buildAuditResult("chat-rec-3");
        AuditRecommendation rec = buildAuditRecommendation(auditResult, RecommendationType.ALTERNATIVE_FLOW, "Derivar a agente", null);
        auditResult.getRecommendations().add(rec);

        AuditResult savedResult = auditResultRepository.saveAndFlush(auditResult);
        String recommendationId = savedResult.getRecommendations().get(0).getRecommendationId();

        assertThat(auditRecommendationRepository.findById(recommendationId)).isPresent();

        auditResultRepository.delete(savedResult);
        auditResultRepository.flush();

        Optional<AuditRecommendation> found = auditRecommendationRepository.findById(recommendationId);
        assertThat(found).isEmpty();
    }
}
