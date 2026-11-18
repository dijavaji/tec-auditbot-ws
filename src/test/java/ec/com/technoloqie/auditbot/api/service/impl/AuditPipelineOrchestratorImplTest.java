package ec.com.technoloqie.auditbot.api.service.impl;

import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;
import ec.com.technoloqie.auditbot.api.model.AuditResult;
import ec.com.technoloqie.auditbot.api.model.AuditStatus;
import ec.com.technoloqie.auditbot.api.repository.IAuditResultRepository;
import ec.com.technoloqie.auditbot.api.service.IKnowledgeGapService;
import ec.com.technoloqie.auditbot.api.service.ISentimentAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para {@link AuditPipelineOrchestratorImpl}.
 * Cubre R27, R28.
 */
@ExtendWith(MockitoExtension.class)
class AuditPipelineOrchestratorImplTest {

    @Mock
    private IKnowledgeGapService knowledgeGapService;

    @Mock
    private ISentimentAnalysisService sentimentAnalysisService;

    @Mock
    private IAuditResultRepository auditResultRepository;

    private AuditPipelineOrchestratorImpl orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new AuditPipelineOrchestratorImpl(
                knowledgeGapService,
                sentimentAnalysisService,
                auditResultRepository
        );
    }

    // R27 — Invocación secuencial: gaps y luego sentimiento cuando gaps finaliza con éxito
    @Test
    void shouldTriggerSentimentAnalysisAfterSuccessfulGaps() {
        String chatId = "chat-orch-1";
        AuditConversationDto conv = new AuditConversationDto(chatId, "asst-1", Instant.now(), null, List.of());

        AuditResult successfulResult = new AuditResult();
        successfulResult.setChatId(chatId);
        successfulResult.setStatus(AuditStatus.PROCESSING);

        when(auditResultRepository.findByChatId(chatId)).thenReturn(Optional.of(successfulResult));

        orchestrator.trigger(chatId, conv);

        InOrder inOrder = Mockito.inOrder(knowledgeGapService, auditResultRepository, sentimentAnalysisService);
        inOrder.verify(knowledgeGapService).analyzeAndPersistGaps(eq(chatId), eq(conv));
        inOrder.verify(auditResultRepository).findByChatId(eq(chatId));
        inOrder.verify(sentimentAnalysisService).analyzeAndPersistSentiment(eq(chatId), eq(conv));
    }

    // R28 — Detención de pipeline si la etapa de gaps falló (marcando AuditResult en FAILED)
    @Test
    void shouldNotTriggerSentimentAnalysisIfGapsFailed() {
        String chatId = "chat-orch-failed";
        AuditConversationDto conv = new AuditConversationDto(chatId, "asst-1", Instant.now(), null, List.of());

        AuditResult failedResult = new AuditResult();
        failedResult.setChatId(chatId);
        failedResult.setStatus(AuditStatus.FAILED);

        when(auditResultRepository.findByChatId(chatId)).thenReturn(Optional.of(failedResult));

        orchestrator.trigger(chatId, conv);

        verify(knowledgeGapService).analyzeAndPersistGaps(eq(chatId), eq(conv));
        verify(sentimentAnalysisService, never()).analyzeAndPersistSentiment(any(), any());
    }
}
