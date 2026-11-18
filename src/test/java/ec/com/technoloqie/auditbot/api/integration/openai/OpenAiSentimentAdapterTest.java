package ec.com.technoloqie.auditbot.api.integration.openai;

import ec.com.technoloqie.auditbot.api.dto.AuditMessageDto;
import ec.com.technoloqie.auditbot.api.dto.SentimentAnalysisResponseDto;
import ec.com.technoloqie.auditbot.api.dto.SentimentItemClassificationDto;
import ec.com.technoloqie.auditbot.api.model.SentimentLabel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para {@link OpenAiSentimentAdapter}.
 * Cubre R6, R7, R8, R29.
 */
@ExtendWith(MockitoExtension.class)
class OpenAiSentimentAdapterTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private OpenAiSentimentAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OpenAiSentimentAdapter(chatClient, 5);
    }

    // R6, R8 — Invocación en único lote y deserialización estructurada
    @Test
    void shouldSendUserMessagesInSingleBatchRequest() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        SentimentItemClassificationDto item1 = new SentimentItemClassificationDto(
                "msg-1", SentimentLabel.POSITIVE, new BigDecimal("0.950")
        );
        SentimentItemClassificationDto item2 = new SentimentItemClassificationDto(
                "msg-2", SentimentLabel.FRUSTRATED, new BigDecimal("0.880")
        );
        SentimentAnalysisResponseDto mockResponse = new SentimentAnalysisResponseDto(List.of(item1, item2));

        when(callResponseSpec.entity(eq(SentimentAnalysisResponseDto.class))).thenReturn(mockResponse);

        List<AuditMessageDto> messages = List.of(
                new AuditMessageDto("msg-1", "USER", "Hola, me encanta el servicio", Instant.now()),
                new AuditMessageDto("msg-2", "USER", "Pero ahora me está fallando y no responden", Instant.now())
        );

        SentimentAnalysisResponseDto actual = adapter.classifySentiment(messages);

        assertThat(actual).isNotNull();
        assertThat(actual.classifications()).hasSize(2);
        assertThat(actual.classifications().get(0).messageId()).isEqualTo("msg-1");
        assertThat(actual.classifications().get(0).label()).isEqualTo(SentimentLabel.POSITIVE);
        assertThat(actual.classifications().get(1).messageId()).isEqualTo("msg-2");
        assertThat(actual.classifications().get(1).label()).isEqualTo(SentimentLabel.FRUSTRATED);

        verify(chatClient).prompt();
        verify(callResponseSpec).entity(eq(SentimentAnalysisResponseDto.class));
    }

    // R7 — System prompt estricto con las 4 categorías
    @Test
    void shouldIncludeStrictSentimentSystemPrompt() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        when(callResponseSpec.entity(eq(SentimentAnalysisResponseDto.class)))
                .thenReturn(new SentimentAnalysisResponseDto(List.of()));

        List<AuditMessageDto> messages = List.of(
                new AuditMessageDto("msg-1", "USER", "Hola", Instant.now())
        );

        adapter.classifySentiment(messages);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(promptCaptor.capture());

        String capturedPrompt = promptCaptor.getValue();
        assertThat(capturedPrompt).contains("POSITIVE");
        assertThat(capturedPrompt).contains("NEUTRAL");
        assertThat(capturedPrompt).contains("NEGATIVE");
        assertThat(capturedPrompt).contains("FRUSTRATED");
    }

    // R8 — Deserialización de respuesta estructurada
    @Test
    void shouldDeserializeStructuredOutput() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        SentimentItemClassificationDto item = new SentimentItemClassificationDto(
                "msg-10", SentimentLabel.NEGATIVE, new BigDecimal("0.720")
        );
        when(callResponseSpec.entity(eq(SentimentAnalysisResponseDto.class)))
                .thenReturn(new SentimentAnalysisResponseDto(List.of(item)));

        List<AuditMessageDto> messages = List.of(
                new AuditMessageDto("msg-10", "USER", "Tengo un problema", Instant.now())
        );

        SentimentAnalysisResponseDto response = adapter.classifySentiment(messages);

        assertThat(response.classifications()).hasSize(1);
        assertThat(response.classifications().get(0).score()).isEqualByComparingTo(new BigDecimal("0.720"));
    }

    // R29 — Cumplimiento de timeout configurable
    @Test
    void shouldEnforceConfiguredTimeout() {
        OpenAiSentimentAdapter fastTimeoutAdapter = new OpenAiSentimentAdapter(chatClient, 1);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        when(callResponseSpec.entity(eq(SentimentAnalysisResponseDto.class))).thenAnswer(invocation -> {
            Thread.sleep(2000); // 2 segundos, superior al timeout de 1s
            return new SentimentAnalysisResponseDto(List.of());
        });

        List<AuditMessageDto> messages = List.of(
                new AuditMessageDto("msg-1", "USER", "Hola", Instant.now())
        );

        assertThatThrownBy(() -> fastTimeoutAdapter.classifySentiment(messages))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Tiempo de espera agotado");
    }

    // Omisión de llamadas ante listas nulas o vacías
    @Test
    void shouldReturnEmptyResponseWhenUserMessagesListIsEmptyOrNull() {
        SentimentAnalysisResponseDto nullResult = adapter.classifySentiment(null);
        assertThat(nullResult.classifications()).isEmpty();

        SentimentAnalysisResponseDto emptyResult = adapter.classifySentiment(List.of());
        assertThat(emptyResult.classifications()).isEmpty();

        verify(chatClient, never()).prompt();
    }

    // Fallback NEUTRAL si el modelo omite clasificar algún mensaje del batch
    @Test
    void shouldFillOmittedMessagesWithNeutralFallback() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        // El modelo solo devolvió respuesta para msg-1, omitiendo msg-2
        SentimentItemClassificationDto item1 = new SentimentItemClassificationDto(
                "msg-1", SentimentLabel.POSITIVE, new BigDecimal("0.900")
        );
        when(callResponseSpec.entity(eq(SentimentAnalysisResponseDto.class)))
                .thenReturn(new SentimentAnalysisResponseDto(List.of(item1)));

        List<AuditMessageDto> messages = List.of(
                new AuditMessageDto("msg-1", "USER", "Mensaje 1", Instant.now()),
                new AuditMessageDto("msg-2", "USER", "Mensaje 2", Instant.now())
        );

        SentimentAnalysisResponseDto response = adapter.classifySentiment(messages);

        assertThat(response.classifications()).hasSize(2);
        assertThat(response.classifications().get(0).messageId()).isEqualTo("msg-1");
        assertThat(response.classifications().get(0).label()).isEqualTo(SentimentLabel.POSITIVE);

        // msg-2 autocompletado con fallback NEUTRAL
        assertThat(response.classifications().get(1).messageId()).isEqualTo("msg-2");
        assertThat(response.classifications().get(1).label()).isEqualTo(SentimentLabel.NEUTRAL);
        assertThat(response.classifications().get(1).score()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // Formato estructurado del user prompt
    @Test
    void shouldBuildDelimitedUserPromptCorrectly() {
        List<AuditMessageDto> messages = List.of(
                new AuditMessageDto("msg-1", "USER", "¿Tienen saldo?", Instant.now()),
                new AuditMessageDto("msg-2", "USER", "Quiero cancelar mi cuenta", Instant.now())
        );

        String prompt = adapter.buildUserPrompt(messages);

        assertThat(prompt).contains("[ID: msg-1] Mensaje: \"¿Tienen saldo?\"");
        assertThat(prompt).contains("[ID: msg-2] Mensaje: \"Quiero cancelar mi cuenta\"");
    }
}
