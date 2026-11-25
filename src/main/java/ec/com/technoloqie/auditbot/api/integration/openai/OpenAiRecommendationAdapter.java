package ec.com.technoloqie.auditbot.api.integration.openai;

import ec.com.technoloqie.auditbot.api.dto.AuditFindingsContextDto;
import ec.com.technoloqie.auditbot.api.dto.AuditMessageDto;
import ec.com.technoloqie.auditbot.api.dto.RecommendationAnalysisResponseDto;
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
 * la API compatible con OpenAI (Ollama /v1) y generar recomendaciones de mejora (US-07).
 */
@Component
public class OpenAiRecommendationAdapter implements IRecommendationAiClient {

    private static final String SYSTEM_PROMPT = """
            Eres un experto consultor de experiencia conversacional y auditor de calidad de chatbots empresariales.
            Tu misión es analizar la interacción del usuario junto con los hallazgos previos detectados en la conversación (brechas de conocimiento, frustración y oportunidades comerciales perdidas) para formular recomendaciones concretas y accionables de mejora.

            Clasifica cada recomendación en uno de los siguientes tipos:
            1. "NEW_FAQ": Recomendación para incorporar una nueva pregunta frecuente o contenido informativo a la base de conocimiento del chatbot para cubrir dudas que el bot no pudo responder.
            2. "ALTERNATIVE_FLOW": Recomendación de ajuste en el flujo conversacional, lógica de derivación a un asesor humano, o manejo de objeciones comerciales ante situaciones de frustración del cliente o pérdida de ventas.

            Instrucciones para cada recomendación:
            - "type": "NEW_FAQ" o "ALTERNATIVE_FLOW".
            - "suggestedText": texto descriptivo, claro y conciso de la sugerencia (máximo 400 caracteres).
            - "originMessageId": el identificador del mensaje exacto de la conversación que motivó la recomendación, o null si aplica a la conversación en general.

            Devuelve estrictamente un objeto JSON con la clave "recommendations" que contenga una lista de objetos con los campos: "type", "suggestedText", "originMessageId".
            """;

    private final ChatClient chatClient;
    private final long timeoutSeconds;

    public OpenAiRecommendationAdapter(
            ChatClient chatClient,
            @Value("${auditbot.ai.openai.timeout-seconds:30}") long timeoutSeconds) {
        this.chatClient = chatClient;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public RecommendationAnalysisResponseDto analyzeRecommendations(
            List<AuditMessageDto> conversationMessages,
            AuditFindingsContextDto findings) {

        if (conversationMessages == null || conversationMessages.isEmpty()) {
            return new RecommendationAnalysisResponseDto(List.of());
        }

        String userPrompt = buildUserPrompt(conversationMessages, findings);

        CompletableFuture<RecommendationAnalysisResponseDto> future = CompletableFuture.supplyAsync(() ->
                chatClient.prompt()
                        .system(SYSTEM_PROMPT)
                        .user(userPrompt)
                        .call()
                        .entity(RecommendationAnalysisResponseDto.class)
        );

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
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

    String buildUserPrompt(List<AuditMessageDto> messages, AuditFindingsContextDto findings) {
        StringBuilder sb = new StringBuilder();

        sb.append("HALLAZGOS PREVIOS DETECTADOS EN LA AUDITORÍA:\n");
        if (findings != null) {
            sb.append("- Brechas de conocimiento detectadas: ").append(findings.gapsCount()).append("\n");
            if (findings.gapQuestions() != null && !findings.gapQuestions().isEmpty()) {
                sb.append("  Preguntas sin respuesta: ").append(String.join("; ", findings.gapQuestions())).append("\n");
            }
            sb.append("- Frustración detectada: ").append(findings.frustrationDetected()).append("\n");
            sb.append("- Oportunidad de venta perdida: ").append(findings.lostLeadDetected()).append("\n");
            if (findings.lostLeadDetected()) {
                sb.append("  Score de lead: ").append(findings.leadScore()).append("\n");
                sb.append("  Motivo de pérdida: ").append(findings.lostLeadReason()).append("\n");
            }
        } else {
            sb.append("Sin hallazgos previos detallados.\n");
        }

        sb.append("\nDIÁLOGO DE LA CONVERSACIÓN:\n");
        for (AuditMessageDto msg : messages) {
            String role = msg.role() != null ? msg.role() : "DESCONOCIDO";
            String msgId = msg.messageId() != null ? msg.messageId() : "sin-id";
            String content = msg.content() != null ? msg.content().replace("\"", "\\\"") : "";
            sb.append("[").append(role).append("] (id: ").append(msgId).append("): \"").append(content).append("\"\n");
        }

        return sb.toString();
    }
}
