package ec.com.technoloqie.auditbot.api.integration.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ec.com.technoloqie.auditbot.api.commons.exception.InvalidAuditMessageException;
import ec.com.technoloqie.auditbot.api.dto.ConversationAuditRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas unitarias de {@link AuditMessageDeserializer}. Cubre R8, R41.
 */
class AuditMessageDeserializerTest {

    private AuditMessageDeserializer deserializer;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        deserializer = new AuditMessageDeserializer(mapper);
    }

    @Test
    void shouldDeserializeVersionOneContract() {
        String json = """
                {
                  "eventId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                  "schemaVersion": "1.0",
                  "occurredAt": "2026-08-31T09:00:00Z",
                  "producer": "smart-chatbot",
                  "conversation": {
                    "chatId": "chat-123",
                    "assistantId": "assistant-1",
                    "startedAt": "2026-08-31T09:00:00Z",
                    "endedAt": "2026-08-31T09:00:30Z",
                    "messages": [
                      {
                        "messageId": "msg-1",
                        "role": "USER",
                        "content": "hola",
                        "occurredAt": "2026-08-31T09:00:00Z"
                      }
                    ]
                  }
                }
                """;

        ConversationAuditRequestDto result = deserializer.deserialize(json.getBytes(StandardCharsets.UTF_8));

        assertThat(result).isNotNull();
        assertThat(result.schemaVersion()).isEqualTo("1.0");
        assertThat(result.conversation().chatId()).isEqualTo("chat-123");
        assertThat(result.conversation().messages()).hasSize(1);
    }

    @Test
    void shouldPreserveInstantsInUtc() {
        String json = """
                {
                  "eventId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                  "schemaVersion": "1.0",
                  "occurredAt": "2026-08-31T09:00:00Z",
                  "producer": "smart-chatbot",
                  "conversation": {
                    "chatId": "chat-123",
                    "assistantId": "assistant-1",
                    "startedAt": "2026-08-31T09:00:00Z",
                    "messages": [
                      {
                        "messageId": "msg-1",
                        "role": "USER",
                        "content": "hola",
                        "occurredAt": "2026-08-31T09:00:00Z"
                      }
                    ]
                  }
                }
                """;

        ConversationAuditRequestDto result = deserializer.deserialize(json.getBytes(StandardCharsets.UTF_8));

        assertThat(result.occurredAt().toString()).endsWith("Z");
        assertThat(result.conversation().startedAt().toString()).endsWith("Z");
        assertThat(result.conversation().messages().get(0).occurredAt().toString()).endsWith("Z");
    }

    @Test
    void shouldWrapMalformedJsonAsPermanentError() {
        byte[] malformed = "{ not valid json }}}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> deserializer.deserialize(malformed))
                .isInstanceOf(InvalidAuditMessageException.class)
                .satisfies(ex -> {
                    InvalidAuditMessageException e = (InvalidAuditMessageException) ex;
                    assertThat(e.getReason()).isEqualTo("MALFORMED_JSON");
                    assertThat(e.getCause()).isNotNull();
                });
    }
}
