package ec.com.technoloqie.auditbot.api.service.impl;

import ec.com.technoloqie.auditbot.api.analyzer.RecommendationAnalyzer;
import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;
import ec.com.technoloqie.auditbot.api.dto.AuditFindingsContextDto;
import ec.com.technoloqie.auditbot.api.dto.AuditMessageDto;
import ec.com.technoloqie.auditbot.api.dto.RecommendationAnalysisResponseDto;
import ec.com.technoloqie.auditbot.api.dto.RecommendationCandidateDto;
import ec.com.technoloqie.auditbot.api.integration.openai.IRecommendationAiClient;
import ec.com.technoloqie.auditbot.api.model.AuditGap;
import ec.com.technoloqie.auditbot.api.model.AuditRecommendation;
import ec.com.technoloqie.auditbot.api.model.AuditResult;
import ec.com.technoloqie.auditbot.api.model.AuditStatus;
import ec.com.technoloqie.auditbot.api.model.RecommendationType;
import ec.com.technoloqie.auditbot.api.repository.IAuditRecommendationRepository;
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
 * Pruebas unitarias para {@link RecommendationServiceImpl} (US-07).
 * Cubre R3, R4, R5, R17, R18, R19, R20, R21.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceImplTest {

    @Mock
    private IAuditResultRepository auditResultRepository;

    @Mock
    private IAuditRecommendationRepository auditRecommendationRepository;

    @Mock
    private IRecommendationAiClient aiClient;

    private RecommendationAnalyzer analyzer;
    private RecommendationServiceImpl service;

    @BeforeEach
    void setUp() {
        analyzer = new RecommendationAnalyzer();
        service = new RecommendationServiceImpl(
                auditResultRepository,
                auditRecommendationRepository,
                aiClient,
                analyzer
        );
    }

    private AuditResult buildAuditRecord(String chatId) {
        AuditResult record = new AuditResult();
        record.setAuditId("audit-uuid-1");
        record.setChatId(chatId);
        record.setAssistantId("assistant-rec");
        record.setStatus(AuditStatus.PROCESSING);
        record.setEventId("event-uuid-1");
        record.setAttemptCount(1);
        Instant now = Instant.now();
        record.setCreatedDate(now);
        record.setUpdatedDate(now);
        return record;
    }

    // R3 — Consolidación de hallazgos previos y contexto conversacional
    @Test
    @DisplayName("Debe consolidar hallazgos previos correctamente en AuditFindingsContextDto")
    void shouldConsolidatePreviousFindingsAndConversation() {
        AuditResult record = buildAuditRecord("chat-findings");
        record.setGapsCount(2);

        AuditGap gap1 = new AuditGap();
        gap1.setQuestionText("¿Tienen garantía?");
        AuditGap gap2 = new AuditGap();
        gap2.setQuestionText("¿Hacen envíos internacionales?");
        record.setGaps(List.of(gap1, gap2));

        record.setFrustrationDetected(true);
        record.setLostLeadDetected(true);
        record.setLeadScore(new BigDecimal("0.750"));
        record.setLostLeadReason("No se brindó información de envío a tiempo.");

        AuditFindingsContextDto context = service.buildFindingsContext(record);

        assertThat(context.gapsCount()).isEqualTo(2);
        assertThat(context.gapQuestions()).containsExactly("¿Tienen garantía?", "¿Hacen envíos internacionales?");
        assertThat(context.frustrationDetected()).isTrue();
        assertThat(context.lostLeadDetected()).isTrue();
        assertThat(context.leadScore()).isEqualByComparingTo(new BigDecimal("0.750"));
        assertThat(context.lostLeadReason()).isEqualTo("No se brindó información de envío a tiempo.");
    }

    // R4 — Exclusión de mensajes de sistema
    @Test
    @DisplayName("Debe filtrar y excluir mensajes de rol SYSTEM")
    void shouldFilterOutSystemMessages() {
        AuditConversationDto conv = new AuditConversationDto("chat-sys", "asst-1", Instant.now(), null, List.of(
                new AuditMessageDto("msg-0", "SYSTEM", "Prompt inicial", Instant.now()),
                new AuditMessageDto("msg-1", "USER", "Hola", Instant.now())
        ));

        List<AuditMessageDto> evaluable = service.extractEvaluableMessages(conv);
        assertThat(evaluable).hasSize(1);
        assertThat(evaluable.get(0).messageId()).isEqualTo("msg-1");
    }

    // R5 — Conversación limpia sin hallazgos adversos
    @Test
    @DisplayName("Debe finalizar directamente en COMPLETED sin llamar a IA cuando no hay hallazgos adversos")
    void shouldCompleteWithoutAiCallWhenNoFindingsExist() {
        String chatId = "chat-clean";
        AuditResult record = buildAuditRecord(chatId);
        record.setGapsCount(0);
        record.setFrustrationDetected(false);
        record.setLostLeadDetected(false);

        when(auditResultRepository.findByChatId(chatId)).thenReturn(Optional.of(record));

        AuditConversationDto conv = new AuditConversationDto(chatId, "asst-1", Instant.now(), null, List.of(
                new AuditMessageDto("msg-1", "USER", "Hola", Instant.now()),
                new AuditMessageDto("msg-2", "ASSISTANT", "Hola, todo bien", Instant.now())
        ));

        service.generateAndPersistRecommendations(chatId, conv);

        verify(aiClient, never()).analyzeRecommendations(any(), any());
        verify(auditRecommendationRepository, never()).saveAll(any());
        assertThat(record.getStatus()).isEqualTo(AuditStatus.COMPLETED);
        assertThat(record.getAuditedDate()).isNotNull();
        verify(auditResultRepository).saveAndFlush(record);
    }

    // R17, R18 — Persistencia de recomendaciones y promoción a COMPLETED con auditedDate
    @Test
    @DisplayName("Debe persistir recomendaciones y culminar agregada en COMPLETED con auditedDate")
    void shouldPersistAllValidRecommendations() {
        String chatId = "chat-has-gaps";
        AuditResult record = buildAuditRecord(chatId);
        record.setGapsCount(1);
        record.setFrustrationDetected(false);
        record.setLostLeadDetected(false);

        when(auditResultRepository.findByChatId(chatId)).thenReturn(Optional.of(record));

        AuditConversationDto conv = new AuditConversationDto(chatId, "asst-1", Instant.now(), null, List.of(
                new AuditMessageDto("msg-1", "USER", "¿Cómo devuelvo?", Instant.now()),
                new AuditMessageDto("msg-2", "ASSISTANT", "No sé", Instant.now())
        ));

        RecommendationAnalysisResponseDto aiResponse = new RecommendationAnalysisResponseDto(List.of(
                new RecommendationCandidateDto("NEW_FAQ", "Crear FAQ de devoluciones", "msg-1")
        ));
        when(aiClient.analyzeRecommendations(any(), any())).thenReturn(aiResponse);

        service.generateAndPersistRecommendations(chatId, conv);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AuditRecommendation>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditRecommendationRepository).saveAll(captor.capture());

        List<AuditRecommendation> savedList = captor.getValue();
        assertThat(savedList).hasSize(1);
        assertThat(savedList.get(0).getType()).isEqualTo(RecommendationType.NEW_FAQ);
        assertThat(savedList.get(0).getSuggestedText()).isEqualTo("Crear FAQ de devoluciones");
        assertThat(savedList.get(0).getOriginMessageId()).isEqualTo("msg-1");

        assertThat(record.getStatus()).isEqualTo(AuditStatus.COMPLETED);
        assertThat(record.getAuditedDate()).isNotNull();
        verify(auditResultRepository).saveAndFlush(record);
    }

    // R19 — Idempotencia: eliminación previa en caso de reprocesamiento
    @Test
    @DisplayName("Debe eliminar recomendaciones previas asociadas al auditId antes de persistir")
    void shouldDeletePreviousRecommendationsOnRetry() {
        String chatId = "chat-retry";
        AuditResult record = buildAuditRecord(chatId);
        record.setFrustrationDetected(true);

        when(auditResultRepository.findByChatId(chatId)).thenReturn(Optional.of(record));

        AuditConversationDto conv = new AuditConversationDto(chatId, "asst-1", Instant.now(), null, List.of(
                new AuditMessageDto("msg-1", "USER", "Estoy molesto", Instant.now())
        ));

        when(aiClient.analyzeRecommendations(any(), any()))
                .thenReturn(new RecommendationAnalysisResponseDto(List.of(
                        new RecommendationCandidateDto("ALTERNATIVE_FLOW", "Derivar a agente", "msg-1")
                )));

        service.generateAndPersistRecommendations(chatId, conv);

        verify(auditRecommendationRepository).deleteByAuditResult_AuditId(eq("audit-uuid-1"));
        verify(auditRecommendationRepository).flush();
    }

    // R20 — Manejo controlado de fallos en IA marca status FAILED
    @Test
    @DisplayName("Debe marcar status FAILED con prefijo [AI_ERROR] si la llamada a IA falla")
    void shouldMarkAuditFailedOnAiError() {
        String chatId = "chat-ai-error";
        AuditResult record = buildAuditRecord(chatId);
        record.setLostLeadDetected(true);

        when(auditResultRepository.findByChatId(chatId)).thenReturn(Optional.of(record));

        AuditConversationDto conv = new AuditConversationDto(chatId, "asst-1", Instant.now(), null, List.of(
                new AuditMessageDto("msg-1", "USER", "Quiero cotizar", Instant.now())
        ));

        when(aiClient.analyzeRecommendations(any(), any()))
                .thenThrow(new RuntimeException("Timeout de conexión al servicio de IA"));

        service.generateAndPersistRecommendations(chatId, conv);

        assertThat(record.getStatus()).isEqualTo(AuditStatus.FAILED);
        assertThat(record.getErrorMessage()).contains("[AI_ERROR] Timeout de conexión al servicio de IA");
        verify(auditResultRepository).saveAndFlush(record);
        verify(auditRecommendationRepository, never()).saveAll(any());
    }

    // Omisión si el estado ya es FAILED
    @Test
    @DisplayName("Debe omitir la ejecución si la auditoría ya está en estado FAILED")
    void shouldSkipIfAuditResultAlreadyFailed() {
        String chatId = "chat-failed-prev";
        AuditResult record = buildAuditRecord(chatId);
        record.setStatus(AuditStatus.FAILED);

        when(auditResultRepository.findByChatId(chatId)).thenReturn(Optional.of(record));

        AuditConversationDto conv = new AuditConversationDto(chatId, "asst-1", Instant.now(), null, List.of(
                new AuditMessageDto("msg-1", "USER", "Hola", Instant.now())
        ));

        service.generateAndPersistRecommendations(chatId, conv);

        verify(auditRecommendationRepository, never()).deleteByAuditResult_AuditId(any());
        verify(aiClient, never()).analyzeRecommendations(any(), any());
    }
}
