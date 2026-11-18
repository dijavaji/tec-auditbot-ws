package ec.com.technoloqie.auditbot.api.service.impl;

import ec.com.technoloqie.auditbot.api.analyzer.LeadAnalyzer;
import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;
import ec.com.technoloqie.auditbot.api.dto.AuditMessageDto;
import ec.com.technoloqie.auditbot.api.dto.LeadAnalysisResponseDto;
import ec.com.technoloqie.auditbot.api.integration.openai.ILeadDetectionAiClient;
import ec.com.technoloqie.auditbot.api.model.AuditResult;
import ec.com.technoloqie.auditbot.api.model.AuditStatus;
import ec.com.technoloqie.auditbot.api.repository.IAuditResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para {@link LeadDetectionServiceImpl} (US-05).
 * Cubre R1, R2, R3, R16, R17, R18, R21, R22.
 */
@ExtendWith(MockitoExtension.class)
class LeadDetectionServiceImplTest {

    @Mock
    private ILeadDetectionAiClient aiClient;

    @Mock
    private IAuditResultRepository auditResultRepository;

    private LeadAnalyzer analyzer;
    private LeadDetectionServiceImpl service;

    @BeforeEach
    void setUp() {
        analyzer = new LeadAnalyzer();
        service = new LeadDetectionServiceImpl(aiClient, analyzer, auditResultRepository);
    }

    private AuditResult buildAuditRecord(String chatId) {
        AuditResult record = new AuditResult();
        record.setAuditId("audit-123");
        record.setChatId(chatId);
        record.setAssistantId("assistant-sales");
        record.setStatus(AuditStatus.PROCESSING);
        record.setEventId("event-123");
        record.setAttemptCount(1);
        Instant now = Instant.now();
        record.setCreatedDate(now);
        record.setUpdatedDate(now);
        return record;
    }

    // R1, R2 — Provee contexto cronológico descartando SYSTEM y reteniendo USER y ASSISTANT
    @Test
    @DisplayName("Debe extraer mensajes cronológicamente descartando los de rol SYSTEM")
    void shouldProvideOrderedConversationContext() {
        Instant t1 = Instant.parse("2026-11-18T10:00:00Z");
        Instant t2 = Instant.parse("2026-11-18T10:01:00Z");
        Instant t3 = Instant.parse("2026-11-18T10:02:00Z");

        AuditMessageDto sysMsg = new AuditMessageDto("msg-0", "SYSTEM", "Prompt de inicio", t1);
        AuditMessageDto userMsg = new AuditMessageDto("msg-1", "USER", "¿Tienen planes de pago?", t2);
        AuditMessageDto botMsg = new AuditMessageDto("msg-2", "ASSISTANT", "Aceptamos tarjetas de crédito", t3);

        AuditConversationDto conv = new AuditConversationDto("chat-1", "asst-1", t1, null, List.of(botMsg, sysMsg, userMsg));

        List<AuditMessageDto> evaluable = service.extractEvaluableMessages(conv);

        assertThat(evaluable).hasSize(2);
        assertThat(evaluable.get(0).messageId()).isEqualTo("msg-1");
        assertThat(evaluable.get(1).messageId()).isEqualTo("msg-2");
    }

    // R2 — Filtrar mensajes de sistema en conversación
    @Test
    @DisplayName("No debe incluir mensajes de rol SYSTEM")
    void shouldFilterOutSystemMessages() {
        AuditConversationDto conv = new AuditConversationDto("chat-2", "asst-1", Instant.now(), null, List.of(
                new AuditMessageDto("msg-sys", "SYSTEM", "Contexto de sistema", Instant.now())
        ));

        List<AuditMessageDto> evaluable = service.extractEvaluableMessages(conv);
        assertThat(evaluable).isEmpty();
    }

    // R3 — Conversación sin mensajes de usuario omite llamada a IA y persiste neutros
    @Test
    @DisplayName("Debe persistir valores neutros y omitir IA si no hay mensajes de usuario")
    void shouldHandleConversationWithoutUserMessages() {
        String chatId = "chat-no-user";
        AuditResult record = buildAuditRecord(chatId);
        when(auditResultRepository.findByChatId(chatId)).thenReturn(Optional.of(record));

        AuditConversationDto conv = new AuditConversationDto(chatId, "asst-1", Instant.now(), null, List.of(
                new AuditMessageDto("msg-bot", "ASSISTANT", "Hola, ¿en qué te ayudo?", Instant.now())
        ));

        service.analyzeAndPersistLeads(chatId, conv);

        verify(aiClient, never()).evaluateCommercialOpportunity(any());
        assertThat(record.getLostLeadDetected()).isFalse();
        assertThat(record.getLeadScore()).isEqualByComparingTo(new BigDecimal("0.000"));
        assertThat(record.getLostLeadReason()).isNull();
        assertThat(record.getStatus()).isEqualTo(AuditStatus.PROCESSING);
        verify(auditResultRepository).saveAndFlush(record);
    }

