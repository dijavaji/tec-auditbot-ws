package ec.com.technoloqie.auditbot.api.integration.openai;

import ec.com.technoloqie.auditbot.api.dto.AuditFindingsContextDto;
import ec.com.technoloqie.auditbot.api.dto.AuditMessageDto;
import ec.com.technoloqie.auditbot.api.dto.RecommendationAnalysisResponseDto;
import ec.com.technoloqie.auditbot.api.dto.RecommendationCandidateDto;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para {@link OpenAiRecommendationAdapter} (US-07).
 * Cubre R6, R7, R8, R9, R23.
 */
@ExtendWith(MockitoExtension.class)
class OpenAiRecommendationAdapterTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private OpenAiRecommendationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OpenAiRecommendationAdapter(chatClient, 5);
    }

    // R6, R8 — Invocación en única llamada y deserialización estructurada
    @Test
    void shouldSendFindingsAndDialogueInSingleCall() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        RecommendationAnalysisResponseDto mockResponse = new RecommendationAnalysisResponseDto(List.of(
                new RecommendationCandidateDto("NEW_FAQ", "Crear FAQ de reembolsos", "msg-1"),
                new RecommendationCandidateDto("ALTERNATIVE_FLOW", "Derivar a agente comercial", "msg-2")
        ));
        when(callResponseSpec.entity(eq(RecommendationAnalysisResponseDto.class))).thenReturn(mockResponse);

        List<AuditMessageDto> messages = List.of(
                new AuditMessageDto("msg-1", "USER", "¿Cómo pido reembolso?", Instant.now()),
                new AuditMessageDto("msg-2", "ASSISTANT", "No sé.", Instant.now())
        );
        AuditFindingsContextDto findings = new AuditFindingsContextDto(
                1,
                List.of("¿Cómo pido reembolso?"),
                false,
                false,
                BigDecimal.ZERO,
                null
        );

        RecommendationAnalysisResponseDto actual = adapter.analyzeRecommendations(messages, findings);

        assertThat(actual).isNotNull();
        assertThat(actual.recommendations()).hasSize(2);
        assertThat(actual.recommendations().get(0).type()).isEqualTo("NEW_FAQ");
        assertThat(actual.recommendations().get(0).suggestedText()).isEqualTo("Crear FAQ de reembolsos");
        assertThat(actual.recommendations().get(0).originMessageId()).isEqualTo("msg-1");

        verify(chatClient).prompt();
        verify(callResponseSpec).entity(eq(RecommendationAnalysisResponseDto.class));
    }

    // R7 — System prompt estricto con los tipos de recomendación
    @Test
    void shouldApplyStrictRecommendationPrompt() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        when(callResponseSpec.entity(eq(RecommendationAnalysisResponseDto.class)))
                .thenReturn(new RecommendationAnalysisResponseDto(List.of()));

        List<AuditMessageDto> messages = List.of(
                new AuditMessageDto("msg-1", "USER", "Hola", Instant.now())
        );

        adapter.analyzeRecommendations(messages, null);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(promptCaptor.capture());

        String capturedPrompt = promptCaptor.getValue();
        assertThat(capturedPrompt).contains("NEW_FAQ");
        assertThat(capturedPrompt).contains("ALTERNATIVE_FLOW");
        assertThat(capturedPrompt).contains("suggestedText");
        assertThat(capturedPrompt).contains("originMessageId");
    }

    // R8 — Deserialización de candidatos de recomendación
    @Test
    void shouldDeserializeStructuredRecommendationResponse() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        RecommendationCandidateDto candidate = new RecommendationCandidateDto(
                "ALTERNATIVE_FLOW",
                "Ofrecer atención telefónica si el bot no responde 2 veces seguidas",
                "msg-turn-4"
        );
        when(callResponseSpec.entity(eq(RecommendationAnalysisResponseDto.class)))
                .thenReturn(new RecommendationAnalysisResponseDto(List.of(candidate)));

        List<AuditMessageDto> messages = List.of(
                new AuditMessageDto("msg-turn-4", "USER", "Sigo sin respuesta", Instant.now())
        );

        RecommendationAnalysisResponseDto response = adapter.analyzeRecommendations(messages, null);

        assertThat(response.recommendations()).hasSize(1);
        assertThat(response.recommendations().get(0).suggestedText())
                .isEqualTo("Ofrecer atención telefónica si el bot no responde 2 veces seguidas");
        assertThat(response.recommendations().get(0).originMessageId()).isEqualTo("msg-turn-4");
    }

    // R9 — Timeout configurable de la llamada a IA
    @Test
    void shouldTimeoutWhenConfiguredTimeExceeded() {
        OpenAiRecommendationAdapter fastTimeoutAdapter = new OpenAiRecommendationAdapter(chatClient, 1);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        when(callResponseSpec.entity(eq(RecommendationAnalysisResponseDto.class))).thenAnswer(invocation -> {
            Thread.sleep(2000); // 2 segundos, superior al timeout de 1s
            return new RecommendationAnalysisResponseDto(List.of());
        });

        List<AuditMessageDto> messages = List.of(
                new AuditMessageDto("msg-1", "USER", "Hola", Instant.now())
        );

        assertThatThrownBy(() -> fastTimeoutAdapter.analyzeRecommendations(messages, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Tiempo de espera agotado");
    }

    // Omisión de llamadas ante listas nulas o vacías
    @Test
    void shouldReturnEmptyWhenConversationIsEmptyOrNull() {
        RecommendationAnalysisResponseDto nullResult = adapter.analyzeRecommendations(null, null);
        assertThat(nullResult.recommendations()).isEmpty();

        RecommendationAnalysisResponseDto emptyResult = adapter.analyzeRecommendations(List.of(), null);
        assertThat(emptyResult.recommendations()).isEmpty();

        verify(chatClient, never()).prompt();
    }

    // Formato estructurado del user prompt con hallazgos y diálogo
    @Test
    void shouldBuildUserPromptWithFindingsAndDialogue() {
        List<AuditMessageDto> messages = List.of(
                new AuditMessageDto("msg-1", "USER", "Pregunta con \"comillas\"", Instant.now()),
                new AuditMessageDto("msg-2", "ASSISTANT", "Respuesta", Instant.now())
        );
        AuditFindingsContextDto findings = new AuditFindingsContextDto(
                1,
                List.of("Pregunta con comillas"),
                true,
                true,
                new BigDecimal("0.750"),
                "El bot no cerró la venta"
        );

        String prompt = adapter.buildUserPrompt(messages, findings);

        assertThat(prompt).contains("HALLAZGOS PREVIOS DETECTADOS EN LA AUDITORÍA:");
        assertThat(prompt).contains("Brechas de conocimiento detectadas: 1");
        assertThat(prompt).contains("Frustración detectada: true");
        assertThat(prompt).contains("Oportunidad de venta perdida: true");
        assertThat(prompt).contains("Score de lead: 0.750");
        assertThat(prompt).contains("El bot no cerró la venta");
        assertThat(prompt).contains("[USER] (id: msg-1): \"Pregunta con \\\"comillas\\\"\"");
    }
}
