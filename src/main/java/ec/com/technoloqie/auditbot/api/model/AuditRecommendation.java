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

import java.time.Instant;

/**
 * Registro individual de una recomendación de mejora generada automáticamente (US-07).
 * <p>
 * Contenedor de datos puro: sin lógica de negocio ni métodos de dominio.
 */
@Entity
@Table(name = "audit_recommendation")
@Getter
@Setter
@NoArgsConstructor
public class AuditRecommendation {

    @Id
    @Column(name = "recommendation_id", length = 36, nullable = false, updatable = false)
    private String recommendationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "audit_id", nullable = false, updatable = false)
    private AuditResult auditResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 32, nullable = false)
    private RecommendationType type;

    @Column(name = "suggested_text", nullable = false, columnDefinition = "TEXT")
    private String suggestedText;

    @Column(name = "origin_message_id", length = 128)
    private String originMessageId;

    @Column(name = "created_date", nullable = false, updatable = false)
    private Instant createdDate;
}
