package com.example.services;

import com.example.annotations.FetchAndRefreshCache;
import com.example.annotations.RefreshCache;
import com.example.dto.UserDTO;
import com.example.entities.User;
import com.example.repositories.UserRepository;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.cache.Cache;
import javax.transaction.Transactional;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.example.cache.api.AbstractCacheRefreshStrategy.extractIgniteCache;
import static com.example.utils.AppConstants.*;

@Service
@Transactional
public class UserService implements IUserService{

    public static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    @Value("${indicator}")
    private String indicator;

    public static String prop1;

    @Value("${property1}")
    public void setProp1(String p1){
        prop1 = p1;
    }

    public String getProp1(){
        return prop1;
    }

//    @Autowired
//    IgniteCache<UserDTO, Long> userCache;

    @Autowired
    Ignite ignite;

    public static final Function<Collection<User>, Map<Long, UserDTO>> CACHE_MAP_CONVERTER = (users) -> {
        Map<Long, UserDTO> userMap = users.stream().map(UserDTO::new).collect(Collectors.toConcurrentMap(UserDTO::getId, Function.identity(), (existing, newer) -> newer));
        return userMap;
    };

    @Override
    public Collection<UserDTO> findAll() {
        LOGGER.info("Indicator Value is : {} and prop1 is: {}", indicator, prop1);
        return userRepository.findAll().stream().map(UserDTO::new).collect(Collectors.toList());
    }

    @Override
    @FetchAndRefreshCache(cacheName = USERS_BY_LOCATION, repositoryMethod = "findByLocationAndIdNotIn")
    public Map<Long, UserDTO> findByLocation(String location) {
        Map<Long, UserDTO> results = Maps.newHashMap();
        return results;
    }

    @Override
    public String printCaches(){
        Collection<String> cacheNames = Lists.newArrayList(ignite.cacheNames());
        if (CollectionUtils.isEmpty(cacheNames)) {
            cacheNames = Lists.newArrayList();
            cacheNames.add(USERS_BY_LOCATION);
            cacheNames.add(USERS_BY_DEPARTMENT);
            cacheNames.add(USERS_BY_LOCATION_AND_DEPARTMENT);
        }
        LOGGER.info("### Cache Names are: {} ###", cacheNames);

        cacheNames.stream().forEach(cacheName -> {
            LOGGER.info("### Cache Name is: {} ###", cacheName);
            Cache cache = ignite.getOrCreateCache(cacheName);
            if(cache != null){
                LOGGER.info("Size of Cache {} is: {}", cacheName, ((IgniteCache)cache).size());
            }
        });
        return "Ok";
    }

    @Override
    public String clearCaches(){
        Collection<String> cacheNames = Lists.newArrayList(ignite.cacheNames());
        if (CollectionUtils.isEmpty(cacheNames)) {
            cacheNames = Lists.newArrayList();
            cacheNames.add(USERS_BY_LOCATION);
            cacheNames.add(USERS_BY_DEPARTMENT);
            cacheNames.add(USERS_BY_LOCATION_AND_DEPARTMENT);
        }
        LOGGER.info("### Cache Names are: {} ###", cacheNames);

        cacheNames.stream().forEach(cacheName -> {
            LOGGER.info("### Cache Name is: {} ###", cacheName);
            Cache cache = ignite.getOrCreateCache(cacheName);
            if(cache != null){
                IgniteCache igniteCache = extractIgniteCache.apply(cache);
                igniteCache.clear();
                LOGGER.info("### Cache {} Cleared ###", cacheName);
            }
        });
        return "Ok";
    }

    @Override
    @FetchAndRefreshCache(cacheName = USERS_BY_DEPARTMENT, repositoryMethod = "findByDepartmentAndIdNotIn")
    public Map<Long, UserDTO> findByDepartment(String department) {
        Map<Long, UserDTO> results = Maps.newHashMap();
        return results;
    }

    @Override
    @FetchAndRefreshCache(cacheName = USERS_BY_LOCATION_AND_DEPARTMENT, repositoryMethod = "findByLocationAndDepartmentAndIdNotIn")
    public Map<Long, UserDTO> findByLocationAndDepartment(String location, String department) {
        Map<Long, UserDTO> results = Maps.newHashMap();
        return results;
    }

    @Override
    public UserDTO findById(Long id) {
        return userRepository.findById(id).map(UserDTO::new).orElseGet(() -> null);
    }

    @Override
    @RefreshCache(cacheNames = {USERS_BY_LOCATION, USERS_BY_DEPARTMENT, USERS_BY_LOCATION_AND_DEPARTMENT})
    public UserDTO save(User user) {
        UserDTO saved = new UserDTO(userRepository.save(user));
        return saved;
    }

    @Override
    @RefreshCache(cacheNames = {USERS_BY_LOCATION, USERS_BY_DEPARTMENT, USERS_BY_LOCATION_AND_DEPARTMENT})
    public UserDTO create(User user) {
        UserDTO saved = new UserDTO(userRepository.save(user));
        return saved;
    }

    @Override
    @RefreshCache(cacheNames = {USERS_BY_LOCATION, USERS_BY_DEPARTMENT, USERS_BY_LOCATION_AND_DEPARTMENT}, isDelete = "Y")
    public void deleteById(Long id) {
        userRepository.deleteById(id);
    }
}
