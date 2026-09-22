package com.novibe.common.data_sources;

import com.novibe.common.base_structures.HostsLine;
import com.novibe.common.exception.UserInputException;
import com.novibe.common.util.DataParser;
import com.novibe.common.util.Log;
import com.novibe.common.util.RetryUtils;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.StructuredTaskScope;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Setter(onMethod_ = @Autowired)
public abstract class ListLoader<T> {

    private static final int RETRY_ATTEMPTS = 3;

    private static final Duration RETRY_DELAY = Duration.ofSeconds(5);

    private HttpClient client;

    protected abstract T toObject(HostsLine hostsLine);

    protected abstract String listType();

    protected abstract Predicate<HostsLine> filterRelatedLines();

    protected int retryAttempts() {
        return RETRY_ATTEMPTS;
    }

    protected Duration retryDelay() {
        return RETRY_DELAY;
    }

    @SuppressWarnings("preview")
    public List<T> fetchWebsites(List<String> urls) {
        try (var scope = StructuredTaskScope.open()) {
            List<StructuredTaskScope.Subtask<String>> requests = new ArrayList<>();
            urls.stream()
                    .map(url -> scope.fork(() -> fetchList(url)))
                    .forEach(requests::add);
            scope.join();

            return requests.stream()
                    .map(StructuredTaskScope.Subtask::get)
                    .flatMap(DataParser::splitByEol)
                    .map(String::strip)
                    .parallel()
                    .filter(line -> !line.isBlank())
                    .filter(line -> !DataParser.isComment(line))
                    .map(String::toLowerCase)
                    .map(DataParser::parseHostsLine)
                    .filter(Objects::nonNull)
                    .filter(filterRelatedLines())
                    .distinct()
                    .map(this::toObject)
                    .collect(Collectors.toCollection(ArrayList::new));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private String fetchList(String url) throws IOException, InterruptedException {
        Log.io("Loading %s list from url: %s".formatted(listType(), url));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .build();
        for (int attempt = 1; ; attempt++) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int responseCode = response.statusCode();
            if (responseCode < 300) {
                return response.body();
            }
            if (attempt >= retryAttempts() || !RetryUtils.isTemporaryError(responseCode)) {
                throw UserInputException.noStackTrace("Failed to load %s list, response code %s from url: %s"
                        .formatted(listType(), responseCode, url));
            }
            RetryUtils.waitBeforeRetry(retryDelay(), responseCode, attempt, retryAttempts());
        }
    }

}
