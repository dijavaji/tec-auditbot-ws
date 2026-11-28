package ec.com.technoloqie.auditbot.api.analyzer;

import ec.com.technoloqie.auditbot.api.dto.AuditMessageDto;
import ec.com.technoloqie.auditbot.api.dto.RecommendationAnalysisResponseDto;
import ec.com.technoloqie.auditbot.api.dto.RecommendationCandidateDto;
import ec.com.technoloqie.auditbot.api.dto.RecommendationEvaluationResultDto;
import ec.com.technoloqie.auditbot.api.model.RecommendationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias de lógica pura para {@link RecommendationAnalyzer} (US-07).
 * Libre de contexto Spring.
 * Cubre R10, R11, R12, R13, R22.
 */
class RecommendationAnalyzerTest {

    private RecommendationAnalyzer analyzer;
    private List<AuditMessageDto> sampleMessages;

    @BeforeEach
    void setUp() {
        analyzer = new RecommendationAnalyzer();
        sampleMessages = List.of(
                new AuditMessageDto("msg-1", "USER", "Hola, cómo devuelvo un producto?", Instant.now()),
                new AuditMessageDto("msg-2", "ASSISTANT", "No tengo información al respecto.", Instant.now()),
                new AuditMessageDto("msg-3", "USER", "Pésimo servicio, quiero cancelar mi suscripción.", Instant.now())
        );
    }

    // R10 — Normalización y filtrado de RecommendationType
    @Test
    @DisplayName("Debe normalizar tipos válidos y descartar tipos no reconocidos o nulos")
    void shouldNormalizeAndFilterRecommendationTypes() {
        RecommendationAnalysisResponseDto response = new RecommendationAnalysisResponseDto(List.of(
                new RecommendationCandidateDto("new_faq", "Añadir FAQ sobre devoluciones", "msg-1"),
                new RecommendationCandidateDto("ALTERNATIVE_FLOW", "Ofrecer retención antes de cancelar", "msg-3"),
                new RecommendationCandidateDto("INVALID_TYPE", "Texto descartado", "msg-1"),
                new RecommendationCandidateDto(null, "Texto sin tipo", null),
                new RecommendationCandidateDto("   ", "Texto con tipo en blanco", null)
        ));

        RecommendationEvaluationResultDto result = analyzer.evaluate(response, sampleMessages);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).type()).isEqualTo(RecommendationType.NEW_FAQ);
        assertThat(result.items().get(1).type()).isEqualTo(RecommendationType.ALTERNATIVE_FLOW);
    }

    // R11 — Saneamiento y validación de texto sugerido
    @Test
    @DisplayName("Debe aplicar trim al texto sugerido y descartar sugerencias vacías o nulas")
    void shouldRejectBlankOrNullSuggestedText() {
        RecommendationAnalysisResponseDto response = new RecommendationAnalysisResponseDto(List.of(
                new RecommendationCandidateDto("NEW_FAQ", "   Sugerencia con espacios   ", "msg-1"),
                new RecommendationCandidateDto("NEW_FAQ", null, "msg-1"),
                new RecommendationCandidateDto("NEW_FAQ", "   ", "msg-1")
        ));

        RecommendationEvaluationResultDto result = analyzer.evaluate(response, sampleMessages);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).suggestedText()).isEqualTo("Sugerencia con espacios");
    }

    // R12 — Validación de referencia de mensaje origen
    @Test
    @DisplayName("Debe validar originMessageId contra los mensajes de la conversación y setear a null si no existe")
    void shouldSetOriginMessageIdToNullWhenNotFoundInConversation() {
        RecommendationAnalysisResponseDto response = new RecommendationAnalysisResponseDto(List.of(
                new RecommendationCandidateDto("NEW_FAQ", "FAQ válida con mensaje existente", "msg-1"),
                new RecommendationCandidateDto("ALTERNATIVE_FLOW", "Flujo con mensaje inexistente", "msg-999"),
                new RecommendationCandidateDto("ALTERNATIVE_FLOW", "Flujo con mensaje nulo", null),
                new RecommendationCandidateDto("NEW_FAQ", "FAQ con mensaje en blanco", "   ")
        ));

        RecommendationEvaluationResultDto result = analyzer.evaluate(response, sampleMessages);

        assertThat(result.items()).hasSize(4);
        assertThat(result.items().get(0).originMessageId()).isEqualTo("msg-1");
        assertThat(result.items().get(1).originMessageId()).isNull();
        assertThat(result.items().get(2).originMessageId()).isNull();
        assertThat(result.items().get(3).originMessageId()).isNull();
    }

    // R13 — Deduplicación de recomendaciones idénticas
    @Test
    @DisplayName("Debe deduplicar recomendaciones con idéntico tipo y texto sugerido")
    void shouldDeduplicateIdenticalRecommendations() {
        RecommendationAnalysisResponseDto response = new RecommendationAnalysisResponseDto(List.of(
                new RecommendationCandidateDto("NEW_FAQ", "Agregar FAQ sobre reembolsos", "msg-1"),
                new RecommendationCandidateDto("NEW_FAQ", "  agregar faq sobre reembolsos  ", "msg-2"),
                new RecommendationCandidateDto("ALTERNATIVE_FLOW", "Agregar FAQ sobre reembolsos", "msg-1")
        ));

        RecommendationEvaluationResultDto result = analyzer.evaluate(response, sampleMessages);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).type()).isEqualTo(RecommendationType.NEW_FAQ);
        assertThat(result.items().get(0).originMessageId()).isEqualTo("msg-1");
        assertThat(result.items().get(1).type()).isEqualTo(RecommendationType.ALTERNATIVE_FLOW);
    }

    // Respuesta nula o vacía
    @Test
    @DisplayName("Debe manejar respuesta nula o lista vacía retornando lista vacía segura")
    void shouldHandleNullOrEmptyResponse() {
        assertThat(analyzer.evaluate(null, sampleMessages).items()).isEmpty();
        assertThat(analyzer.evaluate(new RecommendationAnalysisResponseDto(null), sampleMessages).items()).isEmpty();
        assertThat(analyzer.evaluate(new RecommendationAnalysisResponseDto(List.of()), sampleMessages).items()).isEmpty();
        assertThat(analyzer.evaluate(new RecommendationAnalysisResponseDto(List.of()), null).items()).isEmpty();
    }
}
