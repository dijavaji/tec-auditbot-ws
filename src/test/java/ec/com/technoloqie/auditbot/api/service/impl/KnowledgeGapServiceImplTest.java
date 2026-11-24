package ec.com.technoloqie.auditbot.api.service.impl;

import ec.com.technoloqie.auditbot.api.analyzer.KnowledgeGapAnalyzer;
import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;
import ec.com.technoloqie.auditbot.api.dto.AuditMessageDto;
import ec.com.technoloqie.auditbot.api.dto.ConversationTurnDto;
import ec.com.technoloqie.auditbot.api.dto.GapAnalysisResponseDto;
import ec.com.technoloqie.auditbot.api.dto.KnowledgeGapCandidateDto;
import ec.com.technoloqie.auditbot.api.dto.KnowledgeGapItemDto;
import ec.com.technoloqie.auditbot.api.integration.openai.IKnowledgeGapAiClient;
import ec.com.technoloqie.auditbot.api.model.AuditGap;
import ec.com.technoloqie.auditbot.api.model.AuditResult;
import ec.com.technoloqie.auditbot.api.model.AuditStatus;
import ec.com.technoloqie.auditbot.api.repository.IAuditGapRepository;
import ec.com.technoloqie.auditbot.api.repository.IAuditResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para {@link KnowledgeGapServiceImpl}.
 * Cubre R1, R2, R3, R4, R22, R23, R24, R25, R26, R27, R29, R30, R31, R32, R33.
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeGapServiceImplTest {

    @Mock
    private IKnowledgeGapAiClient aiClient;

    @Mock
    private KnowledgeGapAnalyzer analyzer;

    @Mock
    private IAuditResultRepository auditResultRepository;

    @Mock
    private IAuditGapRepository auditGapRepository;

    private KnowledgeGapServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeGapServiceImpl(
                aiClient,
                analyzer,
                auditResultRepository,
                auditGapRepository,
                new BigDecimal("0.600"),
                0.70
        );
    }

    private AuditResult buildAuditRecord(String chatId) {
        AuditResult r = new AuditResult();
        r.setAuditId(UUID.randomUUID().toString());
        r.setChatId(chatId);
        r.setAssistantId("asst-1");
        r.setStatus(AuditStatus.PROCESSING);
        r.setAttemptCount(1);
        r.setCreatedDate(Instant.now());
        r.setUpdatedDate(Instant.now());
        return r;
    }

    // R1, R2, R3, R4 — Extracción y emparejamiento de turnos de diálogo
    @Test
    void shouldExtractTurnsCorrectly() {
        Instant now = Instant.now();
        AuditMessageDto sysMsg = new AuditMessageDto("m0", "SYSTEM", "Instrucciones", now.minusSeconds(30));
        AuditMessageDto user1 = new AuditMessageDto("m1", "USER", "¿Tienen envíos?", now.minusSeconds(20));
        AuditMessageDto asst1 = new AuditMessageDto("m2", "ASSISTANT", "Sí, a nivel nacional", now.minusSeconds(10));
        AuditMessageDto user2 = new AuditMessageDto("m3", "USER", "¿Cuál es el costo?", now);

        AuditConversationDto conversation = new AuditConversationDto(
                "chat-1", "asst-1", now.minusSeconds(60), now, List.of(sysMsg, user1, asst1, user2)
        );

        List<ConversationTurnDto> turns = service.extractTurns(conversation);

        // SYSTEM descartado, dos turnos generados
        assertThat(turns).hasSize(2);

        // Turno 1 emparejado
        assertThat(turns.get(0).userMessageId()).isEqualTo("m1");
        assertThat(turns.get(0).userQuestion()).isEqualTo("¿Tienen envíos?");
        assertThat(turns.get(0).assistantMessageId()).isEqualTo("m2");
        assertThat(turns.get(0).assistantResponse()).isEqualTo("Sí, a nivel nacional");

        // Turno 2 sin respuesta final
        assertThat(turns.get(1).userMessageId()).isEqualTo("m3");
        assertThat(turns.get(1).userQuestion()).isEqualTo("¿Cuál es el costo?");
        assertThat(turns.get(1).assistantMessageId()).isNull();
        assertThat(turns.get(1).assistantResponse()).isNull();
    }

    // R22, R23, R24, R25 — Persistencia exitosa de gaps y actualización en PROCESSING
    @Test
    void shouldPersistConsolidatedGapsAndUpdateAuditResultAsProcessing() {
        AuditResult record = buildAuditRecord("chat-success");
        when(auditResultRepository.findByChatId("chat-success")).thenReturn(Optional.of(record));

        Instant now = Instant.now();
        AuditConversationDto conversation = new AuditConversationDto(
                "chat-success", "asst-1", now.minusSeconds(10), now, List.of(
                new AuditMessageDto("m1", "USER", "¿Duda 1?", now.minusSeconds(5)),
                new AuditMessageDto("m2", "ASSISTANT", "No sé", now)
        )
        );

        GapAnalysisResponseDto aiResponse = new GapAnalysisResponseDto(List.of(
                new KnowledgeGapCandidateDto("m1", "¿Duda 1?", true, new BigDecimal("0.900"), "Desconocimiento")
        ));
        when(aiClient.classifyTurns(any())).thenReturn(aiResponse);

        KnowledgeGapItemDto consolidated = new KnowledgeGapItemDto("m1", "¿Duda 1?", new BigDecimal("0.900"), 1);
        when(analyzer.groupAndConsolidate(any(), any(), anyDouble())).thenReturn(List.of(consolidated));

        service.analyzeAndPersistGaps("chat-success", conversation);

        // Verificaciones
        verify(auditGapRepository).deleteByAuditResult_AuditId(record.getAuditId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AuditGap>> gapCaptor = ArgumentCaptor.forClass(List.class);
        verify(auditGapRepository).saveAll(gapCaptor.capture());

        List<AuditGap> savedGaps = gapCaptor.getValue();
        assertThat(savedGaps).hasSize(1);
        assertThat(savedGaps.get(0).getMessageId()).isEqualTo("m1");
        assertThat(savedGaps.get(0).getQuestionText()).isEqualTo("¿Duda 1?");
        assertThat(savedGaps.get(0).getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.900"));

        // AuditResult actualizado en PROCESSING
        assertThat(record.getGapsCount()).isEqualTo(1);
        assertThat(record.getStatus()).isEqualTo(AuditStatus.PROCESSING);
        verify(auditResultRepository).saveAndFlush(record);
    }

    // R26 — Caso con 0 gaps detectados
    @Test
    void shouldHandleZeroGapsDetectedCleanly() {
        AuditResult record = buildAuditRecord("chat-zero");
        when(auditResultRepository.findByChatId("chat-zero")).thenReturn(Optional.of(record));

        AuditConversationDto conversation = new AuditConversationDto(
                "chat-zero", "asst-1", Instant.now().minusSeconds(10), Instant.now(), List.of(
                new AuditMessageDto("m1", "USER", "Hola", Instant.now().minusSeconds(2)),
                new AuditMessageDto("m2", "ASSISTANT", "Hola, ¿en qué te ayudo?", Instant.now())
        )
        );

        when(aiClient.classifyTurns(any())).thenReturn(new GapAnalysisResponseDto(List.of()));
        when(analyzer.groupAndConsolidate(any(), any(), anyDouble())).thenReturn(List.of());

        service.analyzeAndPersistGaps("chat-zero", conversation);

        verify(auditGapRepository, never()).saveAll(any());
        assertThat(record.getGapsCount()).isEqualTo(0);
        assertThat(record.getStatus()).isEqualTo(AuditStatus.PROCESSING);
        verify(auditResultRepository).saveAndFlush(record);
    }

    // R29, R30 — Manejo controlado de fallo del cliente de IA sin lanzar excepción
    @Test
    void shouldMarkAuditAsFailedOnAiClientError() {
        AuditResult record = buildAuditRecord("chat-fail");
        when(auditResultRepository.findByChatId("chat-fail")).thenReturn(Optional.of(record));

        AuditConversationDto conversation = new AuditConversationDto(
                "chat-fail", "asst-1", Instant.now().minusSeconds(10), Instant.now(), List.of(
                new AuditMessageDto("m1", "USER", "¿Duda?", Instant.now())
        )
        );

        when(aiClient.classifyTurns(any())).thenThrow(new RuntimeException("Conexión rechazada al endpoint de Ollama"));

        // No debe lanzar excepción
        service.analyzeAndPersistGaps("chat-fail", conversation);

        verify(auditGapRepository, never()).saveAll(any());
        assertThat(record.getStatus()).isEqualTo(AuditStatus.FAILED);
        assertThat(record.getErrorMessage()).startsWith("[AI_ERROR] Conexión rechazada");
        verify(auditResultRepository).saveAndFlush(record);
    }

    // Caso de chatId no encontrado en BD
    @Test
    void shouldSkipProcessingWhenAuditNotFound() {
        when(auditResultRepository.findByChatId("chat-unknown")).thenReturn(Optional.empty());

        service.analyzeAndPersistGaps("chat-unknown", null);

        verify(aiClient, never()).classifyTurns(any());
        verify(auditGapRepository, never()).saveAll(any());
    }
}
