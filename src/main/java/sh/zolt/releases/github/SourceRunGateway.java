package sh.zolt.releases.github;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public interface SourceRunGateway {
    JsonNode fetchRun(String repository, String runId);

    List<JsonNode> fetchJobs(String repository, String runId);
}
