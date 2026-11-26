package ec.com.technoloqie.auditbot.api.config;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

/**
 * Utilitario para validación y generación de tokens JWT basados en HMAC-SHA.
 */
@Component
@Slf4j
public class JwtTokenValidator {

    private final SecretKey secretKey;

    public JwtTokenValidator(
            @Value("${ec.com.technoloqie.auditbot.security.jwt.secret:AuditBotSecretKeyForJwtTokenValidationMustBeAtLeast256BitsLong!}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Valida la firma y vigencia temporal del token JWT.
     *
     * @param token cadena JWT
     * @return {@code true} si el token es válido y no ha expirado; {@code false} en caso contrario
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token.trim());
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Token JWT inválido o expirado: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extrae el sujeto (subject) del token JWT.
     *
     * @param token cadena JWT
     * @return subject o {@code null} si es inválido
     */
    public String extractSubject(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token.trim())
                    .getPayload();
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Genera un token JWT firmado para propósitos de prueba o integración.
     *
     * @param subject        sujeto o identidad del usuario
     * @param validityMillis tiempo de validez en milisegundos
     * @return token JWT firmado
     */
    public String generateToken(String subject, long validityMillis) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + validityMillis);
        return Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }
    
    public static void main(String[] args) {
        String secret = System.getenv().getOrDefault(
                "JWT_SECRET", 
                "AuditBotSecretKeyForJwtTokenValidationMustBeAtLeast256BitsLong!"
        );
        JwtTokenValidator validator = new JwtTokenValidator(secret);
        
        String subject = args.length > 0 ? args[0] : "admin";
        long validityDays = args.length > 1 ? Long.parseLong(args[1]) : 30L;
        long validityMillis = validityDays * 24 * 60 * 60 * 1000L;
        
        String token = validator.generateToken(subject, validityMillis);
        System.out.println(token);
    }
    
}
