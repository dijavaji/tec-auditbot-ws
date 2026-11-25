package ec.com.technoloqie.auditbot.api.repository;

import ec.com.technoloqie.auditbot.api.model.AuditRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repositorio JPA para {@link AuditRecommendation} (US-07).
 * <p>
 * Sin lógica de decisión ni {@code @Transactional}: la gestión de transacciones
 * es responsabilidad exclusiva de la capa de servicio.
 */
public interface IAuditRecommendationRepository extends JpaRepository<AuditRecommendation, String> {

    /**
     * Busca las recomendaciones asociadas a una auditoría por su ID.
     *
     * @param auditId identificador de la auditoría raíz
     * @return lista de recomendaciones asociadas
     */
    List<AuditRecommendation> findByAuditResult_AuditId(String auditId);

    /**
     * Elimina las recomendaciones asociadas a una auditoría por su ID (usado en reintentos).
     *
     * @param auditId identificador de la auditoría raíz
     */
    void deleteByAuditResult_AuditId(String auditId);
}
