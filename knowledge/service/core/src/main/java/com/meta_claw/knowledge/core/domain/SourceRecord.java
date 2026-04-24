package com.meta_claw.knowledge.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * 来源注册表中的当前视图。
 * 保存来源稳定身份、当前元数据和指向最新快照的派生指针。
 */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SourceRecord {
    /** 来源所属知识空间标识。 */
    private String spaceId;
    /** 来源稳定标识；同一来源多次采集时保持不变。 */
    private String sourceId;
    /** 来源类型，例如 git 仓库、文件或网页。 */
    private String sourceType;
    /** 来源原始位置；用于重新定位来源，不表示某次快照内容。 */
    private String location;
    /** 面向人类展示的来源名称。 */
    private String displayName;
    /** 来源当前处理状态，不表示历史快照状态。 */
    private String status;
    /** 来源的补充描述信息。 */
    private String description;
    /** 工作区身份信息，用于识别来源所在宿主环境。 */
    private WorkspaceIdentity workspaceIdentity;
    /** 生成快照时使用的提示信息，不保存历史事实。 */
    private SnapshotHint snapshotHint;
    /** 来源首次注册时间。 */
    private Instant createdAt;
    /** 来源当前视图最近更新时间。 */
    private Instant updatedAt;
    /** 指向当前最新快照的派生指针，不保存快照历史本体。 */
    private String latestSnapshotId;

    /** 来源所在工作区的稳定识别信息。 */
    @Data
    @SuperBuilder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkspaceIdentity {
        /** 工作区标识。 */
        private String workspaceId;
        /** 工作区根路径。 */
        private String workspaceRoot;
        /** 版本控制系统类型，例如 git。 */
        private String vcs;
        /** 来源接入模式，例如 native_git。 */
        private String originMode;
        /** 远端仓库地址；本地来源可为空。 */
        private String remoteUrl;
        /** 默认分支名。 */
        private String defaultBranch;
    }

    /** 生成快照时可参考的临时提示信息。 */
    @Data
    @SuperBuilder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SnapshotHint {
        /** 采集时关注的分支名。 */
        private String branch;
        /** 采集时观察到的提交标识。 */
        private String headCommit;
        /** 工作区状态提示，例如 clean 或 dirty。 */
        private String worktreeState;
    }
}
