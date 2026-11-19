package ec.com.technoloqie.auditbot.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Registro individual del análisis de sentimiento de un mensaje de usuario (US-04).
 * <p>
 * Contenedor de datos puro: sin lógica de negocio ni métodos de dominio.
 */
@Entity
@Table(name = "audit_sentiment")
@Getter
@Setter
@NoArgsConstructor
public class AuditSentiment {

    @Id
    @Column(name = "sentiment_id", length = 36, nullable = false, updatable = false)
    private String sentimentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "audit_id", nullable = false, updatable = false)
    private AuditResult auditResult;

    @Column(name = "message_id", length = 128, nullable = false, updatable = false)
    private String messageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "label", length = 32, nullable = false)
    private SentimentLabel label;

    @Column(name = "score", precision = 4, scale = 3, nullable = false)
    private BigDecimal score;

    @Column(name = "sequence_order", nullable = false)
    private int sequenceOrder;

    @Column(name = "created_date", nullable = false, updatable = false)
    private Instant createdDate;
}
