package meta.claw.core.memory.shortterm;

import meta.claw.core.memory.MemoryMessage;
import meta.claw.core.memory.SessionMemory;
import meta.claw.core.prompt.PromptContext;

import java.util.List;
import java.util.function.Supplier;

/**
 * 短期记忆 backend 契约。
 */
public interface ShortMemory {
    String type();
    SessionSelection selectSession(String vesselId, String resumeSessionId, Supplier<String> newSessionIdSupplier);

    void appendMessage(String vesselId, String sessionId, MemoryMessage message);
    List<MemoryMessage> loadMessages(String vesselId, String sessionId, int limit);

    List<SessionMemory> listSessions(String vesselId);
    boolean clearSession(String vesselId, String sessionId);
    boolean conversationExists(String vesselId, String sessionId);

    SessionMemory loadSession(String vesselId, String sessionId);
    void saveSession(String vesselId, String sessionId, SessionMemory session);
    String summarizeConversation(String vesselId, List<MemoryMessage> history);
}
