
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;


/**
 * A historical manual client. <b>Not part of the product, and not part of the delivered
 * application.</b>
 *
 * <p>It sits in a directory outside every Maven source root, so it is never compiled, never
 * packaged and never copied into the image -- {@code src/Dockerfile} takes {@code src} and
 * nothing else. It was run by hand with {@code java testclient/TestClient.java} while the API
 * was being built, and it is kept because it records how the endpoints were exercised before
 * there was a test suite.
 *
 * <p>Read the rest of this file as a record rather than as instructions. Three things in it are
 * out of date and are deliberately not being rewritten, because rewriting a record destroys it:
 *
 * <ul>
 *   <li>The note below about {@code 193.196.38.181:8080} describes a problem somebody hit
 *       months ago. It has not been true since; the field further down points at the bw-cloud
 *       host over HTTPS.</li>
 *   <li>{@code SERVER_IP} is a hard-coded default with no way to override it, and it names the
 *       bw-cloud hostname rather than {@code https://ratemyprofessor.dev}, which is the
 *       address everything else uses.</li>
 *   <li>{@code main} calls only {@code sendBugReport}. The other three methods, and the two KIT
 *       addresses baked into them, are unreachable from it.</li>
 * </ul>
 *
 * <p>The bearer token is the one thing here that was corrected rather than recorded: it is read
 * from the environment now. See the field's own note for why.
 *
 * <p>Original note follows.
 *
 * <p>Problem: Server currently doesnt allow acces to 193.196.38.181:8080
 * <br>Solution: Server does allow connection to localhost:8080
 *
 * <p>This class simulates a user who wants to login using the token based login.
 *
 * <p>WARNING: Token should be unique, therefore creating two identical tokens leads to an
 * exception.
 */
public class TestClient {



    private static final String SERVER_IP = "https://8a1babdc-cf6d-4fdc-80a7-dc585f5853ed.ka.bw-cloud-instance.org";

    private static final HttpClient client = HttpClient.newHttpClient();

    /**
     * Read from the environment, never written here. A bearer token committed to a tracked
     * file is a credential published to everyone who can clone the repository, and it stays
     * in the history after it is edited out -- so the one that used to sit on this line has
     * to be revoked server-side rather than merely deleted.
     *
     * <p>Run with: {@code TEST_CLIENT_TOKEN=<token> java testclient/TestClient.java}
     */
    private static String authToken = System.getenv("TEST_CLIENT_TOKEN");



    public static void main(String[] args) throws Exception {


        sendBugReport();


    }

    public static void sendBugReport() throws Exception {

        String json = """
        {
            "title": "Test Bug Report",
            "description": "Das ist ein Test-Bugreport vom TestClient.",
            "severity": "LOW"
        }
        """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SERVER_IP + "/reports"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("SEND BUG REPORT STATUS: " + response.statusCode());
        System.out.println(response.body());
    }

    /**
     * Requests all ratings of the currently logged-in user.
     */
    public static void getRatings() throws Exception {

        String json = """
            {
                "email": "uabcd@student.kit.edu"
            }
            """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SERVER_IP + "/account/ratings"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("RATINGS STATUS: " + response.statusCode());
        System.out.println(response.body());
    }


    /**
     * Sends email to Server and prints its answer
     */
    public static void requestLogin() throws Exception {

        String json = """
                {
                    "email": "ugjlu@student.kit.edu"
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SERVER_IP + "/auth/request-login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response
                = client.send(request, HttpResponse.BodyHandlers.ofString());


        System.out.println("REQUEST LOGIN STATUS: " + response.statusCode());
        System.out.println(response.body());

        
    }

    /**
     * Sends email and token to server and hopes that the Server wirtes the JSESSIONID in its cookie.
     */
    public static void login(String email, String token) throws Exception {

        String json = """
            {

                "email": "%s",
                "loginToken": "%s"
            }
            """.formatted(email, token);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SERVER_IP + "/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response
                = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("AUTH LOGIN STATUS: " + response.statusCode());
        System.out.println(response.body());

        if (!response.body().contains("\"success\":true")) {
            System.out.println("Login failed, no auth token.");
            return;
        }

        
        authToken = extractAuthToken(response.body());

        System.out.println("AUTH TOKEN:");
        System.out.println(authToken);
    }

    /**
     * Very simple JSON extraction.
     *
     * Expected response:
     * {
     *   "message": "Login successful",
     *   "success": true,
     *   "authToken": "abc..."
     * }
     */
    private static String extractAuthToken(String json) {

        String key = "\"authToken\":\"";
        int start = json.indexOf(key);

        if (start == -1) {
            throw new IllegalStateException("No authToken found in response: " + json);
        }

        start += key.length();

        int end = json.indexOf("\"", start);

        if (end == -1) {
            throw new IllegalStateException("Invalid authToken JSON: " + json);
        }

        return json.substring(start, end);
    }
}
