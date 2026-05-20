package meta.claw.cli.workspace;

import meta.claw.core.spi.workspace.WorkspaceProvider;
import meta.claw.vessel.ProjectRootFinder;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 基于 .meta-claw 目录结构的 Vessel 工作区提供者。
 * 每个 Vessel 拥有独立的工作区：{@code .meta-claw/vessels/<vesselId>/workspace}。
 */
@Component
public class MetaClawWorkspaceProvider implements WorkspaceProvider {

    @Override
    public Path getWorkspaceDir(String vesselId) {
        return ProjectRootFinder.getMetaClawDir()
                .resolve("vessels")
                .resolve(vesselId)
                .resolve("workspace");
    }
}
