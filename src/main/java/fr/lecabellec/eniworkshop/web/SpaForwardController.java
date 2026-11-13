package fr.lecabellec.eniworkshop.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards root SPA routes cleanly to the bundled Svelte {@code index.html}.
 *
 * <p>Requirement: REQ-00010, Feature: FR-00010.
 */
@Controller
public final class SpaForwardController {

    /**
     * Forwards UI routes without file extensions to {@code /index.html}.
     *
     * @return forward view name
     */
    @GetMapping(value = {"/workbench", "/catalog", "/quasar"})
    public String forwardSpaRoutes() {
        return "forward:/index.html";
    }
}
