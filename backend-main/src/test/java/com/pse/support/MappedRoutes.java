package com.pse.support;

import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;

/**
 * Every route Spring has actually mapped, read from {@link RequestMappingHandlerMapping}.
 *
 * <p>The rule this suite works to is that a sweep reads the route list from Spring's own
 * mapping and never from a hand-kept list, because a hand-kept list goes stale in silence and
 * then passes while looking at less than it thinks. Three classes each grew their own copy of
 * the same walk to obey it -- {@code ApiAuthorizationMatrixTests},
 * {@code ApiOwnershipMatrixTests} and {@code ApiProtocolContractTests} -- and a fourth was
 * about to. P-4 in {@code docs/test-findings.md} is what that costs: several places state the
 * same thing and nothing says which to edit when it changes.
 *
 * <p><b>A mapping that declares no verb answers all of them,</b> and the three callers disagree
 * about what to do with that: the authorization sweep probes it as a GET, the other two skip
 * it. So it is reported rather than decided here -- such a route arrives with a {@code null}
 * verb, and every call site has to say which it means.
 */
public final class MappedRoutes {

    private MappedRoutes() {
    }

    /**
     * One mapped route.
     *
     * @param pattern the path pattern, placeholders included ({@code /users/{id}/warnings})
     * @param verb    the HTTP method, or {@code null} when the mapping declares none and so
     *                answers every verb
     * @param handler the controller method behind it
     */
    public record Route(String pattern, String verb, HandlerMethod handler) {

        /** Whether this project published the route, rather than a dependency on the path. */
        public boolean isApplication() {
            return handler.getBeanType().getPackageName().startsWith("com.pse");
        }

        /** {@code "GET /users/{id}"} -- the key the sweeps compare on. */
        public String verbAndPattern() {
            return verb + " " + pattern;
        }

        /** The pattern with every placeholder collapsed, so twin spellings share a key. */
        public String shape() {
            return pattern.replaceAll("\\{[^}]+}", "{}");
        }

        public boolean hasPathVariable() {
            return pattern.contains("{");
        }

        /** {@code "LectureController.addLecture"}, for a failure message that can be acted on. */
        public String describe() {
            return handler.getBeanType().getSimpleName() + "." + handler.getMethod().getName();
        }
    }

    /** Every mapped route, this project's and its dependencies'. */
    public static List<Route> of(RequestMappingHandlerMapping handlerMapping) {
        List<Route> routes = new ArrayList<>();

        handlerMapping.getHandlerMethods().forEach((info, handler) -> {
            for (String pattern : info.getPatternValues()) {
                var verbs = info.getMethodsCondition().getMethods();
                if (verbs.isEmpty()) {
                    routes.add(new Route(pattern, null, handler));
                    continue;
                }
                for (var verb : verbs) {
                    routes.add(new Route(pattern, verb.name(), handler));
                }
            }
        });

        return routes;
    }

    /** Only the routes this project published. */
    public static List<Route> application(RequestMappingHandlerMapping handlerMapping) {
        return of(handlerMapping).stream().filter(Route::isApplication).toList();
    }
}
