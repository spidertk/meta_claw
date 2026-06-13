package meta.claw.tool;

import meta.claw.core.tool.annotation.ToolService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Web 搜索与网页抓取工具。
 * <p>
 * 搜索功能使用 DuckDuckGo HTML 端点，无需 API Key；网页抓取使用 JDK {@link HttpClient}。
 * 适合 Spring AI 1.1.7 环境，不依赖外部搜索 SDK。
 */
@ToolService
public class WebSearchTool {

    private static final String DUCKDUCKGO_HTML_URL = "https://html.duckduckgo.com/html/?q=";
    private static final int DEFAULT_MAX_RESULTS = 5;
    private static final int DEFAULT_MAX_CHARS = 8000;
    private static final Pattern RESULT_LINK_PATTERN =
            Pattern.compile("<a[^>]+class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern RESULT_SNIPPET_PATTERN =
            Pattern.compile("<a[^>]+class=\"result__snippet\"[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    // 包级可访问，方便单元测试替换为 mock
    HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Tool(description = "Search the web using DuckDuckGo HTML and return a JSON list of results with title, url and snippet.")
    public String searchWeb(
            @ToolParam(description = "Search query") String query,
            @ToolParam(description = "Maximum number of results (default 5)", required = false) Integer limit) {
        if (query == null || query.isBlank()) {
            return "[]";
        }
        int maxResults = limit != null && limit > 0 ? limit : DEFAULT_MAX_RESULTS;
        try {
            String html = fetchHtml(DUCKDUCKGO_HTML_URL + URLEncoder.encode(query, StandardCharsets.UTF_8));
            List<SearchResult> results = parseResults(html, maxResults);
            return toJson(results);
        } catch (Exception e) {
            return "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}";
        }
    }

    @Tool(description = "Fetch the text content of a web page. Returns plain text with a configurable maximum length.")
    public String fetchPage(
            @ToolParam(description = "Full URL to fetch, e.g. https://example.com") String url,
            @ToolParam(description = "Maximum characters to return (default 8000)", required = false) Integer maxChars) {
        if (url == null || url.isBlank()) {
            return "Error: URL is empty";
        }
        int max = maxChars != null && maxChars > 0 ? maxChars : DEFAULT_MAX_CHARS;
        try {
            String html = fetchHtml(url);
            String text = htmlToText(html);
            if (text.length() > max) {
                text = text.substring(0, max) + "\n...[truncated]";
            }
            return text;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String fetchHtml(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (compatible; Meta-Claw-Agent/1.0)")
                .header("Accept", "text/html")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }
        return response.body();
    }

    private List<SearchResult> parseResults(String html, int maxResults) {
        List<String> links = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        Matcher linkMatcher = RESULT_LINK_PATTERN.matcher(html);
        while (linkMatcher.find()) {
            links.add(decodeRedirectUrl(linkMatcher.group(1)));
            titles.add(stripHtml(linkMatcher.group(2)));
        }

        List<String> snippets = new ArrayList<>();
        Matcher snippetMatcher = RESULT_SNIPPET_PATTERN.matcher(html);
        while (snippetMatcher.find()) {
            snippets.add(stripHtml(snippetMatcher.group(1)));
        }

        List<SearchResult> results = new ArrayList<>();
        int count = Math.min(maxResults, Math.min(links.size(), titles.size()));
        for (int i = 0; i < count; i++) {
            String snippet = i < snippets.size() ? snippets.get(i) : "";
            results.add(new SearchResult(titles.get(i), links.get(i), snippet));
        }
        return results;
    }

    private String decodeRedirectUrl(String href) {
        if (href == null) {
            return "";
        }
        int uddgStart = href.indexOf("uddg=");
        if (uddgStart < 0) {
            return unescapeHtmlEntities(href);
        }
        String encoded = href.substring(uddgStart + 5);
        int amp = encoded.indexOf('&');
        if (amp >= 0) {
            encoded = encoded.substring(0, amp);
        }
        return java.net.URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }

    private String htmlToText(String html) {
        String text = stripHtml(html);
        return text.replaceAll("\\s+", " ").trim();
    }

    private String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        String noTags = HTML_TAG_PATTERN.matcher(html).replaceAll(" ");
        return unescapeHtmlEntities(noTags).trim();
    }

    private String unescapeHtmlEntities(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ");
    }

    private String toJson(List<SearchResult> results) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"title\":\"").append(escapeJson(r.title))
                    .append("\",\"url\":\"").append(escapeJson(r.url))
                    .append("\",\"snippet\":\"").append(escapeJson(r.snippet))
                    .append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private record SearchResult(String title, String url, String snippet) {
    }
}
