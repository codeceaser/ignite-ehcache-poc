package com.example.cache.api;

import org.apache.commons.lang3.StringUtils;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;

/**
 *
 * @param <K>
 * @param <C>
 * @param <I>
 */
public abstract class AbstractCacheRefreshStrategy<K, C, I> implements CacheRefreshStrategy<K, C, I> {

    public static final Logger LOGGER = LoggerFactory.getLogger(AbstractCacheRefreshStrategy.class);

    @Value("${caching.solution}")
    private String cachingSolution;

    @Autowired
    CacheManager cacheManager;

    @Autowired(required = false)
    Ignite ignite;

    public static Function<Cache, IgniteCache> extractIgniteCache = (cache) -> (IgniteCache) cache;

    @Override
    public Cache createCacheIfAbsent(String cacheName) {
        LOGGER.info("@@@ - {} - If not present, creating cache with name: {}", cachingSolution, cacheName);
        if(StringUtils.equalsIgnoreCase("ignite", cachingSolution)){
            CacheConfiguration<I, C> cacheCfg = new CacheConfiguration<>();
            cacheCfg.setName(cacheName);
            cacheCfg.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);
            cacheCfg.setCacheMode(CacheMode.PARTITIONED);
            cacheCfg.setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(Duration.FIVE_MINUTES));
            IgniteCache<I, C> nativeCache = ignite.getOrCreateCache(cacheCfg);
            return nativeCache;
        } else if(StringUtils.equalsIgnoreCase("ehcache", cachingSolution)){
            MutableConfiguration<String, Map> mutableConfiguration = new MutableConfiguration<String, Map>()
                    .setTypes(String.class, Map.class)
                    .setStoreByValue(false)
                    .setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(Duration.FIVE_MINUTES));
            Cache<String, Map> jCache = cacheManager.createCache(cacheName, mutableConfiguration);
            return jCache;
        }
        return null;
    }
    @Override
    public Cache retrieveCache(String cacheName) {
        LOGGER.info("@@@ - {} - Retrieving cache with name: {}", cachingSolution, cacheName);
        if(StringUtils.equalsIgnoreCase("ignite", cachingSolution)){
            if(ignite.cacheNames().contains(cacheName)){
                return ignite.getOrCreateCache(cacheName);
            } else {
                return null;
            }
        } else if(StringUtils.equalsIgnoreCase("ehcache", cachingSolution)){
            return cacheManager.getCache(cacheName);
        }
        return null;
    }

    @Override
    public Cache extractNativeCache(Cache cache, Object keyForExistingCache) {
        if(StringUtils.equalsIgnoreCase("ignite", cachingSolution)){
            return extractIgniteCache.apply(cache);
        } else if(StringUtils.equalsIgnoreCase("ehcache", cachingSolution)){
            return cache;
        }
        return null;
    }

    @Override
    public Map extractCacheMapUsingKey(Cache cache, Object keyForExistingCache) {
        LOGGER.info("@@@ - {} - From Cache {}, Extracting cache map using key: {}", cachingSolution, cache.getName(), keyForExistingCache);
        Map cacheMap = (Map) cache.get(keyForExistingCache);
        if(StringUtils.equalsIgnoreCase("ignite", cachingSolution)){
            return cacheMap;
        } else if(StringUtils.equalsIgnoreCase("ehcache", cachingSolution)){
            return cacheMap;
        }
        return null;
    }

    @Override
    public Lock lockCache(Cache cache, Object keyForExistingCache) {
        if(StringUtils.equalsIgnoreCase("ignite", cachingSolution)){
            return extractIgniteCache.apply(cache).lock(keyForExistingCache);
        } else if(StringUtils.equalsIgnoreCase("ehcache", cachingSolution)){
            return null;
        }
        return null;
    }

    @Override
    public void cacheSpecificEntryReplacement(Cache cache, Object keyForExistingCache, Map cacheMap) {
        LOGGER.info("@@@ - {} - For Cache {}, Replacing cache map using key: {}", cachingSolution, cache.getName(), keyForExistingCache);
        if(StringUtils.equalsIgnoreCase("ignite", cachingSolution)){
            extractIgniteCache.apply(cache).replace(keyForExistingCache, cacheMap);
        } else if(StringUtils.equalsIgnoreCase("ehcache", cachingSolution)){
            cache.put(keyForExistingCache, cacheMap);
        }
    }

    @Override
    public void cacheSpecificEntryCreation(Cache cache, Object keyForCacheEntry, Map cacheMap) {
        LOGGER.info("@@@ - {} - For Cache {}, Creating cache map using key: {}", cachingSolution, cache.getName(), keyForCacheEntry);
        if(StringUtils.equalsIgnoreCase("ignite", cachingSolution)){
            extractIgniteCache.apply(cache).put(keyForCacheEntry, cacheMap);
        } else if(StringUtils.equalsIgnoreCase("ehcache", cachingSolution)){
            cache.put(keyForCacheEntry, cacheMap);
        }
    }

    @Override
    public boolean cacheSpecificEntryCheck(Cache cache, Object keyForCacheEntry) {
        LOGGER.info("@@@ - {} - For Cache {}, Checking cache map using key: {}", cachingSolution, cache.getName(), keyForCacheEntry);
        if(StringUtils.equalsIgnoreCase("ignite", cachingSolution)){
            return extractIgniteCache.apply(cache).containsKey(keyForCacheEntry);
        } else if(StringUtils.equalsIgnoreCase("ehcache", cachingSolution)){
            return cache.containsKey(keyForCacheEntry);
        }
        return false;
    }

    @Override
    public final C removeObjectFromCache(C existingObject, Cache cachesPresent) {
        return CacheRefreshStrategy.super.removeObjectFromCache(existingObject, cachesPresent);
    }

    @Override
    public final C processEvictionFromExistingCache(C existingObject, C newerObject, Cache cachesPresent) {
        return CacheRefreshStrategy.super.processEvictionFromExistingCache(existingObject, newerObject, cachesPresent);
    }

    @Override
    public final C updateCache(C newerObject, Cache cachesPresent) {
        return CacheRefreshStrategy.super.updateCache(newerObject, cachesPresent);
    }

    @Override
    public final Map<I, C> findElementsAndRefreshCache(String cacheName, List arguments, String findMethodName) {
        return CacheRefreshStrategy.super.findElementsAndRefreshCache(cacheName, arguments, findMethodName);
    }
}
