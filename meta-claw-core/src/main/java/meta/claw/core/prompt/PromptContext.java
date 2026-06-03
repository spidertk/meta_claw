package meta.claw.core.prompt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import meta.claw.core.config.bundle.VesselConfigBundle;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PromptContext {

    /**
     * 统一配置视图。所有配置访问都委托给 bundle。
     */
    private VesselConfigBundle bundle;

    /**
     * 运行时动态数据：当前时间（渲染时刻生成）
     */
    private String currentTime;

    /**
     * 运行时动态数据：当前时区位置
     */
    private String location;
}
