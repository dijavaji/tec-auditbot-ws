package ec.com.technoloqie.auditbot.api.integration.openai;

import ec.com.technoloqie.auditbot.api.dto.ConversationTurnDto;
import ec.com.technoloqie.auditbot.api.dto.GapAnalysisResponseDto;

import java.util.List;

/**
 * Puerto de integración anti-corrupción para invocar la evaluación y clasificación
 * de turnos de diálogo mediante inteligencia artificial.
 */
public interface IKnowledgeGapAiClient {

    /**
     * Clasifica los turnos de diálogo de una conversación indicando si corresponden
     * a una brecha de conocimiento (knowledge gap).
     *
     * @param turns lista de turnos cronológicos usuario-asistente
     * @return respuesta estructurada con las clasificaciones individuales
     */
    GapAnalysisResponseDto classifyTurns(List<ConversationTurnDto> turns);
}
