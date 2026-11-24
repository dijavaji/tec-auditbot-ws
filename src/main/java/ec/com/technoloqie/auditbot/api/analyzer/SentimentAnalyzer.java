package ec.com.technoloqie.auditbot.api.analyzer;

import ec.com.technoloqie.auditbot.api.dto.ProcessedSentimentDto;
import ec.com.technoloqie.auditbot.api.dto.SentimentEvaluationResultDto;
import ec.com.technoloqie.auditbot.api.dto.SentimentItemClassificationDto;
import ec.com.technoloqie.auditbot.api.model.SentimentLabel;
import ec.com.technoloqie.auditbot.api.model.SentimentTrend;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Componente de lógica de negocio pura para el análisis de sentimiento,
 * cálculo de tendencia emocional y determinación de frustración del usuario (US-04).
 * <p>
 * Regla de arquitectura: esta clase no debe importar ningún tipo de Spring,
 * Jakarta Persistence, mensajería AMQP ni Spring AI.
 */
public class SentimentAnalyzer {

    public static final BigDecimal DEFAULT_FRUSTRATION_SCORE_THRESHOLD = new BigDecimal("0.700");
    public static final double DEFAULT_NEGATIVE_RATIO_THRESHOLD = 0.40;
    public static final int DEFAULT_CONSECUTIVE_NEGATIVE_THRESHOLD = 2;
    public static final double TREND_DELTA_THRESHOLD = 0.20;

    /**
     * Evalúa la lista de clasificaciones estructuradas con los umbrales configurados.
     *
     * @param classifications              lista de clasificaciones individuales en orden cronológico
     * @param frustrationScoreThreshold     umbral mínimo de score para considerar frustración por mensaje individual
     * @param negativeRatioThreshold       proporción mínima de mensajes negativos para marcar frustración global
     * @param consecutiveNegativeThreshold cantidad mínima de mensajes negativos consecutivos
     * @return resultado consolidado de la evaluación
     */
    public SentimentEvaluationResultDto evaluate(
            List<SentimentItemClassificationDto> classifications,
            BigDecimal frustrationScoreThreshold,
            Double negativeRatioThreshold,
            Integer consecutiveNegativeThreshold) {

        if (classifications == null || classifications.isEmpty()) {
            return new SentimentEvaluationResultDto(Collections.emptyList(), false, SentimentTrend.STABLE);
        }

        BigDecimal effectiveScoreThreshold = frustrationScoreThreshold != null
                ? frustrationScoreThreshold
                : DEFAULT_FRUSTRATION_SCORE_THRESHOLD;

        double effectiveRatioThreshold = negativeRatioThreshold != null
                ? negativeRatioThreshold
                : DEFAULT_NEGATIVE_RATIO_THRESHOLD;

        int effectiveConsecutiveThreshold = consecutiveNegativeThreshold != null
                ? consecutiveNegativeThreshold
                : DEFAULT_CONSECUTIVE_NEGATIVE_THRESHOLD;

        // 1. Validar y procesar cada elemento asignando sequenceOrder
        List<ProcessedSentimentDto> processedItems = new ArrayList<>(classifications.size());
        for (int i = 0; i < classifications.size(); i++) {
            SentimentItemClassificationDto raw = classifications.get(i);
            int sequenceOrder = i + 1;
            SentimentLabel label = raw.label() != null ? raw.label() : SentimentLabel.NEUTRAL;
            BigDecimal validScore = normalizeScore(raw.score());
            processedItems.add(new ProcessedSentimentDto(raw.messageId(), label, validScore, sequenceOrder));
        }

        // 2. Calcular tendencia emocional
        SentimentTrend trend = calculateTrend(processedItems);

        // 3. Evaluar predicados de frustración global
        boolean frustrationDetected = checkFrustration(
                processedItems,
                effectiveScoreThreshold,
                effectiveRatioThreshold,
                effectiveConsecutiveThreshold
        );

        return new SentimentEvaluationResultDto(
                Collections.unmodifiableList(processedItems),
                frustrationDetected,
                trend
        );
    }

    /**
     * Sobrecarga de conveniencia utilizando los umbrales por defecto.
     */
    public SentimentEvaluationResultDto evaluate(List<SentimentItemClassificationDto> classifications) {
        return evaluate(
                classifications,
                DEFAULT_FRUSTRATION_SCORE_THRESHOLD,
                DEFAULT_NEGATIVE_RATIO_THRESHOLD,
                DEFAULT_CONSECUTIVE_NEGATIVE_THRESHOLD
        );
    }

