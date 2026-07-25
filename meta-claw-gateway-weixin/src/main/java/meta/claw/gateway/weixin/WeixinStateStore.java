package meta.claw.gateway.weixin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;

/**
 * 微信账号状态存储
 * <p>
 * 每个账号一个实例，管理状态目录 {@code <stateDir>} 下的持久化文件：
 * <ul>
 *   <li>{@code login.json}：登录态（botToken/botId/baseUrl/userId/updatedAt），存在则重启免扫码；</li>
 *   <li>{@code sync_buf}：monitor 断点续传游标。</li>
 * </ul>
 * 所有写入采用"临时文件 + 原子移动"，并尽可能设置 600 权限（含 token 敏感信息）。
 * </p>
 */
@Slf4j
public class WeixinStateStore {

    private static final String LOGIN_FILE = "login.json";
    private static final String SYNC_BUF_FILE = "sync_buf";

    private final Path stateDir;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 登录态快照
     *
     * @param botToken 登录获取的长期令牌
     * @param botId    Bot 标识
     * @param baseUrl  登录返回的服务基地址
     * @param userId   扫码微信号 userId
     */
    public record LoginState(String botToken, String botId, String baseUrl, String userId) {
    }

    public WeixinStateStore(Path stateDir) {
        this.stateDir = stateDir;
    }

    public Path getStateDir() {
        return stateDir;
    }

    /**
     * 读取登录态
     *
     * @return 登录态；文件不存在或解析失败返回 null
     */
    public LoginState loadLogin() {
        Path file = stateDir.resolve(LOGIN_FILE);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            var node = objectMapper.readTree(file.toFile());
            String token = textOrNull(node, "botToken");
            if (token == null || token.isBlank()) {
                return null;
            }
            return new LoginState(token, textOrNull(node, "botId"), textOrNull(node, "baseUrl"), textOrNull(node, "userId"));
        } catch (IOException e) {
            log.warn("[WeixinStateStore] login.json 解析失败，按无登录态处理: {}, error={}", file, e.getMessage());
            return null;
        }
    }

    /**
     * 保存登录态（登录成功后调用）
     */
    public void saveLogin(LoginState state) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("botToken", state.botToken());
            node.put("botId", state.botId());
            node.put("baseUrl", state.baseUrl());
            node.put("userId", state.userId());
            node.put("updatedAt", Instant.now().toString());
            byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(node);
            writeAtomic(stateDir.resolve(LOGIN_FILE), bytes);
            log.info("[WeixinStateStore] 登录态已持久化: botId={}, dir={}", state.botId(), stateDir);
        } catch (IOException e) {
            log.error("[WeixinStateStore] 登录态持久化失败: {}, error={}", stateDir, e.getMessage());
        }
    }

    /**
     * 删除登录态（重登录前调用）
     */
    public void deleteLogin() {
        try {
            Files.deleteIfExists(stateDir.resolve(LOGIN_FILE));
        } catch (IOException e) {
            log.warn("[WeixinStateStore] 删除 login.json 失败: {}", e.getMessage());
        }
    }

    /**
     * 读取断点续传游标
     *
     * @return 游标内容；不存在时返回空串
     */
    public String loadSyncBuf() {
        Path file = stateDir.resolve(SYNC_BUF_FILE);
        if (!Files.exists(file)) {
            return "";
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("[WeixinStateStore] sync_buf 读取失败，按空游标处理: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 保存断点续传游标（monitor 的 onBufUpdate 回调中调用）
     */
    public void saveSyncBuf(String buf) {
        if (buf == null || buf.isEmpty()) {
            return;
        }
        try {
            writeAtomic(stateDir.resolve(SYNC_BUF_FILE), buf.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("[WeixinStateStore] sync_buf 持久化失败: {}", e.getMessage());
        }
    }

    private static String textOrNull(com.fasterxml.jackson.databind.JsonNode node, String field) {
        var value = node.get(field);
        return value != null && !value.isNull() ? value.asText() : null;
    }

    private void writeAtomic(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(stateDir);
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, bytes);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        try {
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException e) {
            log.debug("[WeixinStateStore] 当前文件系统不支持 POSIX 权限，跳过 600 设置: {}", target);
        }
    }
}
