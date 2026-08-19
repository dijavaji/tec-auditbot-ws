package ec.com.technoloqie.auditbot.api.integration.rabbitmq;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Propiedades tipadas para la topología y el comportamiento de publicación de
 * {@code US-01}. Se enlazan desde el prefijo {@code auditbot.messaging}.
 * <p>
 * Se valida que los nombres no estén en blanco, que el timeout de confirm sea
 * positivo y que el tamaño máximo de payload sea mayor que cero al iniciar el
 * contexto de Spring, mediante {@link #validate()} invocado desde
 * {@link RabbitMqConfiguration}.
 */
@ConfigurationProperties(prefix = "auditbot.messaging")
public class AuditMessagingProperties {

    private String exchange = "auditbot.exchange";
    private String routingKey = "conversation.audit.requested";
    private String queue = "conversation.audit.queue";
    private Duration confirmTimeout = Duration.ofSeconds(5);
    private DataSize maxPayloadSize = DataSize.ofMegabytes(1);

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public Duration getConfirmTimeout() {
        return confirmTimeout;
    }

    public void setConfirmTimeout(Duration confirmTimeout) {
        this.confirmTimeout = confirmTimeout;
    }

    public DataSize getMaxPayloadSize() {
        return maxPayloadSize;
    }

    public void setMaxPayloadSize(DataSize maxPayloadSize) {
        this.maxPayloadSize = maxPayloadSize;
    }

    /**
     * Valida que las propiedades tengan valores utilizables. Se invoca al declarar
     * los beans de topología para fallar rápido si la configuración es inválida.
     *
     * @throws IllegalStateException si algún valor es inválido
     */
    public void validate() {
        if (isBlank(exchange)) {
            throw new IllegalStateException("auditbot.messaging.exchange no puede estar vacío");
        }
        if (isBlank(routingKey)) {
            throw new IllegalStateException("auditbot.messaging.routing-key no puede estar vacío");
        }
        if (isBlank(queue)) {
            throw new IllegalStateException("auditbot.messaging.queue no puede estar vacío");
        }
        if (confirmTimeout == null || confirmTimeout.isZero() || confirmTimeout.isNegative()) {
            throw new IllegalStateException("auditbot.messaging.confirm-timeout debe ser positivo");
        }
        if (maxPayloadSize == null || maxPayloadSize.toBytes() <= 0) {
            throw new IllegalStateException("auditbot.messaging.max-payload-size debe ser mayor que cero");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
