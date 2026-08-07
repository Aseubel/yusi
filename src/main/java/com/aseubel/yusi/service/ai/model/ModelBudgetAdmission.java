package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.config.ai.properties.ModelGatewayAdmissionProperties;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Atomically reserves and reconciles request/token buckets for each provider
 * attempt. The Redis scripts keep the check-and-increment operation atomic
 * across all configured dimensions, so concurrent requests cannot overbook a
 * bucket between a read and a write.
 */
@Slf4j
@Component
public class ModelBudgetAdmission {

    private static final String RESERVED = "RESERVED";
    private static final String SETTLED = "SETTLED";
    private static final String RELEASED = "RELEASED";

    private static final String RESERVE_SCRIPT = """
            if redis.call('EXISTS', KEYS[1]) == 1 then
              return -2
            end
            local windowSeconds = tonumber(ARGV[1])
            local reservationTtlSeconds = tonumber(ARGV[2])
            local argumentIndex = 3
            for index = 2, #KEYS do
              local limit = tonumber(ARGV[argumentIndex])
              local amount = tonumber(ARGV[argumentIndex + 1])
              local current = tonumber(redis.call('GET', KEYS[index]) or '0')
              if limit > 0 and current + amount > limit then
                return index - 1
              end
              argumentIndex = argumentIndex + 2
            end
            argumentIndex = 4
            for index = 2, #KEYS do
              local amount = tonumber(ARGV[argumentIndex])
              if amount > 0 then
                redis.call('INCRBY', KEYS[index], amount)
                redis.call('EXPIRE', KEYS[index], windowSeconds)
              end
              argumentIndex = argumentIndex + 2
            end
            redis.call('SET', KEYS[1], 'RESERVED', 'EX', reservationTtlSeconds)
            return 1
            """;

    private static final String SETTLE_SCRIPT = """
            if redis.call('GET', KEYS[1]) ~= 'RESERVED' then
              return 0
            end
            local windowSeconds = tonumber(ARGV[1])
            local argumentIndex = 2
            for index = 2, #KEYS do
              local reserved = tonumber(ARGV[argumentIndex])
              local actual = tonumber(ARGV[argumentIndex + 1])
              local delta = actual - reserved
              if delta > 0 then
                redis.call('INCRBY', KEYS[index], delta)
                redis.call('EXPIRE', KEYS[index], windowSeconds)
              elseif delta < 0 then
                local current = tonumber(redis.call('GET', KEYS[index]) or '0')
                local reduction = math.min(current, -delta)
                if reduction > 0 then
                  redis.call('INCRBY', KEYS[index], -reduction)
                end
              end
              argumentIndex = argumentIndex + 2
            end
            redis.call('SET', KEYS[1], ARGV[#ARGV - 1], 'EX', tonumber(ARGV[#ARGV]))
            return 1
            """;

    private final ModelGatewayAdmissionProperties properties;
    private final RedissonClient redissonClient;

    @Autowired
    public ModelBudgetAdmission(ModelGatewayAdmissionProperties properties, RedissonClient redissonClient) {
        this.properties = properties;
        this.redissonClient = redissonClient;
    }

    /**
     * Constructor for callers that intentionally disable external admission,
     * such as isolated unit tests.
     */
    public ModelBudgetAdmission() {
        this.properties = new ModelGatewayAdmissionProperties();
        this.redissonClient = null;
    }

    public ModelBudgetPermit reserve(ModelRouteContext context, ModelRouteCandidate candidate,
            ModelTokenBudget budget) {
        ModelTokenBudget safeBudget = budget == null ? new ModelTokenBudget(0L, 0L) : budget;
        if (!properties.hasConfiguredLimit()) {
            return ModelBudgetPermit.noop(safeBudget);
        }
        if (redissonClient == null) {
            return ModelBudgetPermit.denied("ADMISSION_STORE_UNAVAILABLE");
        }

        List<ModelBudgetPermit.Charge> charges = buildCharges(context, candidate, safeBudget);
        if (charges.isEmpty()) {
            return ModelBudgetPermit.noop(safeBudget);
        }

        String reservationKey = properties.getKeyPrefix() + "reservation:" + UUID.randomUUID();
        List<Object> keys = new ArrayList<>(charges.size() + 1);
        keys.add(reservationKey);
        charges.forEach(charge -> keys.add(charge.key()));
        List<Object> arguments = new ArrayList<>(2 + charges.size() * 2);
        arguments.add(properties.getWindowSeconds());
        arguments.add(properties.getReservationTtlSeconds());
        charges.forEach(charge -> {
            arguments.add(charge.limit());
            arguments.add(charge.reservedAmount());
        });

        try {
            Long result = eval(RESERVE_SCRIPT, keys, arguments);
            if (result == null || result == -2L) {
                return ModelBudgetPermit.denied("RESERVATION_CONFLICT");
            }
            if (result <= 0L) {
                int index = Math.max(0, result.intValue() - 1);
                String dimension = index < charges.size() ? charges.get(index).key() : "unknown";
                return ModelBudgetPermit.denied("LIMIT_EXCEEDED:" + dimension);
            }
            return new ModelBudgetPermit(reservationKey, charges, safeBudget.estimatedInputTokens(),
                    safeBudget.reservedOutputTokens(), true);
        } catch (RuntimeException exception) {
            log.warn("Model admission store failed for model={}: {}",
                    candidate == null ? null : candidate.modelId(), exception.getMessage());
            return ModelBudgetPermit.denied("ADMISSION_STORE_UNAVAILABLE");
        }
    }

