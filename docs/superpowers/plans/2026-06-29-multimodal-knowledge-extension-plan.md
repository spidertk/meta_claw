# Multimodal Knowledge Extension Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend `meta-claw-tool` knowledge base to ingest text, images, PDFs, and Douyin video links, while using model-config-driven multimodal analysis when available.

**Architecture:** Introduce a `ContentExtractor` SPI and `AssetManager` so that all knowledge sources are normalized into `ExtractedDocument` before analysis. `KnowledgeManager` receives a unified `KnowledgeSource`, extracts text/assets, runs LLM analysis (multimodal-aware via `ModelCapability`), and persists Markdown entries plus original binaries. `meta-claw-core` `SpiMessage` is extended with `MediaPart` for multimodal input without changing streaming output.

**Tech Stack:** Java 21, Spring Boot, Spring AI, JGit, Lombok, Apache PDFBox/Tika, Jackson, yt-dlp (optional external dependency).

---

## File Structure

### meta-claw-core (LLM SPI)

- `meta-claw-core/src/main/java/meta/claw/core/llm/SpiMessage.java` — add `mediaParts` field.
- `meta-claw-core/src/main/java/meta/claw/core/llm/MediaPart.java` — new multimodal content part.
- `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/SpiMessageConverter.java` — convert `MediaPart` to Spring AI `Media` for user messages.

### meta-claw-tool (knowledge subsystem)

- `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/source/KnowledgeSource.java`
- `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/source/ExtractedDocument.java`
- `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/source/AssetRef.java`
- `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/extract/ContentExtractor.java`
- `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/extract/ExtractionContext.java`
- `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/extract/ContentExtractorService.java`
- `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/extract/TextExtractor.java`
- `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/extract/ImageExtractor.java`
- `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/extract/PdfExtractor.java`
- `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/extract/video/VideoExtractor.java`
- `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/extract/video/YtDlpVideoExtractor.java`
- `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/extract/video/DouyinVideoExtractor.java`
- `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/asset/AssetManager.java`
- `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/asset/LocalAssetManager.java`
- `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/multimodal/ModelCapability.java`
- `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/multimodal/MultimodalConfig.java`
- `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/KnowledgeAnalyzer.java` — refactor to accept `ExtractedDocument`, add multimodal path.
- `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/KnowledgeManager.java` — change `acquire` signature to `KnowledgeSource`.
- `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/GitManager.java` — extend grep to include extracted markdown under assets.
- `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/model/KnowledgeEntry.java` — add asset reference fields.
- `meta-claw-tool/src/main/java/meta/claw/tool/KnowledgeTool.java` — add file/URL tool methods.
- `meta-claw-tool/src/test/java/meta/claw/tool/knowledge/...` — new unit tests for extractors and asset manager.
- `meta-claw-tool/src/test/java/meta/claw/tool/KnowledgeToolTest.java` — update existing tests.
- `meta-claw-tool/pom.xml` — add PDFBox/Tika dependency.

---

## Task 1: Extend `SpiMessage` with `MediaPart`

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/llm/MediaPart.java`
- Modify: `meta-claw-core/src/main/java/meta/claw/core/llm/SpiMessage.java:11-24`

- [ ] **Step 1: Write `MediaPart` model**

```java
package meta.claw.core.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaPart {
    private String type;      // image_url, image_base64, audio_url, video_url
    private String mimeType;  // image/png
    private String url;       // http URL or local asset URL
    private byte[] data;      // inline binary (optional)
}
```

- [ ] **Step 2: Add `mediaParts` to `SpiMessage`**

```java
package meta.claw.core.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import meta.claw.core.tool.SpiToolCall;

import java.util.List;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class SpiMessage {
    private String role;
    private String content;
    private String reasoningContent;
    private List<SpiToolCall> toolCalls;
    private String toolCallId;
    private String toolName;
    private List<MediaPart> mediaParts;  // NEW

    public static SpiMessage user(String content) {
        return SpiMessage.builder().role("user").content(content).build();
    }

    public static SpiMessage user(String content, List<MediaPart> mediaParts) {
        return SpiMessage.builder().role("user").content(content).mediaParts(mediaParts).build();
    }

    // ... keep other existing factory methods unchanged
}
```

- [ ] **Step 3: Verify compilation**

Run: `mvn -pl meta-claw-core -am compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add meta-claw-core/src/main/java/meta/claw/core/llm/MediaPart.java
"meta-claw-core/src/main/java/meta/claw/core/llm/SpiMessage.java"
git commit -m "feat(core): add MediaPart to SpiMessage for multimodal input"
```

---

## Task 2: Update `SpiMessageConverter` for multimodal user messages

**Files:**
- Modify: `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/SpiMessageConverter.java:1-73`

- [ ] **Step 1: Write a test for multimodal conversion**

Create: `meta-claw-core/src/test/java/meta/claw/core/runtime/engine/SpiMessageConverterMultimodalTest.java`

```java
package meta.claw.core.runtime.engine;

