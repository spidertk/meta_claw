package meta.claw.session.storage;

import meta.claw.session.model.UserSession;

import java.util.List;

/**
 * 会话存储接口
 * 定义了用户会话的持久化与检索操作规范，具体实现可以是内存、数据库或缓存等方式
 */
public interface SessionStorage {

    /**
     * 根据会话键获取用户会话
     *
     * @param sessionKey 会话唯一标识键
     * @return 对应的用户会话，若不存在则返回 null
     */
    UserSession get(String sessionKey);

    /**
     * 保存用户会话到存储中
     * 若会话已存在则更新，不存在则新增
     *
     * @param session 待保存的用户会话对象
     */
    void save(UserSession session);

    /**
     * 根据会话键删除指定的用户会话
     *
     * @param sessionKey 会话唯一标识键
     */
    void delete(String sessionKey);

    /**
     * 获取存储中的所有用户会话列表
     *
     * @return 全部用户会话的列表
     */
    List<UserSession> listAll();

    /**
     * 清理已过期的会话
     * 根据会话的最后活动时间判断其是否超出存活期限，过期会话将被移除
     *
     * @param maxInactiveMinutes 允许的最大不活跃分钟数
     */
    void cleanupExpired(long maxInactiveMinutes);
}
