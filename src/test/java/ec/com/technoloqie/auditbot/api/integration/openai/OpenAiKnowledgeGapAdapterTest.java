package ec.com.technoloqie.auditbot.api.integration.openai;

import ec.com.technoloqie.auditbot.api.dto.ConversationTurnDto;
import ec.com.technoloqie.auditbot.api.dto.GapAnalysisResponseDto;
import ec.com.technoloqie.auditbot.api.dto.KnowledgeGapCandidateDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
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
 * Pruebas unitarias para {@link OpenAiKnowledgeGapAdapter}.
 * Cubre R5, R6, R7, R8, R28.
 */
@ExtendWith(MockitoExtension.class)
class OpenAiKnowledgeGapAdapterTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private OpenAiKnowledgeGapAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OpenAiKnowledgeGapAdapter(chatClient, 5);
    }

    // R5, R6, R7 — Invocación con system prompt estricto y obtención estructurada
    @Test
    void shouldInvokeChatClientAndReturnStructuredResponse() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        KnowledgeGapCandidateDto candidate = new KnowledgeGapCandidateDto(
                "msg-1", "¿Tienen entrega a domicilio?", true, new BigDecimal("0.950"), "Bot no sabe"
        );
        GapAnalysisResponseDto expectedResponse = new GapAnalysisResponseDto(List.of(candidate));
        when(callResponseSpec.entity(eq(GapAnalysisResponseDto.class))).thenReturn(expectedResponse);

        List<ConversationTurnDto> turns = List.of(
                new ConversationTurnDto("msg-1", "¿Tienen entrega a domicilio?", "asst-1", "Lo siento, no tengo esa info.")
        );

        GapAnalysisResponseDto actual = adapter.classifyTurns(turns);

        assertThat(actual).isNotNull();
        assertThat(actual.classifications()).hasSize(1);
        assertThat(actual.classifications().get(0).messageId()).isEqualTo("msg-1");
        assertThat(actual.classifications().get(0).isGap()).isTrue();
        assertThat(actual.classifications().get(0).confidenceScore()).isEqualByComparingTo(new BigDecimal("0.950"));

        verify(requestSpec).system(anyString());
        verify(requestSpec).user(anyString());
        verify(callResponseSpec).entity(eq(GapAnalysisResponseDto.class));
    }

    // Caso de lista vacía o nula: no debe llamar al chatClient
    @Test
    void shouldReturnEmptyResponseWhenTurnsListIsEmptyOrNull() {
        GapAnalysisResponseDto nullResult = adapter.classifyTurns(null);
        assertThat(nullResult.classifications()).isEmpty();

        GapAnalysisResponseDto emptyResult = adapter.classifyTurns(List.of());
        assertThat(emptyResult.classifications()).isEmpty();

        verify(chatClient, never()).prompt();
    }

    // R28 — Timeout al consultar el modelo de IA
    @Test
    void shouldTimeoutWhenModelDoesNotRespondInTime() {
        OpenAiKnowledgeGapAdapter fastTimeoutAdapter = new OpenAiKnowledgeGapAdapter(chatClient, 1);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        when(callResponseSpec.entity(eq(GapAnalysisResponseDto.class))).thenAnswer(invocation -> {
            Thread.sleep(2000); // 2 segundos, superior al timeout de 1s
            return new GapAnalysisResponseDto(List.of());
        });

        List<ConversationTurnDto> turns = List.of(
                new ConversationTurnDto("msg-1", "pregunta", "asst-1", "respuesta")
        );

        assertThatThrownBy(() -> fastTimeoutAdapter.classifyTurns(turns))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Tiempo de espera agotado");
    }

    // Verifica formato delimitado del user prompt
    @Test
    void shouldBuildDelimitedUserPromptCorrectly() {
        List<ConversationTurnDto> turns = List.of(
                new ConversationTurnDto("msg-1", "¿Horario?", "asst-1", "9 a 18"),
                new ConversationTurnDto("msg-2", "¿Precios?", null, null)
        );

        String prompt = adapter.buildUserPrompt(turns);

        assertThat(prompt).contains("--- Turno [msg-1] ---");
        assertThat(prompt).contains("Usuario: ¿Horario?");
        assertThat(prompt).contains("Asistente: 9 a 18");
        assertThat(prompt).contains("--- Turno [msg-2] ---");
        assertThat(prompt).contains("Usuario: ¿Precios?");
        assertThat(prompt).contains("Asistente: (Sin respuesta)");
    }
}
