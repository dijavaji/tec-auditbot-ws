package ec.com.technoloqie.auditbot.api.dto;

import ec.com.technoloqie.auditbot.api.model.SentimentTrend;
import java.util.List;

/**
 * Resultado integral del análisis de sentimiento de una conversación auditada (US-04).
 *
 * @param items               lista de sentimientos procesados con su orden secuencial
 * @param frustrationDetected bandera de frustración a nivel de conversación completa
 * @param trend               tendencia de evolución emocional (IMPROVING, WORSENING, STABLE)
 */
public record SentimentEvaluationResultDto(
        List<ProcessedSentimentDto> items,
        boolean frustrationDetected,
        SentimentTrend trend) {

    public SentimentEvaluationResultDto {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
