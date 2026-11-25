package sakila.demo.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Metriques metier (KPI section 6 du document) :
 *  - sakila_operation_total{operation,result}           : nombre d'operations, echecs, taux de succes
 *  - sakila_operation_duration_seconds{operation,result}: duree d'une operation (histogramme + p50/p95/p99)
 *  - sakila_operation_inflight{operation}               : traitements en cours (gauge)
 *  - sakila_records_processed_total{operation}          : volume de donnees traitees
 */
@Component
public class BusinessMetrics {

    private final MeterRegistry registry;
    private final Map<String, AtomicInteger> inFlight = new ConcurrentHashMap<>();

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Execute l'operation en mesurant duree, succes/echec et nombre de traitements en cours. */
    public <T> T track(String operation, Supplier<T> action) {
        AtomicInteger gauge = inFlight.computeIfAbsent(operation, op ->
                registry.gauge("sakila.operation.inflight", Tags.of("operation", op), new AtomicInteger()));
        gauge.incrementAndGet();
        Timer.Sample sample = Timer.start(registry);
        String result = "success";
        try {
            return action.get();
        } catch (RuntimeException | Error e) {
            result = "error";
            throw e;
        } finally {
            gauge.decrementAndGet();
            sample.stop(Timer.builder("sakila.operation.duration")
                    .description("Duree d'une operation metier")
                    .tags("operation", operation, "result", result)
                    .publishPercentileHistogram()
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(registry));
            Counter.builder("sakila.operation")
                    .description("Nombre d'operations metier")
                    .tags("operation", operation, "result", result)
                    .register(registry)
                    .increment();
        }
    }

    /** Volume d'enregistrements traites par une operation. */
    public void recordVolume(String operation, long records) {
        Counter.builder("sakila.records.processed")
                .description("Nombre d'enregistrements traites")
                .tag("operation", operation)
                .register(registry)
                .increment(records);
    }
}
