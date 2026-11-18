package ec.com.technoloqie.auditbot.api.analyzer;

import ec.com.technoloqie.auditbot.api.dto.LeadAnalysisResponseDto;
import ec.com.technoloqie.auditbot.api.dto.LeadEvaluationResultDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias de lógica pura para {@link LeadAnalyzer} (US-05).
 * Libre de contexto Spring.
 * Cubre R8, R9, R10, R11, R12, R13, R23.
 */
class LeadAnalyzerTest {

    private LeadAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new LeadAnalyzer();
    }

    // R8 — Oportunidad de venta perdida cuando hay intención y no hay cierre
    @Test
    @DisplayName("Debe detectar oportunidad perdida cuando hay intención comercial y no hubo cierre")
    void shouldDetectLostLeadWhenIntentTrueAndClosureFalse() {
        LeadAnalysisResponseDto response = new LeadAnalysisResponseDto(
                true,
                new BigDecimal("0.850"),
                false,
                "Usuario consultó por precios y no se capturaron datos de contacto"
        );

        LeadEvaluationResultDto result = analyzer.evaluate(response);

        assertThat(result.lostLeadDetected()).isTrue();
        assertThat(result.leadScore()).isEqualByComparingTo(new BigDecimal("0.850"));
        assertThat(result.lostLeadReason())
                .isEqualTo("Usuario consultó por precios y no se capturaron datos de contacto");
    }

    // R9 — Sin intención comercial -> score 0.000 y reason null
    @Test
    @DisplayName("Debe establecer score 0.000 y reason null cuando no hay intención comercial")
    void shouldSetZeroScoreAndNullReasonWhenNoCommercialIntent() {
        LeadAnalysisResponseDto response = new LeadAnalysisResponseDto(
                false,
                new BigDecimal("0.700"),
                false,
                "Alguna razón ignorada"
        );

        LeadEvaluationResultDto result = analyzer.evaluate(response);

        assertThat(result.lostLeadDetected()).isFalse();
        assertThat(result.leadScore()).isEqualByComparingTo(new BigDecimal("0.000"));
        assertThat(result.lostLeadReason()).isNull();
    }

    // R9 — Sin intención comercial con closure true -> score 0.000 y reason null
    @Test
    @DisplayName("Debe ignorar closure si no hay intención comercial")
    void shouldIgnoreClosureWhenNoCommercialIntent() {
        LeadAnalysisResponseDto response = new LeadAnalysisResponseDto(
                false,
                new BigDecimal("0.400"),
                true,
                "Soporte técnico cerrado"
        );

        LeadEvaluationResultDto result = analyzer.evaluate(response);

        assertThat(result.lostLeadDetected()).isFalse();
        assertThat(result.leadScore()).isEqualByComparingTo(new BigDecimal("0.000"));
        assertThat(result.lostLeadReason()).isNull();
    }

    // R10 — Oportunidad con cierre exitoso -> no es venta perdida pero conserva leadScore
    @Test
    @DisplayName("No debe marcar como venta perdida cuando hubo cierre exitoso pero conserva el score")
    void shouldNotMarkLostLeadWhenClosureAchieved() {
        LeadAnalysisResponseDto response = new LeadAnalysisResponseDto(
                true,
                new BigDecimal("0.900"),
                true,
                "Se capturó el teléfono del cliente para contactarlo"
        );

        LeadEvaluationResultDto result = analyzer.evaluate(response);

        assertThat(result.lostLeadDetected()).isFalse();
        assertThat(result.leadScore()).isEqualByComparingTo(new BigDecimal("0.900"));
        assertThat(result.lostLeadReason()).isNull();
    }

    // R11 — Clamping de leadScore cuando es negativo
    @Test
    @DisplayName("Debe acotar el score a 0.000 si la IA retorna un valor menor a cero")
    void shouldClampNegativeScoreToZero() {
        LeadAnalysisResponseDto response = new LeadAnalysisResponseDto(
                true,
                new BigDecimal("-0.250"),
                false,
                "Motivo válido"
        );

        LeadEvaluationResultDto result = analyzer.evaluate(response);

        assertThat(result.lostLeadDetected()).isTrue();
        assertThat(result.leadScore()).isEqualByComparingTo(new BigDecimal("0.000"));
    }

    // R11 — Clamping de leadScore cuando es mayor a 1.000
    @Test
    @DisplayName("Debe acotar el score a 1.000 si la IA retorna un valor mayor a uno")
    void shouldClampOverflowScoreToOne() {
        LeadAnalysisResponseDto response = new LeadAnalysisResponseDto(
                true,
                new BigDecimal("1.500"),
                false,
                "Motivo válido"
        );

        LeadEvaluationResultDto result = analyzer.evaluate(response);

        assertThat(result.lostLeadDetected()).isTrue();
        assertThat(result.leadScore()).isEqualByComparingTo(new BigDecimal("1.000"));
    }

    // R11 — Clamping de leadScore cuando es null
    @Test
    @DisplayName("Debe tratar score nulo como 0.000")
    void shouldTreatNullScoreAsZero() {
        LeadAnalysisResponseDto response = new LeadAnalysisResponseDto(
                true,
                null,
                false,
                "Motivo válido"
        );

        LeadEvaluationResultDto result = analyzer.evaluate(response);

        assertThat(result.lostLeadDetected()).isTrue();
        assertThat(result.leadScore()).isEqualByComparingTo(new BigDecimal("0.000"));
    }

    // R12 — Truncado de motivo a 500 caracteres
    @Test
    @DisplayName("Debe truncar lostLeadReason a 500 caracteres si excede el límite")
    void shouldTruncateLostLeadReasonTo500Chars() {
        String longReason = "a".repeat(600);
        LeadAnalysisResponseDto response = new LeadAnalysisResponseDto(
                true,
                new BigDecimal("0.800"),
                false,
                longReason
        );

        LeadEvaluationResultDto result = analyzer.evaluate(response);

        assertThat(result.lostLeadReason()).hasSize(500);
        assertThat(result.lostLeadReason()).isEqualTo("a".repeat(500));
    }

    // R13 — Saneamiento y trim de espacios en blanco
    @Test
    @DisplayName("Debe aplicar trim a los espacios en blanco del motivo")
    void shouldTrimLostLeadReason() {
        LeadAnalysisResponseDto response = new LeadAnalysisResponseDto(
                true,
                new BigDecimal("0.750"),
                false,
                "   Motivo con espacios en los extremos   "
        );

        LeadEvaluationResultDto result = analyzer.evaluate(response);

        assertThat(result.lostLeadReason()).isEqualTo("Motivo con espacios en los extremos");
    }

    // Motivo nulo o vacío en venta perdida -> asigna texto por defecto
    @Test
    @DisplayName("Debe asignar motivo por defecto si la IA no proporciona uno en venta perdida")
    void shouldAssignDefaultReasonWhenReasonIsBlank() {
        LeadAnalysisResponseDto response = new LeadAnalysisResponseDto(
                true,
                new BigDecimal("0.600"),
                false,
                "   "
        );

        LeadEvaluationResultDto result = analyzer.evaluate(response);

        assertThat(result.lostLeadDetected()).isTrue();
        assertThat(result.lostLeadReason())
                .isEqualTo("Oportunidad de venta no concretada sin cierre registrado");
    }

    // Respuesta nula completa
    @Test
    @DisplayName("Debe manejar respuesta nula retornando valores neutros seguros")
    void shouldHandleNullResponse() {
        LeadEvaluationResultDto result = analyzer.evaluate(null);

        assertThat(result.lostLeadDetected()).isFalse();
        assertThat(result.leadScore()).isEqualByComparingTo(new BigDecimal("0.000"));
        assertThat(result.lostLeadReason()).isNull();
    }
}
