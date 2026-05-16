package meta.claw.core.session;

import java.nio.file.Path;
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
     * 列出指定 Vessel 下的会话概要信息
     *
     * @param vesselId Vessel 唯一标识
     * @return 该 Vessel 下的会话信息列表
     */
    List<ConversationInfo> listConversations(String vesselId);

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

    /**
     * 获取指定会话的历史消息（带过滤条件）
     *
     * @param sessionKey 会话唯一标识键
     * @param limit      最大返回条数，0 或负数表示无限制
     * @param filter     过滤条件，null 表示不过滤
     * @return 过滤后的历史消息列表
     */
    List<ChatMessage> getHistory(String sessionKey, int limit, MessageFilter filter);

    /**
     * 获取会话统计信息
     *
     * @param sessionKey 会话唯一标识键
     * @return 会话统计，若会话不存在则返回 null
     */
    ConversationStats getStats(String sessionKey);

    /**
     * 保存媒体文件到会话目录
     *
     * @param sessionKey 会话唯一标识键
     * @param data       文件二进制数据
     * @param filename   原始文件名
     * @param mediaType  媒体类型（MIME）
     * @return 媒体引用信息
     */
    MediaReference saveMedia(String sessionKey, byte[] data, String filename, String mediaType);

    /**
     * 获取媒体文件的完整路径
     *
     * @param sessionKey   会话唯一标识键
     * @param relativePath 相对路径（如 media/filename.png）
     * @return 绝对路径
     */
    Path getMediaPath(String sessionKey, String relativePath);
}
