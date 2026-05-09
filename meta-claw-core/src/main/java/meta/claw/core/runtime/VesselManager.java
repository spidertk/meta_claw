package meta.claw.core.runtime;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.model.VesselConfig;
import meta.claw.core.model.SessionConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Vessel 管理器
 * <p>
 * 负责扫描、加载和管理 vessels/ 目录下的所有 Vessel 配置，
 * 并维护 Vessel 运行时实例的注册与查询。
 * </p>
 */
@Slf4j
public class VesselManager {

    /**
     * 默认 vessels 配置目录名称
     */
    private static final String DEFAULT_VESSELS_DIR = "vessels";

    /**
     * Vessel 配置文件名称
     */
    private static final String VESSEL_CONFIG_FILE = "vessel.md";

    /**
     * 存储已加载的 Vessel 配置，key 为 vesselId
     */
    private final ConcurrentHashMap<String, VesselConfig> vessels = new ConcurrentHashMap<>();

    /**
     * 存储已注册的 Vessel 运行时实例，key 为 vesselId
     */
    private final ConcurrentHashMap<String, VesselRuntime> runtimes = new ConcurrentHashMap<>();

    /**
     * vessels 目录的绝对路径
     */
    private final String vesselsDir;

    /**
     * 构造方法：使用默认 vessels 目录
     */
    public VesselManager() {
        this(DEFAULT_VESSELS_DIR);
    }

    /**
     * 构造方法：指定 vessels 目录
     *
     * @param vesselsDir vessels 配置目录路径
     */
    public VesselManager(String vesselsDir) {
        this.vesselsDir = vesselsDir;
    }

    /**
     * 扫描 vessels/ 目录下的所有 vessel.md 文件并加载配置
     * <p>
     * 遍历 vesselsDir 下的每个子目录，若存在 vessel.md 则使用 SnakeYAML 解析，
     * 将解析结果转换为 VesselConfig 并缓存到内存中。
     * </p>
     */
    public void loadVessels() {
        File dir = new File(vesselsDir);
        if (!dir.exists() || !dir.isDirectory()) {
            log.warn("Vessels 目录不存在或不是有效目录: {}", vesselsDir);
            return;
        }

        File[] subDirs = dir.listFiles(File::isDirectory);
        if (subDirs == null) {
            return;
        }

        Yaml yaml = new Yaml();
        for (File subDir : subDirs) {
            File configFile = new File(subDir, VESSEL_CONFIG_FILE);
            if (!configFile.exists()) {
                log.debug("跳过目录 {}，未找到 {}", subDir.getName(), VESSEL_CONFIG_FILE);
                continue;
            }

            try (InputStream input = new FileInputStream(configFile)) {
                Map<String, Object> map = yaml.load(input);
                if (map == null) {
                    log.warn("配置文件为空: {}", configFile.getAbsolutePath());
                    continue;
                }

                VesselConfig config = mapToVesselConfig(map);
                if (config.getId() == null || config.getId().isEmpty()) {
                    log.warn("Vessel 配置缺少 id 字段，跳过: {}", configFile.getAbsolutePath());
                    continue;
                }

                vessels.put(config.getId(), config);
                log.info("成功加载 Vessel 配置: {} ({})", config.getId(), config.getName());
            } catch (Exception e) {
                log.error("加载 Vessel 配置失败: {}", configFile.getAbsolutePath(), e);
            }
        }
    }

    /**
     * 将 YAML 解析后的 Map 转换为 VesselConfig 对象
     *
     * @param map SnakeYAML 解析得到的 Map
     * @return 转换后的 VesselConfig 实例
     */
    @SuppressWarnings("unchecked")
    public VesselConfig mapToVesselConfig(Map<String, Object> map) {
        VesselConfig config = new VesselConfig();
        config.setId(getString(map, "id"));
        config.setName(getString(map, "name"));
        config.setDescription(getString(map, "description"));
        config.setEmoji(getString(map, "emoji"));
        config.setModel(getString(map, "model"));
        config.setSystemPrompt(getString(map, "systemPrompt"));
        config.setPreferencesEnabled(getBoolean(map, "preferencesEnabled"));
        config.setKnowledgeDir(getString(map, "knowledgeDir"));
        config.setExcludeTools(getStringList(map, "excludeTools"));

        return config;
    }

    /**
     * 从 Map 中安全获取字符串值
     *
     * @param map Map 对象
     * @param key 键名
     * @return 字符串值，若不存在则返回 null
     */
    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 从 Map 中安全获取布尔值
     *
     * @param map Map 对象
     * @param key 键名
     * @return 布尔值，若不存在则返回 false
     */
    private boolean getBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return false;
    }

    /**
     * 从 Map 中安全获取字符串列表
     *
     * @param map Map 对象
     * @param key 键名
     * @return 字符串列表，若不存在则返回空列表
     */
    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return ((List<?>) value).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * 根据 vesselId 获取 Vessel 配置
     *
     * @param vesselId Vessel 唯一标识
     * @return VesselConfig 实例，若不存在则返回 null
     */
    public VesselConfig getConfig(String vesselId) {
        return vessels.get(vesselId);
    }

    /**
     * 根据 vesselId 获取已注册的 Vessel 运行时实例
     *
     * @param vesselId Vessel 唯一标识
     * @return VesselRuntime 实例，若未注册则返回 null
     */
    public VesselRuntime getRuntime(String vesselId) {
        return runtimes.get(vesselId);
    }

    /**
     * 注册 Vessel 运行时实例
     *
     * @param vesselId Vessel 唯一标识
     * @param runtime  Vessel 运行时实例
     */
    public void registerRuntime(String vesselId, VesselRuntime runtime) {
        runtimes.put(vesselId, runtime);
        log.info("成功注册 Vessel 运行时: {}", vesselId);
    }

    /**
     * 获取所有已加载的 Vessel 配置列表
     *
     * @return VesselConfig 列表
     */
    public List<VesselConfig> listAvailableVessels() {
        return Collections.unmodifiableList(List.copyOf(vessels.values()));
    }

    /**
     * 判断是否存在指定 Vessel 配置
     *
     * @param vesselId Vessel 唯一标识
     * @return true 表示存在，false 表示不存在
     */
    public boolean hasVessel(String vesselId) {
        return vessels.containsKey(vesselId);
    }

}
