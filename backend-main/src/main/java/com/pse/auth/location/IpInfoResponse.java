package com.pse.auth.location;


/**
 * Represents IpInfoResponse.
 *
 * @param ip the ip
 * @param city the city
 * @param region the region
 * @param country the country
 * @param loc the loc
 */
public record IpInfoResponse(
        String ip,
        String city,
        String region,
        String country,
        String loc
) {
}
