package meta.claw.gateway.channel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 渠道 Vessel 路由器
 * <p>
 * 维护 {@code {channelKey, chatKey} → vesselId} 的路由表，并持久化到 routes.json。
 * 解析优先级：
 * <ol>
 *   <li>路由表绑定（用户通过 /vessel 命令显式切换）；</li>
 *   <li>渠道账号配置的默认 vesselId；</li>
 *   <li>返回 null，由调用方兜底（通常为系统第一个可用 Vessel）。</li>
 * </ol>
 * 路由表 key 格式：{@code channelKey|chatKey}。
 * </p>
 */
@Slf4j
public class ChannelVesselRouter {

    /**
     * 路由表持久化文件（JSON：Map&lt;String, String&gt;）
     */
    private final Path routesFile;

    /**
     * 内存路由表，key 为 {@code channelKey|chatKey}
     */
    private final Map<String, String> routes = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造路由器并加载已有路由表
     *
     * @param routesFile 路由表持久化文件路径，不存在时视为空表
     */
    public ChannelVesselRouter(Path routesFile) {
        this.routesFile = routesFile;
        load();
    }

    /**
     * 解析目标 Vessel
     *
     * @param channelKey      渠道实例键（如 weixin:main）
     * @param chatKey         对话标识（私聊为对端 userId，群聊为 groupId）
     * @param defaultVesselId 账号配置的默认 vesselId，可为空
     * @return 解析到的 vesselId；无任何匹配时返回 null
     */
    public String resolve(String channelKey, String chatKey, String defaultVesselId) {
        String bound = routes.get(routeKey(channelKey, chatKey));
        if (bound != null && !bound.isBlank()) {
            return bound;
        }
        if (defaultVesselId != null && !defaultVesselId.isBlank()) {
            return defaultVesselId;
        }
        return null;
    }

    /**
     * 绑定对话到指定 Vessel 并持久化
     *
     * @param channelKey 渠道实例键
     * @param chatKey    对话标识
     * @param vesselId   目标 vesselId
     */
    public void bind(String channelKey, String chatKey, String vesselId) {
        routes.put(routeKey(channelKey, chatKey), vesselId);
        save();
        log.info("[ChannelVesselRouter] 路由绑定: {}|{} -> {}", channelKey, chatKey, vesselId);
    }

    /**
     * 查询当前绑定（测试与管理用）
     *
     * @return 路由表副本
     */
    public Map<String, String> listRoutes() {
        return Map.copyOf(routes);
    }

    private static String routeKey(String channelKey, String chatKey) {
        return channelKey + "|" + chatKey;
    }

    private void load() {
        if (routesFile == null || !Files.exists(routesFile)) {
            return;
        }
        try {
            Map<String, String> loaded = objectMapper.readValue(routesFile.toFile(),
                    new TypeReference<>() {
                    });
            if (loaded != null) {
                routes.putAll(loaded);
            }
            log.info("[ChannelVesselRouter] 已加载路由表: {} 条, file={}", routes.size(), routesFile);
        } catch (IOException e) {
            log.warn("[ChannelVesselRouter] 路由表加载失败，按空表处理: {}, error={}", routesFile, e.getMessage());
        }
    }

    private void save() {
        if (routesFile == null) {
            return;
        }
        try {
            Files.createDirectories(routesFile.getParent());
            Path tmp = routesFile.resolveSibling(routesFile.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), routes);
            Files.move(tmp, routesFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            try {
                Files.setPosixFilePermissions(routesFile, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException e) {
                log.debug("[ChannelVesselRouter] 当前文件系统不支持 POSIX 权限，跳过 600 设置");
            }
        } catch (IOException e) {
            log.error("[ChannelVesselRouter] 路由表持久化失败: {}, error={}", routesFile, e.getMessage());
        }
    }

    /**
     * 供测试读取原始内容
     */
    String readRaw() throws IOException {
        return Files.exists(routesFile) ? Files.readString(routesFile, StandardCharsets.UTF_8) : "";
    }
}
