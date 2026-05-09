package meta.claw.core.session;

import java.util.List;

/**
 * 对话历史存储接口
 * 定义对话消息历史的持久化与检索规范，实现可以是内存、文件（JSONL）或数据库等
 */
public interface ConversationStore {

    /**
     * 向指定会话追加一条消息
     *
     * @param sessionKey 会话唯一标识键
     * @param message    待追加的消息
     */
    void appendMessage(String sessionKey, ChatMessage message);

    /**
     * 获取指定会话的历史消息
     *
     * @param sessionKey 会话唯一标识键
     * @param limit      最大返回条数，0 或负数表示无限制
     * @return 历史消息列表，按时间正序排列；若会话不存在则返回空列表
     */
    List<ChatMessage> getHistory(String sessionKey, int limit);

    /**
     * 获取指定会话的历史消息（无限制）
     *
     * @param sessionKey 会话唯一标识键
     * @return 历史消息列表
     */
    default List<ChatMessage> getHistory(String sessionKey) {
        return getHistory(sessionKey, 0);
    }

    /**
     * 列出所有会话概要信息
     *
     * @return 会话信息列表
     */
    List<ConversationInfo> listConversations();

    /**
     * 清除指定会话的所有历史消息
     *
     * @param sessionKey 会话唯一标识键
     * @return 是否成功清除
     */
    boolean clearHistory(String sessionKey);

    /**
     * 删除指定会话及其所有历史消息
     *
     * @param sessionKey 会话唯一标识键
     * @return 是否成功删除
     */
    boolean deleteConversation(String sessionKey);

    /**
     * 检查指定会话是否存在
     *
     * @param sessionKey 会话唯一标识键
     * @return 是否存在
     */
    boolean conversationExists(String sessionKey);
}
