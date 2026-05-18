package meta.claw.store.memory.shortterm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.memory.MemoryMessage;
import meta.claw.core.memory.MemoryMessageConverter;
import meta.claw.core.memory.SessionMemory;
import meta.claw.core.memory.shortterm.ShortMemoryStore;
import meta.claw.core.spi.llm.SpiMessage;

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
import java.util.List;
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
    public void appendMessage(String sessionKey, MemoryMessage message) {
        String vesselId = resolveVesselId(sessionKey);
        ReentrantReadWriteLock lock = getLock(sessionKey);
        lock.writeLock().lock();
        try {
            Path filePath = getHistoryFilePath(vesselId, sessionKey);
            initializeConversation(sessionKey);
            message.setContent(stripBase64(message.getContent()));
            String jsonLine = objectMapper.writeValueAsString(message) + "\n";
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
    public List<MemoryMessage> getHistory(String sessionKey, int limit) {
        String vesselId = resolveVesselId(sessionKey);
        Path filePath = getHistoryFilePath(vesselId, sessionKey);
        if (!Files.exists(filePath)) {
            return Collections.emptyList();
        }
        ReentrantReadWriteLock lock = getLock(sessionKey);
        lock.readLock().lock();
        try {
            List<MemoryMessage> messages = Files.lines(filePath)
                    .filter(line -> !line.isBlank())
                    .map(this::parseMessage)
                    .filter(msg -> msg != null)
                    .collect(Collectors.toList());
            if (limit > 0) {
                return trimByRound(messages, limit);
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
    public List<SessionMemory> listSessions(String vesselId) {
        List<SessionMemory> result = new ArrayList<>();
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
                List<MemoryMessage> history = getHistoryForVessel(vesselId, sessionId);
                SessionMemory summary = loadSummaryForVessel(vesselId, sessionId);
                result.add(SessionMemory.builder()
                        .sessionId(sessionId)
                        .updatedAt(getFileUpdatedTime(historyFile))
                        .messageCount(history.size())
                        .summary(summary == null ? null : summary.getSummary())
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

    private List<MemoryMessage> trimByRound(List<MemoryMessage> history, int maxRounds) {
        if (maxRounds <= 0 || history == null || history.isEmpty()) {
            return history == null ? new ArrayList<>() : new ArrayList<>(history);
        }

        int roundsFound = 0;
        int cutoffIndex = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("assistant".equalsIgnoreCase(history.get(i).getRole())) {
                roundsFound++;
                if (roundsFound > maxRounds) {
                    cutoffIndex = i + 1;
                    break;
                }
            }
        }

        List<MemoryMessage> result = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            MemoryMessage message = history.get(i);
            if ("system".equalsIgnoreCase(message.getRole()) || i >= cutoffIndex) {
                result.add(message);
            }
        }
        return result;
    }

    @Override
    public List<MemoryMessage> getHistoryByToken(String sessionKey, int maxTokens) {
        return trimByToken(getHistory(sessionKey), maxTokens);
    }

    private List<MemoryMessage> trimByToken(List<MemoryMessage> history, int maxTokens) {
        if (maxTokens <= 0 || history == null || history.isEmpty()) {
            return history == null ? new ArrayList<>() : new ArrayList<>(history);
        }

        int currentTokens = 0;
        int cutoffIndex = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            MemoryMessage message = history.get(i);
            int tokens = estimateTokens(message.getContent());
            if ("system".equalsIgnoreCase(message.getRole())) {
                currentTokens += tokens;
                continue;
            }
            if (currentTokens + tokens > maxTokens) {
                cutoffIndex = i + 1;
                break;
            }
            currentTokens += tokens;
        }

        List<MemoryMessage> result = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            MemoryMessage message = history.get(i);
            if ("system".equalsIgnoreCase(message.getRole()) || i >= cutoffIndex) {
                result.add(message);
            }
        }
        return result;
    }

    @Override
    public SessionMemory loadSummary(String sessionKey) {
        return loadSummaryForVessel(resolveVesselId(sessionKey), sessionKey);
    }

    @Override
    public void saveSummary(String sessionKey, SessionMemory summary) {
        String vesselId = resolveVesselId(sessionKey);
        Path filePath = getSummaryFilePath(vesselId, sessionKey);
        ReentrantReadWriteLock lock = getLock(sessionKey);
        lock.writeLock().lock();
        try {
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, objectMapper.writeValueAsString(summary),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Summary save failed", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public String summarizeConversation(List<MemoryMessage> history) {
        return "Earlier conversation summarized.";
    }

    private List<MemoryMessage> getHistoryForVessel(String vesselId, String sessionKey) {
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

    private MemoryMessage parseMessage(String jsonLine) {
        try {
            MemoryMessage message = objectMapper.readValue(jsonLine, MemoryMessage.class);
            if (message.getRole() != null) {
                return message;
            }
        } catch (JsonProcessingException e) {
            // Older conversation files used the previous aggregate shape or SpiMessage.
        }
        try {
            JsonNode node = objectMapper.readTree(jsonLine);
            if (node.hasNonNull("category") && node.hasNonNull("metadata")) {
                JsonNode metadata = node.get("metadata");
                MemoryMessage message = MemoryMessage.builder()
                        .role(metadata.hasNonNull("role") ? metadata.get("role").asText() : null)
                        .content(node.hasNonNull("content") ? node.get("content").asText() : null)
                        .build();
                if (node.hasNonNull("timestamp")) {
                    message.setTimestamp(LocalDateTime.parse(node.get("timestamp").asText(), TIMESTAMP_FORMATTER));
                }
                return message;
            }
        } catch (JsonProcessingException e) {
            // Older conversation files may have persisted SpiMessage directly.
        }
        try {
            return MemoryMessageConverter.fromSpiMessage(objectMapper.readValue(jsonLine, SpiMessage.class));
        } catch (JsonProcessingException legacyException) {
            log.warn("Failed to parse memory message JSON: {}", legacyException.getMessage());
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

    private Path getSummaryFilePath(String vesselId, String sessionKey) {
        return baseDir.resolve(vesselId).resolve("conversations").resolve(sessionKey).resolve("summary.json");
    }

    private SessionMemory loadSummaryForVessel(String vesselId, String sessionKey) {
        Path filePath = getSummaryFilePath(vesselId, sessionKey);
        if (!Files.exists(filePath)) {
            return null;
        }
        try {
            return objectMapper.readValue(Files.readString(filePath), SessionMemory.class);
        } catch (IOException e) {
            log.warn("Failed to read summary for session {}: {}", sessionKey, e.getMessage());
            return null;
        }
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
