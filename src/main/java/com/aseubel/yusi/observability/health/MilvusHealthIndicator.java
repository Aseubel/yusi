package com.aseubel.yusi.observability.health;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/** Read-only Milvus probe against the application-owned embedding collection. */
@Component("milvus")
@ConditionalOnBean(name = "milvusClientV2")
public class MilvusHealthIndicator implements HealthIndicator {

    private static final String COLLECTION = "yusi_embedding_collection";

    private final MilvusClientV2 milvusClient;

    public MilvusHealthIndicator(MilvusClientV2 milvusClient) {
        this.milvusClient = milvusClient;
    }

    @Override
    public Health health() {
        try {
            boolean available = Boolean.TRUE.equals(milvusClient.hasCollection(HasCollectionReq.builder()
                    .collectionName(COLLECTION)
                    .build()));
            Health.Builder builder = available ? Health.up() : Health.down();
            return builder
                    .withDetail("dependency", "milvus")
                    .withDetail("classification", available ? "available" : "unavailable")
                    .build();
        } catch (RuntimeException exception) {
            return Health.down()
                    .withDetail("dependency", "milvus")
                    .withDetail("classification", classify(exception))
                    .build();
        }
    }

    private String classify(RuntimeException exception) {
        String type = exception.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
        if (type.contains("timeout")) {
            return "timeout";
        }
        if (type.contains("connect") || type.contains("milvus")) {
            return "connection_failure";
        }
        return "unavailable";
    }
}
