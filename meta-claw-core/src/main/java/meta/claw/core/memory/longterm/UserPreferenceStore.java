package meta.claw.core.memory.longterm;

import java.util.List;

/**
 * 用户偏好存储接口
 * 定义用户偏好、个人习惯、工具使用模式等非领域知识的持久化与检索规范
 */
public interface UserPreferenceStore {

    /**
     * 添加一条用户偏好记录
     *
     * @param vesselId 数字员工标识
     * @param entry    偏好条目
     */
    void addPreference(String vesselId, PreferenceEntry entry);

    /**
     * 根据查询条件查找用户偏好
     *
     * @param vesselId 数字员工标识
     * @param query    查询关键词
     * @return 匹配的偏好条目列表
     */
    List<PreferenceEntry> lookupPreference(String vesselId, String query);

    /**
     * 获取最近的用户偏好记录
     *
     * @param vesselId 数字员工标识
     * @param limit    最大返回条数
     * @return 最近的偏好条目列表
     */
    List<PreferenceEntry> listRecentPreferences(String vesselId, int limit);

    /**
     * 删除指定的用户偏好记录
     *
     * @param vesselId    数字员工标识
     * @param preferenceId 偏好条目标识
     * @return 是否成功删除
     */
    boolean deletePreference(String vesselId, String preferenceId);

    /**
     * 清除指定数字员工的所有用户偏好
     *
     * @param vesselId 数字员工标识
     * @return 是否成功清除
     */
    boolean clearPreferences(String vesselId);
}
