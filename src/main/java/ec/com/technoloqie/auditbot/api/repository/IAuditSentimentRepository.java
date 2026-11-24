package ec.com.technoloqie.auditbot.api.repository;

import ec.com.technoloqie.auditbot.api.model.AuditSentiment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repositorio JPA para {@link AuditSentiment}.
 * <p>
 * Sin lógica de decisión ni {@code @Transactional}: la gestión de transacciones
 * es responsabilidad exclusiva de la capa de servicio.
 */
public interface IAuditSentimentRepository extends JpaRepository<AuditSentiment, String> {

    /**
     * Busca los registros de sentimiento asociados a una auditoría por su ID.
     *
     * @param auditId identificador de la auditoría raíz
     * @return lista de sentimientos asociados
     */
    List<AuditSentiment> findByAuditResult_AuditId(String auditId);

    /**
     * Elimina los registros de sentimiento asociados a una auditoría por su ID (usado en reintentos).
     *
     * @param auditId identificador de la auditoría raíz
     */
    void deleteByAuditResult_AuditId(String auditId);
}
