package me.vacuity.chat.bot.chatbot.dto;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class CaffeineFixedSizeListMap<K, V> {
    private final int maxSize;
    private final Cache<K, ConcurrentLinkedQueue<V>> cache;

    public CaffeineFixedSizeListMap(int maxSize) {
        this.maxSize = maxSize;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(12)) // 写入12小时后过期
                .maximumSize(1000) // 最多缓存1000个key
                .recordStats() // 记录统计信息
                .build();
    }

    public void add(K key, V value) {
        ConcurrentLinkedQueue<V> queue = cache.get(key, k -> new ConcurrentLinkedQueue<>());

        synchronized (queue) {
            if (queue.size() >= maxSize) {
                queue.poll(); // 移除最早的元素
            }
            queue.offer(value);
        }
    }

    public void add(K key, V value, int specialMaxSize) {
        ConcurrentLinkedQueue<V> queue = cache.get(key, k -> new ConcurrentLinkedQueue<>());

        synchronized (queue) {
            if (queue.size() >= specialMaxSize) {
                queue.poll(); // 移除最早的元素
            }
            queue.offer(value);
        }
    }

    public List<V> get(K key) {
        ConcurrentLinkedQueue<V> queue = cache.getIfPresent(key);
        if (queue == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(queue);
    }

    public void remove(K key) {
        cache.invalidate(key);
    }

    public boolean containsKey(K key) {
        return cache.getIfPresent(key) != null;
    }

    public void clear() {
        cache.invalidateAll();
    }
}
