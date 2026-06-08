package meta.claw.core.runtime.subsystem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 子系统注册表。
 */
public class SubSystemRegistry {

    private final Map<String, VesselSubSystem> subSystems = new HashMap<>();

    public void register(VesselSubSystem subSystem) {
        if (subSystem == null || subSystem.name() == null) {
            throw new IllegalArgumentException("SubSystem name must not be null");
        }
        subSystems.put(subSystem.name(), subSystem);
    }

    @SuppressWarnings("unchecked")
    public <T extends VesselSubSystem> T get(String name) {
        return (T) subSystems.get(name);
    }

    public boolean has(String name) {
        return subSystems.containsKey(name);
    }

    public List<VesselSubSystem> listAll() {
        return new ArrayList<>(subSystems.values()).stream()
                .sorted(Comparator.comparingInt(VesselSubSystem::priority))
                .toList();
    }
}
