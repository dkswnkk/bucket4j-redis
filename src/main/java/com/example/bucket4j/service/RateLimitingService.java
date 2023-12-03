package com.example.bucket4j.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Slf4j
public class RateLimitingService {

    private static final int CAPACITY = 3;
    private static final int REFILL_AMOUNT = 3;
    private static final Duration REFILL_DURATION = Duration.ofSeconds(5);

    private final LettuceBasedProxyManager<String> proxyManager;
    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitingService(RedisClient redisClient) {
        StatefulRedisConnection<String, byte[]> redisConnection = redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
        this.proxyManager = LettuceBasedProxyManager.builderFor(redisConnection)
                .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(10)))
                .build();
    }

    private Bucket getOrCreateBucket(String apiKey) {
        return buckets.computeIfAbsent(apiKey, key -> {
            BucketConfiguration configuration = createBucketConfiguration();
            return proxyManager.builder().build(key, configuration);
        });
    }

    private BucketConfiguration createBucketConfiguration() {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder().capacity(CAPACITY).refillIntervally(REFILL_AMOUNT, REFILL_DURATION).build())
                .build();
    }

    public boolean consume(String apiKey) {
        Bucket bucket = getOrCreateBucket(apiKey);
        boolean consumed = bucket.tryConsume(1);
        log.info("API Key: {}, Consumed: {}, time{}", apiKey, consumed, LocalDateTime.now());
        return consumed;
    }

    public String run() {
        if (consume("test")) {
            return "요청 성공";
        } else {
            return "요청 한도 초과";
        }
    }
}