package com.netdisk.service.chat;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class ChatCacheIndexService {
    private static final long CACHE_INDEX_TTL_SECONDS = 86400L;

    private final StringRedisTemplate redisTemplate;

    public ChatCacheIndexService(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redisTemplate = redisProvider.getIfAvailable();
    }

    public void putCacheValue(String key, String value, long ttlSeconds, Collection<String> indexKeys) {
        if (redisTemplate == null || key == null || key.trim().isEmpty() || value == null || ttlSeconds <= 0L) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
            if (indexKeys == null) {
                return;
            }
            for (String indexKey : indexKeys) {
                if (indexKey == null || indexKey.trim().isEmpty()) {
                    continue;
                }
                redisTemplate.opsForSet().add(indexKey, key);
                redisTemplate.expire(indexKey, CACHE_INDEX_TTL_SECONDS, TimeUnit.SECONDS);
            }
        } catch (Exception ignore) {
        }
    }

    public void evictByIndex(String indexKey) {
        if (redisTemplate == null || indexKey == null || indexKey.trim().isEmpty()) {
            return;
        }
        try {
            Set<String> keys = redisTemplate.opsForSet().members(indexKey);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            redisTemplate.delete(indexKey);
        } catch (Exception ignore) {
        }
    }

    public void evictByIndexes(Collection<String> indexKeys) {
        if (indexKeys == null || indexKeys.isEmpty()) {
            return;
        }
        Set<String> uniq = new LinkedHashSet<String>(indexKeys);
        for (String indexKey : uniq) {
            evictByIndex(indexKey);
        }
    }

    public String userListIndexKey(Long userId) {
        return userId == null ? "" : "chat:cache:index:user:" + userId + ":list";
    }

    public String conversationUserIndexKey(Long userId, String conversationUuid) {
        if (userId == null || conversationUuid == null || conversationUuid.trim().isEmpty()) {
            return "";
        }
        return "chat:cache:index:conv:" + conversationUuid.trim() + ":user:" + userId;
    }

    public List<String> singleIndex(String indexKey) {
        if (indexKey == null || indexKey.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.singletonList(indexKey);
    }
}
