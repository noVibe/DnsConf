package com.novibe.common.data_sources;

import com.novibe.common.util.Log;
import lombok.Cleanup;
import lombok.Setter;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Setter(onMethod_ = @Autowired)
public abstract class ListLoader<T> {

    private static final String CHATGPT_OVERRIDE_IP = "37.230.192.51";
    private static final String GOOGLE_AI_OVERRIDE_IP = "37.230.192.51";

    private HttpClient client;

    protected abstract T toObject(String line);

    protected abstract String listType();

    protected abstract Predicate<String> filterRelatedLines();

    @SneakyThrows
    @SuppressWarnings("preview")
    public List<T> fetchWebsites(List<String> urls) {
        @Cleanup var scope = StructuredTaskScope.open();

        List<StructuredTaskScope.Subtask<String>> requests = new ArrayList<>();

        urls.stream()
                .map(url -> scope.fork(() -> fetchList(url)))
                .forEach(requests::add);

        scope.join();

        return requests.stream()
                .map(StructuredTaskScope.Subtask::get)
                .map(String::stripIndent)
                .flatMap(s -> Pattern.compile("\\r?\\n").splitAsStream(s))
                .parallel()
                .filter(line -> !line.isBlank())
                .filter(line -> !line.startsWith("#"))
                .map(String::toLowerCase)
                .filter(filterRelatedLines())
                .distinct()
                .map(this::toObject)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @SneakyThrows
    private String fetchList(String url) {
        Log.io("Loading %s list from url: %s".formatted(listType(), url));

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .build();

        String body = client.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        ).body();

        return rewriteSpecialBlocks(body);
    }

    private String rewriteSpecialBlocks(String body) {

        StringBuilder result = new StringBuilder(body.length());

        boolean inChatGptBlock = false;
        boolean inGoogleAiBlock = false;

        String[] lines = body.split("\\R", -1);

        for (int i = 0; i < lines.length; i++) {

            String line = lines[i];

            if (line.startsWith("# ChatGPT (OpenAI)")) {
                inChatGptBlock = true;
                inGoogleAiBlock = false;

            } else if (line.startsWith("# Google AI")) {
                inChatGptBlock = false;
                inGoogleAiBlock = true;

            } else if (line.startsWith("#")) {
                inChatGptBlock = false;
                inGoogleAiBlock = false;

            } else {

                if (inChatGptBlock) {
                    line = replaceGeoHideIp(
                            line,
                            CHATGPT_OVERRIDE_IP
                    );
                }

                if (inGoogleAiBlock) {
                    line = replaceGeoHideIp(
                            line,
                            GOOGLE_AI_OVERRIDE_IP
                    );
                }
            }

            result.append(line);

            if (i < lines.length - 1) {
                result.append('\n');
            }
        }

        return result.toString();
    }

    private String replaceGeoHideIp(String line, String replacementIp) {

        if (line.startsWith("45.155.204.190")) {
            return replacementIp
                    + line.substring("45.155.204.190".length());
        }

        if (line.startsWith("37.230.192.51")) {
            return replacementIp
                    + line.substring("37.230.192.51".length());
        }

        return line;
    }

    protected String removeWWW(String domain) {
        if (domain.startsWith("www.")) {
            return domain.substring("www.".length());
        }

        return domain;
    }
}
