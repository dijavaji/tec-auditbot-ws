package ec.com.technoloqie.auditbot.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class SecurityConfigurationTest {

    private static final String SECRET = "TestSecretKeyForJwtTokenValidationMustBeAtLeast256BitsLongForHmacSha!";
    private static final String APP_URL = "http://127.0.0.1:3000/";

    private JwtTokenValidator validator;

    @BeforeEach
    void setUp() {
        validator = new JwtTokenValidator(SECRET);
    }

    @Test
    void shouldValidateGeneratedValidToken() {
        String token = validator.generateToken("user-1", 60000);
        assertNotNull(token);
        assertTrue(validator.validateToken(token));
        assertEquals("user-1", validator.extractSubject(token));
    }

    @Test
    void shouldRejectInvalidToken() {
        assertFalse(validator.validateToken("invalid.token.here"));
        assertFalse(validator.validateToken(null));
        assertFalse(validator.validateToken(""));
    }

    @Test
    void shouldRejectExpiredToken() {
        String token = validator.generateToken("user-expired", -1000);
        assertFalse(validator.validateToken(token));
    }

    @Test
    void shouldConfigureCorsSourceProperly() {
        SecurityConfiguration securityConfig = new SecurityConfiguration(
                APP_URL,
                "/api/v1",
                java.time.Clock.systemUTC()
        );

        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        assertNotNull(source);

        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/audits/conversations");
        request.addHeader("Origin", "http://127.0.0.1:3000");

        CorsConfiguration config = source.getCorsConfiguration(request);
        assertNotNull(config);
        assertTrue(config.getAllowedOrigins().contains("http://127.0.0.1:3000"));
        assertTrue(config.getAllowedMethods().contains("POST"));
        assertTrue(config.getAllowedMethods().contains("OPTIONS"));
    }
}
