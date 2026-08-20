package com.aseubel.yusi.config;

import com.aseubel.yusi.observability.task.TaskHealthRegistry;
import com.aseubel.yusi.observability.alert.AlertEvaluator;
import com.aseubel.yusi.observability.alert.AlertPolicy;
import com.aseubel.yusi.observability.alert.AlertStateStore;
import com.aseubel.yusi.observability.alert.FeishuAlertNotifier;
import com.aseubel.yusi.observability.alert.FeishuAlertProperties;
import com.aseubel.yusi.observability.alert.InMemoryAlertStateStore;
import com.aseubel.yusi.observability.task.TaskScheduleCatalog;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Infrastructure wiring for low-sensitivity health state. */
@Configuration(proxyBeanMethods = false)
public class ObservabilityConfig {

    @Bean
    @ConditionalOnMissingBean
    public TaskHealthRegistry taskHealthRegistry() {
        return new TaskHealthRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskScheduleCatalog taskScheduleCatalog() {
        return new TaskScheduleCatalog();
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertPolicy alertPolicy() {
        return AlertPolicy.initial();
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock alertClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertEvaluator alertEvaluator(AlertPolicy policy, Clock alertClock) {
        return new AlertEvaluator(policy, alertClock);
    }

    @Bean
    @Profile("test")
    @ConditionalOnMissingBean
    public AlertStateStore alertStateStore() {
        return new InMemoryAlertStateStore(256);
    }

    @Bean
    @ConditionalOnMissingBean
    public FeishuAlertProperties feishuAlertProperties() {
        return FeishuAlertProperties.fromEnvironment(System::getenv);
    }

    @Bean(name = "alertExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "alertExecutor")
    public ThreadPoolExecutor alertExecutor() {
        return new ThreadPoolExecutor(1, 1, 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(32), new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    @org.springframework.context.annotation.Profile("!test")
    @ConditionalOnMissingBean
    public FeishuAlertNotifier.WebhookClient feishuWebhookClient() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
        return request -> {
            try {
                HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(request.destination()))
                        .timeout(java.time.Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .header("X-Lark-Signature", request.signature())
                        .POST(HttpRequest.BodyPublishers.ofString(request.payload()))
                        .build();
                HttpResponse<Void> response = client.send(httpRequest, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("http_failure");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("delivery_interrupted");
            } catch (java.io.IOException | RuntimeException exception) {
                throw new IllegalStateException("delivery_failed");
            }
        };
    }

    @Bean
    @org.springframework.context.annotation.Profile("test")
    @ConditionalOnMissingBean
    public FeishuAlertNotifier.WebhookClient testFeishuWebhookClient() {
        return request -> {
            // Test profile proves the request contract without network delivery.
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public FeishuAlertNotifier feishuAlertNotifier(FeishuAlertProperties properties,
            FeishuAlertNotifier.WebhookClient client, @Qualifier("alertExecutor") Executor executor,
            Clock alertClock) {
        return new FeishuAlertNotifier(properties, client, executor, alertClock);
    }
}
