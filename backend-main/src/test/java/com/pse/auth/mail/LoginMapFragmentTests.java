package com.pse.auth.mail;

import com.pse.auth.location.LoginLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The map that goes in the login mail. Moved here with the code from
 * {@code LoginLocationServiceTests}, where it tested the HTML half of a class whose other
 * half looked up IP addresses.
 */
class LoginMapFragmentTests {

    private static final String GEOAPIFY_KEY = "geoapify-key";

    private static LoginMapFragment fragmentWith(String geoapifyKey) {
        return new LoginMapFragment(geoapifyKey);
    }

    /** Only the two fields the fragment reads; the description never reaches the map. */
    private static LoginLocation location(String mapsUrl, String coordinates) {
        return new LoginLocation("Karlsruhe, DE", mapsUrl, coordinates);
    }


    @Test
    void html_validCoordinatesAndAKey_rendersTheStaticImage() {
        // When
        String html = fragmentWith(GEOAPIFY_KEY).html(location("https://maps.example/x", "49.0069,8.4037"));

        // Then -- Geoapify takes lonlat, so the pair is emitted the other way round
        assertThat(html).contains("<img");
        assertThat(html).contains("center=lonlat:8.4037,49.0069");
        assertThat(html).contains("marker=lonlat:8.4037,49.0069");
        assertThat(html).contains("apiKey=" + GEOAPIFY_KEY);
    }

    @Test
    void html_blankGeoapifyKey_fallsBackToTheMapsButton() {
        // When
        String html = fragmentWith("").html(location("https://maps.example/x", "49.0069,8.4037"));

        // Then
        assertThat(html).doesNotContain("<img").contains("View on Google Maps");
    }

    @ParameterizedTest
    @CsvSource({
            "'49.0069,8.4037',true",
            "'-33.8,151.2',true",
            "'0,0',true",
            "'49.0, 8.4',false",
            "'not-coordinates',false",
            "'1000.0,1.0',false",
            "'49.0',false",
            "'',false"
    })
    void html_coordinateFormat_decidesBetweenImageAndButton(
            String coordinates, boolean rendersImage) {
        // When
        String html = fragmentWith(GEOAPIFY_KEY).html(location("https://maps.example/x", coordinates));

        // Then -- the regex rejects whitespace around the comma, which a real client may send
        assertThat(html.contains("<img")).isEqualTo(rendersImage);
    }

    @Test
    void html_noCoordinatesAndNoMapsUrl_rendersNothing() {
        // When
        String html = fragmentWith(GEOAPIFY_KEY).html(location(null, null));

        // Then -- the mail template must not gain an empty box
        assertThat(html).isEmpty();
    }

    @Test
    void html_mapsUrlContainingMarkup_escapesItIntoTheHtml() {
        // Given -- the value is interpolated straight into an href
        String hostile = "https://maps.example/?a=1&b=\"><script>alert(1)</script>";

        // When
        String html = fragmentWith(GEOAPIFY_KEY).html(location(hostile, null));

        // Then
        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;").contains("&amp;");
    }
}
