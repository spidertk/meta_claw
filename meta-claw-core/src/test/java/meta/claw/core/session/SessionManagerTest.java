package meta.claw.core.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SessionManager 单元测试
 * 验证会话管理器的核心逻辑，包括会话创建、模式切换、目标 Vessel 解析等功能
 */
class SessionManagerTest {

    /**
     * 测试用的会话存储实现（内存版）
     */
    private SessionStorage sessionStorage;

    /**
     * 被测试的会话管理器实例
     */
    private SessionManager sessionManager;

    /**
     * 每个测试方法执行前的初始化操作
     */
    @BeforeEach
    void setUp() {
        sessionStorage = new InMemorySessionStorage();
        sessionManager = new SessionManager(sessionStorage);
    }

    /**
     * 测试：获取不存在的会话时应自动创建新会话，并自动更新最后活动时间
     */
    @Test
    void testGetSessionCreatesNewSession() {
        String userId = "user_001";
        String source = "wechat";
        String agentId = "agent_001";

        // 首次获取会话，应该自动创建
        UserSession session = sessionManager.getSession(userId, source, agentId);

        // 断言新会话的基本属性
        assertNotNull(session, "获取的会话不应为 null");
        assertEquals("user_001:wechat:agent_001", session.getSessionKey(), "会话键应正确拼接");
        assertEquals(userId, session.getUserId(), "用户 ID 应一致");
        assertEquals(source, session.getSource(), "来源渠道应一致");
        assertEquals(agentId, session.getAgentId(), "代理 ID 应一致");
        assertEquals(ChatMode.SINGLE, session.getMode(), "默认模式应为单聊");
        assertNotNull(session.getLastActivity(), "最后活动时间应被自动设置");
        assertNotNull(session.getCreatedAt(), "创建时间应被自动设置");

        // 断言会话已被持久化到存储中
        UserSession stored = sessionStorage.get(session.getSessionKey());
        assertNotNull(stored, "会话应已被保存到存储中");
        assertEquals(session.getLastActivity(), stored.getLastActivity(), "存储中的会话活动时间应一致");
    }

    /**
     * 测试：设置单聊模式后，会话状态应正确更新
     */
    @Test
    void testSetSingleMode() {
        String userId = "user_002";
        String source = "web";
        String vesselName = "VesselA";
        String agentId = "agent_002";

        // 设置单聊模式
        sessionManager.setSingleMode(userId, source, vesselName, agentId);

        // 从存储中读取并验证
        String sessionKey = sessionManager.buildSessionKey(userId, source, agentId);
        UserSession session = sessionStorage.get(sessionKey);

        assertNotNull(session, "会话应已被创建并保存");
        assertEquals(ChatMode.SINGLE, session.getMode(), "会话模式应为单聊");
        assertEquals(vesselName, session.getTargetVessel(), "目标 Vessel 应被正确绑定");
        assertNull(session.getGroupSessionId(), "群聊会话 ID 应为 null");
    }

    /**
     * 测试：单聊模式下，getTargetVessels 应返回已绑定的 Vessel
     */
    @Test
    void testGetTargetVesselsSingleMode() {
        String userId = "user_003";
        String source = "slack";
        String vesselName = "VesselB";
        String agentId = "agent_003";
        List<String> availableVessels = Arrays.asList("VesselA", "VesselB", "VesselC");

        // 先设置为单聊模式并绑定 Vessel
        sessionManager.setSingleMode(userId, source, vesselName, agentId);
        UserSession session = sessionManager.getSession(userId, source, agentId);

        // 获取目标 Vessel
        List<String> targets = sessionManager.getTargetVessels(session, availableVessels);

        assertNotNull(targets, "返回的 Vessel 列表不应为 null");
        assertEquals(1, targets.size(), "单聊模式应只返回一个 Vessel");
        assertEquals(vesselName, targets.get(0), "应返回已绑定的 Vessel");
    }

