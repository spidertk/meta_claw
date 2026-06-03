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
import meta.claw.core.memory.shortterm.SessionSelection;
import meta.claw.core.memory.shortterm.ShortMemory;
import meta.claw.core.llm.SpiMessage;
import meta.claw.core.infra.ProjectRootFinder;
import org.springframework.stereotype.Component;

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
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 基于 JSONL 文件的短期记忆 backend。Spring 单例，按 vesselId 隔离数据。
 */
@Slf4j
@Component
public class ShortMemoryJsonlStore implements ShortMemory {


    private static final Pattern BASE64_PATTERN = Pattern.compile(
            "data:([^;]+);base64,[A-Za-z0-9+/=]{200,}"
    );
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, ReentrantReadWriteLock> lockMap = new ConcurrentHashMap<>();

    public ShortMemoryJsonlStore() {
        this.objectMapper = createObjectMapper();
    }

    @Override
    public String type() {
        return "jsonl";
    }

    @Override
    public SessionSelection selectSession(String vesselId, String resumeSessionId, Supplier<String> newSessionIdSupplier){

        String sessionId;
        if (resumeSessionId != null && !resumeSessionId.isBlank()) {
            if (!conversationExists(vesselId, resumeSessionId)) {
                throw new IllegalArgumentException("Session not found for vessel '" +vesselId + "': " + resumeSessionId);
            }
            sessionId = resumeSessionId;
        } else {
            sessionId = newSessionIdSupplier.get();
            initializeConversation(vesselId, sessionId);
        }

        Path historyFilePath = getHistoryFilePath(vesselId, sessionId);

        return SessionSelection.builder()
                .sessionId(sessionId)
                .historyFilePath(historyFilePath)
                .build();
   }



    private void initializeConversation(String vesselId, String sessionKey) {
        Path filePath = getHistoryFilePath(vesselId, sessionKey);
        ReentrantReadWriteLock lock = getLock(sessionKey);
        lock.writeLock().lock();
        try {
            Files.createDirectories(filePath.getParent());
            try (FileChannel channel = FileChannel.open(filePath,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            syncParentDirectory(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Conversation initialization failed", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void appendMessage(String vesselId, String sessionKey, MemoryMessage message) {
        ReentrantReadWriteLock lock = getLock(sessionKey);
        lock.writeLock().lock();
        try {
            Path filePath = getHistoryFilePath(vesselId, sessionKey);
            initializeConversation(vesselId, sessionKey);
            message.setContent(stripBase64(message.getContent()));
            String jsonLine = objectMapper.writeValueAsString(message) + "\n";
            try (FileChannel channel = FileChannel.open(filePath,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE)) {
                channel.write(ByteBuffer.wrap(jsonLine.getBytes(StandardCharsets.UTF_8)));
                channel.force(true);
            }
            syncParentDirectory(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Message append failed", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<MemoryMessage> loadMessages(String vesselId, String sessionKey, int limit) {
        Path filePath = getHistoryFilePath(vesselId, sessionKey);
        if (!Files.exists(filePath)) {
            return Collections.emptyList();
        }
        ReentrantReadWriteLock lock = getLock(sessionKey);
        lock.readLock().lock();
        try (var lines = Files.lines(filePath)) {
            List<MemoryMessage> messages = lines
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
        Path conversationsDir = resolveBaseDir().resolve(vesselId).resolve("conversations");
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
    public boolean clearSession(String vesselId, String sessionKey) {
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
    public boolean conversationExists(String vesselId, String sessionKey) {
        return Files.exists(getHistoryFilePath(vesselId, sessionKey));
    }


    @Override
    public SessionMemory loadSession(String vesselId, String sessionKey) {
        return loadSummaryForVessel(vesselId, sessionKey);
    }

    @Override
    public void saveSession(String vesselId, String sessionKey, SessionMemory session) {
        Path filePath = getSummaryFilePath(vesselId, sessionKey);
        ReentrantReadWriteLock lock = getLock(sessionKey);
        lock.writeLock().lock();
        try {
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, objectMapper.writeValueAsString(session),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Summary save failed", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public String summarizeConversation(String vesselId, List<MemoryMessage> history) {
        return "Earlier conversation summarized.";
    }

    private List<MemoryMessage> getHistoryForVessel(String vesselId, String sessionKey) {
        Path filePath = getHistoryFilePath(vesselId, sessionKey);
        if (!Files.exists(filePath)) {
            return Collections.emptyList();
        }
        try (var lines = Files.lines(filePath)) {
            return lines
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

    private Path resolveBaseDir() {
        return ProjectRootFinder.getMetaClawDir().resolve("vessels");
    }

    private Path getHistoryFilePath(String vesselId, String sessionKey) {
        return resolveBaseDir().resolve(vesselId).resolve("conversations").resolve(sessionKey).resolve("history.jsonl");
    }

    private Path getSummaryFilePath(String vesselId, String sessionKey) {
        return resolveBaseDir().resolve(vesselId).resolve("conversations").resolve(sessionKey).resolve("summary.json");
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

    private void syncParentDirectory(Path filePath) {
        try {
            Path parent = filePath.getParent();
            if (parent != null && Files.exists(parent)) {
                try (FileChannel dirChannel = FileChannel.open(parent, StandardOpenOption.READ)) {
                    dirChannel.force(true);
                }
            }
        } catch (IOException e) {
            // 部分文件系统（如 Windows）不支持对目录调用 force，忽略即可
            log.debug("Failed to sync parent directory for {}: {}", filePath, e.getMessage());
        }
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
}
