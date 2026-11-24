package ec.com.technoloqie.auditbot.api.service.impl;

import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;
import ec.com.technoloqie.auditbot.api.model.AuditResult;
import ec.com.technoloqie.auditbot.api.model.AuditStatus;
import ec.com.technoloqie.auditbot.api.repository.IAuditResultRepository;
import ec.com.technoloqie.auditbot.api.service.IKnowledgeGapService;
import ec.com.technoloqie.auditbot.api.service.ILeadDetectionService;
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
 * Cubre R19, R20.
 */
@ExtendWith(MockitoExtension.class)
class AuditPipelineOrchestratorImplTest {

    @Mock
    private IKnowledgeGapService knowledgeGapService;

    @Mock
    private ISentimentAnalysisService sentimentAnalysisService;

    @Mock
    private ILeadDetectionService leadDetectionService;

    @Mock
    private IAuditResultRepository auditResultRepository;

    private AuditPipelineOrchestratorImpl orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new AuditPipelineOrchestratorImpl(
                knowledgeGapService,
                sentimentAnalysisService,
                leadDetectionService,
                auditResultRepository
        );
    }

    // Invocación secuencial completa: gaps -> sentiment -> leads cuando todas finalizan con éxito (R19)
    @Test
    void shouldTriggerAllStagesSequentiallyWhenSuccessful() {
        String chatId = "chat-orch-full";
        AuditConversationDto conv = new AuditConversationDto(chatId, "asst-1", Instant.now(), null, List.of());

        AuditResult successfulResult = new AuditResult();
        successfulResult.setChatId(chatId);
        successfulResult.setStatus(AuditStatus.PROCESSING);

        when(auditResultRepository.findByChatId(chatId)).thenReturn(Optional.of(successfulResult));

        orchestrator.trigger(chatId, conv);

        InOrder inOrder = Mockito.inOrder(knowledgeGapService, sentimentAnalysisService, leadDetectionService);
        inOrder.verify(knowledgeGapService).analyzeAndPersistGaps(eq(chatId), eq(conv));
        inOrder.verify(sentimentAnalysisService).analyzeAndPersistSentiment(eq(chatId), eq(conv));
        inOrder.verify(leadDetectionService).analyzeAndPersistLeads(eq(chatId), eq(conv));
    }

    // Detención de pipeline si la etapa de gaps falló (marcando AuditResult en FAILED) (R20)
    @Test
    void shouldNotTriggerSentimentOrLeadsIfGapsFailed() {
        String chatId = "chat-orch-failed-gaps";
        AuditConversationDto conv = new AuditConversationDto(chatId, "asst-1", Instant.now(), null, List.of());

        AuditResult failedResult = new AuditResult();
        failedResult.setChatId(chatId);
        failedResult.setStatus(AuditStatus.FAILED);

        when(auditResultRepository.findByChatId(chatId)).thenReturn(Optional.of(failedResult));

        orchestrator.trigger(chatId, conv);

        verify(knowledgeGapService).analyzeAndPersistGaps(eq(chatId), eq(conv));
        verify(sentimentAnalysisService, never()).analyzeAndPersistSentiment(any(), any());
        verify(leadDetectionService, never()).analyzeAndPersistLeads(any(), any());
    }

    // Detención de pipeline si la etapa de sentimiento falló (R20)
    @Test
    void shouldNotTriggerLeadsIfSentimentFailed() {
        String chatId = "chat-orch-failed-sentiment";
        AuditConversationDto conv = new AuditConversationDto(chatId, "asst-1", Instant.now(), null, List.of());

        AuditResult intermediateSuccess = new AuditResult();
        intermediateSuccess.setChatId(chatId);
        intermediateSuccess.setStatus(AuditStatus.PROCESSING);

        AuditResult failedAfterSentiment = new AuditResult();
        failedAfterSentiment.setChatId(chatId);
        failedAfterSentiment.setStatus(AuditStatus.FAILED);

        // Primera llamada tras gaps retorna PROCESSING; segunda llamada tras sentiment retorna FAILED
        when(auditResultRepository.findByChatId(chatId))
                .thenReturn(Optional.of(intermediateSuccess))
                .thenReturn(Optional.of(failedAfterSentiment));

        orchestrator.trigger(chatId, conv);

        verify(knowledgeGapService).analyzeAndPersistGaps(eq(chatId), eq(conv));
        verify(sentimentAnalysisService).analyzeAndPersistSentiment(eq(chatId), eq(conv));
        verify(leadDetectionService, never()).analyzeAndPersistLeads(any(), any());
    }
}