    /**
     * 测试：未绑定 Vessel 时回退返回第一个可用 Vessel；群聊模式返回所有 Vessel
     */
    @Test
    void testGetTargetVesselsFallback() {
        String userId = "user_004";
        String source = "web";
        String agentId = "agent_004";
        List<String> availableVessels = Arrays.asList("VesselX", "VesselY", "VesselZ");

        // 场景一：单聊模式但未绑定 Vessel，应回退返回第一个可用 Vessel
        UserSession session = sessionManager.getSession(userId, source, agentId);
        // 确保当前为单聊模式且未设置 targetVessel
        assertEquals(ChatMode.SINGLE, session.getMode());
        assertNull(session.getTargetVessel());

        List<String> fallbackTargets = sessionManager.getTargetVessels(session, availableVessels);
        assertEquals(1, fallbackTargets.size(), "未绑定 Vessel 时应回退返回一个 Vessel");
        assertEquals("VesselX", fallbackTargets.get(0), "应返回可用 Vessel 列表中的第一个");

        // 场景二：群聊模式应返回所有可用 Vessel
        sessionManager.setGroupMode(userId, source, "group_001", agentId);
        UserSession groupSession = sessionManager.getSession(userId, source, agentId);
        List<String> groupTargets = sessionManager.getTargetVessels(groupSession, availableVessels);

        assertEquals(3, groupTargets.size(), "群聊模式应返回所有可用 Vessel");
        assertTrue(groupTargets.containsAll(availableVessels), "返回列表应包含所有可用 Vessel");
    }

    /**
     * 测试：清除会话后，存储中应不再存在该会话
     */
    @Test
    void testClearSession() {
        String userId = "user_005";
        String source = "wechat";
        String agentId = "agent_005";

        // 先创建会话
        UserSession session = sessionManager.getSession(userId, source, agentId);
        assertNotNull(sessionStorage.get(session.getSessionKey()), "会话应存在于存储中");

        // 清除会话
        sessionManager.clearSession(userId, source, agentId);

        // 验证已删除
        assertNull(sessionStorage.get(session.getSessionKey()), "会话应已被清除");
    }

    /**
     * 测试：cleanupExpiredSessions 应正确清理过期会话
     */
    @Test
    void testCleanupExpiredSessions() {
        String userId = "user_006";
        String source = "web";
        String agentId = "agent_006";

        // 使用极短的超时时间（1 秒）构造管理器，便于测试过期逻辑
        SessionManager shortTimeoutManager = new SessionManager(sessionStorage, 1);

        // 创建会话
        UserSession session = shortTimeoutManager.getSession(userId, source, agentId);
        assertNotNull(sessionStorage.get(session.getSessionKey()));

        // 手动将最后活动时间回退到 2 分钟前，使其过期
        session.setLastActivity(LocalDateTime.now().minusMinutes(2));
        sessionStorage.save(session);

        // 执行过期清理（超时 1 秒即约 0 分钟，会被视为过期）
        shortTimeoutManager.cleanupExpiredSessions();

        // 验证会话已被清理
        assertNull(sessionStorage.get(session.getSessionKey()), "过期会话应被清理");
    }

    /**
     * 测试：传入 null 会话或空可用 Vessel 列表时，应返回空列表
     */
    @Test
    void testGetTargetVesselsWithNullOrEmpty() {
        List<String> availableVessels = Arrays.asList("VesselA", "VesselB");

        // null 会话
        List<String> result1 = sessionManager.getTargetVessels(null, availableVessels);
        assertTrue(result1.isEmpty(), "null 会话应返回空列表");

        // null 可用 Vessel 列表
        UserSession session = sessionManager.getSession("u1", "s1", "a1");
        List<String> result2 = sessionManager.getTargetVessels(session, null);
        assertTrue(result2.isEmpty(), "null 可用 Vessel 列表应返回空列表");

        // 空可用 Vessel 列表
        List<String> result3 = sessionManager.getTargetVessels(session, Collections.emptyList());
        assertTrue(result3.isEmpty(), "空可用 Vessel 列表应返回空列表");
    }

    /**
     * 测试：默认构造函数应使用 3600 秒作为默认超时时间
     */
    @Test
    void testDefaultTimeout() {
        assertEquals(3600, sessionManager.getDefaultTimeoutSeconds(), "默认超时时间应为 3600 秒");
    }
}
