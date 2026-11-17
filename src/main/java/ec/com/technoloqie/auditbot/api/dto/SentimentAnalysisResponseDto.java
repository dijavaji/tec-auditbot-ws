package ec.com.technoloqie.auditbot.api.dto;

import java.util.List;

/**
 * DTO contenedor de la respuesta estructurada devuelta por el modelo de IA (US-04).
 *
 * @param classifications lista de clasificaciones estructuradas por mensaje
 */
public record SentimentAnalysisResponseDto(
        List<SentimentItemClassificationDto> classifications) {

    public SentimentAnalysisResponseDto {
        classifications = classifications == null ? List.of() : List.copyOf(classifications);
    }
}
