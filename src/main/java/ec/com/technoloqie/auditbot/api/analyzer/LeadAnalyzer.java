package ec.com.technoloqie.auditbot.api.analyzer;

import ec.com.technoloqie.auditbot.api.dto.LeadAnalysisResponseDto;
import ec.com.technoloqie.auditbot.api.dto.LeadEvaluationResultDto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Componente de lógica de negocio pura para la evaluación de oportunidades comerciales y leads no calificados (US-05).
 * <p>
 * Regla de arquitectura: esta clase no debe importar ningún tipo de Spring,
 * Jakarta Persistence, mensajería AMQP ni Spring AI.
 */
public class LeadAnalyzer {

    private static final BigDecimal ZERO_SCORE = BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_SCORE = BigDecimal.ONE.setScale(3, RoundingMode.HALF_UP);
    private static final int MAX_REASON_LENGTH = 500;
    private static final String DEFAULT_LOST_LEAD_REASON = "Oportunidad de venta no concretada sin cierre registrado";

    /**
     * Evalúa la respuesta comercial estructurada devuelta por el modelo de IA aplicando reglas deterministas.
     *
     * @param aiResponse respuesta estructurada del modelo de IA (puede ser nula)
     * @return resultado consolidado de la evaluación de lead
     */
    public LeadEvaluationResultDto evaluate(LeadAnalysisResponseDto aiResponse) {
        if (aiResponse == null) {
            return new LeadEvaluationResultDto(false, ZERO_SCORE, null);
        }

        boolean commercialIntent = Boolean.TRUE.equals(aiResponse.commercialIntentDetected());
        boolean closure = Boolean.TRUE.equals(aiResponse.closureAchieved());

        if (!commercialIntent) {
            return new LeadEvaluationResultDto(false, ZERO_SCORE, null);
        }

        BigDecimal clampedScore = clampScore(aiResponse.leadScore());

        if (closure) {
            return new LeadEvaluationResultDto(false, clampedScore, null);
        }

        String sanitizedReason = sanitizeReason(aiResponse.reason());
        return new LeadEvaluationResultDto(true, clampedScore, sanitizedReason);
    }

    private BigDecimal clampScore(BigDecimal rawScore) {
        if (rawScore == null) {
            return ZERO_SCORE;
        }
        if (rawScore.compareTo(BigDecimal.ZERO) < 0) {
            return ZERO_SCORE;
        }
        if (rawScore.compareTo(BigDecimal.ONE) > 0) {
            return ONE_SCORE;
        }
        return rawScore.setScale(3, RoundingMode.HALF_UP);
    }

    private String sanitizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return DEFAULT_LOST_LEAD_REASON;
        }
        String trimmed = reason.trim();
        return trimmed.length() > MAX_REASON_LENGTH ? trimmed.substring(0, MAX_REASON_LENGTH) : trimmed;
    }
}
