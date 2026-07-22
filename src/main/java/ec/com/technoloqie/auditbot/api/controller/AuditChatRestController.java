package ec.com.technoloqie.auditbot.api.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

@CrossOrigin(origins = "${ec.com.technoloqie.chatbot.app.url}")
@RestController
@RequestMapping("${ec.com.technoloqie.auditbot.api.prefix}/audits")
@Slf4j
public class AuditChatRestController {
	
	@Value("${spring.application.name}")
	private String appName;
	
	@GetMapping
    public String getMessage() {
        return String.format("Now this finally works out. Welcome %s",appName);
    }
}
