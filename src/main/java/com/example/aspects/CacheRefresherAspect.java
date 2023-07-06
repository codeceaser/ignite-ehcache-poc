package com.example.aspects;

import com.example.annotations.FetchAndRefreshCache;
import com.example.annotations.RefreshCache;
import com.example.cache.api.CacheRefreshStrategy;
import com.example.components.ApplicationContextProvider;
import com.example.components.Cacheable;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.TriFunction;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Aspect
public class CacheRefresherAspect {

    public static final Logger LOGGER = LoggerFactory.getLogger(CacheRefresherAspect.class);

    @Pointcut("@annotation(com.example.annotations.RefreshCache)")
    public void refreshCacheAnnotateMethods(){}

    @Pointcut("@annotation(com.example.annotations.FetchAndRefreshCache)")
    public void fetchAndRefreshCacheAnnotationMethods(){}

    @Pointcut("within(com.example..services..*)")
    public void withinServiceLayer(){}

    public static final TriFunction<Class, MethodSignature, Class, Object> EXTRACT_ANNOTATION_METADATA = (aClass, signature, annotationClass) -> {
        final Method method = signature.getMethod();
        String methodName = signature.getName();
        try {
            return aClass.getDeclaredMethod(methodName, method.getParameterTypes()).getAnnotation(annotationClass);
        } catch (NoSuchMethodException e) {
            LOGGER.error("RefreshCache Configuration Error, Method {} is missing the annotation {}", methodName, annotationClass.getSimpleName());
        }
        return null;
    };

    @Around(value = "withinServiceLayer() && fetchAndRefreshCacheAnnotationMethods()")
    public Object fetchAndRefreshCache(ProceedingJoinPoint joinPoint) {
        Class<?> aClass = joinPoint.getTarget().getClass();
        String className = aClass.getSimpleName();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getName();
        LOGGER.info("Intercepting [{}#{}] for Refreshing the Cache", className, methodName);
        Object result = null;

        try{
            result = joinPoint.proceed();
        } catch (Throwable throwable) {
            LOGGER.error("Error while executing the method {}#{}", className, methodName, throwable);
            throw new RuntimeException(throwable);
        }

        List arguments = Optional.ofNullable(joinPoint.getArgs()).map(args -> Lists.newArrayList(args)).orElseGet(Lists::newArrayList);
        FetchAndRefreshCache fetchAndRefreshCache = (FetchAndRefreshCache) EXTRACT_ANNOTATION_METADATA.apply(aClass, signature, FetchAndRefreshCache.class);
        if(Objects.nonNull(fetchAndRefreshCache)) {
            CacheRefreshStrategy cacheRefreshStrategy = (CacheRefreshStrategy) ApplicationContextProvider.getBeanUsingQualifier(CacheRefreshStrategy.class, fetchAndRefreshCache.cacheName());
            if(Objects.nonNull(cacheRefreshStrategy)){
                LOGGER.info("Fetch and Refresh Cache Strategy found for {}", fetchAndRefreshCache.cacheName());
                Map refreshedElementsFromCache = cacheRefreshStrategy.findElementsAndRefreshCache(fetchAndRefreshCache.cacheName(), arguments, fetchAndRefreshCache.repositoryMethod());
                if (Map.class.isAssignableFrom(result.getClass())) {
                    ((Map) result).putAll(refreshedElementsFromCache);
                    LOGGER.info("Fetch And Refresh Strategy brought {} elements from the cache", refreshedElementsFromCache.size());
                }
            }
        } else {
            LOGGER.error("No Fetch and Refresh Cache Annotation Found");
        }
        return result;
    }

