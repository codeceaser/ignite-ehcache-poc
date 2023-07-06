package com.example.conf;


import com.example.dto.UserDTO;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.*;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.kubernetes.TcpDiscoveryKubernetesIpFinder;
import org.apache.ignite.ssl.SslContextFactory;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.LinkedHashMap;


@Configuration
//@EnableCaching
@EnableAutoConfiguration
public class ApplicationConfiguration {



    @Bean
    @Primary
    @ConditionalOnMissingBean(javax.sql.DataSource.class)
    public javax.sql.DataSource dataSource(DataSourceProperties dataSourceProperties,
                                           @Value("${spring.datasource.tomcat.max-active}") Integer maxActive) {
        DataSource dataSource = new DataSource();
        dataSource.setDriverClassName(dataSourceProperties.getDriverClassName());
        dataSource.setUrl(dataSourceProperties.getUrl());
        dataSource.setUsername(dataSourceProperties.getUsername());
        dataSource.setPassword(dataSourceProperties.getPassword());
        dataSource.setMaxActive(maxActive);

        return dataSource;
    }

    @Bean
    public DataSourceInitializer dataSourceInitializer(final DataSource dataSource) {
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(new ResourceDatabasePopulator(new ClassPathResource("data.sql")));
        return initializer;
    }

    @Value("${ignite.kubernetes.service.name}")
    private String igniteServiceName;

    @Value("${ignite.cache.name}")
    private String cacheName;
    @Bean
    public Ignite igniteInstance() {
        IgniteConfiguration cfg = new IgniteConfiguration();

        // Enable peer-class loading feature.
//        cfg.setPeerClassLoadingEnabled(true);

        TcpDiscoverySpi spi = new TcpDiscoverySpi();
        TcpDiscoveryKubernetesIpFinder ipFinder = new TcpDiscoveryKubernetesIpFinder();

        // Set the Kubernetes service name of the Ignite nodes.
        ipFinder.setServiceName(igniteServiceName);
        spi.setIpFinder(ipFinder);

        cfg.setDiscoverySpi(spi);

        // Setting up SSL context for secure connection
        SslContextFactory sslContextFactory = new SslContextFactory();
        // Client does not need to know the keystore in one-way SSL/TLS
        // sslContextFactory.setKeyStoreFilePath("/path/to/keystore.jks");
        // sslContextFactory.setKeyStorePassword("changeit");
        // Truststore contains the server's certificate or the certificate of the CA that signed the server's certificate.
        sslContextFactory.setTrustStoreFilePath("/etc/truststore/user_service_truststore.jks");
        sslContextFactory.setTrustStorePassword("changeit".toCharArray());

        sslContextFactory.setKeyStoreFilePath("/etc/keystore/user_service_keystore.jks");
        sslContextFactory.setKeyStorePassword("changeit".toCharArray());
//        cfg.setSslContextFactory(sslContextFactory);
        cfg.setClientMode(true);

        // Start Ignite with these configurations
        Ignite ignite = Ignition.start(cfg);

        return ignite;
    }

    @Autowired
    private Ignite ignite;

    private IgniteCache<Long, UserDTO> cache;
    private CacheConfiguration<Long, UserDTO> cacheCfg;

    @PostConstruct
    public void init() {
        // Create the UserDTO cache configuration.
        cacheCfg = new CacheConfiguration<>();
        cacheCfg.setCacheMode(CacheMode.PARTITIONED);
        cacheCfg.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);
        cacheCfg.setName(cacheName);
        cacheCfg.setIndexedTypes(Long.class, UserDTO.class);

        // Define fields to be indexed.
        QueryEntity qryEntity = new QueryEntity();
        qryEntity.setKeyType(Long.class.getName());
        qryEntity.setValueType(UserDTO.class.getName());

        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("location", String.class.getName());
        fields.put("department", String.class.getName());
        qryEntity.setFields(fields);

        // Define which fields are part of the index.
        LinkedHashMap<String, Boolean> idxFields = new LinkedHashMap<>();
        idxFields.put("location", false);
        idxFields.put("department", false);
        qryEntity.setIndexes(Collections.singletonList(new QueryIndex(idxFields, QueryIndexType.SORTED)));

        // Add QueryEntity to the configuration.
        cacheCfg.setQueryEntities(Collections.singletonList(qryEntity));

        // Create cache with the provided configuration
        cache = ignite.getOrCreateCache(cacheCfg);

        // Add this cache configuration to the Ignite configuration.
//        cfg.setCacheConfiguration(cacheCfg);
    }

    @Bean
    public IgniteCache<Long, UserDTO> igniteCache() {
        return cache;
    }

    @Bean
    public CacheConfiguration<Long, UserDTO> cacheConfiguration() {
        return cacheCfg;
    }



}
