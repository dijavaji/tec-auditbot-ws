package ec.com.technoloqie.auditbot.api.repository;

import ec.com.technoloqie.auditbot.api.model.AuditGap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repositorio JPA para {@link AuditGap}.
 * <p>
 * Sin lógica de decisión ni {@code @Transactional}: la gestión de transacciones
 * es responsabilidad exclusiva de la capa de servicio.
 */
public interface IAuditGapRepository extends JpaRepository<AuditGap, String> {

    /**
     * Busca los gaps de conocimiento asociados a una auditoría por su ID.
     *
     * @param auditId identificador de la auditoría raíz
     * @return lista de gaps asociados
     */
    List<AuditGap> findByAuditResult_AuditId(String auditId);

    /**
     * Elimina los gaps asociados a una auditoría por su ID (usado en reintentos).
     *
     * @param auditId identificador de la auditoría raíz
     */
    void deleteByAuditResult_AuditId(String auditId);
}
