package sh.zolt.releases.github;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import sh.zolt.releases.io.JsonSupport;

public final class GitHubActionsClient implements SourceRunGateway {
    private final HttpClient http;
    private final String token;

    public GitHubActionsClient(String token) {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(SourceCiConstants.REQUEST_TIMEOUT)
                        .build(),
                token);
    }

    GitHubActionsClient(HttpClient http, String token) {
        this.http = http;
        this.token = token;
    }

    @Override
    public JsonNode fetchRun(String repository, String runId) {
        return request("/repos/" + repository + "/actions/runs/" + runId);
    }

    @Override
    public List<JsonNode> fetchJobs(String repository, String runId) {
        List<JsonNode> jobs = new ArrayList<>();
        for (int page = 1; ; page++) {
            JsonNode response = request(
                    "/repos/" + repository + "/actions/runs/" + runId
                            + "/jobs?filter=latest&per_page=100&page=" + page);
            JsonNode batch = response.get("jobs");
            if (batch == null || !batch.isArray()) {
                throw new IllegalArgumentException(
                        "GitHub jobs response did not contain a jobs array");
            }
            batch.forEach(jobs::add);
            if (batch.size() < 100) {
                return List.copyOf(jobs);
            }
        }
    }

    private JsonNode request(String path) {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create(SourceCiConstants.API_ROOT + path))
                .timeout(SourceCiConstants.REQUEST_TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", SourceCiConstants.USER_AGENT)
                .header("X-GitHub-Api-Version", SourceCiConstants.API_VERSION)
                .GET();
        if (token != null && !token.isBlank()) {
            request.header("Authorization", "Bearer " + token);
        }
        try {
            HttpResponse<String> response =
                    http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String remaining = response.headers()
                        .firstValue("X-RateLimit-Remaining")
                        .orElse("unknown");
                throw new IllegalArgumentException(
                        "GitHub API request failed with HTTP " + response.statusCode()
                                + "; rate-limit remaining: " + remaining);
            }
            JsonNode result = JsonSupport.read(response.body());
            if (!result.isObject()) {
                throw new IllegalArgumentException("GitHub API response must be an object");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "GitHub API request failed: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("GitHub API request was interrupted", exception);
        }
    }
}
