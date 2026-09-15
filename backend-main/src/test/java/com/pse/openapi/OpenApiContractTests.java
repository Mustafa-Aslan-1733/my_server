package com.pse.openapi;

import com.pse.auth.repository.TokenRepository;
import com.pse.moderation.repository.AdminRepository;
import com.pse.support.AdminSessions;
import com.pse.support.TestDeliveryConfig;
import com.pse.support.TestGitLabConfig;
import com.pse.user.repository.StudentRepository;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.pse.support.ApiIntegrationTest;

/**
 * The generated OpenAPI schema, checked against the API it claims to describe.
 *
 * <p>A schema generated from the code cannot drift the way a hand-written one does, but it
 * can be <em>incomplete</em> in ways nobody notices: a controller springdoc skips, a route
 * whose annotations it cannot read. The failure mode is a client generated from the schema
 * that silently lacks an endpoint. So this asks the same question the route sweeps ask, in
 * the same way -- read the truth from Spring's own mapping, and require the schema to cover
 * it -- rather than sampling a few paths.
 */
@ApiIntegrationTest
class OpenApiContractTests {

    private static final String ADMIN_EMAIL = "openapi-admin@student.kit.edu";

    @Autowired MockMvc mockMvc;
    @Autowired RequestMappingHandlerMapping handlerMapping;
    @Autowired StudentRepository studentRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired TokenRepository tokenRepository;

    private String adminToken;

    @BeforeEach
    void createAdminSession() {
        adminToken = AdminSessions.createAdmin(
                ADMIN_EMAIL, studentRepository, adminRepository, tokenRepository);
    }

    /**
     * The schema describes the whole administrative surface, so it is admin-tier rather than
     * public. Adding springdoc served it to anonymous callers until {@code SecurityConfig}
     * named it -- the chain ends in {@code permitAll()} -- and all three forms have to be
     * named, because a matcher for one path is not a matcher for another that merely looks
     * similar.
     */
    @Test
    void theSchemaIsAdminTierInEveryFormItIsServedIn() throws Exception {
        for (String path : Set.of("/v3/api-docs", "/v3/api-docs.yaml")) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get(path).header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }
    }

    /**
     * Every route the application maps has to appear in the schema, with the verb it maps.
     *
     * <p>Read from {@code RequestMappingHandlerMapping} rather than from a list, for the
     * reason the authorization sweep gives: a list maintained by a person goes stale in
     * silence and then passes because it is looking at less than it thinks.
     */
    @Test
    void everyMappedRouteAppearsInTheSchema() throws Exception {
        OpenAPI schema = fetchSchema();
        Set<String> documented = documentedOperations(schema);

        Set<String> missing = new TreeSet<>();
        for (String route : mappedRoutes()) {
            if (!documented.contains(route)) {
                missing.add(route);
            }
        }

        assertThat(missing.isEmpty()).as("these routes are mapped by the application but absent from the generated "
                        + "schema, so a client generated from it would not know they exist: "
                        + missing).isTrue();
    }

    /**
     * And the reverse, which is the direction that misleads a client rather than
     * shortchanging it: a schema entry for a route that no longer exists sends a generated
     * client at a 404 it was told to expect a body from.
     */
    @Test
    void theSchemaDescribesNoRouteTheApplicationDoesNotMap() throws Exception {
        OpenAPI schema = fetchSchema();
        Set<String> mapped = mappedRoutes();

        Set<String> phantom = new TreeSet<>();
        for (String operation : documentedOperations(schema)) {
            if (!mapped.contains(operation)) {
                phantom.add(operation);
            }
        }

        assertThat(phantom.isEmpty())
                .as("the schema describes routes the application does not map: " + phantom)
                .isTrue();
    }

    /** The version matters to every tool that reads this, so it is asserted rather than assumed. */
    @Test
    void theSchemaDeclaresTheOpenApiVersionItIsGeneratedAs() throws Exception {
        assertThat(fetchSchema().getOpenapi()).isEqualTo("3.1.0");
    }

    /**
     * The schema as a typed model rather than as nested maps.
     *
     * <p>swagger-parser is already on the test classpath -- it arrives under the OpenAPI
     * response validator -- so walking the document by casting {@code Map<String, Object>}
     * was rebuilding, less safely, something that was sitting there unused.
     */
    private OpenAPI fetchSchema() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        SwaggerParseResult parsed = new OpenAPIParser().readContents(body, null, null);
        assertThat(parsed.getOpenAPI())
                .as("the served document did not parse as OpenAPI: " + parsed.getMessages())
                .isNotNull();
        return parsed.getOpenAPI();
    }

    /** {@code "GET /health"} for every operation the schema declares. */
    private static Set<String> documentedOperations(OpenAPI schema) {
        Set<String> operations = new TreeSet<>();
        schema.getPaths().forEach((path, item) ->
                item.readOperationsMap().keySet().forEach(verb ->
                        operations.add(verb.name() + " " + path)));
        return operations;
    }

    /** The same shape, read from the mapping. Only this project's own controllers. */
    private Set<String> mappedRoutes() {
        Set<String> routes = new TreeSet<>();
        handlerMapping.getHandlerMethods().forEach((info, handler) -> {
            if (!handler.getBeanType().getPackageName().startsWith("com.pse")) {
                return;
            }
            for (String pattern : info.getPatternValues()) {
                for (var method : info.getMethodsCondition().getMethods()) {
                    routes.add(method.name() + " " + pattern);
                }
            }
        });
        return routes;
    }
}
