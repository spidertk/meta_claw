package meta.claw.store.memory.shortterm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.memory.MemoryEntry;
import meta.claw.core.memory.shortterm.ShortMemoryStore;
import meta.claw.core.spi.llm.SpiMessage;
import meta.claw.core.spi.llm.SpiToolCall;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * 基于 JSONL 文件的短期记忆 backend。
 */
@Slf4j
@Component
@Scope("prototype")
public class JsonlShortMemoryStore implements ShortMemoryStore {

    private static final Pattern BASE64_PATTERN = Pattern.compile(
            "data:([^;]+);base64,[A-Za-z0-9+/=]{200,}"
    );
    private static final String MESSAGE_CATEGORY = "message";
    private static final String ROLE_KEY = "role";
    private static final String TOOL_CALLS_KEY = "toolCalls";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path baseDir;
    private final String boundVesselId;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, ReentrantReadWriteLock> lockMap = new ConcurrentHashMap<>();

    public JsonlShortMemoryStore(Path baseDir) {
        this(baseDir, null);
    }

    public JsonlShortMemoryStore(Path baseDir, String vesselId) {
        this.baseDir = baseDir;
        this.boundVesselId = vesselId;
        this.objectMapper = createObjectMapper();
    }

    @Override
    public void initializeConversation(String sessionKey) {
        String vesselId = resolveVesselId(sessionKey);
        Path filePath = getHistoryFilePath(vesselId, sessionKey);
        ReentrantReadWriteLock lock = getLock(sessionKey);
        lock.writeLock().lock();
        try {
            Files.createDirectories(filePath.getParent());
            if (!Files.exists(filePath)) {
                Files.createFile(filePath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Conversation initialization failed", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void appendMessage(String sessionKey, SpiMessage message) {
        String vesselId = resolveVesselId(sessionKey);
        ReentrantReadWriteLock lock = getLock(sessionKey);
        lock.writeLock().lock();
        try {
            Path filePath = getHistoryFilePath(vesselId, sessionKey);
            initializeConversation(sessionKey);
            SpiMessage safeMessage = new SpiMessage(message.role(), stripBase64(message.content()), message.toolCalls());
            String jsonLine = objectMapper.writeValueAsString(toMemoryEntry(sessionKey, safeMessage)) + "\n";
            try (FileChannel channel = FileChannel.open(filePath,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE)) {
                channel.write(ByteBuffer.wrap(jsonLine.getBytes(StandardCharsets.UTF_8)));
                channel.force(true);
            }
        } catch (IOException e) {
            throw new RuntimeException("Message append failed", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<SpiMessage> getHistory(String sessionKey, int limit) {
        String vesselId = resolveVesselId(sessionKey);
        Path filePath = getHistoryFilePath(vesselId, sessionKey);
        if (!Files.exists(filePath)) {
            return Collections.emptyList();
        }
        ReentrantReadWriteLock lock = getLock(sessionKey);
        lock.readLock().lock();
        try {
            List<SpiMessage> messages = Files.lines(filePath)
                    .filter(line -> !line.isBlank())
                    .map(this::parseMessage)
                    .filter(msg -> msg != null)
                    .collect(Collectors.toList());
            if (limit > 0 && messages.size() > limit) {
                return messages.subList(messages.size() - limit, messages.size());
            }
            return messages;
        } catch (IOException e) {
            log.error("Failed to read history for session {}: {}", sessionKey, e.getMessage());
            return Collections.emptyList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<MemoryEntry> listSessions(String vesselId) {
        List<MemoryEntry> result = new ArrayList<>();
        Path conversationsDir = baseDir.resolve(vesselId).resolve("conversations");
        if (!Files.exists(conversationsDir)) {
            return result;
        }
        try (var dirs = Files.list(conversationsDir)) {
            dirs.forEach(sessionDir -> {
                String sessionId = sessionDir.getFileName().toString();
                Path historyFile = sessionDir.resolve("history.jsonl");
                if (!Files.exists(historyFile)) {
                    return;
                }
                List<SpiMessage> history = getHistoryForVessel(vesselId, sessionId);
                result.add(MemoryEntry.builder()
                        .sessionId(sessionId)
                        .updatedAt(getFileUpdatedTime(historyFile))
                        .messageCount(history.size())
                        .build());
            });
        } catch (IOException e) {
            log.warn("Failed to list sessions for vessel {}: {}", vesselId, e.getMessage());
        }
        result.sort((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()));
        return result;
    }

    @Override
    public boolean clearHistory(String sessionKey) {
        String vesselId = resolveVesselId(sessionKey);
        Path filePath = getHistoryFilePath(vesselId, sessionKey);
        ReentrantReadWriteLock lock = getLock(sessionKey);
        lock.writeLock().lock();
        try {
            if (!Files.exists(filePath)) {
                return false;
            }
            Files.writeString(filePath, "", StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean conversationExists(String sessionKey) {
        return Files.exists(getHistoryFilePath(resolveVesselId(sessionKey), sessionKey));
    }

    @Override
    public List<SpiMessage> truncateByRound(List<SpiMessage> history, int maxRounds) {
        if (maxRounds <= 0 || history == null || history.isEmpty()) {
            return history == null ? new ArrayList<>() : new ArrayList<>(history);
        }

        int roundsFound = 0;
        int cutoffIndex = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("assistant".equalsIgnoreCase(history.get(i).role())) {
                roundsFound++;
                if (roundsFound > maxRounds) {
                    cutoffIndex = i + 1;
                    break;
                }
            }
        }

        List<SpiMessage> result = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            SpiMessage message = history.get(i);
            if ("system".equalsIgnoreCase(message.role()) || i >= cutoffIndex) {
                result.add(message);
            }
        }
        return result;
    }

    @Override
    public List<SpiMessage> truncateByToken(List<SpiMessage> history, int maxTokens) {
        if (maxTokens <= 0 || history == null || history.isEmpty()) {
            return history == null ? new ArrayList<>() : new ArrayList<>(history);
        }

        int currentTokens = 0;
        int cutoffIndex = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            SpiMessage message = history.get(i);
            int tokens = estimateTokens(message.content());
            if ("system".equalsIgnoreCase(message.role())) {
                currentTokens += tokens;
                continue;
            }
            if (currentTokens + tokens > maxTokens) {
                cutoffIndex = i + 1;
                break;
            }
            currentTokens += tokens;
        }

        List<SpiMessage> result = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            SpiMessage message = history.get(i);
            if ("system".equalsIgnoreCase(message.role()) || i >= cutoffIndex) {
                result.add(message);
            }
        }
        return result;
    }

    @Override
    public String summarizeConversation(List<SpiMessage> history) {
        return "Earlier conversation summarized.";
    }

    private List<SpiMessage> getHistoryForVessel(String vesselId, String sessionKey) {
        Path filePath = getHistoryFilePath(vesselId, sessionKey);
        if (!Files.exists(filePath)) {
            return Collections.emptyList();
        }
        try {
            return Files.lines(filePath)
                    .filter(line -> !line.isBlank())
                    .map(this::parseMessage)
                    .filter(msg -> msg != null)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private SpiMessage parseMessage(String jsonLine) {
        try {
            MemoryEntry entry = objectMapper.readValue(jsonLine, MemoryEntry.class);
            if (MESSAGE_CATEGORY.equals(entry.getCategory())) {
                return toSpiMessage(entry);
            }
        } catch (JsonProcessingException e) {
            // Older conversation files persisted SpiMessage directly.
        }
        try {
            return objectMapper.readValue(jsonLine, SpiMessage.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse memory message JSON: {}", e.getMessage());
            return null;
        }
    }

    private ObjectMapper createObjectMapper() {
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(TIMESTAMP_FORMATTER));
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(TIMESTAMP_FORMATTER));
        return new ObjectMapper()
                .registerModule(javaTimeModule)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private MemoryEntry toMemoryEntry(String sessionKey, SpiMessage message) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(ROLE_KEY, message.role());
        if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
            metadata.put(TOOL_CALLS_KEY, message.toolCalls());
        }
        return MemoryEntry.builder()
                .timestamp(LocalDateTime.now())
                .category(MESSAGE_CATEGORY)
                .content(message.content())
                .metadata(metadata)
                .sessionId(sessionKey)
                .build();
    }

    private SpiMessage toSpiMessage(MemoryEntry entry) {
        Map<String, Object> metadata = entry.getMetadata() == null ? Collections.emptyMap() : entry.getMetadata();
        String role = metadata.get(ROLE_KEY) == null ? null : metadata.get(ROLE_KEY).toString();
        List<SpiToolCall> toolCalls = metadata.containsKey(TOOL_CALLS_KEY)
                ? objectMapper.convertValue(metadata.get(TOOL_CALLS_KEY), new TypeReference<>() {})
                : null;
        return new SpiMessage(role, entry.getContent(), toolCalls);
    }

    private ReentrantReadWriteLock getLock(String sessionKey) {
        return lockMap.computeIfAbsent(sessionKey, k -> new ReentrantReadWriteLock());
    }

    private String resolveVesselId(String sessionKey) {
        if (boundVesselId != null && !boundVesselId.isBlank()) {
            return boundVesselId;
        }
        if (!Files.exists(baseDir)) {
            return "default";
        }
        try (var vessels = Files.list(baseDir)) {
            return vessels
                    .filter(vesselDir -> Files.exists(getHistoryFilePath(vesselDir.getFileName().toString(), sessionKey)))
                    .map(vesselDir -> vesselDir.getFileName().toString())
                    .findFirst()
                    .orElse("default");
        } catch (IOException e) {
            return "default";
        }
    }

    private Path getHistoryFilePath(String vesselId, String sessionKey) {
        return baseDir.resolve(vesselId).resolve("conversations").resolve(sessionKey).resolve("history.jsonl");
    }

    private LocalDateTime getFileUpdatedTime(Path file) {
        try {
            return LocalDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(), ZoneId.systemDefault());
        } catch (IOException e) {
            return LocalDateTime.now();
        }
    }

    private String stripBase64(String content) {
        if (content == null || !content.contains("data:") || !content.contains(";base64,")) {
            return content;
        }
        return BASE64_PATTERN.matcher(content).replaceAll(match ->
                "[media:" + match.group(1) + ":base64:<stripped>]");
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : text.toCharArray()) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        return chineseChars + (otherChars / 4) + 1;
    }
}
