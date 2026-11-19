package ec.com.technoloqie.auditbot.api.analyzer;

import ec.com.technoloqie.auditbot.api.dto.KnowledgeGapCandidateDto;
import ec.com.technoloqie.auditbot.api.dto.KnowledgeGapItemDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Componente de lógica de negocio pura para el análisis, filtrado y consolidación
 * de brechas de conocimiento (knowledge gaps) dentro de una conversación.
 * <p>
 * Regla de arquitectura: esta clase no debe importar ningún tipo de Spring,
 * Jakarta Persistence, mensajería AMQP ni Spring AI.
 */
public class KnowledgeGapAnalyzer {

    private static final Set<String> SPANISH_STOPWORDS = Set.of(
            "el", "la", "los", "las", "un", "una", "unos", "unas",
            "de", "del", "a", "al", "en", "para", "por", "con", "sin", "sobre",
            "que", "es", "son", "como", "y", "o", "u", "si", "no",
            "se", "su", "sus", "mi", "mis", "tu", "tus", "me", "te", "le", "les",
            "hola", "gracias", "favor", "buenas", "buenos", "dias", "tardes", "noches"
    );

    /**
     * Agrupa y consolida los candidatos a gaps detectados por el modelo de IA.
     *
     * @param candidates          lista de candidatos clasificados por turno en orden cronológico
     * @param minConfidence       umbral mínimo de confianza (candidatos inferiores son descartados)
     * @param similarityThreshold umbral Jaccard para considerar dos consultas paráfrasis del mismo gap
     * @return lista inmutable de gaps consolidados
     */
    public List<KnowledgeGapItemDto> groupAndConsolidate(
            List<KnowledgeGapCandidateDto> candidates,
            BigDecimal minConfidence,
            double similarityThreshold) {

        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        BigDecimal effectiveMinConfidence = minConfidence != null ? minConfidence : new BigDecimal("0.600");

        // 1. Filtrar únicamente los gaps válidos que superan el umbral mínimo
        List<KnowledgeGapCandidateDto> filtered = candidates.stream()
                .filter(c -> c != null && c.isGap())
                .filter(c -> c.confidenceScore() != null && c.confidenceScore().compareTo(effectiveMinConfidence) >= 0)
                .filter(c -> c.questionText() != null && !c.questionText().trim().isEmpty())
                .toList();

        if (filtered.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. Agrupación greedy determinista en orden cronológico
        List<List<KnowledgeGapCandidateDto>> groups = new ArrayList<>();

        for (KnowledgeGapCandidateDto candidate : filtered) {
            boolean matched = false;
            for (List<KnowledgeGapCandidateDto> group : groups) {
                KnowledgeGapCandidateDto leader = group.get(0);
                double similarity = calculateJaccardSimilarity(candidate.questionText(), leader.questionText());
                if (similarity >= similarityThreshold) {
                    group.add(candidate);
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                List<KnowledgeGapCandidateDto> newGroup = new ArrayList<>();
                newGroup.add(candidate);
                groups.add(newGroup);
            }
        }

        // 3. Consolidación de cada grupo
        List<KnowledgeGapItemDto> consolidated = new ArrayList<>();

        for (List<KnowledgeGapCandidateDto> group : groups) {
            String firstMessageId = group.get(0).messageId();

            // Determinar score máximo redondeado a 3 decimales
            BigDecimal maxScore = group.stream()
                    .map(KnowledgeGapCandidateDto::confidenceScore)
                    .max(BigDecimal::compareTo)
                    .orElse(effectiveMinConfidence)
                    .setScale(3, RoundingMode.HALF_UP);

            // Seleccionar pregunta canónica: mayor cantidad de tokens significativos, luego longitud, luego primera
            KnowledgeGapCandidateDto canonical = selectCanonicalCandidate(group);

            consolidated.add(new KnowledgeGapItemDto(
                    firstMessageId,
                    canonical.questionText().trim(),
                    maxScore,
                    group.size()
            ));
        }

        return Collections.unmodifiableList(consolidated);
    }

    /**
     * Calcula la similitud Jaccard de conjuntos de tokens entre dos textos.
     *
     * @param textA primer texto
     * @param textB segundo texto
     * @return coeficiente en el intervalo [0.0, 1.0]
     */
    public double calculateJaccardSimilarity(String textA, String textB) {
        Set<String> tokensA = tokenizeAndFilterStopwords(textA);
        Set<String> tokensB = tokenizeAndFilterStopwords(textB);

        if (tokensA.isEmpty() && tokensB.isEmpty()) {
            String normA = normalize(textA);
            String normB = normalize(textB);
            return normA.equals(normB) ? 1.0 : 0.0;
        }

        Set<String> intersection = new HashSet<>(tokensA);
        intersection.retainAll(tokensB);

        Set<String> union = new HashSet<>(tokensA);
        union.addAll(tokensB);

        if (union.isEmpty()) {
            return 0.0;
        }

        return (double) intersection.size() / (double) union.size();
    }

    /**
     * Normaliza un texto removiendo puntuación, tildes y pasando a minúsculas.
     *
     * @param text texto a normalizar
     * @return texto normalizado sin diacríticos ni puntuación
     */
    public String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String lower = text.trim().toLowerCase(Locale.ROOT);
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        String withoutDiacritics = decomposed.replaceAll("\\p{M}", "");
        // Reemplazar la virgulilla de la ñ normalizada si es necesario, o mantener caracteres alfanuméricos
        return withoutDiacritics.replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
    }

    /**
     * Tokeniza un texto normalizado y remueve stopwords del idioma español.
     *
     * @param text texto a procesar
     * @return conjunto de tokens significativos
     */
    public Set<String> tokenizeAndFilterStopwords(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return Collections.emptySet();
        }

        String[] parts = normalized.split("\\s+");
        Set<String> tokens = new HashSet<>();
        for (String part : parts) {
            if (!part.isBlank() && !SPANISH_STOPWORDS.contains(part)) {
                tokens.add(part);
            }
        }
        return Collections.unmodifiableSet(tokens);
    }

    private KnowledgeGapCandidateDto selectCanonicalCandidate(List<KnowledgeGapCandidateDto> group) {
        KnowledgeGapCandidateDto best = group.get(0);
        int maxTokens = tokenizeAndFilterStopwords(best.questionText()).size();
        int maxLength = best.questionText().trim().length();

        for (int i = 1; i < group.size(); i++) {
            KnowledgeGapCandidateDto current = group.get(i);
            int currentTokens = tokenizeAndFilterStopwords(current.questionText()).size();
            int currentLength = current.questionText().trim().length();

            if (currentTokens > maxTokens || (currentTokens == maxTokens && currentLength > maxLength)) {
                best = current;
                maxTokens = currentTokens;
                maxLength = currentLength;
            }
        }

        return best;
    }
}
