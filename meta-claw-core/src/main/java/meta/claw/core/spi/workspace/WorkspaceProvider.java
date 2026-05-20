package meta.claw.core.spi.workspace;

import java.nio.file.Path;

/**
 * 工作区目录提供者接口。
 * 为 Vessel 提供独立的工作区目录路径，避免 PromptContextFactory 直接依赖文件系统细节。
 */
public interface WorkspaceProvider {

    /**
     * 获取指定 Vessel 的工作区目录。
     *
     * @param vesselId Vessel 唯一标识
     * @return 工作区目录路径；若无法解析可返回 null
     */
    Path getWorkspaceDir(String vesselId);
}