    /**
     * Mapea cada etiqueta de sentimiento a su polaridad escalar base (R10).
     *
     * @param label etiqueta de sentimiento
     * @return valor escalar en [-1.0, +1.0]
     */
    public double getScalarPolarity(SentimentLabel label) {
        if (label == null) {
            return 0.0;
        }
        return switch (label) {
            case POSITIVE -> 1.0;
            case NEUTRAL -> 0.0;
            case NEGATIVE -> -0.5;
            case FRUSTRATED -> -1.0;
        };
    }

    /**
     * Valida si un score numérico se encuentra en el rango cerrado [0.000, 1.000] (R9).
     */
    public boolean isValidScore(BigDecimal score) {
        return score != null
                && score.compareTo(BigDecimal.ZERO) >= 0
                && score.compareTo(BigDecimal.ONE) <= 0;
    }

    /**
     * Normaliza y acota el score al rango [0.000, 1.000] con escala a 3 decimales.
     */
    public BigDecimal normalizeScore(BigDecimal score) {
        if (score == null) {
            return BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
        }
        if (score.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
        }
        if (score.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE.setScale(3, RoundingMode.HALF_UP);
        }
        return score.setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * Calcula la variación de polaridad (delta) entre la segunda y la primera mitad (R12).
     */
    public double calculatePolarityDelta(List<ProcessedSentimentDto> items) {
        if (items == null || items.size() < 2) {
            return 0.0;
        }

        int n = items.size();
        int halfSize = n / 2;

        double sumFirstHalf = 0.0;
        for (int i = 0; i < halfSize; i++) {
            sumFirstHalf += calculateWeightedPolarity(items.get(i));
        }
        double avgFirstHalf = sumFirstHalf / halfSize;

        double sumSecondHalf = 0.0;
        int secondHalfCount = n - halfSize;
        for (int i = halfSize; i < n; i++) {
            sumSecondHalf += calculateWeightedPolarity(items.get(i));
        }
        double avgSecondHalf = sumSecondHalf / secondHalfCount;

        return avgSecondHalf - avgFirstHalf;
    }

    /**
     * Determina la tendencia de evolución emocional de la conversación (R11, R13, R14, R15).
     */
    public SentimentTrend calculateTrend(List<ProcessedSentimentDto> items) {
        if (items == null || items.size() < 2) {
            return SentimentTrend.STABLE;
        }

        double delta = calculatePolarityDelta(items);

        if (delta >= TREND_DELTA_THRESHOLD) {
            return SentimentTrend.IMPROVING;
        } else if (delta <= -TREND_DELTA_THRESHOLD) {
            return SentimentTrend.WORSENING;
        } else {
            return SentimentTrend.STABLE;
        }
    }

    /**
     * Evalúa las condiciones disyuntivas para determinar si se detectó frustración (R16..R19).
     */
    private boolean checkFrustration(
            List<ProcessedSentimentDto> items,
            BigDecimal frustrationScoreThreshold,
            double negativeRatioThreshold,
            int consecutiveNegativeThreshold) {

        if (items.isEmpty()) {
            return false;
        }

        int totalCount = items.size();
        int negativeOrFrustratedCount = 0;
        int currentConsecutive = 0;
        int maxConsecutive = 0;
        boolean hasFrustrationPeak = false;

        for (ProcessedSentimentDto item : items) {
            boolean isNegativeOrFrustrated = (item.label() == SentimentLabel.NEGATIVE || item.label() == SentimentLabel.FRUSTRATED);

            // Condición 1: Pico individual de frustración (R16)
            if (item.label() == SentimentLabel.FRUSTRATED && item.score().compareTo(frustrationScoreThreshold) >= 0) {
                hasFrustrationPeak = true;
            }

            // Contador para ratio acumulado (R17)
            if (isNegativeOrFrustrated) {
                negativeOrFrustratedCount++;
                currentConsecutive++;
                if (currentConsecutive > maxConsecutive) {
                    maxConsecutive = currentConsecutive;
                }
            } else {
                currentConsecutive = 0;
            }
        }

        // Criterio 1: Pico individual
        if (hasFrustrationPeak) {
            return true;
        }

        // Criterio 2: Ratio de negatividad acumulada
        double ratio = (double) negativeOrFrustratedCount / totalCount;
        if (ratio >= negativeRatioThreshold) {
            return true;
        }

        // Criterio 3: Mensajes negativos o frustrados consecutivos
        return maxConsecutive >= consecutiveNegativeThreshold;
    }

    private double calculateWeightedPolarity(ProcessedSentimentDto item) {
        double polarity = getScalarPolarity(item.label());
        double score = item.score().doubleValue();
        return polarity * score;
    }
}
