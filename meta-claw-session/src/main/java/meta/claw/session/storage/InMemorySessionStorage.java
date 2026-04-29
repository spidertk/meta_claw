package meta.claw.session.storage;

import meta.claw.session.model.UserSession;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存会话存储实现
 * 基于 ConcurrentHashMap 实现线程安全的会话存储，适用于单机部署或开发测试环境
 */
public class InMemorySessionStorage implements SessionStorage {

    /**
     * 线程安全的会话存储映射表，键为 sessionKey，值为 UserSession
     */
    private final ConcurrentHashMap<String, UserSession> sessions = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public UserSession get(String sessionKey) {
        return sessions.get(sessionKey);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void save(UserSession session) {
        sessions.put(session.getSessionKey(), session);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(String sessionKey) {
        sessions.remove(sessionKey);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<UserSession> listAll() {
        return new ArrayList<>(sessions.values());
    }

    /**
     * {@inheritDoc}
     * 根据会话的最后活动时间与当前时间的差值判断是否超出最大不活跃时长，
     * 若已过期则从存储中移除该会话
     */
    @Override
    public void cleanupExpired(long maxInactiveMinutes) {
        LocalDateTime now = LocalDateTime.now();
        sessions.entrySet().removeIf(entry -> {
            UserSession session = entry.getValue();
            LocalDateTime lastActivity = session.getLastActivity();
            // 若最后活动时间为空，视为已过期
            if (lastActivity == null) {
                return true;
            }
            // 计算不活跃时长是否超过阈值
            long inactiveMinutes = Duration.between(lastActivity, now).toMinutes();
            return inactiveMinutes > maxInactiveMinutes;
        });
    }
}
