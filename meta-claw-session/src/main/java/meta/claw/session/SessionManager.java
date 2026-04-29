package meta.claw.session;

import meta.claw.session.model.ChatMode;
import meta.claw.session.model.UserSession;
import meta.claw.session.storage.SessionStorage;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 会话管理器
 * 负责用户会话的生命周期管理，包括会话的获取、创建、模式切换、目标专家解析以及过期清理等核心逻辑
 */
public class SessionManager {

    /**
     * 会话存储接口，具体实现由外部注入（如内存存储、Redis 存储等）
     */
    private final SessionStorage sessionStorage;

    /**
     * 默认会话超时时间，单位：秒
     * 若会话超过此时间未活动，将被视为过期并可被清理
     */
    private final long defaultTimeoutSeconds;

    /**
     * 构造会话管理器，使用默认超时时间 3600 秒（1 小时）
     *
     * @param sessionStorage 会话存储实现
     */
    public SessionManager(SessionStorage sessionStorage) {
        this(sessionStorage, 3600);
    }

    /**
     * 构造会话管理器，支持自定义超时时间
     *
     * @param sessionStorage       会话存储实现
     * @param defaultTimeoutSeconds 默认超时时间（秒）
     */
    public SessionManager(SessionStorage sessionStorage, long defaultTimeoutSeconds) {
        this.sessionStorage = sessionStorage;
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
    }

    /**
     * 构建会话唯一标识键
     * 格式为：userId:source:agentId
     *
     * @param userId  用户唯一标识
     * @param source  来源渠道
     * @param agentId 代理标识
     * @return 拼接后的会话键
     */
    public String buildSessionKey(String userId, String source, String agentId) {
        return userId + ":" + source + ":" + agentId;
    }

    /**
     * 获取或创建用户会话
     * 若会话已存在则直接返回并更新最后活动时间；若不存在则创建新会话并持久化
     *
     * @param userId  用户唯一标识
     * @param source  来源渠道
     * @param agentId 代理标识
     * @return 用户会话对象
     */
    public UserSession getSession(String userId, String source, String agentId) {
        String sessionKey = buildSessionKey(userId, source, agentId);
        UserSession session = sessionStorage.get(sessionKey);
        if (session == null) {
            // 会话不存在，创建新会话
            session = UserSession.builder()
                    .sessionKey(sessionKey)
                    .userId(userId)
                    .source(source)
                    .agentId(agentId)
                    .mode(ChatMode.SINGLE) // 默认单聊模式
                    .createdAt(LocalDateTime.now())
                    .build();
        }
        // 自动更新最后活动时间，保持会话活跃
        session.touch();
        sessionStorage.save(session);
        return session;
    }

    /**
     * 设置会话为单聊模式，并绑定目标专家
     *
     * @param userId     用户唯一标识
     * @param source     来源渠道
     * @param expertName 目标专家名称
     * @param agentId    代理标识
     */
    public void setSingleMode(String userId, String source, String expertName, String agentId) {
        UserSession session = getSession(userId, source, agentId);
        session.setSingleMode();
        session.setTargetExpert(expertName);
        sessionStorage.save(session);
    }

    /**
     * 设置会话为群聊模式，并绑定群组会话标识
     *
     * @param userId         用户唯一标识
     * @param source         来源渠道
     * @param groupSessionId 群组会话标识
     * @param agentId        代理标识
     */
    public void setGroupMode(String userId, String source, String groupSessionId, String agentId) {
        UserSession session = getSession(userId, source, agentId);
        session.setGroupMode();
        session.setGroupSessionId(groupSessionId);
        sessionStorage.save(session);
    }

    /**
     * 根据当前会话模式获取目标专家列表
     * <ul>
     *   <li>单聊模式：返回已绑定的专家（若未绑定则回退返回可用专家列表中的第一个）</li>
     *   <li>群聊模式：返回所有可用专家</li>
     * </ul>
     *
     * @param session          当前用户会话
     * @param availableExperts 可用专家列表
     * @return 目标专家列表，不会返回 null
     */
    public List<String> getTargetExperts(UserSession session, List<String> availableExperts) {
        if (session == null || availableExperts == null || availableExperts.isEmpty()) {
            return Collections.emptyList();
        }

        if (session.getMode() == ChatMode.GROUP) {
            // 群聊模式：返回所有可用专家
            return new ArrayList<>(availableExperts);
        }

        // 单聊模式
        String targetExpert = session.getTargetExpert();
        if (targetExpert != null && !targetExpert.isBlank()) {
            // 已绑定专家，返回该专家
            return Collections.singletonList(targetExpert);
        }

        // 未绑定专家，回退返回第一个可用专家
        return Collections.singletonList(availableExperts.get(0));
    }

    /**
     * 清除指定用户的会话
     *
     * @param userId  用户唯一标识
     * @param source  来源渠道
     * @param agentId 代理标识
     */
    public void clearSession(String userId, String source, String agentId) {
        String sessionKey = buildSessionKey(userId, source, agentId);
        sessionStorage.delete(sessionKey);
    }

    /**
     * 清理所有已过期的会话
     * 将会话超时时间（秒）转换为分钟进行清理
     */
    public void cleanupExpiredSessions() {
        long maxInactiveMinutes = defaultTimeoutSeconds / 60;
        if (maxInactiveMinutes <= 0) {
            maxInactiveMinutes = 1;
        }
        sessionStorage.cleanupExpired(maxInactiveMinutes);
    }

    /**
     * 获取默认超时时间（秒）
     *
     * @return 超时时间
     */
    public long getDefaultTimeoutSeconds() {
        return defaultTimeoutSeconds;
    }
}
