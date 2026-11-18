package ec.com.technoloqie.auditbot.api.dto;

import java.math.BigDecimal;

/**
 * DTO inmutable con el resultado final evaluado por {@link ec.com.technoloqie.auditbot.api.analyzer.LeadAnalyzer} (US-05).
 *
 * @param lostLeadDetected verdadero si existió intención comercial no concretada
 * @param leadScore        puntaje validado y acotado entre 0.000 y 1.000
 * @param lostLeadReason   motivo explicativo de la oportunidad perdida (o null)
 */
public record LeadEvaluationResultDto(
        boolean lostLeadDetected,
        BigDecimal leadScore,
        String lostLeadReason) {
}
