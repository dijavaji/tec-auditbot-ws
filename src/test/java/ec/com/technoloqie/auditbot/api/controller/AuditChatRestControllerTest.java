package ec.com.technoloqie.auditbot.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import ec.com.technoloqie.auditbot.api.commons.exception.AuditPublicationException;
import ec.com.technoloqie.auditbot.api.commons.exception.InvalidAuditRequestException;
import ec.com.technoloqie.auditbot.api.config.JwtAuthenticationFilter;
import ec.com.technoloqie.auditbot.api.config.JwtTokenValidator;
import ec.com.technoloqie.auditbot.api.config.SecurityConfiguration;
import ec.com.technoloqie.auditbot.api.controller.advice.AuditChatControllerAdvice;
import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;
import ec.com.technoloqie.auditbot.api.dto.AuditMessageDto;
import ec.com.technoloqie.auditbot.api.dto.ConversationAuditSubmissionResponseDto;
import ec.com.technoloqie.auditbot.api.service.IConversationAuditSubmissionService;

@WebMvcTest(AuditChatRestController.class)
@Import({SecurityConfiguration.class, JwtTokenValidator.class, AuditChatControllerAdvice.class})
class AuditChatRestControllerTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-25T11:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @Autowired
    private JwtTokenValidator jwtTokenValidator;

    @MockitoBean
    private IConversationAuditSubmissionService submissionService;

    @MockitoBean
    private Clock clock;

    private String validToken;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(FIXED_NOW);
        validToken = jwtTokenValidator.generateToken("test-user", 60000);
    }

    @Test
    void shouldAllowAccessToGetAuditsWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/audits"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn401WhenAuthorizationHeaderIsMissing() throws Exception {
        AuditConversationDto body = new AuditConversationDto("c1", "a1", FIXED_NOW, null, List.of());

        mockMvc.perform(post("/api/v1/audits/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void shouldReturn401WhenTokenIsInvalid() throws Exception {
        AuditConversationDto body = new AuditConversationDto("c1", "a1", FIXED_NOW, null, List.of());

        mockMvc.perform(post("/api/v1/audits/conversations")
                        .header("Authorization", "Bearer invalid-token-sample")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void shouldAcceptValidConversationAndReturn202() throws Exception {
        AuditMessageDto message = new AuditMessageDto("msg-1", "USER", "Hola", FIXED_NOW);
        AuditConversationDto conversation = new AuditConversationDto(
                "chat-123",
                "asst-1",
                FIXED_NOW,
                FIXED_NOW.plusSeconds(30),
                List.of(message)
        );

        UUID eventId = UUID.randomUUID();
        ConversationAuditSubmissionResponseDto responseDto = new ConversationAuditSubmissionResponseDto(
                eventId,
                "chat-123",
                "ACCEPTED",
                "Solicitud de auditoría recibida y enviada a procesamiento asíncrono",
                FIXED_NOW
        );

        when(submissionService.submitConversation(any())).thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/audits/conversations")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conversation)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.chatId").value("chat-123"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.submittedAt").value(FIXED_NOW.toString()));
    }

    @Test
    void shouldReturn400WhenPayloadIsInvalid() throws Exception {
        AuditConversationDto invalidConv = new AuditConversationDto("", "asst-1", FIXED_NOW, null, List.of());

        when(submissionService.submitConversation(any()))
                .thenThrow(new InvalidAuditRequestException(List.of("conversation.chatId", "conversation.messages")));

        mockMvc.perform(post("/api/v1/audits/conversations")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidConv)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.invalidFields[0]").value("conversation.chatId"))
                .andExpect(jsonPath("$.invalidFields[1]").value("conversation.messages"));
    }

    @Test
    void shouldReturn400WhenJsonIsMalformed() throws Exception {
        mockMvc.perform(post("/api/v1/audits/conversations")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{malformed-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void shouldReturn502WhenRabbitMqFails() throws Exception {
        AuditMessageDto message = new AuditMessageDto("msg-1", "USER", "¿Precios?", FIXED_NOW);
        AuditConversationDto conversation = new AuditConversationDto("chat-999", "asst-1", FIXED_NOW, null, List.of(message));

        when(submissionService.submitConversation(any()))
                .thenThrow(new AuditPublicationException(UUID.randomUUID(), "broker caído"));

        mockMvc.perform(post("/api/v1/audits/conversations")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conversation)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.error").value("Bad Gateway"));
    }

    @Test
    void shouldSupportCorsForConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/audits/conversations")
                        .header("Origin", "http://127.0.0.1:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"));
    }
}
