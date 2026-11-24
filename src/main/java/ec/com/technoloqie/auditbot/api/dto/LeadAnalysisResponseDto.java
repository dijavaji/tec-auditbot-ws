package ec.com.technoloqie.auditbot.api.dto;

import java.math.BigDecimal;

/**
 * DTO contenedor de la respuesta estructurada de evaluación comercial devuelta por el modelo de IA (US-05).
 *
 * @param commercialIntentDetected indica si el usuario mostró intención comercial o de compra
 * @param leadScore                puntaje de calidad/intensidad del lead asignado por la IA
 * @param closureAchieved          indica si la conversación culminó en un cierre exitoso
 * @param reason                   motivo explicativo del resultado comercial
 */
public record LeadAnalysisResponseDto(
        Boolean commercialIntentDetected,
        BigDecimal leadScore,
        Boolean closureAchieved,
        String reason) {
}
