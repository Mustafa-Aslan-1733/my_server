package com.pse.auth.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.pse.auth.location.Coordinates;
import com.pse.auth.location.IpInfoResponse;
import com.pse.auth.location.LoginLocation;


/**
 * Resolves the approximate place a login came from, by asking IPinfo about the address.
 *
 * <p>It used to render the HTML map for the login mail as well. That half is
 * {@code LoginMapFragment} now: looking up where an address is and drawing a picture of it
 * are different jobs, and the second one was the only reason the mail code reached in here.
 */
@Service
public class LoginLocationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoginLocationService.class);

    private final RestClient ipInfoClient;
    private final String ipInfoToken;

    /**
     * Creates LoginLocationService.
     *
     * @param ipInfoRestClient the ipInfoRestClient
     * @param ipInfoToken the ipInfoToken
     */
    public LoginLocationService(
            RestClient ipInfoRestClient,
            @Value("${ipinfo.token:}") String ipInfoToken
    ) {
        this.ipInfoClient = ipInfoRestClient;
        this.ipInfoToken = ipInfoToken;
    }

    /**
     * Warns once at startup when no IPinfo token is configured. Without this the short
     * circuit in {@link #getLoginLocation} is silent, so an unset IPINFO_TOKEN turns every
     * lookup into "Unknown" and leaves nothing in the log to say why.
     */
    @PostConstruct
    void warnIfTokenMissing() {
        if (ipInfoToken.isBlank()) {
            LOGGER.warn("IPINFO_TOKEN is not set; every login location will resolve to Unknown");
        }
    }

    /**
     * Resolves an approximate location for an IP address.
     *
     * @param rawIp the rawIp
     * @return the result
     */
    public LoginLocation getLoginLocation(String rawIp) {

        LOGGER.info("Trying to determine location. rawIp={}", rawIp);

        String ip = normalizeIp(rawIp);

        if (ip == null || ipInfoToken.isBlank()) {
            return new LoginLocation("Unknown", null, null);
        }

        try {
            IpInfoResponse response = ipInfoClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{ip}/json")
                            .queryParam("token", ipInfoToken)
                            .build(ip)
                    )
                    .retrieve()
                    .body(IpInfoResponse.class);

            if (response == null) {
                return new LoginLocation("Unknown", null, null);
            }

            String description = createLocationDescription(response);

            String mapsUrl = createMapsUrl(response.loc());

            return new LoginLocation(description, mapsUrl, response.loc());

        } catch (RestClientResponseException exception) {
            // F-8: told apart from the two below on purpose. A 401 or 403 means the token is
            // there but no longer works -- the case the startup warning cannot catch, because
            // the token was fine when the application booted -- and a 429 means the quota ran
            // out. All three used to arrive as the same anonymous "could not determine".
            LOGGER.warn("IPinfo refused the lookup for IP {} with {}; "
                            + "the token may be revoked or out of quota",
                    ip, exception.getStatusCode(), exception);

            return new LoginLocation("Unknown", null, null);

        } catch (ResourceAccessException exception) {
            LOGGER.warn("IPinfo was unreachable for IP {} -- network or timeout", ip, exception);

            return new LoginLocation("Unknown", null, null);

        } catch (Exception exception) {
            LOGGER.warn("Could not determine location for IP {}", ip, exception);

            return new LoginLocation("Unknown", null, null);
        }
    }

    /**
     * Whether a location can be resolved at all. Reported through {@code GET /system/status}
     * so an operator can see that lookups are off rather than inferring it from every login
     * mail saying "Unknown" -- F-8. The startup warning covers a token that was missing at
     * boot; this covers the whole life of the process.
     *
     * @return the result
     */
    public boolean isConfigured() {
        return !ipInfoToken.isBlank();
    }

    /**
     * Creates a readable location such as:
     * Karlsruhe, Baden-Württemberg, DE
     */
    private String createLocationDescription(IpInfoResponse response) {

        StringBuilder location = new StringBuilder();

        appendLocationPart(location, response.city());
        appendLocationPart(location, response.region());
        appendLocationPart(location, response.country());

        if (location.isEmpty()) {
            return "Unknown";
        }

        return location.toString();
    }

    /**
     * Adds a non-empty value to the location description.
     */
    private void appendLocationPart(StringBuilder location, String value) {

        if (value == null || value.isBlank()) {
            return;
        }

        if (!location.isEmpty()) {
            location.append(", ");
        }

        location.append(value.trim());
    }

    /**
     * Creates a Google Maps URL from IPinfo coordinates.
     */
    private String createMapsUrl(String coordinates) {

        if (!Coordinates.isValid(coordinates)) {
            return null;
        }

        String encodedCoordinates = URLEncoder.encode(
                coordinates,
                StandardCharsets.UTF_8
        );

        return "https://www.google.com/maps/search/"
                + "?api=1&query="
                + encodedCoordinates;
    }

    /**
     * Problem rawIp = X-Forwarded-For: "203.0.113.42, 10.0.0.5"
     *
     * Handles X-Forwarded-For values containing multiple addresses.
     */
    private String normalizeIp(String rawIp) {

        if (rawIp == null || rawIp.isBlank()) {
            return null;
        }

        String ip = rawIp.split(",", -1)[0].trim();

        if (ip.isBlank()) {
            return null;
        }

        return ip;
    }

}
