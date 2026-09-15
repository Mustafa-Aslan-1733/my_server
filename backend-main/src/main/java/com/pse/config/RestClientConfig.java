package com.pse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * The IPinfo client, assembled here rather than inside the service that uses it so the
 * geolocation lookup can be exercised without reaching the network.
 *
 * <p>Built explicitly instead of injecting a {@code RestClient.Builder}: Spring Boot only
 * auto-configures that builder when {@code spring-boot-restclient} is on the classpath, and
 * it is not — {@code spring-boot-starter-webmvc} does not pull it in.
 */
@Configuration
public class RestClientConfig {

    @Bean
    RestClient ipInfoRestClient() {
        return RestClient.builder()
                .baseUrl("https://ipinfo.io")
                .build();
    }
}
