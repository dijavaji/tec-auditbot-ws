package ec.com.technoloqie.auditbot.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Contexto consolidado de hallazgos previos (US-03, US-04, US-05) para alimentar
 * la generación de recomendaciones (US-07).
 */
public record AuditFindingsContextDto(
        int gapsCount,
        List<String> gapQuestions,
        boolean frustrationDetected,
        boolean lostLeadDetected,
        BigDecimal leadScore,
        String lostLeadReason
) {}