    @Around(value = "withinServiceLayer() && refreshCacheAnnotateMethods()")
    public Object refreshCaches(ProceedingJoinPoint joinPoint) {
        Object savedObject = null;
        Class<?> aClass = joinPoint.getTarget().getClass();
        String className = aClass.getSimpleName();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getName();
        LOGGER.info("Intercepting [{}#{}] for Refreshing the Cache", className, methodName);
        Object existingObject = null;
        RefreshCache refreshCache = (RefreshCache) EXTRACT_ANNOTATION_METADATA.apply(aClass, signature, RefreshCache.class);
        if (Objects.nonNull(refreshCache)) {
            Object id = null;
            if (StringUtils.equalsIgnoreCase("N", refreshCache.isDelete())) {
                Cacheable objectToBeSaved = Optional.ofNullable(joinPoint.getArgs()).map(args -> Lists.newArrayList(args)).orElseGet(Lists::newArrayList).stream().filter(arg -> Cacheable.class.isAssignableFrom(arg.getClass())).map(arg -> (Cacheable)arg).findFirst().orElseGet(() -> null);
                if (Objects.nonNull(objectToBeSaved) && Objects.nonNull(objectToBeSaved.getId())) {
                    id = objectToBeSaved.getId();
                } else {
                    LOGGER.error("No Cacheable Object is being Saved or it does not have the id");
                }
            } else {
                id = Optional.ofNullable(joinPoint.getArgs()).map(args -> Lists.newArrayList(args)).orElseGet(Lists::newArrayList).stream().findFirst().orElseGet(() -> null);
            }
            if (Objects.nonNull(id)) {
                for (String cacheName : refreshCache.cacheNames()) {
                    CacheRefreshStrategy cacheRefreshStrategy = (CacheRefreshStrategy) ApplicationContextProvider.getBeanUsingQualifier(CacheRefreshStrategy.class, cacheName);
                    if (Objects.isNull(existingObject) && Objects.nonNull(id)) {
                        existingObject = cacheRefreshStrategy.getExistingObjectByIdentifier(id);
                        break;
                    }
                }
            }
            if (Objects.nonNull(existingObject)) {
                LOGGER.info("Existing Object {} is found", existingObject);
            } else {
                LOGGER.info("No Existing Object is found with Id {}", id);
            }
        } else {
            LOGGER.error("No Refresh Cache Annotation Found");
        }

        try {
            savedObject = joinPoint.proceed();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        final Object existing = existingObject;
        final Object newer = savedObject;

        if (Objects.nonNull(refreshCache)) {
            /*Stream<CompletableFuture<Object>> futureStream = Arrays.stream(refreshCache.cacheNames()).map(cacheName -> supplyAsync(() -> {
                LOGGER.info("Refreshing the Cache : {}", cacheName);
                CacheRefreshStrategy cacheRefreshStrategy = (CacheRefreshStrategy) ApplicationContextProvider.getBeanUsingQualifier(CacheRefreshStrategy.class, cacheName);
                Object replacedObject = cacheRefreshStrategy.refreshCache(existing, newer, refreshCache.isDelete());
                LOGGER.info("Object {} replaced {} in Cache {}", newer, existing, cacheName);
                return replacedObject;
            })).map((future) -> future.whenComplete((evictedObject, exception) -> {
                if (Objects.nonNull(exception)) {
                    LOGGER.error("Error while refreshing cache ", exception);
                } else {
                    LOGGER.info("As a result of Cache Refresh, object {} was evicted", evictedObject);
                }
            }));
            futureStream.collect(Collectors.toList());*/
            for (String cacheName : refreshCache.cacheNames()) {
                LOGGER.info("Refreshing the Cache : {}", cacheName);
                CacheRefreshStrategy cacheRefreshStrategy = (CacheRefreshStrategy) ApplicationContextProvider.getBeanUsingQualifier(CacheRefreshStrategy.class, cacheName);
                Object replacedObject = cacheRefreshStrategy.refreshCache(existingObject, savedObject, refreshCache.isDelete());
                LOGGER.info("Object {} replaced {} in Cache {}", savedObject, existingObject, cacheName);
            }
        }

        return savedObject;
    }

}