    // R16, R17 — Actualización atómica de resultados y estado se mantiene en PROCESSING
    @Test
    @DisplayName("Debe actualizar atómicamente los campos de lead en AuditResult y mantener PROCESSING")
    void shouldUpdateAuditResultFieldsAtomically() {
        String chatId = "chat-success";
        AuditResult record = buildAuditRecord(chatId);
        when(auditResultRepository.findByChatId(chatId)).thenReturn(Optional.of(record));

        AuditConversationDto conv = new AuditConversationDto(chatId, "asst-1", Instant.now(), null, List.of(
                new AuditMessageDto("msg-1", "USER", "Quiero contratar el plan anual", Instant.now()),
                new AuditMessageDto("msg-2", "ASSISTANT", "El plan cuesta $500", Instant.now())
        ));

        LeadAnalysisResponseDto aiResponse = new LeadAnalysisResponseDto(
                true,
                new BigDecimal("0.850"),
                false,
                "Usuario interesado en plan anual sin datos capturados"
        );
        when(aiClient.evaluateCommercialOpportunity(any())).thenReturn(aiResponse);

        service.analyzeAndPersistLeads(chatId, conv);

        assertThat(record.getLostLeadDetected()).isTrue();
        assertThat(record.getLeadScore()).isEqualByComparingTo(new BigDecimal("0.850"));
        assertThat(record.getLostLeadReason()).isEqualTo("Usuario interesado en plan anual sin datos capturados");
        assertThat(record.getStatus()).isEqualTo(AuditStatus.PROCESSING);
        verify(auditResultRepository).saveAndFlush(record);
    }

    // R18 — Idempotencia: sobreescritura limpia en reintentos
    @Test
    @DisplayName("Debe sobreescribir valores previos de lead en caso de reintento")
    void shouldOverwritePreviousLeadResultsOnRetry() {
        String chatId = "chat-retry";
        AuditResult record = buildAuditRecord(chatId);
        record.setLostLeadDetected(true);
        record.setLeadScore(new BigDecimal("0.900"));
        record.setLostLeadReason("Razón previa");

        when(auditResultRepository.findByChatId(chatId)).thenReturn(Optional.of(record));

        AuditConversationDto conv = new AuditConversationDto(chatId, "asst-1", Instant.now(), null, List.of(
                new AuditMessageDto("msg-1", "USER", "Solo quiero soporte técnico", Instant.now())
        ));

        LeadAnalysisResponseDto aiResponse = new LeadAnalysisResponseDto(
                false,
                BigDecimal.ZERO,
                false,
                "Sin intención comercial"
        );
        when(aiClient.evaluateCommercialOpportunity(any())).thenReturn(aiResponse);

        service.analyzeAndPersistLeads(chatId, conv);

        assertThat(record.getLostLeadDetected()).isFalse();
        assertThat(record.getLeadScore()).isEqualByComparingTo(new BigDecimal("0.000"));
        assertThat(record.getLostLeadReason()).isNull();
        verify(auditResultRepository).saveAndFlush(record);
    }

    // R21 — Manejo de fallo en llamada de IA marca FAILED con prefijo [AI_ERROR]
    @Test
    @DisplayName("Debe marcar AuditResult en FAILED con prefijo [AI_ERROR] cuando la IA falla")
    void shouldMarkAuditFailedOnAiError() {
        String chatId = "chat-ai-error";
        AuditResult record = buildAuditRecord(chatId);
        when(auditResultRepository.findByChatId(chatId)).thenReturn(Optional.of(record));

        AuditConversationDto conv = new AuditConversationDto(chatId, "asst-1", Instant.now(), null, List.of(
                new AuditMessageDto("msg-1", "USER", "Quiero comprar", Instant.now())
        ));

        when(aiClient.evaluateCommercialOpportunity(any()))
                .thenThrow(new RuntimeException("Connection refused to Ollama /v1"));

        service.analyzeAndPersistLeads(chatId, conv);

        assertThat(record.getStatus()).isEqualTo(AuditStatus.FAILED);
        assertThat(record.getErrorMessage()).contains("[AI_ERROR] Connection refused to Ollama /v1");
        verify(auditResultRepository).saveAndFlush(record);
    }

    // Omitir si la auditoría ya está en estado FAILED
    @Test
    @DisplayName("Debe omitir la ejecución si la auditoría se encuentra en estado FAILED")
    void shouldSkipIfAuditResultAlreadyFailed() {
        String chatId = "chat-already-failed";
        AuditResult record = buildAuditRecord(chatId);
        record.setStatus(AuditStatus.FAILED);
        when(auditResultRepository.findByChatId(chatId)).thenReturn(Optional.of(record));

        AuditConversationDto conv = new AuditConversationDto(chatId, "asst-1", Instant.now(), null, List.of(
                new AuditMessageDto("msg-1", "USER", "Hola", Instant.now())
        ));

        service.analyzeAndPersistLeads(chatId, conv);

        verify(aiClient, never()).evaluateCommercialOpportunity(any());
        verify(auditResultRepository, never()).saveAndFlush(any());
    }

    // Omitir si la conversación no existe en audit_result
    @Test
    @DisplayName("Debe omitir la ejecución si el chatId no existe en la base de datos")
    void shouldSkipIfAuditResultNotFound() {
        String chatId = "chat-not-found";
        when(auditResultRepository.findByChatId(chatId)).thenReturn(Optional.empty());

        AuditConversationDto conv = new AuditConversationDto(chatId, "asst-1", Instant.now(), null, List.of(
                new AuditMessageDto("msg-1", "USER", "Hola", Instant.now())
        ));

        service.analyzeAndPersistLeads(chatId, conv);

        verify(aiClient, never()).evaluateCommercialOpportunity(any());
    }
}
