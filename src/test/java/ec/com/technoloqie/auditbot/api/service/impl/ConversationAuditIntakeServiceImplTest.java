package ec.com.technoloqie.auditbot.api.service.impl;

import ec.com.technoloqie.auditbot.api.commons.exception.AuditProcessingException;
import ec.com.technoloqie.auditbot.api.commons.exception.InvalidAuditMessageException;
import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;
import ec.com.technoloqie.auditbot.api.dto.AuditIntakeOutcome;
import ec.com.technoloqie.auditbot.api.dto.AuditIntakeResultDto;
import ec.com.technoloqie.auditbot.api.dto.AuditMessageDto;
import ec.com.technoloqie.auditbot.api.dto.ConversationAuditRequestDto;
import ec.com.technoloqie.auditbot.api.mapper.AuditResultMapper;
import ec.com.technoloqie.auditbot.api.model.AuditResult;
import ec.com.technoloqie.auditbot.api.model.AuditStatus;
import ec.com.technoloqie.auditbot.api.repository.IAuditResultRepository;
import ec.com.technoloqie.auditbot.api.service.ConversationAuditRequestValidator;
import ec.com.technoloqie.auditbot.api.service.IAuditPipelineTrigger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de {@link ConversationAuditIntakeServiceImpl}. Cubre R13, R28-R42, R49, R50, R58, R69.
 */
@ExtendWith(MockitoExtension.class)
class ConversationAuditIntakeServiceImplTest {

    @Mock private IAuditResultRepository auditResultRepository;
    @Mock private IAuditPipelineTrigger auditPipelineTrigger;

    private ConversationAuditIntakeServiceImpl service;

