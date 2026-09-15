package com.pse.auth.location;



/**
 * Represents LoginLocation.
 *
 * @param description the description
 * @param mapsUrl the mapsUrl
 * @param coordinates the coordinates
 */
public record LoginLocation(
        String description,
        String mapsUrl,
        String coordinates
)
{ }
