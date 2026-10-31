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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.StructuredTaskScope;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Setter(onMethod_ = @Autowired)
public abstract class ListLoader<T> {

    private static final String CHATGPT_OVERRIDE_IP = "37.230.192.51";
    private static final String GOOGLE_AI_OVERRIDE_IP = "193.233.112.88";
    private static final Set<String> LEGACY_GEOHIDE_IPS = Set.of(
            "45.155.204.190", "37.230.192.51"
    );
    private static final Pattern GEOHIDE_IP_COMMENT = Pattern.compile(
            "^\\s*#\\s*((?:\\d{1,3}\\.){3}\\d{1,3})\\s*$"
    );
    private static final Pattern HOSTS_ENTRY = Pattern.compile(
            "^(\\s*)((?:\\d{1,3}\\.){3}\\d{1,3})(\\s+.*)$"
    );

    private HttpClient client;

    protected abstract T toObject(String line);
    protected abstract String listType();
    protected abstract Predicate<String> filterRelatedLines();

    @SneakyThrows
    @SuppressWarnings("preview")
    public List<T> fetchWebsites(List<String> urls) {
        @Cleanup var scope = StructuredTaskScope.open();
        List<StructuredTaskScope.Subtask<String>> requests = new ArrayList<>();
        urls.stream().map(url -> scope.fork(() -> fetchList(url))).forEach(requests::add);
        scope.join();
        return requests.stream()
                .map(StructuredTaskScope.Subtask::get)
                .map(String::stripIndent)
                .flatMap(s -> Pattern.compile("\\r?\\n").splitAsStream(s))
                .filter(line -> !line.isBlank())
                .filter(line -> !line.startsWith("#"))
                .map(line -> line.toLowerCase(Locale.ROOT))
                .filter(filterRelatedLines())
                .distinct()
                .map(this::toObject)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @SneakyThrows
    private String fetchList(String url) {
        Log.io("Loading %s list from url: %s".formatted(listType(), url));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Source returned HTTP " + response.statusCode() + ": " + url);
        }
        String body = response.body();
        if (body == null || body.isBlank()) throw new IllegalStateException("Source returned an empty body: " + url);
        return rewriteSpecialBlocks(body);
    }

    private String rewriteSpecialBlocks(String body) {
        String[] lines = body.split("\\R", -1);
        Set<String> geoHideIps = extractGeoHideIps(lines);
        StringBuilder result = new StringBuilder(body.length());
        boolean inChatGptBlock = false;
        boolean inGoogleAiBlock = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String header = line.stripLeading().toLowerCase(Locale.ROOT);
            if (header.startsWith("# chatgpt (openai)")) {
                inChatGptBlock = true;
                inGoogleAiBlock = false;
            } else if (header.startsWith("# google gemini ai") || header.startsWith("# google ai")) {
                Log.io("Using GeoHide Google AI override IP: " + GOOGLE_AI_OVERRIDE_IP);
                inChatGptBlock = false;
                inGoogleAiBlock = true;
            } else if (header.startsWith("#")) {
                inChatGptBlock = false;
                inGoogleAiBlock = false;
            } else if (inChatGptBlock) {
                line = replaceGeoHideIp(line, CHATGPT_OVERRIDE_IP, geoHideIps);
            } else if (inGoogleAiBlock) {
                line = replaceGeoHideIp(line, GOOGLE_AI_OVERRIDE_IP, geoHideIps);
            }
            result.append(line);
            if (i < lines.length - 1) result.append('\n');
        }
        return result.toString();
    }

    private Set<String> extractGeoHideIps(String[] lines) {
        Set<String> ips = new HashSet<>(LEGACY_GEOHIDE_IPS);
        boolean inHeader = false;
        for (String line : lines) {
            String normalized = line.strip().toLowerCase(Locale.ROOT);
            if (!inHeader && normalized.contains("ip-адреса принадлежат geohide dns")) {
                inHeader = true;
                continue;
            }
            if (inHeader && normalized.contains("остальные ip-адреса принадлежат")) break;
            if (inHeader) {
                Matcher matcher = GEOHIDE_IP_COMMENT.matcher(line);
                if (matcher.matches()) ips.add(matcher.group(1));
            }
        }
        return ips;
    }

    private String replaceGeoHideIp(String line, String replacementIp, Set<String> geoHideIps) {
        Matcher matcher = HOSTS_ENTRY.matcher(line);
        if (!matcher.matches() || !geoHideIps.contains(matcher.group(2))) return line;
        return matcher.group(1) + replacementIp + matcher.group(3);
    }

    protected String removeWWW(String domain) {
        return domain.startsWith("www.") ? domain.substring("www.".length()) : domain;
    }
}
