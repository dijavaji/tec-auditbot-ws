package ec.com.technoloqie.auditbot.api.service.impl;

import ec.com.technoloqie.auditbot.api.commons.exception.AuditProcessingException;
import ec.com.technoloqie.auditbot.api.commons.exception.InvalidAuditMessageException;
import ec.com.technoloqie.auditbot.api.commons.exception.InvalidAuditRequestException;
import ec.com.technoloqie.auditbot.api.dto.AuditIntakeOutcome;
import ec.com.technoloqie.auditbot.api.dto.AuditIntakeResultDto;
import ec.com.technoloqie.auditbot.api.dto.ConversationAuditRequestDto;
import ec.com.technoloqie.auditbot.api.mapper.AuditResultMapper;
import ec.com.technoloqie.auditbot.api.model.AuditResult;
import ec.com.technoloqie.auditbot.api.model.AuditStatus;
import ec.com.technoloqie.auditbot.api.repository.IAuditResultRepository;
import ec.com.technoloqie.auditbot.api.service.ConversationAuditRequestValidator;
import ec.com.technoloqie.auditbot.api.service.IAuditPipelineTrigger;
import ec.com.technoloqie.auditbot.api.service.IConversationAuditIntakeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Implementación del puerto de admisión de mensajes de auditoría.
 * <p>
 * Único método {@code @Transactional} de esta feature. Orquesta validación,
 * claim atómico por restricción única, resolución de duplicados, gestión
 * de reintentos explícitos y disparo del pipeline (no-op en US-02).
 */
