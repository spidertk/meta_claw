package meta.claw.store.memory.longterm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.memory.PreferenceMemory;
import meta.claw.core.memory.longterm.LongMemoryStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * 基于 JSONL 文件的用户偏好存储实现
 * 每个 Vessel 拥有独立的 preferences.jsonl 文件，存储用户偏好、个人习惯等信息
 */
@Slf4j
@Component
@Scope("prototype")
public class FileLongMemoryStore implements LongMemoryStore {

    private final Path baseDir;
    private final ObjectMapper objectMapper;

    /**
     * 构造文件偏好存储
     *
     * @param baseDir 存储根目录，通常指向 vessels 目录，如 ~/.meta-claw/vessels/
     */
    public FileLongMemoryStore(Path baseDir) {
        this.baseDir = baseDir;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    private Path getPreferencesFilePath(String vesselId) {
        return baseDir.resolve(vesselId).resolve("preferences").resolve("preferences.jsonl");
    }

    @Override
    public void addPreference(String vesselId, PreferenceMemory entry) {
        Path filePath = getPreferencesFilePath(vesselId);
        try {
            Files.createDirectories(filePath.getParent());
            String jsonLine = objectMapper.writeValueAsString(entry) + "\n";
            Files.writeString(filePath, jsonLine,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize preference entry for vessel {}: {}", vesselId, e.getMessage());
            throw new RuntimeException("Preference serialization failed", e);
        } catch (IOException e) {
            log.error("Failed to append preference to vessel {}: {}", vesselId, e.getMessage());
            throw new RuntimeException("Preference append failed", e);
        }
    }

    @Override
    public List<PreferenceMemory> lookupPreference(String vesselId, String query) {
        Path filePath = getPreferencesFilePath(vesselId);
        if (!Files.exists(filePath)) {
            return Collections.emptyList();
        }

        String lowerQuery = query.toLowerCase(Locale.ROOT);
        try {
            return Files.lines(filePath)
                    .filter(line -> !line.isBlank())
                    .map(this::parseEntry)
                    .filter(entry -> entry != null)
                    .filter(entry -> matchesQuery(entry, lowerQuery))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to lookup preferences for vessel {}: {}", vesselId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<PreferenceMemory> listRecentPreferences(String vesselId, int limit) {
        Path filePath = getPreferencesFilePath(vesselId);
        if (!Files.exists(filePath)) {
            return Collections.emptyList();
        }

        try {
            List<PreferenceMemory> entries = Files.lines(filePath)
                    .filter(line -> !line.isBlank())
                    .map(this::parseEntry)
                    .filter(entry -> entry != null)
                    .collect(Collectors.toList());

            if (limit > 0 && entries.size() > limit) {
                return entries.subList(entries.size() - limit, entries.size());
            }
            return entries;
        } catch (IOException e) {
            log.error("Failed to list preferences for vessel {}: {}", vesselId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public boolean deletePreference(String vesselId, String preferenceId) {
        Path filePath = getPreferencesFilePath(vesselId);
        if (!Files.exists(filePath)) {
            return false;
        }

        try {
            List<String> lines = Files.readAllLines(filePath);
            List<String> filtered = lines.stream()
                    .filter(line -> {
                        if (line.isBlank()) return false;
                        PreferenceMemory entry = parseEntry(line);
                        return entry != null && !preferenceId.equals(entry.getId());
                    })
                    .collect(Collectors.toList());

            Files.write(filePath, filtered, StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (IOException e) {
            log.error("Failed to delete preference {} for vessel {}: {}", preferenceId, vesselId, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean clearPreferences(String vesselId) {
        Path filePath = getPreferencesFilePath(vesselId);
        try {
            if (Files.exists(filePath)) {
                Files.writeString(filePath, "", StandardOpenOption.TRUNCATE_EXISTING);
                return true;
            }
            return false;
        } catch (IOException e) {
            log.error("Failed to clear preferences for vessel {}: {}", vesselId, e.getMessage());
            return false;
        }
    }

    private PreferenceMemory parseEntry(String jsonLine) {
        try {
            return objectMapper.readValue(jsonLine, PreferenceMemory.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse preference entry JSON: {}", e.getMessage());
            return null;
        }
    }

    private boolean matchesQuery(PreferenceMemory entry, String lowerQuery) {
        if (entry.getContent() != null && entry.getContent().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
            return true;
        }
        if (entry.getCategory() != null && entry.getCategory().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
            return true;
        }
        if (entry.getMetadata() != null) {
            return entry.getMetadata().values().stream()
                    .anyMatch(v -> v != null && v.toString().toLowerCase(Locale.ROOT).contains(lowerQuery));
        }
        return false;
    }
}
