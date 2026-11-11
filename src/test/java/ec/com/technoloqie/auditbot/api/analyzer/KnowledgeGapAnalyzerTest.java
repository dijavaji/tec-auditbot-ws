package ec.com.technoloqie.auditbot.api.analyzer;

import ec.com.technoloqie.auditbot.api.dto.KnowledgeGapCandidateDto;
import ec.com.technoloqie.auditbot.api.dto.KnowledgeGapItemDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pruebas unitarias para {@link KnowledgeGapAnalyzer}.
 * Lógica pura de negocio, sin contexto Spring ni base de datos.
 * Cubre R9, R10, R11, R12, R13, R14, R15, R16, R17, R18, R34.
 */
class KnowledgeGapAnalyzerTest {

    private KnowledgeGapAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new KnowledgeGapAnalyzer();
    }

    // R11 — Normalización léxica determinista
    @Test
    void shouldNormalizeQuestionTextDeterministically() {
        String input = "¿¿Tienen Cobertura en el Valle de los Chillos?? ¡¡URGENTE!!";
        String normalized = analyzer.normalize(input);

        assertThat(normalized).isEqualTo("tienen cobertura en el valle de los chillos urgente");
    }

    // R12 — Filtrado de stopwords
    @Test
    void shouldRemoveStopwordsDuringTokenization() {
        String input = "¿Tienen entrega a domicilio en la ciudad?";
        Set<String> tokens = analyzer.tokenizeAndFilterStopwords(input);

        // "a", "en", "la" son stopwords eliminadas
        assertThat(tokens).containsExactlyInAnyOrder("tienen", "entrega", "domicilio", "ciudad");
    }

    // R13 — Cálculo de similitud Jaccard
    @Test
    void shouldCalculateCorrectJaccardSimilarity() {
        String q1 = "¿Tienen entrega a domicilio?"; // tokens: tienen, entrega, domicilio (3)
        String q2 = "¿Hacen envíos a domicilio?";  // tokens: hacen, envios, domicilio (3)
        // Intersección: {domicilio} (1)
        // Unión: {tienen, entrega, domicilio, hacen, envios} (5)
        // Jaccard = 1 / 5 = 0.20

        double similarity = analyzer.calculateJaccardSimilarity(q1, q2);
        assertThat(similarity).isCloseTo(0.20, within(0.01));

        // Idénticas normalizadas
        double identical = analyzer.calculateJaccardSimilarity(
                "¿Tienen entrega a domicilio?",
                "tienen entrega a domicilio"
        );
        assertThat(identical).isEqualTo(1.0);

        // Sin intersección
        double different = analyzer.calculateJaccardSimilarity(
                "¿Cuál es el horario?",
                "¿Aceptan tarjeta de crédito?"
        );
        assertThat(different).isEqualTo(0.0);
    }

    // R9, R10 — Descarte por score inferior al umbral mínimo de confianza
    @Test
    void shouldDiscardGapsBelowMinConfidence() {
        KnowledgeGapCandidateDto valid = new KnowledgeGapCandidateDto(
                "msg-1", "¿Tienen entrega a domicilio?", true, new BigDecimal("0.750"), "Bot no sabe"
        );
        KnowledgeGapCandidateDto belowThreshold = new KnowledgeGapCandidateDto(
                "msg-2", "¿Tienen cobertura?", true, new BigDecimal("0.550"), "Duda baja"
        );
        KnowledgeGapCandidateDto notGap = new KnowledgeGapCandidateDto(
                "msg-3", "Hola buenas tardes", false, new BigDecimal("0.900"), "Saludo resuelto"
        );

        List<KnowledgeGapItemDto> result = analyzer.groupAndConsolidate(
                List.of(valid, belowThreshold, notGap),
                new BigDecimal("0.600"),
                0.70
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).messageId()).isEqualTo("msg-1");
    }

    // R14, R15, R16, R17, R18 — Agrupación de preguntas paráfrasis y selección canónica
    @Test
    void shouldConsolidateSimilarQuestionsIntoSingleGap() {
        // Dos preguntas altamente similares: tokens q1={cobertura, valle, chillos}, q2={tienen, cobertura, valle, chillos}
        // Intersección: {cobertura, valle, chillos} (3)
        // Unión: {tienen, cobertura, valle, chillos} (4)
        // Jaccard = 3/4 = 0.75 >= 0.70
        KnowledgeGapCandidateDto q1 = new KnowledgeGapCandidateDto(
                "msg-101", "¿Cobertura Valle de los Chillos?", true, new BigDecimal("0.850"), "Sin datos"
        );
        KnowledgeGapCandidateDto q2 = new KnowledgeGapCandidateDto(
                "msg-105", "¿Tienen cobertura en el Valle de los Chillos por favor?", true, new BigDecimal("0.980"), "Sin datos"
        );

        List<KnowledgeGapItemDto> result = analyzer.groupAndConsolidate(
                List.of(q1, q2),
                new BigDecimal("0.600"),
                0.70
        );

        assertThat(result).hasSize(1);
        KnowledgeGapItemDto consolidated = result.get(0);

        // R16 — Preserva el primer messageId cronológico
        assertThat(consolidated.messageId()).isEqualTo("msg-101");

        // R17 — Pregunta canónica es la de mayor contenido informativo
        assertThat(consolidated.questionText()).isEqualTo("¿Tienen cobertura en el Valle de los Chillos por favor?");

        // R18 — Score máximo del grupo
        assertThat(consolidated.confidenceScore()).isEqualByComparingTo(new BigDecimal("0.980"));

        // Conteo de ocurrencias
        assertThat(consolidated.occurrencesCount()).isEqualTo(2);
    }

    // Preguntas distintas no se agrupan
    @Test
    void shouldKeepDistinctQuestionsInSeparateGaps() {
        KnowledgeGapCandidateDto q1 = new KnowledgeGapCandidateDto(
                "msg-201", "¿Cuál es el costo del plan pro?", true, new BigDecimal("0.900"), "Tarifas no disponibles"
        );
        KnowledgeGapCandidateDto q2 = new KnowledgeGapCandidateDto(
                "msg-205", "¿Tienen soporte los días domingos?", true, new BigDecimal("0.880"), "Horario de soporte no cargado"
        );

        List<KnowledgeGapItemDto> result = analyzer.groupAndConsolidate(
                List.of(q1, q2),
                new BigDecimal("0.600"),
                0.70
        );

        assertThat(result).hasSize(2);
        assertThat(result.get(0).messageId()).isEqualTo("msg-201");
        assertThat(result.get(1).messageId()).isEqualTo("msg-205");
    }

    // Lista nula o vacía
    @Test
    void shouldReturnEmptyListWhenNoCandidatesOrEmpty() {
        assertThat(analyzer.groupAndConsolidate(null, new BigDecimal("0.600"), 0.70)).isEmpty();
        assertThat(analyzer.groupAndConsolidate(List.of(), new BigDecimal("0.600"), 0.70)).isEmpty();
    }
}
