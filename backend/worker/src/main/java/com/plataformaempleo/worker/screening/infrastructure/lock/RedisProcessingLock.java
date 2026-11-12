package com.plataformaempleo.worker.screening.infrastructure.lock;

import com.plataformaempleo.worker.screening.domain.port.out.ProcessingLock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Bloqueo en Redis con {@code SET NX PX}. Cada adquisición lleva un token propio y se libera con un
 * script que solo borra si el token coincide: si el bloqueo caducó y lo tomó otra réplica, no se le quita.
 */
public class RedisProcessingLock implements ProcessingLock {

    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;
    private final Map<String, String> tokens = new ConcurrentHashMap<>();

    public RedisProcessingLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryAcquire(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(lockKey(key), token, ttl);
        if (Boolean.TRUE.equals(acquired)) {
            tokens.put(key, token);
            return true;
        }
        return false;
    }

    @Override
    public void release(String key) {
        String token = tokens.remove(key);
        if (token != null) {
            redis.execute(RELEASE, List.of(lockKey(key)), token);
        }
    }

    private static String lockKey(String key) {
        return "worksy:lock:" + key;
    }
}
