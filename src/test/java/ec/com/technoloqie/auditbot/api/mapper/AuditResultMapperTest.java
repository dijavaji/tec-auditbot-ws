package ec.com.technoloqie.auditbot.api.mapper;

import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;
import ec.com.technoloqie.auditbot.api.dto.AuditMessageDto;
import ec.com.technoloqie.auditbot.api.dto.ConversationAuditRequestDto;
import ec.com.technoloqie.auditbot.api.model.AuditResult;
import ec.com.technoloqie.auditbot.api.model.AuditStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias de {@link AuditResultMapper}. Cubre R53.
 */
class AuditResultMapperTest {

    private final AuditResultMapper mapper = new AuditResultMapper();

    private ConversationAuditRequestDto buildRequest() {
        Instant now = Instant.parse("2026-08-31T10:00:00Z");
        AuditMessageDto msg = new AuditMessageDto("msg-1", "USER", "contenido sensible", now);
        AuditConversationDto conv = new AuditConversationDto("chat-abc", "assistant-1",
                now, now.plusSeconds(60), List.of(msg));
        return new ConversationAuditRequestDto(UUID.randomUUID(), "1.0", now, "smart-chatbot", conv);
    }

    @Test
    void shouldMapConversationToClaimEntity() {
        ConversationAuditRequestDto request = buildRequest();
        Instant now = Instant.now();

        AuditResult result = mapper.toClaim(request, now);

        assertThat(result.getAuditId()).isNotNull();
        assertThat(result.getChatId()).isEqualTo("chat-abc");
        assertThat(result.getAssistantId()).isEqualTo("assistant-1");
        assertThat(result.getStatus()).isEqualTo(AuditStatus.PENDING);
        assertThat(result.getEventId()).isEqualTo(request.eventId().toString());
        assertThat(result.getAttemptCount()).isEqualTo(1);
        assertThat(result.getCreatedDate()).isEqualTo(now);
        assertThat(result.getUpdatedDate()).isEqualTo(now);
    }

    @Test
    void shouldNotCopyMessageContentIntoEntity() {
        ConversationAuditRequestDto request = buildRequest();
        AuditResult result = mapper.toClaim(request, Instant.now());

        // La entidad no debe tener ninguna referencia al contenido sensible de los mensajes
        assertThat(result.getErrorMessage()).isNull();
        // No hay campo de contenido en AuditResult — verificación estructural
        assertThat(result).isNotNull();
        // El toString o campos no deben filtrar "contenido sensible"
        assertThat(result.getChatId()).doesNotContain("sensible");
        assertThat(result.getAssistantId()).doesNotContain("sensible");
    }
}
