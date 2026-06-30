package meta.claw.tool.knowledge.extract.video;

import lombok.extern.slf4j.Slf4j;
import meta.claw.tool.knowledge.extract.ExtractionContext;
import meta.claw.tool.knowledge.source.AssetRef;
import meta.claw.tool.knowledge.source.ExtractedDocument;
import meta.claw.tool.knowledge.source.KnowledgeSource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class YtDlpVideoExtractor implements VideoExtractor {

    @Override
    public boolean supports(URI uri) {
        String host = uri.getHost();
        return host != null && (host.contains("douyin.com") || host.contains("iesdouyin.com"));
    }

    @Override
    public ExtractedDocument extract(URI uri, ExtractionContext ctx) throws Exception {
        AssetRef asset = ctx.getAssetManager().store(
                KnowledgeSource.builder()
                        .mediaType("video/url.douyin")
                        .content(uri.toString())
                        .originalName("douyin.url")
                        .build(),
                ctx.getVesselId());

        Path assetDir = asset.getOriginalPath().getParent();

        Map<String, Object> metadata = fetchMetadata(uri, assetDir);
        String title = String.valueOf(metadata.getOrDefault("title", "抖音视频"));

        String transcript = extractSubtitles(uri, assetDir);
        if (transcript == null || transcript.isBlank()) {
            transcript = extractAudioTranscript(uri, assetDir);
        }

        String markdown = "# " + title + "\n\n" +
                "**来源：** " + uri + "\n\n" +
                "## 内容摘要\n\n" + metadata.getOrDefault("description", "") + "\n\n" +
                "## 字幕/转录\n\n" + (transcript != null && !transcript.isBlank() ? transcript : "（未能提取字幕）") + "\n";

        return ExtractedDocument.builder()
                .markdownBody(markdown)
                .mediaType("video/url.douyin")
                .embeddedAssets(List.of(asset))
                .metadata(metadata)
                .build();
    }

    private String extractSubtitles(URI uri, Path workingDir) throws Exception {
        runYtDlp(uri, workingDir, "--write-subs", "--sub-langs", "zh-CN,zh-Hans,zh-Hant,en", "--skip-download");
        return parseSubtitles(workingDir);
    }

    private String extractAudioTranscript(URI uri, Path workingDir) throws Exception {
        runYtDlp(uri, workingDir, "--extract-audio", "--audio-format", "mp3", "--audio-quality", "64K");
        // Audio transcription requires a separate STT service; leave placeholder for now.
        return "[Audio transcription not yet implemented]";
    }

    private Map<String, Object> fetchMetadata(URI uri, Path workingDir) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("title", "抖音视频");
        metadata.put("description", "");
        metadata.put("url", uri.toString());
        try {
            List<String> output = runYtDlp(uri, workingDir, "--dump-json", "--skip-download");
            for (String line : output) {
                if (line.trim().startsWith("{")) {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    Map<?, ?> map = mapper.readValue(line, Map.class);
                    if (map.containsKey("title")) {
                        metadata.put("title", String.valueOf(map.get("title")));
                    }
                    if (map.containsKey("description")) {
                        metadata.put("description", String.valueOf(map.get("description")));
                    }
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch video metadata: {}", e.getMessage());
        }
        return metadata;
    }

    private List<String> runYtDlp(URI uri, Path workingDir, String... extraArgs) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("yt-dlp");
        cmd.addAll(List.of(extraArgs));
        cmd.add("--output");
        cmd.add("%(title).100s-%(id)s");
        cmd.add(uri.toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workingDir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();

        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
                log.debug("yt-dlp: {}", line);
            }
        }

        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("yt-dlp timed out");
        }
        if (process.exitValue() != 0) {
            throw new RuntimeException("yt-dlp failed: " + String.join("\n", output));
        }
        return output;
    }

    private String parseSubtitles(Path assetDir) throws Exception {
        try (var stream = Files.walk(assetDir)) {
            Path vtt = stream.filter(p -> {
                        String name = p.toString().toLowerCase();
                        return name.endsWith(".vtt") || name.endsWith(".srt");
                    })
                    .findFirst()
                    .orElse(null);
            if (vtt != null) {
                return Files.readString(vtt);
            }
        }
        return "";
    }
}
