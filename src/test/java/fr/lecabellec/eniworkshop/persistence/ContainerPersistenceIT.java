package fr.lecabellec.eniworkshop.persistence;

import fr.lecabellec.eniworkshop.compare.ComparisonModels.CompareScope;
import fr.lecabellec.eniworkshop.compare.ComparisonModels.ComparisonResult;
import fr.lecabellec.eniworkshop.eni.model.EniDocument;
import fr.lecabellec.eniworkshop.web.CompareController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Containerized integration test verifying MongoDB metadata repositories, GridFS raw XML
 * storage, and multi-view ENI comparison against a real MongoDB container (Podman/Docker).
 *
 * <p>Run via {@code mvn verify -Pcontainer-tests} or {@code ./helpers/run-container-tests.sh}.
 * Traces to REQ-00006, REQ-00011, CS-0090.
 */
@Tag("container")
@Testcontainers
@SpringBootTest(
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
    }
)
final class ContainerPersistenceIT {

    /** Container image used for MongoDB + GridFS verification. */
    private static final String MONGO_IMAGE = "docker.io/library/mongo:7.0.14";

    /** Compressed DOR ENI fixture. */
    private static final Path DOR_FIXTURE = Path.of("src/test/resources/eni/DOR.xml.gz");

    /** Compressed IPSO1 ENI fixture. */
    private static final Path IPSO1_FIXTURE = Path.of("src/test/resources/eni/IPSO1.xml.gz");

    /** Expected DOR slave count. */
    private static final int EXPECTED_DOR_SLAVES = 15;

    /** Shared MongoDB container managed by Testcontainers (works with rootless Podman or Docker). */
    @Container
    private static final MongoDBContainer MONGO = new MongoDBContainer(
        DockerImageName.parse(MONGO_IMAGE).asCompatibleSubstituteFor("mongo")
    );

    /** Repository service wired against containerized MongoDB + GridFS. */
    @Autowired
    private EniRepositoryService repositoryService;

    /** MongoDB metadata Spring Data repository. */
    @Autowired
    private EniMetadataRepository metadataRepository;

    /** Spring Data GridFS template verifying real BSON chunk storage. */
    @Autowired
    private GridFsTemplate gridFsTemplate;

    /** Comparison REST controller backed by real Mongo-persisted documents. */
    @Autowired
    private CompareController compareController;

    /**
     * Binds Spring Data MongoDB connection URI dynamically to the running container port.
     *
     * @param registry Spring dynamic property registry
     */
    @DynamicPropertySource
    static void registerMongoProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl("eniworkshop_it"));
    }

    /** Persists ENIs in MongoDB + GridFS, reloads across layers, and executes catalog comparisons. */
    @Test
    void persistsToRealMongoGridFsAndComparesCatalogEntries() throws Exception {
        final byte[] dorBytes = Files.readAllBytes(DOR_FIXTURE);
        final byte[] ipsoBytes = Files.readAllBytes(IPSO1_FIXTURE);

        final EniRepositoryService.ImportResult dorImport = this.repositoryService.importEni(
            "IT-DOR",
            "DOR Container IT",
            "1.0.0",
            dorBytes,
            List.of("container-it")
        );
        final EniRepositoryService.ImportResult ipsoImport = this.repositoryService.importEni(
            "IT-IPSO1",
            "IPSO1 Container IT",
            "1.0.0",
            ipsoBytes,
            List.of("container-it")
        );

        assertThat(this.metadataRepository.findByReference("IT-DOR")).hasSize(1);
        assertThat(
            this.gridFsTemplate.findOne(Query.query(Criteria.where("_id").is(dorImport.metadata().gridFsFileId())))
        ).isNotNull();

        final EniDocument reloadedDor = this.repositoryService.loadDocument(dorImport.metadata().id());
        assertThat(reloadedDor.slaves()).hasSize(EXPECTED_DOR_SLAVES);

        final ComparisonResult eniComparison = this.compareController.compareEni(
            dorImport.metadata().id(),
            ipsoImport.metadata().id()
        );
        assertThat(eniComparison.scope()).isEqualTo(CompareScope.ENI);
        assertThat(eniComparison.outerJoinRows()).isNotEmpty();

        final ComparisonResult topoComparison = this.compareController.compareTopology(
            dorImport.metadata().id(),
            ipsoImport.metadata().id()
        );
        assertThat(topoComparison.scope()).isEqualTo(CompareScope.TOPOLOGY);
        assertThat(topoComparison.leftLinearBus()).hasSize(EXPECTED_DOR_SLAVES);
    }
}