@Slf4j
@Service
public class ConversationAuditIntakeServiceImpl implements IConversationAuditIntakeService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;
    private static final String TRUNCATION_SUFFIX = "...[truncado]";

    private final IAuditResultRepository auditResultRepository;
    private final ConversationAuditRequestValidator validator;
    private final AuditResultMapper auditResultMapper;
    private final IAuditPipelineTrigger auditPipelineTrigger;
    private final Clock clock;

    public ConversationAuditIntakeServiceImpl(
            IAuditResultRepository auditResultRepository,
            ConversationAuditRequestValidator validator,
            AuditResultMapper auditResultMapper,
            IAuditPipelineTrigger auditPipelineTrigger,
            Clock clock) {
        this.auditResultRepository = auditResultRepository;
        this.validator = validator;
        this.auditResultMapper = auditResultMapper;
        this.auditPipelineTrigger = auditPipelineTrigger;
        this.clock = clock;
    }

    /**
     * Admite un mensaje de auditoría.
     * <ol>
     *   <li>Valida el contrato con {@link ConversationAuditRequestValidator}.</li>
     *   <li>Intenta insertar el claim atómicamente; la restricción única {@code uk_audit_result_chat_id}
     *       detecta duplicados sin consulta previa.</li>
     *   <li>Resuelve duplicados, reintentos explícitos y la promoción {@code PENDING → PROCESSING}.</li>
     *   <li>Dispara el pipeline (no-op hasta US-03).</li>
     * </ol>
     */
    @Override
    public AuditIntakeResultDto admit(ConversationAuditRequestDto request, boolean retryRequested) {
        // a) Validación previa a cualquier escritura
        try {
            validator.validate(request);
        } catch (InvalidAuditRequestException ex) {
            throw new InvalidAuditMessageException("INVALID_SCHEMA", ex.getInvalidFields(), ex);
        }

        String chatId = request.conversation().chatId();
        Instant now = Instant.now(clock);

        // b) Intentar insertar el claim atómicamente
        AuditResult claim = auditResultMapper.toClaim(request, now);
        try {
            auditResultRepository.saveAndFlush(claim);
        } catch (DataIntegrityViolationException ex) {
            // c) chatId duplicado — resolver según estado y flag de reintento
            return handleDuplicate(chatId, request, retryRequested, now);
        } catch (DataAccessException ex) {
            throw new AuditProcessingException(chatId, "Error de acceso a datos al crear el claim", ex);
        }

        // d) Claim nuevo: promover PENDING → PROCESSING
        try {
            claim.setStatus(AuditStatus.PROCESSING);
            claim.setUpdatedDate(now);
            auditResultRepository.saveAndFlush(claim);
        } catch (DataAccessException ex) {
            throw new AuditProcessingException(chatId, "Error de acceso a datos al promover el claim", ex);
        }

        // e) Disparar pipeline (no-op en US-02)
        auditPipelineTrigger.trigger(chatId);

        return new AuditIntakeResultDto(AuditIntakeOutcome.ADMITTED, chatId, claim.getAuditId());
    }

    private AuditIntakeResultDto handleDuplicate(String chatId, ConversationAuditRequestDto request,
            boolean retryRequested, Instant now) {
        Optional<AuditResult> existing = auditResultRepository.findByChatId(chatId);
        if (existing.isEmpty()) {
            // La restricción se disparó pero no encontramos el registro — error transitorio
            throw new AuditProcessingException(chatId,
                    "Violación de unicidad sin registro previo localizable para chatId", null);
        }

        AuditResult record = existing.get();
        AuditStatus currentStatus = record.getStatus();

        // Si está en PROCESSING no se reintenta aunque venga el header
        if (currentStatus == AuditStatus.PROCESSING) {
            log.warn("chatId={} ya está en PROCESSING — reintento rechazado", chatId);
            return new AuditIntakeResultDto(AuditIntakeOutcome.DUPLICATE_SKIPPED, chatId, record.getAuditId());
        }

        // Sin reintento explícito: descartar silenciosamente
        if (!retryRequested) {
            return new AuditIntakeResultDto(AuditIntakeOutcome.DUPLICATE_SKIPPED, chatId, record.getAuditId());
        }

        // Reintento explícito sobre FAILED o COMPLETED
        if (currentStatus == AuditStatus.FAILED || currentStatus == AuditStatus.COMPLETED) {
            try {
                record.setStatus(AuditStatus.PROCESSING);
                record.setAttemptCount(record.getAttemptCount() + 1);
                record.setErrorMessage(null);
                record.setEventId(request.eventId().toString());
                record.setUpdatedDate(now);
                auditResultRepository.saveAndFlush(record);
            } catch (DataAccessException ex) {
                throw new AuditProcessingException(chatId, "Error de acceso a datos al reprocesar el claim", ex);
            }

            auditPipelineTrigger.trigger(chatId);
            return new AuditIntakeResultDto(AuditIntakeOutcome.RETRY_ADMITTED, chatId, record.getAuditId());
        }

        // Cualquier otro estado (PENDING sin reintento explícito): descartar
        return new AuditIntakeResultDto(AuditIntakeOutcome.DUPLICATE_SKIPPED, chatId, record.getAuditId());
    }

    /**
     * Marca el registro de auditoría como {@code FAILED} con un mensaje de error
     * legible truncado a {@value MAX_ERROR_MESSAGE_LENGTH} caracteres.
     * Solo se llama desde el listener cuando un mensaje con claim creado termina en la DLQ.
     *
     * @param chatId       identificador de la conversación
     * @param errorMessage descripción del fallo, sin contenido sensible
     */
    @Transactional
    public void markAsFailed(String chatId, String errorMessage) {
        auditResultRepository.findByChatId(chatId).ifPresent(record -> {
            record.setStatus(AuditStatus.FAILED);
            record.setErrorMessage(truncate(errorMessage));
            record.setUpdatedDate(Instant.now(clock));
            auditResultRepository.saveAndFlush(record);
        });
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        if (message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        int cutAt = MAX_ERROR_MESSAGE_LENGTH - TRUNCATION_SUFFIX.length();
        return message.substring(0, cutAt) + TRUNCATION_SUFFIX;
    }
}