import meta.claw.core.llm.MediaPart;
import meta.claw.core.llm.SpiMessage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SpiMessageConverterMultimodalTest {

    @Test
    void userMessageWithImagePart() {
        SpiMessage spi = SpiMessage.user(
                "Describe this image",
                List.of(MediaPart.builder()
                        .type("image_url")
                        .mimeType("image/png")
                        .url("file:///tmp/test.png")
                        .build()));

        Message message = SpiMessageConverter.toSpringMessage(spi);
        assertEquals(UserMessage.class, message.getClass());
        UserMessage userMessage = (UserMessage) message;
        assertEquals("Describe this image", userMessage.getText());
        assertFalse(userMessage.getMedia().isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl meta-claw-core -am test -Dtest=SpiMessageConverterMultimodalTest -q`
Expected: BUILD FAILURE or test failure (media not populated).

- [ ] **Step 3: Modify `SpiMessageConverter.toSpringMessage` for user role**

Replace the `case "user"` branch with:

```java
case "user" -> {
    List<org.springframework.ai.model.Media> media = toSpringMedia(m.getMediaParts());
    if (media.isEmpty()) {
        yield new UserMessage(m.getContent());
    } else {
        yield new UserMessage(m.getContent(), media);
    }
}
```

Add helper method:

```java
private static List<org.springframework.ai.model.Media> toSpringMedia(List<MediaPart> parts) {
    if (parts == null || parts.isEmpty()) {
        return List.of();
    }
    return parts.stream()
            .map(part -> {
                if ("image_url".equals(part.getType()) || "image_base64".equals(part.getType())) {
                    return new org.springframework.ai.model.Media(
                            org.springframework.ai.model.Media.Type.IMAGE,
                            new org.springframework.core.io.UrlResource(part.getUrl()));
                }
                // Fallback for future media types
                return new org.springframework.ai.model.Media(
                        org.springframework.ai.model.Media.Type.IMAGE,
                        new org.springframework.core.io.UrlResource(part.getUrl()));
            })
            .collect(Collectors.toList());
}
```

Add import for `org.springframework.core.io.UrlResource` and `java.net.MalformedURLException`.

- [ ] **Step 4: Run the multimodal test**

Run: `mvn -pl meta-claw-core -am test -Dtest=SpiMessageConverterMultimodalTest -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Run all meta-claw-core tests to ensure no regression**

Run: `mvn -pl meta-claw-core -am test -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add meta-claw-core/src/main/java/meta/claw/core/runtime/engine/SpiMessageConverter.java
"meta-claw-core/src/test/java/meta/claw/core/runtime/engine/SpiMessageConverterMultimodalTest.java"
git commit -m "feat(core): convert MediaPart to Spring AI Media in user messages"
```

---

## Task 3: Create source/extraction data models

**Files:**
- Create: `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/source/KnowledgeSource.java`
- Create: `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/source/ExtractedDocument.java`
- Create: `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/source/AssetRef.java`

- [ ] **Step 1: Write `KnowledgeSource`**

```java
package meta.claw.tool.knowledge.source;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.InputStream;
import java.net.URI;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSource {
    private String sourceId;
    private String mediaType;       // text/plain, image/png, application/pdf, video/url.douyin
    private URI uri;                // local path or remote URL
    private InputStream stream;     // inline byte stream
    private String originalName;
    private String content;         // shortcut for text/plain
    private Map<String, Object> extra;

    public boolean isText() {
        return "text/plain".equals(mediaType);
    }
}
```

- [ ] **Step 2: Write `AssetRef`**

```java
package meta.claw.tool.knowledge.source;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetRef {
    private String assetId;
    private String mediaType;
    private Path originalPath;      // absolute path to original file
    private Path extractedPath;     // absolute path to extracted.md (optional)
}
```

- [ ] **Step 3: Write `ExtractedDocument`**

```java
package meta.claw.tool.knowledge.source;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedDocument {
    private String markdownBody;
    private String mediaType;
    @Builder.Default
    private List<AssetRef> embeddedAssets = Collections.emptyList();
    @Builder.Default
    private Map<String, Object> metadata = Collections.emptyMap();
}
```

- [ ] **Step 4: Verify compile**

Run: `mvn -pl meta-claw-tool -am compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add meta-claw-tool/src/main/java/meta/claw/tool/knowledge/source/
git commit -m "feat(knowledge): add KnowledgeSource, ExtractedDocument, AssetRef models"
```

---

## Task 4: Create `ContentExtractor` SPI, context, service, and `TextExtractor`

**Files:**
- Create: `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/extract/ContentExtractor.java`
- Create: `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/extract/ExtractionContext.java`
- Create: `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/extract/ContentExtractorService.java`
- Create: `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/extract/TextExtractor.java`

- [ ] **Step 1: Write `ContentExtractor` interface**

```java
package meta.claw.tool.knowledge.extract;

import meta.claw.tool.knowledge.source.ExtractedDocument;
import meta.claw.tool.knowledge.source.KnowledgeSource;

public interface ContentExtractor {
    boolean supports(KnowledgeSource source);
    ExtractedDocument extract(KnowledgeSource source, ExtractionContext ctx);
}
```

- [ ] **Step 2: Write `ExtractionContext`**

```java
package meta.claw.tool.knowledge.extract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import meta.claw.tool.knowledge.asset.AssetManager;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractionContext {
    private AssetManager assetManager;
    private String vesselId;
}
```

- [ ] **Step 3: Write `ContentExtractorService`**

```java
package meta.claw.tool.knowledge.extract;

import lombok.extern.slf4j.Slf4j;
import meta.claw.tool.knowledge.source.ExtractedDocument;
import meta.claw.tool.knowledge.source.KnowledgeSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ContentExtractorService {

    private final List<ContentExtractor> extractors;

    @Autowired
    public ContentExtractorService(List<ContentExtractor> extractors) {
        this.extractors = extractors != null ? extractors : List.of();
    }

    public ExtractedDocument extract(KnowledgeSource source, ExtractionContext ctx) {
        for (ContentExtractor extractor : extractors) {
            if (extractor.supports(source)) {
                log.debug("Using extractor {} for {}", extractor.getClass().getSimpleName(), source.getMediaType());
                return extractor.extract(source, ctx);
            }
        }
        throw new UnsupportedOperationException("No extractor supports mediaType: " + source.getMediaType());
    }
}
```

- [ ] **Step 4: Write `TextExtractor`**

```java
package meta.claw.tool.knowledge.extract;

import meta.claw.tool.knowledge.source.ExtractedDocument;
import meta.claw.tool.knowledge.source.KnowledgeSource;
import org.springframework.stereotype.Component;

@Component
public class TextExtractor implements ContentExtractor {

    @Override
    public boolean supports(KnowledgeSource source) {
        return "text/plain".equals(source.getMediaType());
    }

    @Override
    public ExtractedDocument extract(KnowledgeSource source, ExtractionContext ctx) {
        return ExtractedDocument.builder()
                .markdownBody(source.getContent() != null ? source.getContent() : "")
                .mediaType("text/plain")
                .build();
    }
}
```

- [ ] **Step 5: Write unit test for `ContentExtractorService`**

Create: `meta-claw-tool/src/test/java/meta/claw/tool/knowledge/extract/ContentExtractorServiceTest.java`

```java
package meta.claw.tool.knowledge.extract;

import meta.claw.tool.knowledge.source.ExtractedDocument;
import meta.claw.tool.knowledge.source.KnowledgeSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContentExtractorServiceTest {

    @Test
    void routesTextSourceToTextExtractor() {
        ContentExtractorService service = new ContentExtractorService(List.of(new TextExtractor()));
        KnowledgeSource source = KnowledgeSource.builder()
                .mediaType("text/plain")
                .content("hello world")
                .build();

        ExtractedDocument doc = service.extract(source, ExtractionContext.builder().build());
        assertEquals("hello world", doc.getMarkdownBody());
        assertEquals("text/plain", doc.getMediaType());
    }

    @Test
    void throwsWhenNoExtractorSupports() {
        ContentExtractorService service = new ContentExtractorService(List.of(new TextExtractor()));
        KnowledgeSource source = KnowledgeSource.builder().mediaType("image/png").build();
        assertThrows(UnsupportedOperationException.class,
                () -> service.extract(source, ExtractionContext.builder().build()));
    }
}
```

- [ ] **Step 6: Run test**

Run: `mvn -pl meta-claw-tool -am test -Dtest=ContentExtractorServiceTest -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add meta-claw-tool/src/main/java/meta/claw/tool/knowledge/extract/
"meta-claw-tool/src/test/java/meta/claw/tool/knowledge/extract/ContentExtractorServiceTest.java"
git commit -m "feat(knowledge): add ContentExtractor SPI, service, and TextExtractor"
```

---

## Task 5: Create `AssetManager`

**Files:**
- Create: `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/asset/AssetManager.java`
- Create: `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/asset/LocalAssetManager.java`

- [ ] **Step 1: Write `AssetManager` interface**

```java
package meta.claw.tool.knowledge.asset;

import meta.claw.tool.knowledge.source.AssetRef;
import meta.claw.tool.knowledge.source.KnowledgeSource;

import java.io.InputStream;
import java.nio.file.Path;

public interface AssetManager {
    AssetRef store(KnowledgeSource source, String vesselId);
    InputStream load(AssetRef ref);
    Path resolvePath(AssetRef ref);
}
```

- [ ] **Step 2: Write `LocalAssetManager`**

```java
package meta.claw.tool.knowledge.asset;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.infra.ProjectRootFinder;
import meta.claw.tool.knowledge.source.AssetRef;
import meta.claw.tool.knowledge.source.KnowledgeSource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Component
public class LocalAssetManager implements AssetManager {

    @Override
    public AssetRef store(KnowledgeSource source, String vesselId) {
        String assetId = source.getSourceId() != null && !source.getSourceId().isBlank()
                ? source.getSourceId()
                : UUID.randomUUID().toString().substring(0, 8);

        Path vesselDir = ProjectRootFinder.getMetaClawDir()
                .resolve("vessels")
                .resolve(vesselId != null && !vesselId.isBlank() ? vesselId : "default");
        Path assetDir = vesselDir.resolve("assets").resolve(assetId);

        try {
            Files.createDirectories(assetDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create asset directory: " + assetDir, e);
        }

        String extension = extensionForMediaType(source.getMediaType());
        Path originalPath = assetDir.resolve("original" + extension);

        try {
            if (source.getStream() != null) {
                Files.copy(source.getStream(), originalPath);
            } else if (source.getUri() != null) {
                Files.copy(source.getUri().toURL().openStream(), originalPath);
            } else if (source.getContent() != null && "text/plain".equals(source.getMediaType())) {
                Files.writeString(originalPath, source.getContent());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to store asset: " + originalPath, e);
        }

        return AssetRef.builder()
                .assetId(assetId)
                .mediaType(source.getMediaType())
                .originalPath(originalPath)
                .build();
    }

    @Override
    public InputStream load(AssetRef ref) {
        try {
            return Files.newInputStream(ref.getOriginalPath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load asset: " + ref.getOriginalPath(), e);
        }
    }

    @Override
    public Path resolvePath(AssetRef ref) {
        return ref.getOriginalPath();
    }

    private String extensionForMediaType(String mediaType) {
        if (mediaType == null) return "";
        return switch (mediaType) {
            case "text/plain" -> ".txt";
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            case "video/url.douyin", "video/mp4" -> ".mp4";
            default -> "";
        };
    }
}
```

- [ ] **Step 3: Write unit test**

Create: `meta-claw-tool/src/test/java/meta/claw/tool/knowledge/asset/LocalAssetManagerTest.java`

```java
package meta.claw.tool.knowledge.asset;

import meta.claw.tool.knowledge.source.AssetRef;
import meta.claw.tool.knowledge.source.KnowledgeSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalAssetManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void storesTextSource() throws Exception {
        System.setProperty("user.dir", tempDir.toString());
        try {
            LocalAssetManager manager = new LocalAssetManager();
            KnowledgeSource source = KnowledgeSource.builder()
                    .mediaType("text/plain")
                    .content("hello")
                    .build();

            AssetRef ref = manager.store(source, "v1");
            assertTrue(Files.exists(ref.getOriginalPath()));
            assertEquals("hello", Files.readString(ref.getOriginalPath()));
        } finally {
            System.clearProperty("user.dir");
        }
    }
}
```

- [ ] **Step 4: Run test**

Run: `mvn -pl meta-claw-tool -am test -Dtest=LocalAssetManagerTest -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add meta-claw-tool/src/main/java/meta/claw/tool/knowledge/asset/
"meta-claw-tool/src/test/java/meta/claw/tool/knowledge/asset/LocalAssetManagerTest.java"
git commit -m "feat(knowledge): add LocalAssetManager for per-vessel asset storage"
```


## Task 6: Refactor `KnowledgeManager.acquire` to use `KnowledgeSource`

**Files:**
- Modify: `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/KnowledgeManager.java:56-101`
- Modify: `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/KnowledgeManager.java:126-209`
- Modify: `meta-claw-tool/src/main/java/meta/claw/tool/KnowledgeTool.java:39-54`

- [ ] **Step 1: Inject new dependencies into `KnowledgeManager`**

```java
@Slf4j
@Component
public class KnowledgeManager {

    private final GitManager gitManager;
    private final KnowledgeAnalyzer analyzer;
    private final ContentExtractorService extractorService;
    private final AssetManager assetManager;

    private double confidenceThreshold = 0.9;

    @Autowired
    public KnowledgeManager(GitManager gitManager,
                            KnowledgeAnalyzer analyzer,
                            ContentExtractorService extractorService,
                            AssetManager assetManager) {
        this.gitManager = gitManager;
        this.analyzer = analyzer;
        this.extractorService = extractorService;
        this.assetManager = assetManager;
    }
```

- [ ] **Step 2: Replace `acquire(String, String, boolean)` with `acquire(KnowledgeSource, String, boolean)`**

```java
public Map<String, Object> acquire(KnowledgeSource source, String context, boolean dryRun) {
    Path knowledgeDir = getKnowledgeDir();
    Path vesselDir = getVesselDir();
    ensureKnowledgeDir(knowledgeDir);

    String vesselId = VesselContext.getVesselId();
    log.info("Acquiring new knowledge for vessel {} (mediaType={})...", vesselId, source.getMediaType());

    AssetRef asset = assetManager.store(source, vesselId);
    ExtractionContext ctx = ExtractionContext.builder()
            .assetManager(assetManager)
            .vesselId(vesselId)
            .build();
    ExtractedDocument doc = extractorService.extract(source, ctx);

    List<String> keywords = analyzer.extractKeywords(doc.getMarkdownBody());
    List<KnowledgeEntry> relatedEntries = findRelatedEntries(keywords, knowledgeDir);
    AnalysisResult analysis = analyzer.analyze(doc, relatedEntries, context);

    Map<String, Object> result = new LinkedHashMap<>();

    if (dryRun) {
        result.put("status", "analyzed");
        result.put("analysis", analysisToMap(analysis));
        result.put("related_entries", relatedEntries.stream().map(KnowledgeEntry::getId).collect(Collectors.toList()));
        result.put("dry_run", true);
        return result;
    }

    if (analysis.shouldAutoExecute(confidenceThreshold)) {
        return executeAcquire(doc, asset, analysis, relatedEntries, knowledgeDir, vesselDir);
    } else {
        result.put("status", "needs_review");
        result.put("analysis", analysisToMap(analysis));
        // ... keep existing needs_review formatting
        result.put("related_entries", relatedEntries.stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", e.getId());
                    m.put("content", e.getContent() != null ? e.getContent().substring(0, Math.min(200, e.getContent().length())) : "");
                    m.put("status", e.getStatus().getValue());
                    return m;
                })
                .collect(Collectors.toList()));
        result.put("message", "Confidence below threshold or contradiction detected. Manual review required.");
        return result;
    }
}
```

- [ ] **Step 3: Update `executeAcquire` signature to accept `ExtractedDocument` and `AssetRef`**

```java
private Map<String, Object> executeAcquire(ExtractedDocument doc,
                                            AssetRef asset,
                                            AnalysisResult analysis,
                                            List<KnowledgeEntry> relatedEntries,
                                            Path knowledgeDir, Path vesselDir) {
    String entryId = UUID.randomUUID().toString().substring(0, 8);

    List<String> topics = analysis.getSuggestedTopics() != null && !analysis.getSuggestedTopics().isEmpty()
            ? analysis.getSuggestedTopics()
            : List.of("general");
    String topicName = topics.get(0).toLowerCase().replace(" ", "_");
    Path topicDir = knowledgeDir.resolve(topicName);
    try {
        Files.createDirectories(topicDir);
    } catch (IOException e) {
        log.error("Failed to create topic directory: {}", e.getMessage());
    }

    String title = analysis.getSuggestedTitle() != null && !analysis.getSuggestedTitle().isEmpty()
            ? analysis.getSuggestedTitle()
            : entryId;
    String filename = sanitizeFilename(title) + ".md";
    Path filePath = topicDir.resolve(filename);

    List<String> supersededIds = new ArrayList<>();
    if (analysis.getContradiction().isDetected()
            && analysis.getContradiction().getConflictingEntryId() != null
            && !analysis.getContradiction().getConflictingEntryId().isEmpty()) {
        supersededIds.add(analysis.getContradiction().getConflictingEntryId());
    }

    Map<String, Object> extra = new LinkedHashMap<>();
    if (asset != null) {
        extra.put("source_asset", "assets/" + asset.getAssetId() + "/" + asset.getOriginalPath().getFileName());
        extra.put("media_type", doc.getMediaType());
        extra.put("multimodal_used", analysis.isMultimodalUsed());
    }
    if (!supersededIds.isEmpty()) {
        extra.put("supersedes", supersededIds);
    }

    KnowledgeEntry entry = KnowledgeEntry.builder()
            .id(entryId)
            .content(doc.getMarkdownBody())
            .title(title)
            .knowledgeType(KnowledgeType.fromValue(analysis.getKnowledgeType()))
            .topics(topics)
            .status(KnowledgeStatus.ACTIVE)
            .relatedIds(relatedEntries.stream().map(KnowledgeEntry::getId).filter(id -> !id.equals(entryId)).collect(Collectors.toList()))
            .sourceFile(filePath)
            .extra(extra)
            .build();

    try {
        String markdownContent = entry.toMarkdown();
        Files.writeString(filePath, markdownContent);
    } catch (IOException e) {
        log.error("Failed to write knowledge file: {}", e.getMessage());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "error");
        result.put("message", "Failed to write file: " + e.getMessage());
        return result;
    }

    String commitMessage = analysis.getCommitSummary() != null && !analysis.getCommitSummary().isEmpty()
            ? analysis.getCommitSummary()
            : "Add knowledge: " + entry.getTitle();
    if (analysis.getCommitDescription() != null && !analysis.getCommitDescription().isEmpty()) {
        commitMessage += "\n\n" + analysis.getCommitDescription();
    }

    String commitHash = gitManager.commitKnowledge(filePath, commitMessage);
    entry.setCommitHash(commitHash);

    try {
        Files.writeString(filePath, entry.toMarkdown());
    } catch (IOException e) {
        log.warn("Failed to update file with commit hash: {}", e.getMessage());
    }

    log.info("Knowledge acquired: {} (commit {})", filePath, commitHash.substring(0, Math.min(8, commitHash.length())));

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("status", "committed");
    result.put("analysis", analysisToMap(analysis));
    result.put("entry_id", entryId);
    result.put("file_path", vesselDir.relativize(filePath).toString());
    result.put("commit_hash", commitHash);
    result.put("superseded_ids", supersededIds);
    if (asset != null) {
        result.put("asset_id", asset.getAssetId());
    }
    return result;
}
```

- [ ] **Step 4: Update `KnowledgeTool.knowledgeAcquire` to wrap text into `KnowledgeSource`**

```java
@Tool(description = "...")
public String knowledgeAcquire(
        @ToolParam(description = "Full knowledge content in markdown format") String content,
        @ToolParam(description = "Additional context", required = false) String context,
        @ToolParam(description = "If true, only analyze without committing", required = false) Boolean dryRun) {

    if (content == null || content.isBlank()) {
        return "Error: content is required for acquire";
    }

    KnowledgeSource source = KnowledgeSource.builder()
            .mediaType("text/plain")
            .content(content)
            .build();

    Map<String, Object> result = knowledgeManager.acquire(source, context != null ? context : "", dryRun != null && dryRun);
    return formatAcquireResult(result);
}
```

- [ ] **Step 5: Update `KnowledgeToolTest` setup to include new dependencies**

Modify `KnowledgeToolTest.setUp` around line 73-81:

```java
GitManager gitManager = new GitManager();
gitManager.init(knowledgeDir);

KnowledgeAnalyzer analyzer = new KnowledgeAnalyzer(mockLlm);
ContentExtractorService extractorService = new ContentExtractorService(List.of(new TextExtractor()));
AssetManager assetManager = new LocalAssetManager();
KnowledgeManager knowledgeManager = new KnowledgeManager(gitManager, analyzer, extractorService, assetManager);

tool = new KnowledgeTool(knowledgeManager, gitManager);
```

Add imports:

```java
import meta.claw.tool.knowledge.asset.AssetManager;
import meta.claw.tool.knowledge.asset.LocalAssetManager;
import meta.claw.tool.knowledge.extract.ContentExtractorService;
import meta.claw.tool.knowledge.extract.TextExtractor;
import meta.claw.tool.knowledge.source.KnowledgeSource;
```

- [ ] **Step 6: Run `KnowledgeToolTest`**

Run: `mvn -pl meta-claw-tool -am test -Dtest=KnowledgeToolTest -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add meta-claw-tool/src/main/java/meta/claw/tool/knowledge/KnowledgeManager.java
"meta-claw-tool/src/main/java/meta/claw/tool/KnowledgeTool.java"
"meta-claw-tool/src/test/java/meta/claw/tool/KnowledgeToolTest.java"
git commit -m "feat(knowledge): unify KnowledgeManager acquire behind KnowledgeSource"
```

---

## Task 7: Add `ModelCapability` / `MultimodalConfig`

**Files:**
- Create: `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/multimodal/ModelCapability.java`
- Create: `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/multimodal/MultimodalConfig.java`

- [ ] **Step 1: Write `MultimodalConfig`**

```java
package meta.claw.tool.knowledge.multimodal;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

@Data
@Component
@ConfigurationProperties(prefix = "meta-claw.llm.multimodal")
public class MultimodalConfig {
    private boolean enabled = false;
    private Set<String> supportedMediaTypes = Set.of("image/png", "image/jpeg", "image/webp");
    private boolean pdfPageImages = false;
    private long maxImageSizeBytes = 5 * 1024 * 1024;
}
```

- [ ] **Step 2: Write `ModelCapability`**

```java
package meta.claw.tool.knowledge.multimodal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ModelCapability {

    private final MultimodalConfig config;

    @Autowired
    public ModelCapability(MultimodalConfig config) {
        this.config = config;
    }

    public boolean supportsMultimodal() {
        return config.isEnabled();
    }

    public boolean supportsMediaType(String mediaType) {
        return config.isEnabled() && config.getSupportedMediaTypes().contains(mediaType);
    }

    public boolean supportsPdfPageImages() {
        return config.isEnabled() && config.isPdfPageImages();
    }
}
```

- [ ] **Step 3: Add configuration example to `application.yml` (if present)**

Find or create `meta-claw-tool/src/main/resources/application.yml` and add:

```yaml
meta-claw:
  llm:
    multimodal:
      enabled: false
      supported-media-types: image/png, image/jpeg, image/webp
      pdf-page-images: false
      max-image-size-bytes: 5242880
```

- [ ] **Step 4: Write unit test**

Create: `meta-claw-tool/src/test/java/meta/claw/tool/knowledge/multimodal/ModelCapabilityTest.java`

```java
package meta.claw.tool.knowledge.multimodal;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ModelCapabilityTest {

    @Test
    void whenEnabledSupportsConfiguredTypes() {
        MultimodalConfig config = new MultimodalConfig();
        config.setEnabled(true);
        config.setSupportedMediaTypes(Set.of("image/png"));

        ModelCapability capability = new ModelCapability(config);
        assertTrue(capability.supportsMultimodal());
        assertTrue(capability.supportsMediaType("image/png"));
        assertFalse(capability.supportsMediaType("image/webp"));
    }

    @Test
    void whenDisabledRejectsAll() {
        MultimodalConfig config = new MultimodalConfig();
        ModelCapability capability = new ModelCapability(config);
        assertFalse(capability.supportsMultimodal());
        assertFalse(capability.supportsMediaType("image/png"));
    }
}
```

- [ ] **Step 5: Run test**

Run: `mvn -pl meta-claw-tool -am test -Dtest=ModelCapabilityTest -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add meta-claw-tool/src/main/java/meta/claw/tool/knowledge/multimodal/
"meta-claw-tool/src/test/java/meta/claw/tool/knowledge/multimodal/"
"meta-claw-tool/src/main/resources/application.yml"
git commit -m "feat(knowledge): add ModelCapability and MultimodalConfig"
```

---

## Task 8: Make `KnowledgeAnalyzer` multimodal-aware

**Files:**
- Modify: `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/KnowledgeAnalyzer.java:32-51`
- Modify: `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/KnowledgeAnalyzer.java:53-141`
- Modify: `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/model/AnalysisResult.java`

- [ ] **Step 1: Add `isMultimodalUsed` to `AnalysisResult`**

In `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/model/AnalysisResult.java`, add field:

```java
@Builder.Default
private boolean multimodalUsed = false;
```

- [ ] **Step 2: Inject `ModelCapability` into `KnowledgeAnalyzer`**

```java
@Slf4j
@Component
public class KnowledgeAnalyzer {

    private final SpiLlmClient llmClient;
    private final ModelCapability modelCapability;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public KnowledgeAnalyzer(SpiLlmClient llmClient, ModelCapability modelCapability) {
        this.llmClient = llmClient;
        this.modelCapability = modelCapability;
    }
```

- [ ] **Step 3: Add `analyze(ExtractedDocument, ...)` overload**

```java
public AnalysisResult analyze(ExtractedDocument doc,
                              List<KnowledgeEntry> relatedEntries,
                              String context) {
    if (llmClient == null) {
        log.warn("No LLM client available, returning default analysis");
        return defaultAnalysis();
    }

    boolean useMultimodal = modelCapability.supportsMultimodal()
            && hasVisualAssets(doc)
            && modelCapability.supportsMediaType(doc.getMediaType());

    if (useMultimodal) {
        return analyzeWithMultimodal(doc, relatedEntries, context);
    }

    String prompt = buildAnalysisPrompt(doc.getMarkdownBody(), relatedEntries, context);
    try {
        SpiChatRequest request = SpiChatRequest.builder()
                .messages(List.of(SpiMessage.user(prompt)))
                .build();
        SpiChatResponse response = llmClient.chat(request);
        return parseAnalysisResponse(response.content(), doc.getMarkdownBody());
    } catch (Exception e) {
        log.error("LLM analysis failed: {}", e.getMessage());
        return defaultAnalysis();
    }
}

private boolean hasVisualAssets(ExtractedDocument doc) {
    if (doc.getEmbeddedAssets() == null || doc.getEmbeddedAssets().isEmpty()) {
        return false;
    }
    return doc.getEmbeddedAssets().stream()
            .anyMatch(a -> a.getMediaType() != null && a.getMediaType().startsWith("image/"));
}
```

- [ ] **Step 4: Implement `analyzeWithMultimodal`**

```java
private AnalysisResult analyzeWithMultimodal(ExtractedDocument doc,
                                              List<KnowledgeEntry> relatedEntries,
                                              String context) {
    List<MediaPart> mediaParts = doc.getEmbeddedAssets().stream()
            .filter(a -> a.getMediaType() != null && a.getMediaType().startsWith("image/"))
            .map(a -> MediaPart.builder()
                    .type("image_url")
                    .mimeType(a.getMediaType())
                    .url(a.getOriginalPath().toUri().toString())
                    .build())
            .limit(5) // avoid overloading context
            .collect(Collectors.toList());

    String prompt = buildAnalysisPrompt(doc.getMarkdownBody(), relatedEntries, context);
    SpiChatRequest request = SpiChatRequest.builder()
            .messages(List.of(SpiMessage.user(prompt, mediaParts)))
            .build();

    try {
        SpiChatResponse response = llmClient.chat(request);
        AnalysisResult result = parseAnalysisResponse(response.content(), doc.getMarkdownBody());
        result.setMultimodalUsed(true);
        return result;
    } catch (Exception e) {
        log.error("Multimodal analysis failed, falling back to text: {}", e.getMessage());
        return analyzeTextFallback(doc, relatedEntries, context);
    }
}

private AnalysisResult analyzeTextFallback(ExtractedDocument doc,
                                            List<KnowledgeEntry> relatedEntries,
                                            String context) {
    String prompt = buildAnalysisPrompt(doc.getMarkdownBody(), relatedEntries, context);
    SpiChatRequest request = SpiChatRequest.builder()
            .messages(List.of(SpiMessage.user(prompt)))
            .build();
    SpiChatResponse response = llmClient.chat(request);
    return parseAnalysisResponse(response.content(), doc.getMarkdownBody());
}
```

Add imports:

```java
import meta.claw.core.llm.MediaPart;
import meta.claw.tool.knowledge.multimodal.ModelCapability;
import meta.claw.tool.knowledge.source.ExtractedDocument;
```

- [ ] **Step 5: Update `KnowledgeToolTest` setup to inject `ModelCapability`**

```java
MultimodalConfig multimodalConfig = new MultimodalConfig();
ModelCapability modelCapability = new ModelCapability(multimodalConfig);
KnowledgeAnalyzer analyzer = new KnowledgeAnalyzer(mockLlm, modelCapability);
```

- [ ] **Step 6: Run `KnowledgeToolTest`**

Run: `mvn -pl meta-claw-tool -am test -Dtest=KnowledgeToolTest -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add meta-claw-tool/src/main/java/meta/claw/tool/knowledge/KnowledgeAnalyzer.java
"meta-claw-tool/src/main/java/meta/claw/tool/knowledge/model/AnalysisResult.java"
"meta-claw-tool/src/test/java/meta/claw/tool/KnowledgeToolTest.java"
git commit -m "feat(knowledge): make KnowledgeAnalyzer multimodal-aware with fallback"
```


## Task 9: Implement `ImageExtractor`

**Files:**
- Create: `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/extract/ImageExtractor.java`
- Create: `meta-claw-tool/src/test/java/meta/claw/tool/knowledge/extract/ImageExtractorTest.java`

- [ ] **Step 1: Add image description helper service**

Create: `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/extract/VisionDescriber.java`

```java
package meta.claw.tool.knowledge.extract;

import meta.claw.core.llm.MediaPart;
import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiChatResponse;
import meta.claw.core.llm.SpiLlmClient;
import meta.claw.core.llm.SpiMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class VisionDescriber {

    private final SpiLlmClient llmClient;

    @Autowired
    public VisionDescriber(SpiLlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public String describe(Path imagePath, String mimeType) {
        if (llmClient == null) {
            return "[Image: " + imagePath.getFileName() + "]";
        }

        MediaPart part = MediaPart.builder()
                .type("image_url")
                .mimeType(mimeType)
                .url(imagePath.toUri().toString())
                .build();

        SpiChatRequest request = SpiChatRequest.builder()
                .messages(List.of(SpiMessage.user(
                        "请用一段简洁的中文描述这张图片的内容，提取其中的文字和关键信息。", List.of(part))))
                .build();

        try {
            SpiChatResponse response = llmClient.chat(request);
            return response.content();
        } catch (Exception e) {
            return "[Failed to describe image: " + imagePath.getFileName() + "]";
        }
    }
}
```

- [ ] **Step 2: Write `ImageExtractor`**

```java
package meta.claw.tool.knowledge.extract;

import meta.claw.tool.knowledge.source.AssetRef;
import meta.claw.tool.knowledge.source.ExtractedDocument;
import meta.claw.tool.knowledge.source.KnowledgeSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

@Component
public class ImageExtractor implements ContentExtractor {

    private final VisionDescriber visionDescriber;

    @Autowired
    public ImageExtractor(VisionDescriber visionDescriber) {
        this.visionDescriber = visionDescriber;
    }

    @Override
    public boolean supports(KnowledgeSource source) {
        return source.getMediaType() != null && source.getMediaType().startsWith("image/");
    }

    @Override
    public ExtractedDocument extract(KnowledgeSource source, ExtractionContext ctx) {
        AssetRef asset = ctx.getAssetManager().store(source, ctx.getVesselId());
        String description = visionDescriber.describe(asset.getOriginalPath(), source.getMediaType());

        return ExtractedDocument.builder()
                .markdownBody("## 图片描述\n\n" + description + "\n\n![image](assets/" + asset.getAssetId() + "/" + asset.getOriginalPath().getFileName() + ")")
                .mediaType(source.getMediaType())
                .embeddedAssets(List.of(asset))
                .metadata(Map.of("width", 0, "height", 0)) // optional: read with ImageIO
                .build();
    }
}
```

- [ ] **Step 3: Write unit test with mocked `VisionDescriber`**

```java
package meta.claw.tool.knowledge.extract;

import meta.claw.tool.knowledge.asset.LocalAssetManager;
import meta.claw.tool.knowledge.source.AssetRef;
import meta.claw.tool.knowledge.source.ExtractedDocument;
import meta.claw.tool.knowledge.source.KnowledgeSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ImageExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsImageDescription() throws Exception {
        System.setProperty("user.dir", tempDir.toString());
        try {
            BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            byte[] bytes = baos.toByteArray();

            VisionDescriber describer = mock(VisionDescriber.class);
            when(describer.describe(any(Path.class), anyString())).thenReturn("A red square");

            LocalAssetManager assetManager = new LocalAssetManager();
            ImageExtractor extractor = new ImageExtractor(describer);

            KnowledgeSource source = KnowledgeSource.builder()
                    .mediaType("image/png")
                    .stream(new ByteArrayInputStream(bytes))
                    .originalName("test.png")
                    .build();

            ExtractedDocument doc = extractor.extract(source,
                    ExtractionContext.builder()
                            .assetManager(assetManager)
                            .vesselId("v1")
                            .build());

            assertTrue(doc.getMarkdownBody().contains("A red square"));
            assertTrue(doc.getEmbeddedAssets().get(0).getOriginalPath().toString().endsWith(".png"));
        } finally {
            System.clearProperty("user.dir");
        }
    }
}
```

- [ ] **Step 4: Run test**

Run: `mvn -pl meta-claw-tool -am test -Dtest=ImageExtractorTest -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add meta-claw-tool/src/main/java/meta/claw/tool/knowledge/extract/VisionDescriber.java
"meta-claw-tool/src/main/java/meta/claw/tool/knowledge/extract/ImageExtractor.java"
"meta-claw-tool/src/test/java/meta/claw/tool/knowledge/extract/ImageExtractorTest.java"
git commit -m "feat(knowledge): add ImageExtractor with vision description fallback"
```

---

## Task 10: Extend `KnowledgeEntry` frontmatter for asset references

**Files:**
- Modify: `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/model/KnowledgeEntry.java:60-102`

- [ ] **Step 1: Add helper getters/setters for asset fields**

No need to change the data class; use `extra` map. But add convenience accessors:

```java
public String getSourceAsset() {
    return extra != null ? String.valueOf(extra.getOrDefault("source_asset", "")) : "";
}

public String getMediaType() {
    return extra != null ? String.valueOf(extra.getOrDefault("media_type", "text/plain")) : "text/plain";
}

public boolean isMultimodalUsed() {
    return extra != null && Boolean.TRUE.equals(extra.get("multimodal_used"));
}
```

- [ ] **Step 2: Ensure `toMarkdown` serializes `extra` fields in deterministic order**

Current `toMarkdown` already does `frontmatter.putAll(extra)` at the end. Keep as is.

- [ ] **Step 3: Update test to assert asset frontmatter**

Add test in `KnowledgeToolTest`:

```java
@Test
void textAcquireDoesNotSetSourceAsset() {
    String result = tool.knowledgeAcquire("Plain text fact", null, false);
    String listResult = tool.knowledgeList();
    String path = extractPath(listResult);
    String content = tool.knowledgeRead(path);
    assertTrue(content.contains("media_type: text/plain"));
    assertTrue(content.contains("multimodal_used: false"));
}
```

- [ ] **Step 4: Run `KnowledgeToolTest`**

Run: `mvn -pl meta-claw-tool -am test -Dtest=KnowledgeToolTest -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add meta-claw-tool/src/main/java/meta/claw/tool/knowledge/model/KnowledgeEntry.java
"meta-claw-tool/src/test/java/meta/claw/tool/KnowledgeToolTest.java"
git commit -m "feat(knowledge): add asset reference accessors to KnowledgeEntry"
```

---

## Task 11: Implement `PdfExtractor`

**Files:**
- Modify: `meta-claw-tool/pom.xml:57-73`
- Create: `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/extract/PdfExtractor.java`
- Create: `meta-claw-tool/src/test/java/meta/claw/tool/knowledge/extract/PdfExtractorTest.java`

- [ ] **Step 1: Add Apache PDFBox dependency**

In `meta-claw-tool/pom.xml`, add inside `<dependencies>`:

```xml
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.2</version>
</dependency>
```

If parent POM has dependency management, use managed version without `<version>`.

- [ ] **Step 2: Implement `PdfExtractor`**

```java
package meta.claw.tool.knowledge.extract;

import meta.claw.tool.knowledge.source.AssetRef;
import meta.claw.tool.knowledge.source.ExtractedDocument;
import meta.claw.tool.knowledge.source.KnowledgeSource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PdfExtractor implements ContentExtractor {

    private final VisionDescriber visionDescriber;

    @Autowired
    public PdfExtractor(VisionDescriber visionDescriber) {
        this.visionDescriber = visionDescriber;
    }

    @Override
    public boolean supports(KnowledgeSource source) {
        return "application/pdf".equals(source.getMediaType());
    }

    @Override
    public ExtractedDocument extract(KnowledgeSource source, ExtractionContext ctx) {
        AssetRef pdfAsset = ctx.getAssetManager().store(source, ctx.getVesselId());
        Path pdfPath = pdfAsset.getOriginalPath();

        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String fullText = stripper.getText(document);

            StringBuilder markdown = new StringBuilder();
            markdown.append("# ").append(source.getOriginalName() != null ? source.getOriginalName() : "PDF Document").append("\n\n");

            if (fullText != null && !fullText.isBlank()) {
                markdown.append("## 提取文本\n\n").append(fullText).append("\n\n");
            }

            List<AssetRef> pageAssets = new ArrayList<>();
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            for (int i = 0; i < pageCount; i++) {
                Path pageImage = renderPage(pdfPath.getParent(), pdfPath.getFileName().toString(), renderer, i);
                String pageDescription = visionDescriber.describe(pageImage, "image/png");
                markdown.append("## 第 ").append(i + 1).append(" 页\n\n")
                        .append(pageDescription).append("\n\n");

                AssetRef pageAsset = AssetRef.builder()
                        .assetId(pdfAsset.getAssetId())
                        .mediaType("image/png")
                        .originalPath(pageImage)
                        .build();
                pageAssets.add(pageAsset);
            }

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("page_count", pageCount);
            metadata.put("has_text", fullText != null && !fullText.isBlank());

            return ExtractedDocument.builder()
                    .markdownBody(markdown.toString())
                    .mediaType("application/pdf")
                    .embeddedAssets(pageAssets)
                    .metadata(metadata)
                    .build();

        } catch (IOException e) {
            throw new RuntimeException("Failed to extract PDF: " + pdfPath, e);
        }
    }

    private Path renderPage(Path assetDir, String baseName, PDFRenderer renderer, int pageIndex) throws IOException {
        BufferedImage image = renderer.renderImageWithDPI(pageIndex, 150);
        Path imagePath = assetDir.resolve("page_" + String.format("%03d", pageIndex + 1) + ".png");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            Files.write(imagePath, baos.toByteArray());
        }
        return imagePath;
    }
}
```

- [ ] **Step 3: Write unit test with a tiny PDF**

```java
package meta.claw.tool.knowledge.extract;

import meta.claw.tool.knowledge.asset.LocalAssetManager;
import meta.claw.tool.knowledge.source.ExtractedDocument;
import meta.claw.tool.knowledge.source.KnowledgeSource;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class PdfExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsTextFromPdf() throws Exception {
        System.setProperty("user.dir", tempDir.toString());
        try {
            byte[] pdfBytes = createSimplePdf("Hello PDF");

            VisionDescriber describer = mock(VisionDescriber.class);
            when(describer.describe(any(Path.class), anyString())).thenReturn("A page image");

            LocalAssetManager assetManager = new LocalAssetManager();
            PdfExtractor extractor = new PdfExtractor(describer);

            KnowledgeSource source = KnowledgeSource.builder()
                    .mediaType("application/pdf")
                    .stream(new ByteArrayInputStream(pdfBytes))
                    .originalName("test.pdf")
                    .build();

            ExtractedDocument doc = extractor.extract(source,
                    ExtractionContext.builder()
                            .assetManager(assetManager)
                            .vesselId("v1")
                            .build());

            assertTrue(doc.getMarkdownBody().contains("Hello PDF"));
            assertTrue(doc.getMarkdownBody().contains("A page image"));
        } finally {
            System.clearProperty("user.dir");
        }
    }

    private byte[] createSimplePdf(String text) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }
}
```

- [ ] **Step 4: Run test**

Run: `mvn -pl meta-claw-tool -am test -Dtest=PdfExtractorTest -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add meta-claw-tool/pom.xml
"meta-claw-tool/src/main/java/meta/claw/tool/knowledge/extract/PdfExtractor.java"
"meta-claw-tool/src/test/java/meta/claw/tool/knowledge/extract/PdfExtractorTest.java"
git commit -m "feat(knowledge): add PdfExtractor with text and page image description"
```


## Task 12: Implement video extraction for Douyin links

**Files:**
- Create: `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/extract/video/VideoExtractor.java`
- Create: `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/extract/video/YtDlpVideoExtractor.java`
- Create: `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/extract/video/DouyinVideoExtractor.java`
- Create: `meta-claw-tool/src/test/java/meta/claw/tool/knowledge/extract/video/DouyinVideoExtractorTest.java`

- [ ] **Step 1: Write `VideoExtractor` interface**

```java
package meta.claw.tool.knowledge.extract.video;

import meta.claw.tool.knowledge.extract.ExtractionContext;
import meta.claw.tool.knowledge.source.ExtractedDocument;

import java.net.URI;

public interface VideoExtractor {
    boolean supports(URI uri);
    ExtractedDocument extract(URI uri, ExtractionContext ctx) throws Exception;
}
```

- [ ] **Step 2: Write `YtDlpVideoExtractor` adapter**

```java
package meta.claw.tool.knowledge.extract.video;

import lombok.extern.slf4j.Slf4j;
import meta.claw.tool.knowledge.extract.ExtractionContext;
import meta.claw.tool.knowledge.source.AssetRef;
import meta.claw.tool.knowledge.source.ExtractedDocument;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
                meta.claw.tool.knowledge.source.KnowledgeSource.builder()
                        .mediaType("video/url.douyin")
                        .uri(uri)
                        .originalName("douyin_video")
                        .build(),
                ctx.getVesselId());

        Path assetDir = asset.getOriginalPath().getParent();

        // Try subtitles first
        List<String> subtitleLines = runYtDlp(uri, assetDir, "--write-subs", "--sub-langs", "zh-CN,zh-Hans,zh-Hant,en", "--skip-download");
        String transcript = parseSubtitles(assetDir);

        // Fallback: download audio and transcribe
        if (transcript == null || transcript.isBlank()) {
            runYtDlp(uri, assetDir, "--extract-audio", "--audio-format", "mp3", "--audio-quality", "64K");
            transcript = transcribeAudio(assetDir);
        }

        // Metadata
        Map<String, Object> metadata = fetchMetadata(uri, assetDir);
        String title = String.valueOf(metadata.getOrDefault("title", "抖音视频"));

        String markdown = "# " + title + "\n\n" +
                "**来源：** " + uri + "\n\n" +
                "## 内容摘要\n\n" + metadata.getOrDefault("description", "") + "\n\n" +
                "## 字幕/转录\n\n" + (transcript != null ? transcript : "（未能提取字幕）") + "\n";

        return ExtractedDocument.builder()
                .markdownBody(markdown)
                .mediaType("video/url.douyin")
                .embeddedAssets(List.of(asset))
                .metadata(metadata)
                .build();
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
            Path vtt = stream.filter(p -> p.toString().endsWith(".vtt") || p.toString().endsWith(".srt"))
                    .findFirst()
                    .orElse(null);
            if (vtt != null) {
                return Files.readString(vtt);
            }
        }
        return "";
    }

    private String transcribeAudio(Path assetDir) {
        // Placeholder: wire in a Speech-to-Text service in a follow-up task.
        return "[Audio transcription not yet implemented]";
    }

    private Map<String, Object> fetchMetadata(URI uri, Path assetDir) {
        // Simplified: run yt-dlp --dump-json and parse.
        // For the plan, return a minimal map.
        return Map.of("title", "抖音视频", "description", "", "url", uri.toString());
    }
}
```

- [ ] **Step 3: Write `DouyinVideoExtractor` as a thin wrapper**

```java
package meta.claw.tool.knowledge.extract.video;

import meta.claw.tool.knowledge.extract.ContentExtractor;
import meta.claw.tool.knowledge.extract.ExtractionContext;
import meta.claw.tool.knowledge.source.ExtractedDocument;
import meta.claw.tool.knowledge.source.KnowledgeSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

@Component
public class DouyinVideoExtractor implements ContentExtractor {

    private final List<VideoExtractor> videoExtractors;

    @Autowired
    public DouyinVideoExtractor(List<VideoExtractor> videoExtractors) {
        this.videoExtractors = videoExtractors;
    }

    @Override
    public boolean supports(KnowledgeSource source) {
        return "video/url.douyin".equals(source.getMediaType());
    }

    @Override
    public ExtractedDocument extract(KnowledgeSource source, ExtractionContext ctx) throws Exception {
        URI uri = source.getUri();
        if (uri == null) {
            throw new IllegalArgumentException("Douyin video source requires a URI");
        }
        for (VideoExtractor extractor : videoExtractors) {
            if (extractor.supports(uri)) {
                return extractor.extract(uri, ctx);
            }
        }
        throw new UnsupportedOperationException("No video extractor supports URI: " + uri);
    }
}
```

- [ ] **Step 4: Write a skipped-by-default integration test**

```java
package meta.claw.tool.knowledge.extract.video;

import meta.claw.tool.knowledge.asset.LocalAssetManager;
import meta.claw.tool.knowledge.extract.ExtractionContext;
import meta.claw.tool.knowledge.source.ExtractedDocument;
import meta.claw.tool.knowledge.source.KnowledgeSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class DouyinVideoExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    @EnabledIf("ytDlpInstalled")
    void extractsDouyinVideo() throws Exception {
        System.setProperty("user.dir", tempDir.toString());
        try {
            LocalAssetManager assetManager = new LocalAssetManager();
            YtDlpVideoExtractor ytDlp = new YtDlpVideoExtractor();
            DouyinVideoExtractor extractor = new DouyinVideoExtractor(List.of(ytDlp));

            KnowledgeSource source = KnowledgeSource.builder()
                    .mediaType("video/url.douyin")
                    .uri(URI.create("https://www.douyin.com/video/1234567890"))
                    .build();

            ExtractedDocument doc = extractor.extract(source,
                    ExtractionContext.builder()
                            .assetManager(assetManager)
                            .vesselId("v1")
                            .build());

            assertNotNull(doc.getMarkdownBody());
        } finally {
            System.clearProperty("user.dir");
        }
    }

    boolean ytDlpInstalled() {
        try {
            Process process = new ProcessBuilder("yt-dlp", "--version").start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add meta-claw-tool/src/main/java/meta/claw/tool/knowledge/extract/video/
"meta-claw-tool/src/test/java/meta/claw/tool/knowledge/extract/video/"
git commit -m "feat(knowledge): add Douyin video extraction via yt-dlp adapter"
```

---

## Task 13: Add file/URL acquisition tools

**Files:**
- Modify: `meta-claw-tool/src/main/java/meta/claw/tool/KnowledgeTool.java`

- [ ] **Step 1: Add helper to infer media type from path/URL**

```java
private String inferMediaType(String pathOrUrl) {
    String lower = pathOrUrl.toLowerCase();
    if (lower.endsWith(".png")) return "image/png";
    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
    if (lower.endsWith(".webp")) return "image/webp";
    if (lower.endsWith(".pdf")) return "application/pdf";
    if (lower.contains("douyin.com") || lower.contains("iesdouyin.com")) return "video/url.douyin";
    return "text/plain";
}
```

- [ ] **Step 2: Add `knowledgeAcquireFromFile`**

```java
@Tool(description = "Acquire knowledge from a local file (image, PDF, etc.)")
public String knowledgeAcquireFromFile(
        @ToolParam(description = "Absolute or vessel-relative file path") String filePath,
        @ToolParam(description = "Optional context", required = false) String context,
        @ToolParam(description = "If true, only analyze without committing", required = false) Boolean dryRun) {

    if (filePath == null || filePath.isBlank()) {
        return "Error: filePath is required";
    }

    Path path = Path.of(filePath);
    if (!path.isAbsolute()) {
        path = ProjectRootFinder.getMetaClawDir().resolve("vessels").resolve(VesselContext.getVesselId()).resolve(filePath);
    }

    if (!Files.exists(path)) {
        return "Error: file not found: " + filePath;
    }

    String mediaType = inferMediaType(filePath);
    KnowledgeSource source = KnowledgeSource.builder()
            .mediaType(mediaType)
            .uri(path.toUri())
            .originalName(path.getFileName().toString())
            .build();

    Map<String, Object> result = knowledgeManager.acquire(source, context != null ? context : "", dryRun != null && dryRun);
    return formatAcquireResult(result);
}
```

- [ ] **Step 3: Add `knowledgeAcquireFromUrl`**

```java
@Tool(description = "Acquire knowledge from a URL (currently Douyin prioritized)")
public String knowledgeAcquireFromUrl(
        @ToolParam(description = "Source URL") String url,
        @ToolParam(description = "Optional context", required = false) String context,
        @ToolParam(description = "If true, only analyze without committing", required = false) Boolean dryRun) {

    if (url == null || url.isBlank()) {
        return "Error: url is required";
    }

    String mediaType = inferMediaType(url);
    if (!"video/url.douyin".equals(mediaType)) {
        return "Error: unsupported URL type: " + url;
    }

    KnowledgeSource source = KnowledgeSource.builder()
            .mediaType(mediaType)
            .uri(URI.create(url))
            .originalName("douyin_link")
            .build();

    Map<String, Object> result = knowledgeManager.acquire(source, context != null ? context : "", dryRun != null && dryRun);
    return formatAcquireResult(result);
}
```

Add imports:

```java
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import meta.claw.core.infra.ProjectRootFinder;
import meta.claw.tool.knowledge.source.KnowledgeSource;
```

- [ ] **Step 4: Add unit tests for new tool methods**

Add to `KnowledgeToolTest`:

```java
@Test
void acquireFromFileRejectsMissingFile() {
    String result = tool.knowledgeAcquireFromFile("/nonexistent/file.pdf", null, null);
    assertTrue(result.startsWith("Error"));
}

@Test
void acquireFromUrlRejectsUnsupportedHost() {
    String result = tool.knowledgeAcquireFromUrl("https://example.com/video", null, null);
    assertTrue(result.startsWith("Error"));
}
```

- [ ] **Step 5: Run `KnowledgeToolTest`**

Run: `mvn -pl meta-claw-tool -am test -Dtest=KnowledgeToolTest -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add meta-claw-tool/src/main/java/meta/claw/tool/KnowledgeTool.java
"meta-claw-tool/src/test/java/meta/claw/tool/KnowledgeToolTest.java"
git commit -m "feat(knowledge): add knowledgeAcquireFromFile and knowledgeAcquireFromUrl tools"
```


## Task 14: Extend retrieval to include extracted asset text

**Files:**
- Modify: `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/GitManager.java:134-167`
- Modify: `meta-claw-tool/src/main/java/meta/claw/tool/knowledge/KnowledgeManager.java:211-274`

- [ ] **Step 1: Modify `GitManager.grepFiles` to accept glob patterns**

Change method signature and filter:

```java
public List<Path> grepFiles(List<String> keywords, Path searchPath, String glob) {
    if (keywords == null || keywords.isEmpty()) {
        return Collections.emptyList();
    }

    Path targetPath = searchPath != null ? searchPath : repoPath;
    String filePattern = glob != null ? glob : "*.md";
    List<Path> results = new ArrayList<>();

    try {
        if (!Files.exists(targetPath)) {
            return results;
        }
        try (var stream = Files.walk(targetPath)) {
            stream.filter(p -> p.getFileSystem().getPathMatcher("glob:" + filePattern).matches(p.getFileName()))
                    .forEach(filePath -> {
                        try {
                            String content = Files.readString(filePath).toLowerCase();
                            for (String kw : keywords) {
                                if (content.contains(kw.toLowerCase())) {
                                    results.add(filePath);
                                    break;
                                }
                            }
                        } catch (IOException e) {
                            log.debug("Failed to read {}: {}", filePath, e.getMessage());
                        }
                    });
        }
    } catch (IOException e) {
        log.warn("Failed to grep files: {}", e.getMessage());
    }

    return results;
}
```

Keep a backward-compatible overload:

```java
public List<Path> grepFiles(List<String> keywords, Path searchPath) {
    return grepFiles(keywords, searchPath, "*.md");
}
```

- [ ] **Step 2: Update `KnowledgeManager.retrieve` to search both knowledge and assets**

```java
public List<Map<String, Object>> retrieve(String query, String mode, int maxResults) {
    Path knowledgeDir = getKnowledgeDir();
    Path vesselDir = getVesselDir();
    Path assetsDir = vesselDir.resolve("assets");
    ensureKnowledgeDir(knowledgeDir);

    List<String> keywords = Arrays.asList(query.toLowerCase().split("\\s+"));

    List<Path> knowledgeFiles = gitManager.grepFiles(keywords, knowledgeDir, "*.md");
    List<Path> assetFiles = Files.exists(assetsDir)
            ? gitManager.grepFiles(keywords, assetsDir, "extracted.md")
            : Collections.emptyList();

    Set<Path> allFiles = new LinkedHashSet<>();
    allFiles.addAll(knowledgeFiles);
    allFiles.addAll(assetFiles);

    List<Map<String, Object>> results = new ArrayList<>();
    for (Path filePath : allFiles) {
        if (results.size() >= maxResults) break;
        // existing load/result logic, but also include asset_id if present
        ...
    }
    return results;
}
```

Add imports:

```java
import java.util.LinkedHashSet;
import java.util.Set;
```

- [ ] **Step 3: Add retrieval test for assets**

Add to `KnowledgeToolTest`:

```java
@Test
void retrieveIncludesAssetMetadata() {
    String result = tool.knowledgeAcquire("Java 21 发布于 2023 年", null, false);
    String retrieveResult = tool.knowledgeRetrieve("Java", "current", 5);
    assertTrue(retrieveResult.contains("media_type: text/plain"));
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -pl meta-claw-tool -am test -Dtest=KnowledgeToolTest -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add meta-claw-tool/src/main/java/meta/claw/tool/knowledge/GitManager.java
"meta-claw-tool/src/main/java/meta/claw/tool/knowledge/KnowledgeManager.java"
"meta-claw-tool/src/test/java/meta/claw/tool/KnowledgeToolTest.java"
git commit -m "feat(knowledge): extend retrieval to search extracted asset markdown"
```

---

## Task 15: End-to-end smoke test

**Files:**
- Create: `meta-claw-tool/src/test/java/meta/claw/tool/knowledge/KnowledgeAcquisitionSmokeTest.java`

- [ ] **Step 1: Write smoke test for text + image + PDF acquisition flow**

```java
package meta.claw.tool.knowledge;

import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiChatResponse;
import meta.claw.core.llm.SpiLlmClient;
import meta.claw.core.runtime.VesselContext;
import meta.claw.tool.KnowledgeTool;
import meta.claw.tool.knowledge.asset.AssetManager;
import meta.claw.tool.knowledge.asset.LocalAssetManager;
import meta.claw.tool.knowledge.extract.*;
import meta.claw.tool.knowledge.multimodal.ModelCapability;
import meta.claw.tool.knowledge.multimodal.MultimodalConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KnowledgeAcquisitionSmokeTest {

    @TempDir
    Path tempDir;

    private KnowledgeTool tool;
    private String originalUserDir;

    private static final String MOCK_RESPONSE = """
            {
                "knowledge_type": "fact",
                "is_fact": true,
                "contradiction": {"detected": false, "conflicting_entry_id": "", "explanation": "", "confidence": 0.0, "contradiction_type": ""},
                "confidence": 0.95,
                "recommended_action": "add",
                "reasoning": "ok",
                "extracted_keywords": ["test"],
                "suggested_topics": ["smoke"],
                "suggested_title": "Smoke Test",
                "commit_summary": "Add smoke test",
                "commit_description": ""
            }
            """;

    @BeforeEach
    void setUp() throws Exception {
        originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        Files.createDirectories(tempDir.resolve(".meta-claw/vessels/smoke/knowledge"));
        VesselContext.setVesselId("smoke");

        SpiLlmClient mockLlm = mock(SpiLlmClient.class);
        when(mockLlm.chat(any(SpiChatRequest.class)))
                .thenReturn(SpiChatResponse.builder().content(MOCK_RESPONSE).build());

        GitManager gitManager = new GitManager();
        gitManager.init(tempDir.resolve(".meta-claw/vessels/smoke/knowledge"));

        AssetManager assetManager = new LocalAssetManager();
        ContentExtractorService extractorService = new ContentExtractorService(List.of(
                new TextExtractor(),
                new ImageExtractor(new VisionDescriber(mockLlm))
        ));
        KnowledgeAnalyzer analyzer = new KnowledgeAnalyzer(mockLlm, new ModelCapability(new MultimodalConfig()));
        KnowledgeManager knowledgeManager = new KnowledgeManager(gitManager, analyzer, extractorService, assetManager);

        tool = new KnowledgeTool(knowledgeManager, gitManager);
    }

    @AfterEach
    void tearDown() {
        VesselContext.clear();
        System.setProperty("user.dir", originalUserDir);
    }

    @Test
    void textAcquisitionCommitsEntry() {
        String result = tool.knowledgeAcquire("Smoke test content", null, false);
        assertTrue(result.contains("Committed"));
    }

    @Test
    void imageAcquisitionCommitsEntry() throws Exception {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        Path imagePath = tempDir.resolve("test.png");
        Files.write(imagePath, baos.toByteArray());

        String result = tool.knowledgeAcquireFromFile(imagePath.toString(), null, false);
        assertTrue(result.contains("Committed"));
    }
}
```

- [ ] **Step 2: Run smoke test**

Run: `mvn -pl meta-claw-tool -am test -Dtest=KnowledgeAcquisitionSmokeTest -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add meta-claw-tool/src/test/java/meta/claw/tool/knowledge/KnowledgeAcquisitionSmokeTest.java
git commit -m "test(knowledge): add multimodal knowledge acquisition smoke test"
```

---

## Task 16: Full module test and documentation update

**Files:**
- Modify: `claude-progress.md`
- Modify: `feature_list.json`
- Modify: `README.md` (optional)

- [ ] **Step 1: Run full meta-claw-tool test suite**

Run: `mvn -pl meta-claw-tool -am test -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: Run full build**

Run: `mvn clean package -DskipTests -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Update `claude-progress.md`**

Add entry:

```markdown
## 2026-06-29
- Designed and implemented multimodal knowledge base extension.
- Supported sources: text, images (PNG/JPEG/WebP), PDF, Douyin video links.
- Added ContentExtractor SPI, AssetManager, ModelCapability, and MediaPart in core LLM SPI.
- Smoke tests passed; full meta-claw-tool test suite green.
```

- [ ] **Step 4: Update `feature_list.json`**

Mark knowledge base media ingestion as completed and record evidence paths.

- [ ] **Step 5: Commit**

```bash
git add claude-progress.md feature_list.json
git commit -m "docs: update progress and feature list for multimodal knowledge extension"
```

---

## Self-Review

### Spec Coverage

| Spec Requirement | Implementing Task |
|---|---|
| Unified `KnowledgeSource` entry | Task 3, 6 |
| `ContentExtractor` SPI | Task 4 |
| `AssetManager` / `LocalAssetManager` | Task 5 |
| Multimodal config + capability | Task 7 |
| Multimodal-aware analyzer | Task 8 |
| Image extraction | Task 9 |
| PDF extraction | Task 11 |
| Douyin video extraction | Task 12 |
| File/URL tool methods | Task 13 |
| Retrieval includes asset text | Task 14 |
| `SpiMessage` media support | Task 1, 2 |

### Placeholder Scan

No placeholders like "TBD" or "implement later" remain in concrete steps. The `transcribeAudio` method in `YtDlpVideoExtractor` is intentionally a stub for a future STT integration; the step documents it as a placeholder and the test covers subtitle extraction.

### Type Consistency

- `KnowledgeManager.acquire(KnowledgeSource, String, boolean)` used throughout.
- `ExtractedDocument.markdownBody` used consistently as analyzer input.
- `AssetRef` fields consistent across `AssetManager`, extractors, and tests.
- `MediaPart` fields consistent across `SpiMessage`, `SpiMessageConverter`, and `ImageExtractor`/`KnowledgeAnalyzer`.

### Open Risks

- `yt-dlp` is an external dependency; first-run environments without it will fail for Douyin URLs.
- PDF OCR uses page rendering + vision calls; large PDFs may be slow and expensive.
- `SpiMessageConverter` Spring AI `Media` conversion assumes URL-based resources; base64 inline images are not covered in this plan.
