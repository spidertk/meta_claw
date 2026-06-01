package meta.claw.core.runtime;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.config.VesselConfigLoader;
import meta.claw.core.config.VesselConfig;
import meta.claw.core.infra.path.ProjectRootFinder;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Path vesselsDir = ProjectRootFinder.getMetaClawDir();



    /**
     * 扫描 vessels/ 目录下的所有 vessel.md 文件并加载配置
     * <p>
     * 遍历 vesselsDir 下的每个子目录，若存在 vessel.md 则使用 SnakeYAML 解析，
     * 将解析结果转换为 VesselConfig 并缓存到内存中。
     * </p>
     */
    public void loadVessels() {
        List<VesselConfig> loaded = vesselConfigLoader.loadFromDirectory(vesselsDir);
        for (VesselConfig config : loaded) {
            if (config.getId() != null && !config.getId().isEmpty()) {
                vessels.put(config.getId(), config);
                log.info("成功加载 Vessel 配置: {} ({})", config.getId(), config.getName());
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

    @Override
    public void afterPropertiesSet() throws Exception {
        loadVessels();
        for (VesselConfig config : listAvailableVessels()) {
           registerRuntime(config.getId());
        }
    }
}