    private static final Instant FIXED_NOW = Instant.parse("2026-11-16T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new ConversationAuditIntakeServiceImpl(
                auditResultRepository,
                new ConversationAuditRequestValidator(),
                new AuditResultMapper(),
                auditPipelineTrigger,
                FIXED_CLOCK);
    }

    private ConversationAuditRequestDto validRequest(String chatId) {
        Instant now = Instant.parse("2026-08-31T10:00:00Z");
        AuditMessageDto msg = new AuditMessageDto("msg-1", "USER", "hola", now);
        AuditConversationDto conv = new AuditConversationDto(chatId, "assistant-1",
                now, now.plusSeconds(30), List.of(msg));
        return new ConversationAuditRequestDto(UUID.randomUUID(), "1.0", now, "smart-chatbot", conv);
    }

    private AuditResult existingRecord(String chatId, AuditStatus status) {
        AuditResult r = new AuditResult();
        r.setAuditId(UUID.randomUUID().toString());
        r.setChatId(chatId);
        r.setAssistantId("assistant-1");
        r.setStatus(status);
        r.setEventId(UUID.randomUUID().toString());
        r.setAttemptCount(1);
        r.setCreatedDate(FIXED_NOW.minusSeconds(60));
        r.setUpdatedDate(FIXED_NOW.minusSeconds(60));
        return r;
    }

    // R36, R37 — admisión nueva: PENDING luego PROCESSING
    @Test
    void shouldAdmitNewChatIdWithPendingThenProcessing() {
        ConversationAuditRequestDto req = validRequest("chat-new");
        when(auditResultRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        AuditIntakeResultDto result = service.admit(req, false);

        assertThat(result.outcome()).isEqualTo(AuditIntakeOutcome.ADMITTED);
        assertThat(result.chatId()).isEqualTo("chat-new");
        assertThat(result.auditId()).isNotNull();
        // saveAndFlush llamado dos veces: claim (PENDING) y promoción (PROCESSING)
        verify(auditResultRepository, times(2)).saveAndFlush(any());
        verify(auditPipelineTrigger).trigger(eq("chat-new"), any());
    }

    // R30 — duplicado sin reintento: descarte silencioso
    @Test
    void shouldSkipAlreadyProcessedChatId() {
        ConversationAuditRequestDto req = validRequest("chat-dup");
        when(auditResultRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uk_chat_id"));
        AuditResult existing = existingRecord("chat-dup", AuditStatus.COMPLETED);
        when(auditResultRepository.findByChatId("chat-dup")).thenReturn(Optional.of(existing));

        AuditIntakeResultDto result = service.admit(req, false);

        assertThat(result.outcome()).isEqualTo(AuditIntakeOutcome.DUPLICATE_SKIPPED);
        verify(auditPipelineTrigger, never()).trigger(any(), any());
    }

    // R28 — DataIntegrityViolationException tratada como duplicado
    @Test
    void shouldTreatUniqueConstraintViolationAsDuplicate() {
        ConversationAuditRequestDto req = validRequest("chat-dup2");
        when(auditResultRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uk"));
        AuditResult existing = existingRecord("chat-dup2", AuditStatus.PENDING);
        when(auditResultRepository.findByChatId("chat-dup2")).thenReturn(Optional.of(existing));

        AuditIntakeResultDto result = service.admit(req, false);

        assertThat(result.outcome()).isEqualTo(AuditIntakeOutcome.DUPLICATE_SKIPPED);
    }

    // R29 — sin consulta previa antes del insert
    @Test
    void shouldClaimChatIdWithoutPreliminaryExistenceQuery() {
        ConversationAuditRequestDto req = validRequest("chat-atomic");
        when(auditResultRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        service.admit(req, false);

        // findByChatId no debe haberse llamado en el camino feliz
        verify(auditResultRepository, never()).findByChatId(any());
    }

    // R33, R34 — reintento explícito sobre FAILED
    @Test
    void shouldReprocessFailedChatIdWhenRetryRequested() {
        ConversationAuditRequestDto req = validRequest("chat-failed");
        AuditResult existing = existingRecord("chat-failed", AuditStatus.FAILED);
        existing.setErrorMessage("error anterior");
        when(auditResultRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uk"))
                .thenReturn(existing);
        when(auditResultRepository.findByChatId("chat-failed")).thenReturn(Optional.of(existing));

        AuditIntakeResultDto result = service.admit(req, true);

        assertThat(result.outcome()).isEqualTo(AuditIntakeOutcome.RETRY_ADMITTED);
        verify(auditPipelineTrigger).trigger(eq("chat-failed"), any());
    }

    // R34 — incrementa attempt_count en reintento
    @Test
    void shouldIncrementAttemptCountOnExplicitRetry() {
        ConversationAuditRequestDto req = validRequest("chat-retry");
        AuditResult existing = existingRecord("chat-retry", AuditStatus.FAILED);
        existing.setAttemptCount(2);
        when(auditResultRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uk"))
                .thenReturn(existing);
        when(auditResultRepository.findByChatId("chat-retry")).thenReturn(Optional.of(existing));

        service.admit(req, true);

        assertThat(existing.getAttemptCount()).isEqualTo(3);
        assertThat(existing.getErrorMessage()).isNull();
    }

    // R35 — reintento rechazado si está en PROCESSING
    @Test
    void shouldRejectRetryWhileChatIdIsProcessing() {
        ConversationAuditRequestDto req = validRequest("chat-proc");
        when(auditResultRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uk"));
        AuditResult existing = existingRecord("chat-proc", AuditStatus.PROCESSING);
        when(auditResultRepository.findByChatId("chat-proc")).thenReturn(Optional.of(existing));

        AuditIntakeResultDto result = service.admit(req, true);

        assertThat(result.outcome()).isEqualTo(AuditIntakeOutcome.DUPLICATE_SKIPPED);
        verify(auditPipelineTrigger, never()).trigger(any(), any());
    }

    // R13 — validación antes de cualquier escritura
    @Test
    void shouldValidateContractBeforeClaim() {
        ConversationAuditRequestDto invalid = new ConversationAuditRequestDto(null, null, null, null, null);

        assertThatThrownBy(() -> service.admit(invalid, false))
                .isInstanceOf(InvalidAuditMessageException.class);

        verify(auditResultRepository, never()).saveAndFlush(any());
        verify(auditResultRepository, never()).findByChatId(any());
    }

    // R42 — DataAccessException envuelto en AuditProcessingException
    @Test
    void shouldWrapDataAccessFailureAsTransientError() {
        ConversationAuditRequestDto req = validRequest("chat-dberr");
        DataAccessException dbEx = new QueryTimeoutException("timeout");
        when(auditResultRepository.saveAndFlush(any())).thenThrow(dbEx);

        assertThatThrownBy(() -> service.admit(req, false))
                .isInstanceOf(AuditProcessingException.class)
                .hasCause(dbEx);
    }

    // R49, R50 — markAsFailed persiste FAILED con mensaje legible
    @Test
    void shouldMarkAuditAsFailedWithReadableMessage() {
        AuditResult existing = existingRecord("chat-fail2", AuditStatus.PROCESSING);
        when(auditResultRepository.findByChatId("chat-fail2")).thenReturn(Optional.of(existing));
        when(auditResultRepository.saveAndFlush(existing)).thenReturn(existing);

        service.markAsFailed("chat-fail2", "Error de infraestructura");

        assertThat(existing.getStatus()).isEqualTo(AuditStatus.FAILED);
        assertThat(existing.getErrorMessage()).isEqualTo("Error de infraestructura");
    }

    // R69 — truncado a 500 caracteres
    @Test
    void shouldTruncateErrorMessageToFiveHundredCharacters() {
        AuditResult existing = existingRecord("chat-trunc", AuditStatus.PROCESSING);
        when(auditResultRepository.findByChatId("chat-trunc")).thenReturn(Optional.of(existing));
        when(auditResultRepository.saveAndFlush(existing)).thenReturn(existing);

        String longMessage = "x".repeat(600);
        service.markAsFailed("chat-trunc", longMessage);

        assertThat(existing.getErrorMessage()).hasSizeLessThanOrEqualTo(500);
        assertThat(existing.getErrorMessage()).endsWith("...[truncado]");
    }

    // R38 — nunca establece COMPLETED
    @Test
    void shouldNeverSetCompletedStatus() {
        ConversationAuditRequestDto req = validRequest("chat-no-complete");
        when(auditResultRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        service.admit(req, false);

        verify(auditResultRepository, times(2)).saveAndFlush(any(AuditResult.class));
        // Verificar que ninguna llamada persistió COMPLETED
        verify(auditResultRepository, times(2)).saveAndFlush(
                org.mockito.ArgumentMatchers.argThat(r ->
                        r instanceof AuditResult && ((AuditResult) r).getStatus() != AuditStatus.COMPLETED));
    }

    // R58 — sin invocación de analyzers (el trigger no-op no cuenta como analyzer)
    @Test
    void shouldNotInvokeAnalyzers() {
        ConversationAuditRequestDto req = validRequest("chat-noai");
        when(auditResultRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        service.admit(req, false);

        // Solo el trigger (no-op), no hay beans de analyzer que verificar
        verify(auditPipelineTrigger).trigger(eq("chat-noai"), any());
    }
}
