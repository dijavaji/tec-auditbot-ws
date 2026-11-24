package ec.com.technoloqie.auditbot.api.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ec.com.technoloqie.auditbot.api.dto.AuditConversationDto;
import ec.com.technoloqie.auditbot.api.dto.ConversationAuditSubmissionResponseDto;
import ec.com.technoloqie.auditbot.api.service.IConversationAuditSubmissionService;
import lombok.extern.slf4j.Slf4j;

/**
 * Controlador REST para recepción de solicitudes de auditoría de conversaciones y endpoints informativos.
 */
@CrossOrigin(origins = "${ec.com.technoloqie.chatbot.app.url}")
@RestController
@RequestMapping("${ec.com.technoloqie.auditbot.api.prefix}/audits")
@Slf4j
public class AuditChatRestController {

    private final IConversationAuditSubmissionService submissionService;
    private final String appName;

    public AuditChatRestController(IConversationAuditSubmissionService submissionService,
            @Value("${spring.application.name}") String appName) {
        this.submissionService = submissionService;
        this.appName = appName;
    }

    @GetMapping
    public String getMessage() {
        return String.format("Now this finally works out. Welcome %s", appName);
    }

    /**
     * Endpoint de ingesta segura para recibir y encolar conversaciones completas a auditar.
     *
     * @param conversation payload de la conversación a auditar
     * @return confirmación de la admisión asíncrona con HTTP 202 Accepted
     */
    @PostMapping("/conversations")
    public ResponseEntity<ConversationAuditSubmissionResponseDto> submitConversation(
            @RequestBody AuditConversationDto conversation) {
        ConversationAuditSubmissionResponseDto response = submissionService.submitConversation(conversation);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
