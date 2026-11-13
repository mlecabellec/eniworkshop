package fr.lecabellec.eniworkshop.web;

import fr.lecabellec.eniworkshop.project.ProjectManagementService;
import fr.lecabellec.eniworkshop.project.ProjectModels.ActivitySource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Objects;
import java.util.Set;

/**
 * Spring MVC interceptor that automatically attributes mutating REST API actions
 * ({@code POST}, {@code PUT}, {@code PATCH}, {@code DELETE}) to the active or supplied
 * Task ({@code TSK-XXXXX}) or Feature ({@code FR-XXXXX}) identifier via header
 * {@code X-Eni-Work-Ref}, query parameter {@code workRef}, or endpoint mapping.
 *
 * <p>Requirement: REQ-00014, Feature: FR-00014, Task: TSK-00014, Constraint: CS-0010.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class ActivityTrackingInterceptor implements HandlerInterceptor {

    /** HTTP header carrying the caller's active Task or Feature identifier. */
    public static final String HEADER_WORK_REF = "X-Eni-Work-Ref";

    /** Mutating HTTP verbs tracked automatically. */
    private static final Set<String> TRACKED_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    /** HTTP status boundary separating successful / client vs server errors. */
    private static final int ERROR_STATUS_THRESHOLD = 400;

    /** Project management service recording activities. */
    private final ProjectManagementService projectService;

    /**
     * Constructs an {@link ActivityTrackingInterceptor}.
     *
     * @param projectService project management service
     */
    @Autowired
    public ActivityTrackingInterceptor(final ProjectManagementService projectService) {
        this.projectService = Objects.requireNonNull(projectService, "projectService must not be null");
    }

    @Override
    public void afterCompletion(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final Object handler,
            final Exception ex) {
        final String method = request.getMethod();
        final String uri = request.getRequestURI();
        if (!TRACKED_METHODS.contains(method) || uri == null || !uri.startsWith("/api/")) {
            return;
        }
        // Project endpoints emit semantic domain activities directly to prevent duplicate logs.
        if (uri.startsWith("/api/project")) {
            return;
        }
        if (ex != null || response.getStatus() >= ERROR_STATUS_THRESHOLD) {
            return;
        }
        final String explicitRef = resolveExplicitRef(request);
        final String workRef = projectService.resolveWorkRefForRequest(explicitRef, uri);
        final String action = "HTTP_" + method;
        projectService.recordActivity(
                workRef,
                action,
                uri,
                method + " " + uri + " (HTTP " + response.getStatus() + ")",
                ActivitySource.WEB);
    }

    /**
     * Extracts optional work reference from request header or query parameter.
     *
     * @param request servlet request
     * @return explicit work reference or {@code null}
     */
    private String resolveExplicitRef(final HttpServletRequest request) {
        final String header = request.getHeader(HEADER_WORK_REF);
        if (header != null && !header.isBlank()) {
            return header;
        }
        return request.getParameter("workRef");
    }
}
