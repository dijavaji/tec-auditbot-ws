package ec.com.technoloqie.auditbot.api.service.impl;

import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;
import ec.com.technoloqie.auditbot.api.service.IKnowledgeGapService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditPipelineOrchestratorImplTest {

    @Mock
    private IKnowledgeGapService knowledgeGapService;

    @InjectMocks
    private AuditPipelineOrchestratorImpl orchestrator;

    @Test
    void shouldDelegateTriggerToKnowledgeGapService() {
        AuditConversationDto conversation = new AuditConversationDto(
                "chat-orch", "asst-1", Instant.now().minusSeconds(10), Instant.now(), List.of()
        );

        orchestrator.trigger("chat-orch", conversation);

        verify(knowledgeGapService).analyzeAndPersistGaps("chat-orch", conversation);
    }
}
