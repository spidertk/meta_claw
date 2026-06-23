package meta.claw.core.runtime;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.config.HitlConfig;
import meta.claw.core.config.loader.VesselConfigLoader;
import meta.claw.core.config.VesselConfig;
import meta.claw.core.infra.ProjectRootFinder;
import meta.claw.core.runtime.hitl.ConfigurableHitlPolicy;
import meta.claw.core.runtime.hitl.HitlPolicy;
import meta.claw.core.vessel.VesselInitializer;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Vessel 管理器
 * <p>
 * 负责扫描、加载和管理 vessels/ 目录下的所有 Vessel 配置，
 * 并维护 Vessel 运行时实例的注册与查询。
 * </p>
 */
@Slf4j
@Component
public class VesselManager implements InitializingBean {

    @Autowired
    private ObjectProvider<VesselRuntime> vesselRuntime;

    @Autowired
    private VesselConfigLoader vesselConfigLoader;

    @Autowired
    private VesselInitializer vesselInitializer;

    @Autowired
    private HitlPolicy hitlPolicy;

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
    private final Path vesselsDir = ProjectRootFinder.getMetaClawDir().resolve("vessels");



    /**
     * 扫描 vessels/ 目录下的所有 vessel.md 文件并加载配置
     * <p>
     * 遍历 vesselsDir 下的每个子目录，若存在 vessel.md 则使用 SnakeYAML 解析，
     * 将解析结果转换为 VesselConfig 并缓存到内存中。
     * </p>
     */
    public void loadVessels() {
        List<VesselConfig> loaded = vesselConfigLoader.loadFromDirectory(vesselsDir);
        for (VesselConfig meta : loaded) {
            if (meta.getIdentity().getId() != null && !meta.getIdentity().getId().isEmpty()) {
                vessels.put(meta.getIdentity().getId(), meta);
                configureHitlPolicy(meta);
                log.info("Loaded vessel config: {} ({})", meta.getIdentity().getId(), meta.getIdentity().getName());
            }
        }
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
     */
    public void registerRuntime(String vesselId) {
        VesselRuntime runtime = vesselRuntime.getObject(vesselId);
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

    /**
     * 创建 Vessel：写盘 → 加载配置 → 注册内存 → 注册 Runtime。
     * <p>整个操作加锁，保证原子性。</p>
     *
     * @param name        vessel 目录名（即 vesselId）
     * @param description 描述
     * @return 创建后的 VesselConfig
     */
    public synchronized VesselConfig createVessel(String name, String description) {
        if (vessels.containsKey(name)) {
            throw new IllegalStateException("Vessel already exists: " + name);
        }
        try {
            vesselInitializer.createVessel(vesselsDir, name,
                    description != null ? description : "A customized AI vessel for specific tasks.");
        } catch (IOException e) {
            throw new RuntimeException("Failed to create vessel directory: " + name, e);
        }

        VesselConfig config = vesselConfigLoader.load(vesselsDir.resolve(name));
        vessels.put(name, config);
        configureHitlPolicy(config);
        log.info("Loaded vessel config into memory: {}", name);

        registerRuntime(name);
        return config;
    }

    /**
     * 删除 Vessel：销毁 Runtime → 清内存 → 删目录。
     * <p>整个操作加锁，保证原子性。若 runtime 不存在则静默跳过。</p>
     *
     * @param vesselId Vessel 唯一标识
     */
    public synchronized void deleteVessel(String vesselId) {
        VesselRuntime runtime = runtimes.remove(vesselId);
        if (runtime != null) {
            try {
                runtime.shutdown();
            } catch (Exception e) {
                log.warn("Error during runtime shutdown for vessel {}: {}", vesselId, e.getMessage());
            }
        }

        vessels.remove(vesselId);
        log.info("Removed vessel from memory: {}", vesselId);

        Path vesselDir = vesselsDir.resolve(vesselId);
        if (Files.exists(vesselDir)) {
            try {
                deleteDirectory(vesselDir);
                log.info("Deleted vessel directory: {}", vesselDir);
            } catch (IOException e) {
                throw new RuntimeException("Failed to delete vessel directory: " + vesselDir, e);
            }
        }
    }

    /**
     * 重新扫描 vessels/ 目录，同步内存与磁盘。
     * <p>用于 init 完成后或外部手动修改目录后的兜底同步。</p>
     */
    public synchronized void refresh() {
        // 1. 清理已不存在于磁盘的 vessel
        List<String> toRemove = vessels.keySet().stream()
                .filter(id -> !Files.exists(vesselsDir.resolve(id).resolve("vessel.meta.yaml")))
                .toList();
        for (String id : toRemove) {
            deleteVessel(id);
        }

        // 2. 加载新出现的 vessel
        loadVessels();
        for (VesselConfig meta : listAvailableVessels()) {
            String id = meta.getIdentity().getId();
            if (!runtimes.containsKey(id)) {
                registerRuntime(id);
            }
        }
    }

    private void configureHitlPolicy(VesselConfig config) {
        if (config == null || config.getHitl() == null) {
            return;
        }
        if (!(hitlPolicy instanceof ConfigurableHitlPolicy policy)) {
            return;
        }
        HitlConfig hitl = config.getHitl();
        Set<String> require = hitl.getRequire() != null ? Set.copyOf(hitl.getRequire()) : null;
        Set<String> skip = hitl.getSkip() != null ? Set.copyOf(hitl.getSkip()) : null;
        String vesselId = config.getIdentity() != null ? config.getIdentity().getId() : null;
        if (vesselId == null || vesselId.isBlank()) {
            return;
        }
        policy.configure(vesselId, require, skip, hitl.getDefaultRequireApproval());
        log.info("Loaded HITL config for vessel {}: require={}, skip={}, defaultRequireApproval={}",
                vesselId, require, skip, hitl.getDefaultRequireApproval());
    }

    private void deleteDirectory(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to delete: " + path, e);
                        }
                    });
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        loadVessels();
        for (VesselConfig meta : listAvailableVessels()) {
           registerRuntime(meta.getIdentity().getId());
        }
    }
}
