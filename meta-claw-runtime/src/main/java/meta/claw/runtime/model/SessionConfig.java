package meta.claw.runtime.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 会话配置类
 * <p>
 * 用于定义 Expert 的会话存储方式与持久化路径。
 * </p>
 */
@Getter
@Setter
public class SessionConfig {

    /**
     * 会话存储类型，默认为 "memory"（内存存储）
     */
    private String storageType = "memory";

    /**
     * 会话持久化文件路径（当 storageType 为 file 时使用）
     */
    private String filePath;

}
