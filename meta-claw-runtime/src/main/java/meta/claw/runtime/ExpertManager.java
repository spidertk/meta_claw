package meta.claw.runtime;

import lombok.extern.slf4j.Slf4j;
import meta.claw.runtime.model.ExpertConfig;
import meta.claw.runtime.model.SessionConfig;
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
 * Expert 管理器
 * <p>
 * 负责扫描、加载和管理 experts/ 目录下的所有 Expert 配置，
 * 并维护 Expert 运行时实例的注册与查询。
 * </p>
 */
@Slf4j
public class ExpertManager {

    /**
     * 默认 experts 配置目录名称
     */
    private static final String DEFAULT_EXPERTS_DIR = "experts";

    /**
     * Expert 配置文件名称
     */
    private static final String EXPERT_CONFIG_FILE = "expert.yaml";

    /**
     * 存储已加载的 Expert 配置，key 为 expertId
     */
    private final ConcurrentHashMap<String, ExpertConfig> experts = new ConcurrentHashMap<>();

    /**
     * 存储已注册的 Expert 运行时实例，key 为 expertId
     */
    private final ConcurrentHashMap<String, ExpertRuntime> runtimes = new ConcurrentHashMap<>();

    /**
     * experts 目录的绝对路径
     */
    private final String expertsDir;

    /**
     * 构造方法：使用默认 experts 目录
     */
    public ExpertManager() {
        this(DEFAULT_EXPERTS_DIR);
    }

    /**
     * 构造方法：指定 experts 目录
     *
     * @param expertsDir experts 配置目录路径
     */
    public ExpertManager(String expertsDir) {
        this.expertsDir = expertsDir;
    }

    /**
     * 扫描 experts/ 目录下的所有 expert.yaml 文件并加载配置
     * <p>
     * 遍历 expertsDir 下的每个子目录，若存在 expert.yaml 则使用 SnakeYAML 解析，
     * 将解析结果转换为 ExpertConfig 并缓存到内存中。
     * </p>
     */
    public void loadExperts() {
        File dir = new File(expertsDir);
        if (!dir.exists() || !dir.isDirectory()) {
            log.warn("Experts 目录不存在或不是有效目录: {}", expertsDir);
            return;
        }

        File[] subDirs = dir.listFiles(File::isDirectory);
        if (subDirs == null) {
            return;
        }

        Yaml yaml = new Yaml();
        for (File subDir : subDirs) {
            File configFile = new File(subDir, EXPERT_CONFIG_FILE);
            if (!configFile.exists()) {
                log.debug("跳过目录 {}，未找到 {}", subDir.getName(), EXPERT_CONFIG_FILE);
                continue;
            }

            try (InputStream input = new FileInputStream(configFile)) {
                Map<String, Object> map = yaml.load(input);
                if (map == null) {
                    log.warn("配置文件为空: {}", configFile.getAbsolutePath());
                    continue;
                }

                ExpertConfig config = mapToExpertConfig(map);
                if (config.getId() == null || config.getId().isEmpty()) {
                    log.warn("Expert 配置缺少 id 字段，跳过: {}", configFile.getAbsolutePath());
                    continue;
                }

                experts.put(config.getId(), config);
                log.info("成功加载 Expert 配置: {} ({})", config.getId(), config.getName());
            } catch (Exception e) {
                log.error("加载 Expert 配置失败: {}", configFile.getAbsolutePath(), e);
            }
        }
    }

    /**
     * 将 YAML 解析后的 Map 转换为 ExpertConfig 对象
     *
     * @param map SnakeYAML 解析得到的 Map
     * @return 转换后的 ExpertConfig 实例
     */
    @SuppressWarnings("unchecked")
    public ExpertConfig mapToExpertConfig(Map<String, Object> map) {
        ExpertConfig config = new ExpertConfig();
        config.setId(getString(map, "id"));
        config.setName(getString(map, "name"));
        config.setDescription(getString(map, "description"));
        config.setEmoji(getString(map, "emoji"));
        config.setModel(getString(map, "model"));
        config.setSystemPrompt(getString(map, "systemPrompt"));
        config.setMemoryEnabled(getBoolean(map, "memoryEnabled"));
        config.setKnowledgeDir(getString(map, "knowledgeDir"));
        config.setExcludeTools(getStringList(map, "excludeTools"));

        // 解析嵌套的 session 配置
        Object sessionObj = map.get("session");
        if (sessionObj instanceof Map) {
            Map<String, Object> sessionMap = (Map<String, Object>) sessionObj;
            SessionConfig sessionConfig = new SessionConfig();
            sessionConfig.setStorageType(getString(sessionMap, "storageType"));
            sessionConfig.setFilePath(getString(sessionMap, "filePath"));
            config.setSession(sessionConfig);
        }

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
     * 根据 expertId 获取 Expert 配置
     *
     * @param expertId Expert 唯一标识
     * @return ExpertConfig 实例，若不存在则返回 null
     */
    public ExpertConfig getConfig(String expertId) {
        return experts.get(expertId);
    }

    /**
     * 根据 expertId 获取已注册的 Expert 运行时实例
     *
     * @param expertId Expert 唯一标识
     * @return ExpertRuntime 实例，若未注册则返回 null
     */
    public ExpertRuntime getRuntime(String expertId) {
        return runtimes.get(expertId);
    }

    /**
     * 注册 Expert 运行时实例
     *
     * @param expertId Expert 唯一标识
     * @param runtime  Expert 运行时实例
     */
    public void registerRuntime(String expertId, ExpertRuntime runtime) {
        runtimes.put(expertId, runtime);
        log.info("成功注册 Expert 运行时: {}", expertId);
    }

    /**
     * 获取所有已加载的 Expert 配置列表
     *
     * @return ExpertConfig 列表
     */
    public List<ExpertConfig> listAvailableExperts() {
        return Collections.unmodifiableList(List.copyOf(experts.values()));
    }

    /**
     * 判断是否存在指定 Expert 配置
     *
     * @param expertId Expert 唯一标识
     * @return true 表示存在，false 表示不存在
     */
    public boolean hasExpert(String expertId) {
        return experts.containsKey(expertId);
    }

}
