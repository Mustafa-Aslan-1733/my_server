package com.pse.auth.mail;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.pse.auth.location.Coordinates;
import com.pse.auth.location.LoginLocation;
import com.pse.shared.util.Html;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The map that goes in the "you signed in" mail: a rendered static image when Geoapify is
 * configured and the coordinates are usable, a plain "View on Google Maps" button otherwise,
 * and nothing at all when there is no location to show.
 *
 * <p>Split out of {@code LoginLocationService}, which resolved an IP to a place and also
 * returned an HTML fragment. Those are two jobs, and the second one was the only reason the
 * mail code depended on the location code at all.
 */
@Component
public class LoginMapFragment {

    private final String geoapifyApiKey;

    /**
     * Creates LoginMapFragment.
     *
     * @param geoapifyApiKey the geoapifyApiKey
     */
    public LoginMapFragment(@Value("${geoapify.api.key:}") String geoapifyApiKey) {
        this.geoapifyApiKey = geoapifyApiKey;
    }

    /**
     * Returns html.
     *
     * @param location the location
     * @return the result
     */
    public String html(LoginLocation location) {

        String mapsUrl = location.mapsUrl();
        String coordinates = location.coordinates();


        String imageUrl = createStaticMapUrl(coordinates);

        if (imageUrl == null) {
            return createMapsButton(mapsUrl);
        }

        String safeMapsUrl = Html.escape(mapsUrl, "");
        String safeImageUrl = Html.escape(imageUrl, "");

        return """
                <div style="
                    margin-top: 15px;
                    margin-bottom: 15px;
                    text-align: center;
                ">
                    <a
                        href="%s"
                        target="_blank"
                        rel="noopener noreferrer"
                    >
                        <img
                            src="%s"
                            alt="Approximate login location"
                            width="520"
                            style="
                                width: 100%%;
                                max-width: 520px;
                                border-radius: 8px;
                                border: 1px solid #dddddd;
                                display: block;
                            "
                        >
                    </a>

                    <p style="
                        margin-top: 6px;
                        font-size: 11px;
                        color: #888888;
                    ">
                        © OpenStreetMap contributors
                    </p>
                </div>
                """.formatted(
                safeMapsUrl,
                safeImageUrl
        );
    }

    private String createStaticMapUrl(String coordinates) {

        if (!Coordinates.isValid(coordinates) || geoapifyApiKey == null || geoapifyApiKey.isBlank()) {
            return null;
        }

        String latitude = Coordinates.latitude(coordinates);
        String longitude = Coordinates.longitude(coordinates);

        return "https://maps.geoapify.com/v1/staticmap"
                + "?style=osm-bright"
                + "&width=520"
                + "&height=280"
                + "&center=lonlat:"
                + longitude
                + ","
                + latitude
                + "&zoom=9"
                + "&marker=lonlat:"
                + longitude
                + ","
                + latitude
                + ";type:awesome;color:%23d32f2f"
                + "&apiKey="
                + URLEncoder.encode(
                geoapifyApiKey,
                StandardCharsets.UTF_8
        );
    }

    private String createMapsButton(String mapsUrl) {

        if (mapsUrl == null || mapsUrl.isBlank()) {
            return "";
        }

        return """
                <div style="
                    margin-top: 5px;
                    margin-bottom: 10px;
                ">
                    <a
                        href="%s"
                        target="_blank"
                        rel="noopener noreferrer"
                        style="
                            display: inline-block;
                            padding: 10px 16px;
                            background-color: #1a73e8;
                            color: #ffffff;
                            text-decoration: none;
                            font-size: 14px;
                            font-weight: bold;
                            border-radius: 6px;
                        "
                    >
                        View on Google Maps
                    </a>
                </div>
                """.formatted(Html.escape(mapsUrl, ""));
    }
}
