package ec.com.technoloqie.auditbot.api.dto;

import java.util.List;

/**
 * Respuesta estructurada devuelta por el cliente de IA con la clasificación de todos los turnos.
 *
 * @param classifications lista de clasificaciones individuales para cada turno evaluado
 */
public record GapAnalysisResponseDto(
        List<KnowledgeGapCandidateDto> classifications
) {
    public GapAnalysisResponseDto {
        if (classifications == null) {
            classifications = List.of();
        }
    }
}
