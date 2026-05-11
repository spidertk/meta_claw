package meta.claw.core.session;

import lombok.Builder;
import lombok.Getter;

/**
 * 媒体文件引用，记录保存后的绝对路径、相对路径和媒体类型。
 */
@Getter
@Builder
public class MediaReference {
    private String absolutePath;
    private String relativePath;
    private String mediaType;
}
