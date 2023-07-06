package com.example.cache.user;

import com.example.cache.api.AbstractCacheRefreshStrategy;
import com.example.dto.UserDTO;
import com.example.repositories.UserRepository;
import com.example.services.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import static com.example.services.UserService.CACHE_MAP_CONVERTER;

public abstract class AbstractUsersCacheRefreshStrategy extends AbstractCacheRefreshStrategy<String, UserDTO, Long> {

    @Autowired
    IUserService userService;

    @Autowired
    UserRepository userRepository;

    @Override
    public String cacheIdentifierField() {
        return "id";
    }

    @Override
    public Boolean isEvictionFromExistingCacheRequired(UserDTO existingObject) {
        return Objects.nonNull(existingObject);
    }

    @Override
    public UserDTO getExistingObjectByIdentifier(Object id) {
        return userService.findById((Long) id);
    }

    @Override
    public JpaRepository jpaRepository() {
        return userRepository;
    }

    @Override
    public Long getMaxValueForIdentifier() {
        return Long.MAX_VALUE;
    }

    @Override
    public Map<Long, UserDTO> convertCollectionToMap(Collection elements) {
        return CACHE_MAP_CONVERTER.apply(elements);
    }
}
