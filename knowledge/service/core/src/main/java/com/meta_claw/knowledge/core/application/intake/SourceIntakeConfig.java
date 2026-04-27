package com.meta_claw.knowledge.core.application.intake;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 来源采集的运行时配置。
 * 当前只承载单批扫描大小，后续可继续扩展忽略规则、深度限制、文件大小限制等字段。
 */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SourceIntakeConfig {
    /** 默认批次大小。 */
    public static final int DEFAULT_UNIT_LIMIT = 512;

    /** 单批扫描最多纳入的单元数量。 */
    private int unitLimit;

    /** 创建默认配置。 */
    public static SourceIntakeConfig defaultConfig() {
        return SourceIntakeConfig.builder()
                .unitLimit(DEFAULT_UNIT_LIMIT)
                .build();
    }
}
