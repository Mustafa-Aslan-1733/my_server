package com.pse.auth.location;

/**
 * The {@code "latitude,longitude"} string IPinfo returns in its {@code loc} field.
 *
 * <p>Kept as a named type because two unrelated consumers read it and must agree on what
 * counts as usable: the Google Maps link built for the login mail, and the Geoapify static
 * map embedded in it. They used to share a private predicate on one class; splitting that
 * class would otherwise have meant two copies of the regex.
 */
public final class Coordinates {

    private Coordinates() {
    }

    /**
     * Whether this is a pair of numbers that can be put in a URL.
     *
     * @param coordinates the coordinates
     * @return the result
     */
    public static boolean isValid(String coordinates) {
        if (coordinates == null || coordinates.isBlank()) {
            return false;
        }
        return coordinates.matches("-?\\d{1,3}(\\.\\d+)?,-?\\d{1,3}(\\.\\d+)?");
    }

    /**
     * Only meaningful when {@link #isValid} said so.
     *
     * @param coordinates the coordinates
     * @return the result
     */
    public static String latitude(String coordinates) {
        return coordinates.split(",")[0].trim();
    }

    /**
     * Only meaningful when {@link #isValid} said so.
     *
     * @param coordinates the coordinates
     * @return the result
     */
    public static String longitude(String coordinates) {
        return coordinates.split(",")[1].trim();
    }
}
