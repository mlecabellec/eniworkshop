package fr.lecabellec.eniworkshop.web;

import fr.lecabellec.eniworkshop.diagram.PlantUmlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * Offline Apache-2.0 PlantUML SVG rendering endpoint used by the Svelte 5 PlantUML panel.
 *
 * <p>Requirement: REQ-00007, REQ-00010, Feature: FR-00007.
 */
@RestController
@RequestMapping("/api/diagrams")
@Tag(name = "Diagrams", description = "Offline PlantUML ASL SVG diagram rendering")
public final class DiagramController {

    /** Apache-2.0 PlantUML ASL renderer. */
    private final PlantUmlService plantUmlService;

    /**
     * Constructs a {@link DiagramController}.
     *
     * @param plantUmlService PlantUML rendering service
     */
    @Autowired
    public DiagramController(final PlantUmlService plantUmlService) {
        this.plantUmlService = Objects.requireNonNull(plantUmlService, "plantUmlService must not be null");
    }

    /**
     * Renders PlantUML source text to SVG markup.
     *
     * @param source raw {@code @startuml ... @enduml} source
     * @return SVG image text
     */
    @PostMapping(value = "/plantuml", consumes = MediaType.TEXT_PLAIN_VALUE, produces = "image/svg+xml")
    @Operation(summary = "Render PlantUML source to SVG offline")
    public String renderPlantUml(@RequestBody final String source) {
        return plantUmlService.renderSvg(source);
    }
}
