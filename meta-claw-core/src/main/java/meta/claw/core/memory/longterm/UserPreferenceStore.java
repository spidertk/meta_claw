package meta.claw.core.memory.longterm;

/**
 * 用户偏好这一长期记忆子能力的稳定访问契约。
 *
 * 当前它与 {@link LongMemoryStore} 的能力集合一致，但保留这个名字可以让
 * prompt 等调用方依赖“偏好语义”，而不是依赖具体的长期记忆 backend。
 */
public interface UserPreferenceStore extends LongMemoryStore {
}
