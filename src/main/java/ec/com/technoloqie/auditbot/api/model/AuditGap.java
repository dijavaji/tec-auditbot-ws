package ec.com.technoloqie.auditbot.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Registro individual de una brecha de conocimiento (knowledge gap) detectada
 * durante la auditoría de una conversación (US-03).
 * <p>
 * Contenedor de datos puro: sin lógica de negocio ni métodos de dominio.
 */
@Entity
@Table(name = "audit_gap")
@Getter
@Setter
@NoArgsConstructor
public class AuditGap {

    @Id
    @Column(name = "gap_id", length = 36, nullable = false, updatable = false)
    private String gapId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "audit_id", nullable = false, updatable = false)
    private AuditResult auditResult;

    @Column(name = "message_id", length = 128, nullable = false, updatable = false)
    private String messageId;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "confidence_score", precision = 4, scale = 3, nullable = false)
    private BigDecimal confidenceScore;

    @Column(name = "created_date", nullable = false, updatable = false)
    private Instant createdDate;
}
