package fr.lecabellec.eniworkshop.project;

import fr.lecabellec.eniworkshop.project.ProjectModels.ActivityRecord;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Optional Spring Data MongoDB repository persisting tracked {@link ActivityRecord} events.
 *
 * <p>Requirement: REQ-00014, Feature: FR-00014, Task: TSK-00014.
 */
public interface ProjectActivityRepository extends MongoRepository<ActivityRecord, String> {

    /**
     * Lists persisted activities linked to a given Task or Feature ID.
     *
     * @param workRefId task or feature identifier
     * @return matching activity records
     */
    List<ActivityRecord> findByWorkRefIdOrderByTimestampDesc(String workRefId);
}