    /**
     * Reconciles known provider usage. Missing usage is deliberately treated as
     * the original reservation, preventing a failed or interrupted call from
     * releasing tokens that may already have been consumed.
     */
    public void reconcile(ModelBudgetPermit permit, ModelUsageSnapshot usage) {
        settle(permit, usage, false);
    }

    /**
     * Releases a reservation only when the provider was never invoked.
     */
    public void release(ModelBudgetPermit permit) {
        settle(permit, null, true);
    }

    private void settle(ModelBudgetPermit permit, ModelUsageSnapshot usage, boolean release) {
        if (permit == null || !permit.granted() || permit.charges().isEmpty()
                || "noop".equals(permit.reservationKey())) {
            return;
        }
        long actualInput = usage == null || usage.inputTokens() == null
                ? permit.estimatedInputTokens() : Math.max(0L, usage.inputTokens());
        long actualOutput = usage == null || usage.outputTokens() == null
                ? permit.reservedOutputTokens() : Math.max(0L, usage.outputTokens());
        long actualTokens = release ? 0L : actualInput + actualOutput;

        List<Object> keys = new ArrayList<>(permit.charges().size() + 1);
        keys.add(permit.reservationKey());
        permit.charges().forEach(charge -> keys.add(charge.key()));
        List<Object> arguments = new ArrayList<>(2 + permit.charges().size() * 2);
        arguments.add(properties.getWindowSeconds());
        for (ModelBudgetPermit.Charge charge : permit.charges()) {
            long actual = switch (charge.type()) {
                case REQUEST -> release ? 0L : 1L;
                case TOKEN -> release ? 0L : actualTokens;
            };
            arguments.add(charge.reservedAmount());
            arguments.add(actual);
        }
        arguments.add(release ? RELEASED : SETTLED);
        arguments.add(properties.getReservationTtlSeconds());
        try {
            eval(SETTLE_SCRIPT, keys, arguments);
        } catch (RuntimeException exception) {
            // The reservation TTL remains the last-resort protection against a
            // process crash. Reconciliation can be retried by a caller later.
            log.warn("Failed to reconcile model admission reservation={}: {}",
                    permit.reservationKey(), exception.getMessage());
        }
    }

    private Long eval(String script, List<Object> keys, List<Object> arguments) {
        RScript redisScript = redissonClient.getScript(StringCodec.INSTANCE);
        return redisScript.eval(RScript.Mode.READ_WRITE, script, RScript.ReturnType.INTEGER,
                keys, arguments.toArray());
    }

    private List<ModelBudgetPermit.Charge> buildCharges(ModelRouteContext context,
            ModelRouteCandidate candidate, ModelTokenBudget budget) {
        List<ModelBudgetPermit.Charge> charges = new ArrayList<>();
        String window = String.valueOf(Instant.now().toEpochMilli()
                / Math.max(1L, properties.getWindowSeconds() * 1000L));
        addScopeCharges(charges, "user", value(context == null ? null : context.getUserId()),
                properties.getUser(), budget, window);
        addScopeCharges(charges, "tenant", value(context == null ? null : context.getTenantId()),
                properties.getTenant(), budget, window);
        addScopeCharges(charges, "model", candidate == null ? null : candidate.modelId(),
                properties.getModel(), budget, window);
        addScopeCharges(charges, "provider", candidate == null ? null : candidate.provider(),
                properties.getProvider(), budget, window);
        return charges;
    }

    private void addScopeCharges(List<ModelBudgetPermit.Charge> charges, String dimension, String value,
            ModelGatewayAdmissionProperties.ScopeLimit limit, ModelTokenBudget budget, String window) {
        if (value == null || value.isBlank() || limit == null
                || (limit.getMaxRequests() <= 0 && limit.getMaxTokens() <= 0)) {
            return;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        String prefix = properties.getKeyPrefix() + window + ":" + dimension + ":" + normalized;
        if (limit.getMaxRequests() > 0) {
            charges.add(new ModelBudgetPermit.Charge(prefix + ":requests", limit.getMaxRequests(),
                    1L, ModelBudgetPermit.ChargeType.REQUEST));
        }
        if (limit.getMaxTokens() > 0 && budget.totalTokens() > 0) {
            charges.add(new ModelBudgetPermit.Charge(prefix + ":tokens", limit.getMaxTokens(),
                    budget.totalTokens(), ModelBudgetPermit.ChargeType.TOKEN));
        }
    }

    private String value(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
