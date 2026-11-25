package ec.com.technoloqie.auditbot.api.analyzer;

import ec.com.technoloqie.auditbot.api.dto.AuditMessageDto;
import ec.com.technoloqie.auditbot.api.dto.RecommendationAnalysisResponseDto;
import ec.com.technoloqie.auditbot.api.dto.RecommendationCandidateDto;
import ec.com.technoloqie.auditbot.api.dto.RecommendationEvaluationResultDto;
import ec.com.technoloqie.auditbot.api.dto.RecommendationItemDto;
import ec.com.technoloqie.auditbot.api.model.RecommendationType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Analizador de dominio puro para recomendaciones de mejora de chatbots (US-07).
 * <p>
 * Regla arquitectónica: 100% desacoplado de frameworks. Sin dependencias de Spring,
 * JPA, Spring AI o RabbitMQ.
 */
public class RecommendationAnalyzer {

    /**
     * Evalúa, sanea, valida y deduplica las recomendaciones devueltas por la IA.
     *
     * @param aiResponse           respuesta estructurada del modelo de IA
     * @param conversationMessages lista de mensajes de la conversación auditada
     * @return resultado normalizado y validado
     */
    public RecommendationEvaluationResultDto evaluate(
            RecommendationAnalysisResponseDto aiResponse,
            List<AuditMessageDto> conversationMessages) {

        if (aiResponse == null || aiResponse.recommendations() == null || aiResponse.recommendations().isEmpty()) {
            return new RecommendationEvaluationResultDto(List.of());
        }

        Set<String> validMessageIds = extractValidMessageIds(conversationMessages);
        List<RecommendationItemDto> validatedItems = new ArrayList<>();
        Set<String> uniqueKeys = new HashSet<>();

        for (RecommendationCandidateDto candidate : aiResponse.recommendations()) {
            if (candidate == null) {
                continue;
            }

            // 1. Saneamiento y validación de texto sugerido (R11)
            if (candidate.suggestedText() == null || candidate.suggestedText().isBlank()) {
                continue;
            }
            String sanitizedText = candidate.suggestedText().trim();

            // 2. Normalización y filtrado de tipo de recomendación (R10)
            RecommendationType parsedType = parseType(candidate.type());
            if (parsedType == null) {
                continue;
            }

            // 3. Validación referencial de originMessageId contra la conversación (R12)
            String originMsgId = candidate.originMessageId();
            if (originMsgId != null) {
                originMsgId = originMsgId.trim();
                if (originMsgId.isBlank() || !validMessageIds.contains(originMsgId)) {
                    originMsgId = null;
                }
            }

            // 4. Deduplicación de recomendaciones idénticas (R13)
            String deduplicationKey = parsedType.name() + "::" + sanitizedText.toLowerCase();
            if (uniqueKeys.add(deduplicationKey)) {
                validatedItems.add(new RecommendationItemDto(parsedType, sanitizedText, originMsgId));
            }
        }

        return new RecommendationEvaluationResultDto(validatedItems);
    }

    private RecommendationType parseType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return null;
        }
        try {
            return RecommendationType.valueOf(rawType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Set<String> extractValidMessageIds(List<AuditMessageDto> messages) {
        if (messages == null) {
            return Set.of();
        }
        Set<String> ids = new HashSet<>();
        for (AuditMessageDto msg : messages) {
            if (msg != null && msg.messageId() != null && !msg.messageId().isBlank()) {
                ids.add(msg.messageId().trim());
            }
        }
        return ids;
    }
}
