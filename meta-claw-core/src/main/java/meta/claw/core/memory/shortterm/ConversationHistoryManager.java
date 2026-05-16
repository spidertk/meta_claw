package meta.claw.core.memory.shortterm;

import meta.claw.core.spi.llm.SpiMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 短期记忆中的会话历史管理器。
 * 提供按轮数截断、按 Token 估算截断两种策略，以及对话摘要占位接口。
 */
public class ConversationHistoryManager {

    /**
     * 按对话轮数截断历史，保留最近 maxRounds 轮（以 assistant 消息计数）。
     * 系统消息始终保留。
     */
    public List<SpiMessage> truncateByRound(List<SpiMessage> history, int maxRounds) {
        if (maxRounds <= 0 || history == null || history.isEmpty()) {
            return history == null ? new ArrayList<>() : new ArrayList<>(history);
        }

        int roundsFound = 0;
        int cutoffIndex = 0;

        for (int i = history.size() - 1; i >= 0; i--) {
            String role = history.get(i).role();
            if ("assistant".equalsIgnoreCase(role)) {
                roundsFound++;
                if (roundsFound > maxRounds) {
                    cutoffIndex = i + 1;
                    break;
                }
            }
        }

        List<SpiMessage> result = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            SpiMessage msg = history.get(i);
            if ("system".equalsIgnoreCase(msg.role()) || i >= cutoffIndex) {
                result.add(msg);
            }
        }
        return result;
    }

    /**
     * 按 Token 估算值截断历史，保留尾部消息直到达到 maxTokens。
     * 系统消息始终计入但不作为截断触发点。
     */
    public List<SpiMessage> truncateByToken(List<SpiMessage> history, int maxTokens) {
        if (maxTokens <= 0 || history == null || history.isEmpty()) {
            return history == null ? new ArrayList<>() : new ArrayList<>(history);
        }

        int currentTokens = 0;
        int cutoffIndex = 0;

        for (int i = history.size() - 1; i >= 0; i--) {
            SpiMessage msg = history.get(i);
            String content = msg.content() != null ? msg.content() : "";
            int tokens = estimateTokens(content);

            if ("system".equalsIgnoreCase(msg.role())) {
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
            SpiMessage msg = history.get(i);
            if ("system".equalsIgnoreCase(msg.role()) || i >= cutoffIndex) {
                result.add(msg);
            }
        }
        return result;
    }

    /**
     * 简单 Token 估算：中文字符按 1 token，其他字符按 4 字符 1 token，保底 1 token。
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
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

    /**
     * 对话摘要（Phase 2 占位实现，Phase 3+ 接入 LLM 驱动摘要）。
     */
    public String summarizeConversation(List<ChatMessage> history) {
        return "Earlier conversation summarized.";
    }
}
