package ec.com.technoloqie.auditbot.api.integration.openai;

import ec.com.technoloqie.auditbot.api.dto.ConversationTurnDto;
import ec.com.technoloqie.auditbot.api.dto.GapAnalysisResponseDto;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Adaptador de integración que utiliza Spring AI ChatClient para consultar
 * la API compatible con OpenAI (Ollama /v1) y clasificar brechas de conocimiento.
 */
@Component
public class OpenAiKnowledgeGapAdapter implements IKnowledgeGapAiClient {

    private static final String SYSTEM_PROMPT = """
            Eres un auditor experto de conversaciones de atención al cliente y soporte.
            Tu misión es analizar si las consultas del usuario fueron respondidas con éxito o si revelan una brecha de conocimiento (knowledge gap) del asistente virtual.

            Una respuesta constituye un gap de conocimiento si el asistente:
            1) Admite explícitamente no saber o no contar con la información solicitada.
            2) Proporciona respuestas evasivas, genéricas o fuera del contexto de la pregunta.
            3) Pide disculpas reiteradas sin ofrecer una solución o canal de resolución.
            4) No cubre el objetivo central de la consulta del usuario.

            Devuelve estrictamente un objeto JSON con la clave "classifications" que contenga un arreglo con el análisis de cada turno:
            - "messageId": identificador del mensaje del usuario analizado
            - "questionText": texto original de la pregunta del usuario
            - "isGap": booleano (true si es brecha de conocimiento, false en caso contrario)
            - "confidenceScore": número decimal entre 0.000 y 1.000 indicando tu grado de certeza
            - "reason": breve justificación técnica de la clasificación
            """;

    private final ChatClient chatClient;
    private final long timeoutSeconds;

    public OpenAiKnowledgeGapAdapter(
            ChatClient chatClient,
            @Value("${auditbot.ai.openai.timeout-seconds:30}") long timeoutSeconds) {
        this.chatClient = chatClient;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public GapAnalysisResponseDto classifyTurns(List<ConversationTurnDto> turns) {
        if (turns == null || turns.isEmpty()) {
            return new GapAnalysisResponseDto(List.of());
        }

        String userPrompt = buildUserPrompt(turns);

        CompletableFuture<GapAnalysisResponseDto> future = CompletableFuture.supplyAsync(() ->
                chatClient.prompt()
                        .system(SYSTEM_PROMPT)
                        .user(userPrompt)
                        .call()
                        .entity(GapAnalysisResponseDto.class)
        );

        try {
            GapAnalysisResponseDto response = future.get(timeoutSeconds, TimeUnit.SECONDS);
            return response != null ? response : new GapAnalysisResponseDto(List.of());
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("Tiempo de espera agotado al consultar el modelo de IA (" + timeoutSeconds + "s)", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("Error en la llamada al modelo de IA: " + cause.getMessage(), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupción durante la llamada al modelo de IA", e);
        }
    }

    String buildUserPrompt(List<ConversationTurnDto> turns) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analiza los siguientes turnos de conversación delimitados:\n\n");

        for (ConversationTurnDto turn : turns) {
            sb.append("--- Turno [").append(turn.userMessageId()).append("] ---\n");
            sb.append("Usuario: ").append(turn.userQuestion() != null ? turn.userQuestion() : "").append("\n");
            sb.append("Asistente: ")
                    .append(turn.assistantResponse() != null ? turn.assistantResponse() : "(Sin respuesta)")
                    .append("\n\n");
        }

        return sb.toString();
    }
}
