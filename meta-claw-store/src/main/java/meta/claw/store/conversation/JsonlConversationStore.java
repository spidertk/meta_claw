package meta.claw.store.conversation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.session.ChatMessage;
import meta.claw.core.session.ConversationInfo;
import meta.claw.core.session.ConversationStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 基于 JSONL 文件的对话历史存储实现
 * 每个会话的消息以 JSON Lines 格式追加写入文件，支持流式读取和线程安全
 */
@Slf4j
public class JsonlConversationStore implements ConversationStore {

    private final Path baseDir;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, ReentrantReadWriteLock> lockMap = new ConcurrentHashMap<>();

    /**
     * 构造 JSONL 对话存储
     *
     * @param baseDir 存储根目录，通常指向 vessels 目录，如 ~/.meta-claw/vessels/
     */
    public JsonlConversationStore(Path baseDir) {
        this.baseDir = baseDir;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    private ReentrantReadWriteLock getLock(String sessionKey) {
        return lockMap.computeIfAbsent(sessionKey, k -> new ReentrantReadWriteLock());
    }

    private Path getHistoryFilePath(String vesselId, String sessionKey) {
        return baseDir.resolve(vesselId)
                .resolve("conversations")
                .resolve(sessionKey)
                .resolve("history.jsonl");
    }

    private Path getMediaDirPath(String vesselId, String sessionKey) {
        return baseDir.resolve(vesselId)
                .resolve("conversations")
                .resolve(sessionKey)
                .resolve("media");
    }

    @Override
    public void appendMessage(String sessionKey, ChatMessage message) {
        String vesselId = resolveVesselId(sessionKey);
        ReentrantReadWriteLock lock = getLock(sessionKey);
        lock.writeLock().lock();
        try {
            Path filePath = getHistoryFilePath(vesselId, sessionKey);
            Files.createDirectories(filePath.getParent());

            String jsonLine = objectMapper.writeValueAsString(message) + "\n";
            Files.writeString(filePath, jsonLine,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize chat message for session {}: {}", sessionKey, e.getMessage());
            throw new RuntimeException("Message serialization failed", e);
        } catch (IOException e) {
            log.error("Failed to append message to session {}: {}", sessionKey, e.getMessage());
            throw new RuntimeException("Message append failed", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<ChatMessage> getHistory(String sessionKey, int limit) {
        String vesselId = resolveVesselId(sessionKey);
        Path filePath = getHistoryFilePath(vesselId, sessionKey);
        if (!Files.exists(filePath)) {
            return Collections.emptyList();
        }

        ReentrantReadWriteLock lock = getLock(sessionKey);
        lock.readLock().lock();
        try {
            List<ChatMessage> messages = Files.lines(filePath)
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
    public List<ConversationInfo> listConversations() {
        List<ConversationInfo> result = new ArrayList<>();
        if (!Files.exists(baseDir)) {
            return result;
        }

        try {
            Files.list(baseDir).forEach(vesselDir -> {
                Path convDir = vesselDir.resolve("conversations");
                if (!Files.exists(convDir)) return;

                try {
                    Files.list(convDir).forEach(sessionDir -> {
                        String sessionId = sessionDir.getFileName().toString();
                        Path historyFile = sessionDir.resolve("history.jsonl");
                        if (!Files.exists(historyFile)) return;

                        List<ChatMessage> history = getHistory(sessionId);
                        LocalDateTime createdAt = history.isEmpty()
                                ? getFileCreateTime(historyFile)
                                : history.get(0).getTimestamp();
                        LocalDateTime updatedAt = history.isEmpty()
                                ? createdAt
                                : history.get(history.size() - 1).getTimestamp();

                        result.add(ConversationInfo.builder()
                                .sessionId(sessionId)
                                .createdAt(createdAt)
                                .updatedAt(updatedAt)
                                .messageCount(history.size())
                                .build());
                    });
                } catch (IOException e) {
                    log.warn("Failed to list conversations for vessel {}", vesselDir.getFileName());
                }
            });
        } catch (IOException e) {
            log.error("Failed to list vessels in base dir: {}", e.getMessage());
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
            if (Files.exists(filePath)) {
                Files.writeString(filePath, "", StandardOpenOption.TRUNCATE_EXISTING);
                return true;
            }
            return false;
        } catch (IOException e) {
            log.error("Failed to clear history for session {}: {}", sessionKey, e.getMessage());
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean deleteConversation(String sessionKey) {
        String vesselId = resolveVesselId(sessionKey);
        Path sessionDir = baseDir.resolve(vesselId)
                .resolve("conversations")
                .resolve(sessionKey);
        ReentrantReadWriteLock lock = getLock(sessionKey);
        lock.writeLock().lock();
        try {
            if (Files.exists(sessionDir)) {
                deleteDirectoryRecursively(sessionDir);
                return true;
            }
            return false;
        } catch (IOException e) {
            log.error("Failed to delete conversation {}: {}", sessionKey, e.getMessage());
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean conversationExists(String sessionKey) {
        String vesselId = resolveVesselId(sessionKey);
        return Files.exists(getHistoryFilePath(vesselId, sessionKey));
    }

    private ChatMessage parseMessage(String jsonLine) {
        try {
            return objectMapper.readValue(jsonLine, ChatMessage.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse chat message JSON: {}", e.getMessage());
            return null;
        }
    }

    private LocalDateTime getFileCreateTime(Path path) {
        try {
            return LocalDateTime.ofInstant(
                    Files.getLastModifiedTime(path).toInstant(),
                    java.time.ZoneId.systemDefault());
        } catch (IOException e) {
            return LocalDateTime.now();
        }
    }

    private void deleteDirectoryRecursively(Path dir) throws IOException {
        if (Files.isDirectory(dir)) {
            try (var entries = Files.list(dir)) {
                entries.forEach(child -> {
                    try {
                        deleteDirectoryRecursively(child);
                    } catch (IOException e) {
                        log.warn("Failed to delete {}: {}", child, e.getMessage());
                    }
                });
            }
        }
        Files.delete(dir);
    }

    /**
     * 从 sessionKey 解析 vesselId。
     * 默认策略：取 baseDir 下第一个 vessel 目录名；生产环境应由调用方显式传入 vesselId。
     */
    private String resolveVesselId(String sessionKey) {
        try {
            if (Files.exists(baseDir)) {
                return Files.list(baseDir)
                        .filter(Files::isDirectory)
                        .findFirst()
                        .map(p -> p.getFileName().toString())
                        .orElse("default");
            }
        } catch (IOException e) {
            log.debug("Failed to resolve vesselId from baseDir, fallback to default");
        }
        return "default";
    }
}
