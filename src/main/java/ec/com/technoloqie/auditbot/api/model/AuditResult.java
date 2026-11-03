package ec.com.technoloqie.auditbot.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Registro de control de una auditoría de conversación.
 * <p>
 * Actúa como raíz del agregado de auditoría. US-02 crea el registro en {@code PENDING}
 * y lo promueve a {@code PROCESSING}. Las columnas de US-03 a US-05
 * ({@code frustrationDetected}, {@code lostLeadDetected}, {@code leadScore},
 * {@code gapsCount}) se declaran nullable desde esta historia para evitar
 * migraciones adicionales.
 * <p>
 * Contenedor de datos puro: sin lógica de negocio ni métodos de dominio.
 */
@Entity
@Table(
        name = "audit_result",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_audit_result_chat_id",
                columnNames = "chat_id"
        )
)
@Getter
@Setter
@NoArgsConstructor
public class AuditResult {

    @Id
    @Column(name = "audit_id", length = 36, nullable = false, updatable = false)
    private String auditId;

    @Column(name = "chat_id", length = 128, nullable = false, unique = true, updatable = false)
    private String chatId;

    @Column(name = "assistant_id", length = 128, nullable = false, updatable = false)
    private String assistantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private AuditStatus status;

    @Column(name = "event_id", length = 36, nullable = false)
    private String eventId;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /** Escribe US-04. */
    @Column(name = "frustration_detected")
    private Boolean frustrationDetected;

    /** Escribe US-05. */
    @Column(name = "lost_lead_detected")
    private Boolean lostLeadDetected;

    /** Escribe US-05. Rango 0.000 – 1.000. */
    @Column(name = "lead_score", precision = 4, scale = 3)
    private BigDecimal leadScore;

    /** Escribe US-03. */
    @Column(name = "gaps_count")
    private Integer gapsCount;

    @Column(name = "created_date", nullable = false, updatable = false)
    private Instant createdDate;

    @Column(name = "updated_date", nullable = false)
    private Instant updatedDate;

    /** Escribe el pipeline al completar. */
    @Column(name = "audited_date")
    private Instant auditedDate;
}
