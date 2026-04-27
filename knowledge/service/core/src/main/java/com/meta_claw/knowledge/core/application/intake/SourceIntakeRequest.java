package com.meta_claw.knowledge.core.application.intake;

import com.meta_claw.knowledge.core.domain.SourceRecord;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Objects;

/**
 * 单次来源采集请求。
 * 把来源视图、扫描游标和运行时配置打包为一个对象，避免方法签名继续膨胀。
 */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SourceIntakeRequest {
    private SourceRecord sourceRecord;
    private String scanCursor;
    private SourceIntakeConfig config;

    /** 创建一个带默认配置的请求。 */
    public static SourceIntakeRequest of(SourceRecord sourceRecord, String scanCursor) {
        return SourceIntakeRequest.builder()
                .sourceRecord(Objects.requireNonNull(sourceRecord, "sourceRecord"))
                .scanCursor(scanCursor)
                .config(SourceIntakeConfig.defaultConfig())
                .build();
    }
}
