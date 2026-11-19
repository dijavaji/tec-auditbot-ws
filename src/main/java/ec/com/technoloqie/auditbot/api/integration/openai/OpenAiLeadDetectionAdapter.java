package ec.com.technoloqie.auditbot.api.integration.openai;

import ec.com.technoloqie.auditbot.api.dto.AuditMessageDto;
import ec.com.technoloqie.auditbot.api.dto.LeadAnalysisResponseDto;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Adaptador de integración que utiliza Spring AI ChatClient para consultar
 * la API compatible con OpenAI (Ollama /v1) y evaluar oportunidades comerciales
 * y leads en una conversación (US-05).
 */
@Component
public class OpenAiLeadDetectionAdapter implements ILeadDetectionAiClient {

    private static final String SYSTEM_PROMPT = """
            Eres un auditor comercial experto en análisis de conversaciones de chatbots empresariales.
            Tu misión es analizar la interacción completa entre el usuario y el asistente para evaluar si existió una oportunidad de venta o contratación.

            Evalúa los siguientes aspectos:
            1. "commercialIntentDetected" (boolean): true si el usuario mostró intención de compra, contratación, interés en precios, productos, servicios, planes comerciales, suscripciones o demos; false si solo fue soporte técnico general, saludo o consulta informativa sin potencial comercial.
            2. "leadScore" (number): puntaje decimal entre 0.000 y 1.000 que refleja el grado y fuerza del interés comercial mostrado por el usuario. Si no hay intención comercial, debe ser 0.000.
            3. "closureAchieved" (boolean): true si la conversación culminó en un cierre comercial efectivo (se capturó teléfono o correo electrónico de contacto, se confirmó derivación a un asesor humano comercial, o se completó una transacción/pedido); false si la conversación finalizó sin que se capturaran datos ni se derivara oportunamente al prospecto.
            4. "reason" (string): justificación concisa en español (máximo 400 caracteres) explicando por qué se considera una oportunidad de venta perdida o por qué no lo es.

            Devuelve estrictamente un objeto JSON con las claves:
            "commercialIntentDetected", "leadScore", "closureAchieved", "reason".
            """;

    private final ChatClient chatClient;
    private final long timeoutSeconds;

    public OpenAiLeadDetectionAdapter(
            ChatClient chatClient,
            @Value("${auditbot.ai.openai.timeout-seconds:30}") long timeoutSeconds) {
        this.chatClient = chatClient;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public LeadAnalysisResponseDto evaluateCommercialOpportunity(List<AuditMessageDto> conversationMessages) {
        if (conversationMessages == null || conversationMessages.isEmpty()) {
            return new LeadAnalysisResponseDto(false, BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP), false, null);
        }

        String userPrompt = buildUserPrompt(conversationMessages);

        CompletableFuture<LeadAnalysisResponseDto> future = CompletableFuture.supplyAsync(() ->
                chatClient.prompt()
                        .system(SYSTEM_PROMPT)
                        .user(userPrompt)
                        .call()
                        .entity(LeadAnalysisResponseDto.class)
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

    String buildUserPrompt(List<AuditMessageDto> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analiza la siguiente conversación en orden cronológico:\n\n");

        for (AuditMessageDto msg : messages) {
            String role = msg.role() != null ? msg.role() : "DESCONOCIDO";
            String content = msg.content() != null ? msg.content().replace("\"", "\\\"") : "";
            sb.append("[").append(role).append("]: \"").append(content).append("\"\n");
        }

        return sb.toString();
    }
}
