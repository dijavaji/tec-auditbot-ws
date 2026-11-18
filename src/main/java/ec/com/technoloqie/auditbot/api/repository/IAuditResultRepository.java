package ec.com.technoloqie.auditbot.api.repository;

import ec.com.technoloqie.auditbot.api.model.AuditResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repositorio JPA para {@link AuditResult}.
 * <p>
 * Sin lógica de decisión ni {@code @Transactional}: la gestión de transacciones
 * es responsabilidad exclusiva de la capa de servicio.
 */
public interface IAuditResultRepository extends JpaRepository<AuditResult, String> {

    /**
     * Busca un registro de auditoría por identificador de conversación.
     *
     * @param chatId identificador de la conversación
     * @return el registro, si existe
     */
    Optional<AuditResult> findByChatId(String chatId);
}
