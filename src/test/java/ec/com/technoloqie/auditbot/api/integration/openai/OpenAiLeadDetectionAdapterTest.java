package ec.com.technoloqie.auditbot.api.integration.openai;

import ec.com.technoloqie.auditbot.api.dto.AuditMessageDto;
import ec.com.technoloqie.auditbot.api.dto.LeadAnalysisResponseDto;
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
 * Pruebas unitarias para {@link OpenAiLeadDetectionAdapter} (US-05).
 * Cubre R4, R5, R6, R7, R24.
 */
@ExtendWith(MockitoExtension.class)
class OpenAiLeadDetectionAdapterTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private OpenAiLeadDetectionAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OpenAiLeadDetectionAdapter(chatClient, 5);
    }

    // R4, R6 — Invocación en única llamada y deserialización estructurada
    @Test
    void shouldSendConversationInSingleCall() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        LeadAnalysisResponseDto mockResponse = new LeadAnalysisResponseDto(
                true,
                new BigDecimal("0.850"),
                false,
                "Usuario interesado en plan Pro sin captura de contacto"
        );
        when(callResponseSpec.entity(eq(LeadAnalysisResponseDto.class))).thenReturn(mockResponse);

        List<AuditMessageDto> messages = List.of(
                new AuditMessageDto("msg-1", "USER", "¿Cuánto cuesta el plan Pro?", Instant.now()),
                new AuditMessageDto("msg-2", "ASSISTANT", "El plan Pro cuesta $99/mes", Instant.now())
        );

        LeadAnalysisResponseDto actual = adapter.evaluateCommercialOpportunity(messages);

        assertThat(actual).isNotNull();
        assertThat(actual.commercialIntentDetected()).isTrue();
        assertThat(actual.leadScore()).isEqualByComparingTo(new BigDecimal("0.850"));
        assertThat(actual.closureAchieved()).isFalse();
        assertThat(actual.reason()).isEqualTo("Usuario interesado en plan Pro sin captura de contacto");

        verify(chatClient).prompt();
        verify(callResponseSpec).entity(eq(LeadAnalysisResponseDto.class));
    }

    // R5 — System prompt estricto con los 4 campos comerciales
    @Test
    void shouldApplyStrictCommercialPrompt() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        when(callResponseSpec.entity(eq(LeadAnalysisResponseDto.class)))
                .thenReturn(new LeadAnalysisResponseDto(false, BigDecimal.ZERO, false, "Sin intención"));

        List<AuditMessageDto> messages = List.of(
                new AuditMessageDto("msg-1", "USER", "Hola", Instant.now())
        );

        adapter.evaluateCommercialOpportunity(messages);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(promptCaptor.capture());

        String capturedPrompt = promptCaptor.getValue();
        assertThat(capturedPrompt).contains("commercialIntentDetected");
        assertThat(capturedPrompt).contains("leadScore");
        assertThat(capturedPrompt).contains("closureAchieved");
        assertThat(capturedPrompt).contains("reason");
    }

    // R6 — Deserialización de respuesta estructurada
    @Test
    void shouldDeserializeStructuredLeadResponse() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        LeadAnalysisResponseDto mockResponse = new LeadAnalysisResponseDto(
                true,
                new BigDecimal("0.900"),
                true,
                "Se capturó el email del cliente"
        );
        when(callResponseSpec.entity(eq(LeadAnalysisResponseDto.class))).thenReturn(mockResponse);

        List<AuditMessageDto> messages = List.of(
                new AuditMessageDto("msg-1", "USER", "Quiero contratarlo, mi email es cliente@empresa.com", Instant.now())
        );

        LeadAnalysisResponseDto result = adapter.evaluateCommercialOpportunity(messages);

        assertThat(result.closureAchieved()).isTrue();
        assertThat(result.leadScore()).isEqualByComparingTo(new BigDecimal("0.900"));
        assertThat(result.commercialIntentDetected()).isTrue();
    }

    // R7 — Timeout configurable de la llamada a IA
    @Test
    void shouldTimeoutWhenConfiguredTimeExceeded() {
        OpenAiLeadDetectionAdapter fastTimeoutAdapter = new OpenAiLeadDetectionAdapter(chatClient, 1);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        when(callResponseSpec.entity(eq(LeadAnalysisResponseDto.class))).thenAnswer(invocation -> {
            Thread.sleep(2000); // 2 segundos, superior al timeout de 1s
            return new LeadAnalysisResponseDto(false, BigDecimal.ZERO, false, "");
        });

        List<AuditMessageDto> messages = List.of(
                new AuditMessageDto("msg-1", "USER", "Hola", Instant.now())
        );

        assertThatThrownBy(() -> fastTimeoutAdapter.evaluateCommercialOpportunity(messages))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Tiempo de espera agotado");
    }

    // Omisión de llamadas ante listas nulas o vacías
    @Test
    void shouldReturnDefaultWhenConversationIsEmptyOrNull() {
        LeadAnalysisResponseDto nullResult = adapter.evaluateCommercialOpportunity(null);
        assertThat(nullResult.commercialIntentDetected()).isFalse();
        assertThat(nullResult.leadScore()).isEqualByComparingTo(BigDecimal.ZERO);

        LeadAnalysisResponseDto emptyResult = adapter.evaluateCommercialOpportunity(List.of());
        assertThat(emptyResult.commercialIntentDetected()).isFalse();
        assertThat(emptyResult.leadScore()).isEqualByComparingTo(BigDecimal.ZERO);

        verify(chatClient, never()).prompt();
    }

    // Formato estructurado del user prompt
    @Test
    void shouldBuildUserPromptWithRolesAndEscaping() {
        List<AuditMessageDto> messages = List.of(
                new AuditMessageDto("msg-1", "USER", "¿Tienen \"descuentos\" de fin de año?", Instant.now()),
                new AuditMessageDto("msg-2", "ASSISTANT", "Sí, tenemos 20% de descuento.", Instant.now())
        );

        String prompt = adapter.buildUserPrompt(messages);

        assertThat(prompt).contains("[USER]: \"¿Tienen \\\"descuentos\\\" de fin de año?\"");
        assertThat(prompt).contains("[ASSISTANT]: \"Sí, tenemos 20% de descuento.\"");
    }
}
