package ec.com.technoloqie.auditbot.api.analyzer;

import ec.com.technoloqie.auditbot.api.dto.ProcessedSentimentDto;
import ec.com.technoloqie.auditbot.api.dto.SentimentEvaluationResultDto;
import ec.com.technoloqie.auditbot.api.dto.SentimentItemClassificationDto;
import ec.com.technoloqie.auditbot.api.model.SentimentLabel;
import ec.com.technoloqie.auditbot.api.model.SentimentTrend;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pruebas unitarias para {@link SentimentAnalyzer}.
 * Lógica pura de negocio en JUnit 5 sin contexto de Spring.
 * Cubre R9, R10, R11, R12, R13, R14, R15, R16, R17, R18, R19, R34.
 */
class SentimentAnalyzerTest {

    private SentimentAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new SentimentAnalyzer();
    }

    // R9 — Validación de score en el rango [0.000, 1.000]
    @Test
    void shouldValidateConfidenceScoreWithinRange() {
        assertThat(analyzer.isValidScore(new BigDecimal("0.000"))).isTrue();
        assertThat(analyzer.isValidScore(new BigDecimal("0.750"))).isTrue();
        assertThat(analyzer.isValidScore(new BigDecimal("1.000"))).isTrue();

        assertThat(analyzer.isValidScore(new BigDecimal("-0.001"))).isFalse();
        assertThat(analyzer.isValidScore(new BigDecimal("1.001"))).isFalse();
        assertThat(analyzer.isValidScore(null)).isFalse();

        // Normalización defensiva
        assertThat(analyzer.normalizeScore(new BigDecimal("-0.500"))).isEqualByComparingTo("0.000");
        assertThat(analyzer.normalizeScore(new BigDecimal("1.500"))).isEqualByComparingTo("1.000");
        assertThat(analyzer.normalizeScore(null)).isEqualByComparingTo("0.000");
        assertThat(analyzer.normalizeScore(new BigDecimal("0.8567"))).isEqualByComparingTo("0.857");
    }

    // R10 — Mapeo de polaridad escalar base
    @Test
    void shouldMapLabelsToScalarPolarity() {
        assertThat(analyzer.getScalarPolarity(SentimentLabel.POSITIVE)).isEqualTo(1.0);
        assertThat(analyzer.getScalarPolarity(SentimentLabel.NEUTRAL)).isEqualTo(0.0);
        assertThat(analyzer.getScalarPolarity(SentimentLabel.NEGATIVE)).isEqualTo(-0.5);
        assertThat(analyzer.getScalarPolarity(SentimentLabel.FRUSTRATED)).isEqualTo(-1.0);
        assertThat(analyzer.getScalarPolarity(null)).isEqualTo(0.0);
    }

    // R11 — Tendencia estable para conversación con un solo mensaje
    @Test
    void shouldSetStableTrendForSingleMessageConversation() {
        List<SentimentItemClassificationDto> single = List.of(
                new SentimentItemClassificationDto("m-1", SentimentLabel.FRUSTRATED, new BigDecimal("0.900"))
        );

        SentimentEvaluationResultDto result = analyzer.evaluate(single);

        assertThat(result.trend()).isEqualTo(SentimentTrend.STABLE);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).sequenceOrder()).isEqualTo(1);
    }

    // R12 — Cálculo de delta de polaridad entre mitades
    @Test
    void shouldCalculatePolarityDeltaBetweenHalves() {
        // Mitad 1: m-1 (NEGATIVE, score 1.0 -> -0.5)
        // Mitad 2: m-2 (POSITIVE, score 1.0 -> +1.0)
        // delta = 1.0 - (-0.5) = +1.5
        List<ProcessedSentimentDto> items = List.of(
                new ProcessedSentimentDto("m-1", SentimentLabel.NEGATIVE, new BigDecimal("1.000"), 1),
                new ProcessedSentimentDto("m-2", SentimentLabel.POSITIVE, new BigDecimal("1.000"), 2)
        );

        double delta = analyzer.calculatePolarityDelta(items);
        assertThat(delta).isCloseTo(1.5, within(0.001));
    }

    // R13 — Tendencia de mejora (IMPROVING)
    @Test
    void shouldDetectImprovingTrendWhenDeltaIsPositiveAndSignificant() {
        // Inicial: FRUSTRATED (-1.0), NEGATIVE (-0.5) -> avg = -0.75
        // Final: POSITIVE (+1.0), POSITIVE (+1.0) -> avg = +1.0
        // delta = 1.0 - (-0.75) = +1.75 >= +0.20
        List<SentimentItemClassificationDto> list = List.of(
                new SentimentItemClassificationDto("m-1", SentimentLabel.FRUSTRATED, new BigDecimal("1.000")),
                new SentimentItemClassificationDto("m-2", SentimentLabel.NEGATIVE, new BigDecimal("1.000")),
                new SentimentItemClassificationDto("m-3", SentimentLabel.POSITIVE, new BigDecimal("1.000")),
                new SentimentItemClassificationDto("m-4", SentimentLabel.POSITIVE, new BigDecimal("1.000"))
        );

        SentimentEvaluationResultDto result = analyzer.evaluate(list);

        assertThat(result.trend()).isEqualTo(SentimentTrend.IMPROVING);
    }

    // R14 — Tendencia de empeoramiento (WORSENING)
    @Test
    void shouldDetectWorseningTrendWhenDeltaIsNegativeAndSignificant() {
        // Inicial: POSITIVE (+1.0), NEUTRAL (0.0) -> avg = +0.5
        // Final: NEGATIVE (-0.5), FRUSTRATED (-1.0) -> avg = -0.75
        // delta = -0.75 - 0.5 = -1.25 <= -0.20
        List<SentimentItemClassificationDto> list = List.of(
                new SentimentItemClassificationDto("m-1", SentimentLabel.POSITIVE, new BigDecimal("1.000")),
                new SentimentItemClassificationDto("m-2", SentimentLabel.NEUTRAL, new BigDecimal("1.000")),
                new SentimentItemClassificationDto("m-3", SentimentLabel.NEGATIVE, new BigDecimal("1.000")),
                new SentimentItemClassificationDto("m-4", SentimentLabel.FRUSTRATED, new BigDecimal("1.000"))
        );

        SentimentEvaluationResultDto result = analyzer.evaluate(list);

        assertThat(result.trend()).isEqualTo(SentimentTrend.WORSENING);
    }

    // R15 — Tendencia estable dentro del umbral (STABLE)
    @Test
    void shouldDetectStableTrendWhenDeltaIsWithinThreshold() {
        // Inicial: NEUTRAL (0.0), NEUTRAL (0.0) -> avg = 0.0
        // Final: NEUTRAL (0.0), POSITIVE (0.10) -> avg = +0.05
        // delta = +0.05 < +0.20
        List<SentimentItemClassificationDto> list = List.of(
                new SentimentItemClassificationDto("m-1", SentimentLabel.NEUTRAL, new BigDecimal("0.900")),
                new SentimentItemClassificationDto("m-2", SentimentLabel.NEUTRAL, new BigDecimal("0.900")),
                new SentimentItemClassificationDto("m-3", SentimentLabel.NEUTRAL, new BigDecimal("0.900")),
                new SentimentItemClassificationDto("m-4", SentimentLabel.POSITIVE, new BigDecimal("0.100"))
        );

        SentimentEvaluationResultDto result = analyzer.evaluate(list);

        assertThat(result.trend()).isEqualTo(SentimentTrend.STABLE);
    }

    // R16 — Detección de frustración por pico individual de alta certeza
    @Test
    void shouldDetectFrustrationOnSingleHighConfidencePeak() {
        // Un solo mensaje FRUSTRATED con score 0.850 >= 0.700
        List<SentimentItemClassificationDto> list = List.of(
                new SentimentItemClassificationDto("m-1", SentimentLabel.POSITIVE, new BigDecimal("0.900")),
                new SentimentItemClassificationDto("m-2", SentimentLabel.FRUSTRATED, new BigDecimal("0.850")),
                new SentimentItemClassificationDto("m-3", SentimentLabel.POSITIVE, new BigDecimal("0.950"))
        );

        SentimentEvaluationResultDto result = analyzer.evaluate(list);

        assertThat(result.frustrationDetected()).isTrue();
    }

    // R17 — Detección de frustración por ratio de negatividad acumulada
    @Test
    void shouldDetectFrustrationWhenNegativeRatioExceedsThreshold() {
        // 5 mensajes: 2 NEGATIVE (score moderado 0.500) -> 2/5 = 40% >= 40%
        // Ninguno tiene pico FRUSTRATED >= 0.700, no son consecutivos
        List<SentimentItemClassificationDto> list = List.of(
                new SentimentItemClassificationDto("m-1", SentimentLabel.NEGATIVE, new BigDecimal("0.500")),
                new SentimentItemClassificationDto("m-2", SentimentLabel.POSITIVE, new BigDecimal("0.800")),
                new SentimentItemClassificationDto("m-3", SentimentLabel.NEUTRAL, new BigDecimal("0.800")),
                new SentimentItemClassificationDto("m-4", SentimentLabel.NEGATIVE, new BigDecimal("0.500")),
                new SentimentItemClassificationDto("m-5", SentimentLabel.POSITIVE, new BigDecimal("0.800"))
        );

        SentimentEvaluationResultDto result = analyzer.evaluate(list);

        assertThat(result.frustrationDetected()).isTrue();
    }

    // R18 — Detección de frustración por mensajes negativos consecutivos
    @Test
    void shouldDetectFrustrationOnConsecutiveNegativeMessages() {
        // 6 mensajes, ratio 2/6 = 33% (< 40%), pero m-2 y m-3 son NEGATIVE consecutivos (>= 2)
        List<SentimentItemClassificationDto> list = List.of(
                new SentimentItemClassificationDto("m-1", SentimentLabel.POSITIVE, new BigDecimal("0.800")),
                new SentimentItemClassificationDto("m-2", SentimentLabel.NEGATIVE, new BigDecimal("0.550")),
                new SentimentItemClassificationDto("m-3", SentimentLabel.NEGATIVE, new BigDecimal("0.550")),
                new SentimentItemClassificationDto("m-4", SentimentLabel.POSITIVE, new BigDecimal("0.800")),
                new SentimentItemClassificationDto("m-5", SentimentLabel.NEUTRAL, new BigDecimal("0.800")),
                new SentimentItemClassificationDto("m-6", SentimentLabel.POSITIVE, new BigDecimal("0.800"))
        );

        SentimentEvaluationResultDto result = analyzer.evaluate(list);

        assertThat(result.frustrationDetected()).isTrue();
    }

    // R19 — Ausencia de frustración cuando no se cumplen los criterios
    @Test
    void shouldNotDetectFrustrationWhenCriteriaAreNotMet() {
        // 4 mensajes positivos/neutros, 1 negativo aislado con score bajo
        // Ratio = 1/5 = 20% < 40%, consecutivos = 1 < 2, sin pico FRUSTRATED
        List<SentimentItemClassificationDto> list = List.of(
                new SentimentItemClassificationDto("m-1", SentimentLabel.POSITIVE, new BigDecimal("0.900")),
                new SentimentItemClassificationDto("m-2", SentimentLabel.NEUTRAL, new BigDecimal("0.850")),
                new SentimentItemClassificationDto("m-3", SentimentLabel.NEGATIVE, new BigDecimal("0.500")),
                new SentimentItemClassificationDto("m-4", SentimentLabel.POSITIVE, new BigDecimal("0.950")),
                new SentimentItemClassificationDto("m-5", SentimentLabel.POSITIVE, new BigDecimal("0.900"))
        );

        SentimentEvaluationResultDto result = analyzer.evaluate(list);

        assertThat(result.frustrationDetected()).isFalse();
    }

    @Test
    void shouldHandleEmptyOrNullInputGracefully() {
        SentimentEvaluationResultDto emptyResult = analyzer.evaluate(List.of());
        assertThat(emptyResult.items()).isEmpty();
        assertThat(emptyResult.frustrationDetected()).isFalse();
        assertThat(emptyResult.trend()).isEqualTo(SentimentTrend.STABLE);

        SentimentEvaluationResultDto nullResult = analyzer.evaluate(null);
        assertThat(nullResult.items()).isEmpty();
        assertThat(nullResult.frustrationDetected()).isFalse();
        assertThat(nullResult.trend()).isEqualTo(SentimentTrend.STABLE);
    }
}
