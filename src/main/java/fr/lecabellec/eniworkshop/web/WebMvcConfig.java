package fr.lecabellec.eniworkshop.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Objects;

/**
 * Registers servlet MVC interceptors including automatic Task/Feature activity attribution.
 *
 * <p>Requirement: REQ-00014, Feature: FR-00014, Task: TSK-00014, Constraint: CS-0010.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class WebMvcConfig implements WebMvcConfigurer {

    /** Activity attribution interceptor. */
    private final ActivityTrackingInterceptor activityInterceptor;

    /**
     * Constructs a {@link WebMvcConfig}.
     *
     * @param activityInterceptor interceptor recording mutating API calls
     */
    @Autowired
    public WebMvcConfig(final ActivityTrackingInterceptor activityInterceptor) {
        this.activityInterceptor = Objects.requireNonNull(
                activityInterceptor, "activityInterceptor must not be null");
    }

    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        registry.addInterceptor(activityInterceptor).addPathPatterns("/api/**");
    }
}
