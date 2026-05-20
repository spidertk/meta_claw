package meta.claw.core.prompt;

/**
 * 用户偏好查询的窄接口。
 * 将长期记忆存储的偏好读取与格式化逻辑从 PromptContextFactory 中解耦。
 */
public interface PreferenceProvider {

    /**
     * 获取指定 Vessel 的用户偏好文本。
     *
     * @param vesselId Vessel 标识
     * @return 格式化后的偏好文本；若无偏好则返回空字符串
     */
    String getPreferences(String vesselId);
}
