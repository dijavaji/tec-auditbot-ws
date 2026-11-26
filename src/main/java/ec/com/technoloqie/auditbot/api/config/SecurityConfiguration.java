package ec.com.technoloqie.auditbot.api.config;

import java.time.Clock;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import ec.com.technoloqie.auditbot.api.dto.ApiErrorResponseDto;

/**
 * Configuración de seguridad web, políticas CORS y gestión stateless de sesiones.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    private final String chatbotAppUrl;
    private final String apiPrefix;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public SecurityConfiguration(
            @Value("${ec.com.technoloqie.chatbot.app.url:http://127.0.0.1:3000/}") String chatbotAppUrl,
            @Value("${ec.com.technoloqie.auditbot.api.prefix:/api/v1}") String apiPrefix,
            Clock clock) {
        this.chatbotAppUrl = chatbotAppUrl;
        this.apiPrefix = apiPrefix;
        this.clock = clock;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenValidator jwtTokenValidator) {
        return new JwtAuthenticationFilter(jwtTokenValidator);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, apiPrefix + "/audits").permitAll()
                        .requestMatchers(HttpMethod.POST, apiPrefix + "/audits/conversations").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        String origin = chatbotAppUrl.endsWith("/") ? chatbotAppUrl.substring(0, chatbotAppUrl.length() - 1) : chatbotAppUrl;
        configuration.setAllowedOrigins(List.of(chatbotAppUrl, origin));
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS", "PUT", "DELETE"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiErrorResponseDto errorDto = new ApiErrorResponseDto(
                    clock.instant(),
                    HttpStatus.UNAUTHORIZED.value(),
                    HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                    "Acceso no autorizado: se requiere token Bearer v\u00e1lido en la cabecera Authorization",
                    List.of()
            );
            response.getWriter().write(objectMapper.writeValueAsString(errorDto));
        };
    }
}
