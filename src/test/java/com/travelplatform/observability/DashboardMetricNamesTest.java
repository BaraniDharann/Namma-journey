package com.travelplatform.observability;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A dashboard panel or alert that queries a metric name the app never exports is silently empty
 * forever. That happened: the counter "namma.bookings.created" is exported by Prometheus as
 * namma_bookings_total ("_created" is a reserved OpenMetrics suffix), so the panel built on
 * namma_bookings_created_total showed nothing - caught only by the load test.
 *
 * <p>This registers every counter name the code declares in a real PrometheusMeterRegistry and
 * checks that each namma_* series the dashboard and alert rules query is really produced.
 */
class DashboardMetricNamesTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test
    @DisplayName("every namma_* series the dashboard and alerts query is exported under that exact name")
    void dashboardAndAlertsMatchExportedNames() throws IOException {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        Set<String> declared = counterNamesInSource();
        assertFalse(declared.isEmpty(), "found no Metrics.counter(...) declarations to check");
        declared.forEach(name -> registry.counter(name).increment());
        String scrape = registry.scrape();

        Set<String> queried = new TreeSet<>();
        queried.addAll(seriesIn(ROOT.resolve("monitoring/grafana/dashboards/namma-journey.json")));
        queried.addAll(seriesIn(ROOT.resolve("monitoring/prometheus/alerts.yml")));
        assertFalse(queried.isEmpty(), "dashboard and alerts reference no namma_* metrics?");

        for (String series : queried) {
            assertTrue(scrape.contains(series + "{") || scrape.contains(series + " "),
                    series + " is queried by the dashboard/alerts but the app exports no such series. "
                            + "Declared counters: " + declared);
        }
    }

    /** String literals passed to Metrics.counter(...) anywhere under src/main/java. */
    private static Set<String> counterNamesInSource() throws IOException {
        Pattern call = Pattern.compile("Metrics\\.counter\\(\\s*\"([a-z0-9_.]+)\"");
        Set<String> names = new TreeSet<>();
        try (Stream<Path> files = Files.walk(ROOT.resolve("src/main/java"))) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                Matcher m = call.matcher(Files.readString(file));
                while (m.find()) {
                    names.add(m.group(1));
                }
            }
        }
        return names;
    }

    private static Set<String> seriesIn(Path file) throws IOException {
        Matcher m = Pattern.compile("namma_[a-z0-9_]+").matcher(Files.readString(file));
        Set<String> series = new TreeSet<>();
        while (m.find()) {
            series.add(m.group());
        }
        return series;
    }
}
