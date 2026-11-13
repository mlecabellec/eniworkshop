package fr.lecabellec.eniworkshop.web;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end REST API integration tests across ENI import, validation, topology, frame map,
 * catalog discovery, PlantUML SVG rendering, and Quasar status endpoints.
 * Traces to REQ-00001..REQ-00010, CS-0090.
 */
@SpringBootTest
@AutoConfigureMockMvc
final class EniWorkshopWebMvcTest {

    /** DOR compressed ENI test fixture. */
    private static final Path DOR_FIXTURE = Path.of("src/test/resources/eni/DOR.xml.gz");

    /** IPSO1 compressed ENI test fixture. */
    private static final Path IPSO1_FIXTURE = Path.of("src/test/resources/eni/IPSO1.xml.gz");

    /** Expected DOR slave count. */
    private static final int DOR_SLAVE_COUNT = 15;

    /** Spring MockMvc test client. */
    @Autowired
    private MockMvc mockMvc;

    @Test
    void exercisesEniRestLifecycleValidationTopologyAndDiagrams() throws Exception {
        final byte[] gzBytes = Files.readAllBytes(DOR_FIXTURE);
        final MockMultipartFile filePart = new MockMultipartFile(
            "file",
            "DOR.xml.gz",
            MediaType.APPLICATION_OCTET_STREAM_VALUE,
            gzBytes
        );

        final MvcResult importResult = mockMvc.perform(
                multipart("/api/eni/import")
                    .file(filePart)
                    .header("X-Eni-Work-Ref", "TSK-00014")
                    .param("reference", "WEB-DOR-001")
                    .param("name", "DOR Web Test")
                    .param("version", "1.0.0")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.metadata.reference").value("WEB-DOR-001"))
            .andExpect(jsonPath("$.document.slaves.length()").value(DOR_SLAVE_COUNT))
            .andReturn();

        final String json = importResult.getResponse().getContentAsString();
        final String id = com.jayway.jsonpath.JsonPath.read(json, "$.metadata.id");
        assertThat(id).isNotBlank();

        mockMvc.perform(get("/api/eni"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").exists());

        mockMvc.perform(get("/api/eni/" + id + "/validate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(true));

        mockMvc.perform(get("/api/eni/" + id + "/topology"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nodes.length()").value(DOR_SLAVE_COUNT + 1));

        mockMvc.perform(get("/api/eni/" + id + "/frames"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].totalWireBytes").isNumber());

        mockMvc.perform(get("/api/eni/" + id + "/csv").param("kind", "slaves"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/catalog/slaves"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").exists());

        mockMvc.perform(
                post("/api/diagrams/plantuml")
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("@startuml\nMaster -> Slave : ECAT\n@enduml\n")
            )
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/quasar/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.connected").value(false));

        final MockMultipartFile ipsoPart = new MockMultipartFile(
            "file",
            "IPSO1.xml.gz",
            MediaType.APPLICATION_OCTET_STREAM_VALUE,
            Files.readAllBytes(IPSO1_FIXTURE)
        );
        final MvcResult ipsoImport = mockMvc.perform(
                multipart("/api/eni/import")
                    .file(ipsoPart)
                    .param("reference", "WEB-IPSO1-001")
                    .param("name", "IPSO1 Web Test")
                    .param("version", "1.0.0")
            )
            .andExpect(status().isOk())
            .andReturn();
        final String ipsoId = com.jayway.jsonpath.JsonPath.read(
            ipsoImport.getResponse().getContentAsString(),
            "$.metadata.id"
        );
        final int dorPhys = com.jayway.jsonpath.JsonPath.read(json, "$.document.slaves[0].identity.physAddr");
        final int ipsoPhys = com.jayway.jsonpath.JsonPath.read(
            ipsoImport.getResponse().getContentAsString(),
            "$.document.slaves[0].identity.physAddr"
        );

        mockMvc.perform(get("/api/compare/eni").param("leftId", id).param("rightId", ipsoId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scope").value("ENI"))
            .andExpect(jsonPath("$.outerJoinRows").isArray());

        mockMvc.perform(get("/api/compare/topology").param("leftId", id).param("rightId", ipsoId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scope").value("TOPOLOGY"))
            .andExpect(jsonPath("$.leftLinearBus").isArray());

        mockMvc.perform(
                get("/api/compare/slave")
                    .param("leftId", id)
                    .param("leftPhys", String.valueOf(dorPhys))
                    .param("rightId", ipsoId)
                    .param("rightPhys", String.valueOf(ipsoPhys))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scope").value("SLAVE"));

        mockMvc.perform(get("/api/project/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.features").isArray())
            .andExpect(jsonPath("$.tasks").isArray())
            .andExpect(jsonPath("$.traceability.totalJavaFiles").isNumber());

        mockMvc.perform(get("/api/project/activities").param("workRef", "TSK-00014"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].workRefId").value("TSK-00014"));

        mockMvc.perform(
                put("/api/project/active-ref")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"workRefId\":\"FR-00014\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeWorkRef").value("FR-00014"));

        mockMvc.perform(
                post("/api/project/activities")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"workRefId\":\"TSK-00014\",\"action\":\"WEB_TEST\","
                            + "\"target\":\"MockMvc\",\"summary\":\"Verified activity tracking\"}"
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workRefId").value("TSK-00014"));
    }
}
