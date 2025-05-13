package me.vacuity.chat.bot.chatbot.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @description:
 * @author: vacuity
 * @create: 2025-03-18 10:21
 **/

public class RateLimiter {
    // 用户ID -> 对应的令牌桶
    private final Map<String, TokenBucket> userBuckets = new ConcurrentHashMap<>();

    // 获取指定用户的令牌桶
    private TokenBucket getBucket(String userId) {
        // 修改为每3秒1个令牌
        return userBuckets.computeIfAbsent(userId, id -> new TokenBucket(1, 1, 3, TimeUnit.SECONDS));
    }

    // 判断用户是否可以发送消息
    public boolean allowMessage(String userId) {
        return getBucket(userId).tryConsume();
    }

    // 令牌桶实现
    private static class TokenBucket {
        private final int capacity;           // 桶容量
        private final double refillRate;      // 令牌生成速率 (tokens/ns)
        private double tokens;                // 当前令牌数量
        private long lastRefillTimestamp;     // 上次令牌补充时间

        public TokenBucket(int capacity, int refillTokens, long refillPeriod, TimeUnit timeUnit) {
            this.capacity = capacity;
            this.tokens = capacity;
            this.lastRefillTimestamp = System.nanoTime();

            // 计算每纳秒生成多少令牌
            long refillPeriodNanos = timeUnit.toNanos(refillPeriod);
            this.refillRate = (double) refillTokens / refillPeriodNanos;
        }

        // 尝试消费一个令牌
        public synchronized boolean tryConsume() {
            refill();

            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }

            return false;
        }

        // 补充令牌
        private void refill() {
            long now = System.nanoTime();
            long elapsed = now - lastRefillTimestamp;

            // 计算需要补充的令牌数量
            double tokensToAdd = elapsed * refillRate;
            if (tokensToAdd > 0) {
                tokens = Math.min(capacity, tokens + tokensToAdd);
                lastRefillTimestamp = now;
            }
        }
    }
}
