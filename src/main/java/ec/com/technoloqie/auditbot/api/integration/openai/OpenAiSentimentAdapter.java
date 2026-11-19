package ec.com.technoloqie.auditbot.api.integration.openai;

import ec.com.technoloqie.auditbot.api.dto.AuditMessageDto;
import ec.com.technoloqie.auditbot.api.dto.SentimentAnalysisResponseDto;
import ec.com.technoloqie.auditbot.api.dto.SentimentItemClassificationDto;
import ec.com.technoloqie.auditbot.api.model.SentimentLabel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Adaptador de integración que utiliza Spring AI ChatClient para consultar
 * la API compatible con OpenAI (Ollama /v1) y clasificar el sentimiento
 * de mensajes de usuario en lote (US-04).
 */
@Component
public class OpenAiSentimentAdapter implements ISentimentAiClient {

    private static final String SYSTEM_PROMPT = """
            Eres un analizador experto en experiencia de usuario y sentimientos en canales de atención y chatbots.
            Tu misión es clasificar el estado emocional expresado por el cliente en cada uno de sus mensajes.

            Para cada mensaje debes asignar una etiqueta estricta:
            - "POSITIVE": El usuario expresa satisfacción, agradecimiento, amabilidad o agrado.
            - "NEUTRAL": Consulta objetiva, datos informativos, saludos o respuestas estándar sin carga emocional evidente.
            - "NEGATIVE": Leve disgusto, impaciencia, duda persistente o incomodidad no extrema.
            - "FRUSTRATED": Molestia evidente, quejas graves, reclamos por mal servicio, insultos, mayúsculas sostenidas, amenazas de cancelación o repetición insistente por falta de solución.

            Devuelve estrictamente un objeto JSON con la clave "classifications" que contenga un arreglo con el análisis de cada mensaje:
            - "messageId": identificador del mensaje analizado
            - "label": "POSITIVE" | "NEUTRAL" | "NEGATIVE" | "FRUSTRATED"
            - "score": número decimal entre 0.000 y 1.000 indicando la certeza de la clasificación
            """;

    private final ChatClient chatClient;
    private final long timeoutSeconds;

    public OpenAiSentimentAdapter(
            ChatClient chatClient,
            @Value("${auditbot.ai.openai.timeout-seconds:30}") long timeoutSeconds) {
        this.chatClient = chatClient;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public SentimentAnalysisResponseDto classifySentiment(List<AuditMessageDto> userMessages) {
        if (userMessages == null || userMessages.isEmpty()) {
            return new SentimentAnalysisResponseDto(List.of());
        }

        String userPrompt = buildUserPrompt(userMessages);

        CompletableFuture<SentimentAnalysisResponseDto> future = CompletableFuture.supplyAsync(() ->
                chatClient.prompt()
                        .system(SYSTEM_PROMPT)
                        .user(userPrompt)
                        .call()
                        .entity(SentimentAnalysisResponseDto.class)
        );

        SentimentAnalysisResponseDto rawResponse;
        try {
            rawResponse = future.get(timeoutSeconds, TimeUnit.SECONDS);
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

        return enforceCompleteSequence(userMessages, rawResponse);
    }

    String buildUserPrompt(List<AuditMessageDto> userMessages) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analiza los siguientes mensajes emitidos por el usuario en orden cronológico:\n\n");

        for (AuditMessageDto msg : userMessages) {
            String content = msg.content() != null ? msg.content().replace("\"", "\\\"") : "";
            sb.append("[ID: ").append(msg.messageId()).append("] Mensaje: \"").append(content).append("\"\n");
        }

        return sb.toString();
    }

    private SentimentAnalysisResponseDto enforceCompleteSequence(
            List<AuditMessageDto> userMessages,
            SentimentAnalysisResponseDto response) {

        List<SentimentItemClassificationDto> received = (response != null && response.classifications() != null)
                ? response.classifications()
                : List.of();

        Map<String, SentimentItemClassificationDto> byId = received.stream()
                .filter(c -> c != null && c.messageId() != null)
                .collect(Collectors.toMap(
                        SentimentItemClassificationDto::messageId,
                        c -> c,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));

        List<SentimentItemClassificationDto> completeList = new ArrayList<>(userMessages.size());
        for (AuditMessageDto msg : userMessages) {
            if (msg != null && msg.messageId() != null) {
                if (byId.containsKey(msg.messageId())) {
                    completeList.add(byId.get(msg.messageId()));
                } else {
                    completeList.add(new SentimentItemClassificationDto(
                            msg.messageId(),
                            SentimentLabel.NEUTRAL,
                            BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP)
                    ));
                }
            }
        }

        return new SentimentAnalysisResponseDto(completeList);
    }
}
