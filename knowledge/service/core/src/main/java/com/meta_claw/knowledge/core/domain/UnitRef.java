package com.meta_claw.knowledge.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * 快照中的可追踪内容单元引用。
 * 用于把一个 snapshot 拆成 repo/file/document/section 等可定位单元，并保留父子和邻接关系。
 */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class UnitRef {
    /** 单元在当前快照内的稳定标识。 */
    private String unitId;
    /** 单元所属快照标识。 */
    private String snapshotId;
    /** 父单元标识；根单元没有父级时为 null。 */
    private String parentUnitId;
    /** 单元类型，例如 repo、file、document、section、chunk 或 symbol。 */
    private String unitType;
    /** 单元路径；根单元使用绝对路径，子文件单元使用相对根目录路径。 */
    private String path;
    /** 面向人类展示的名称。 */
    private String displayName;
    /** 同层或相关单元标识；当前目录快照中，根单元用它指向展开出的文件单元。 */
    private List<String> neighbors;
}
