package meta.claw.core.llm.advisor;

import lombok.Data;
import meta.claw.core.tool.SpiToolCall;
import meta.claw.core.llm.SpiUsage;

import java.util.List;

/**
 * 单次 LLM 调用的共享上下文，由 {@link org.springframework.ai.chat.client.ChatClient}
 * 通过 advisor param 传入 Advisor 链，Advisors 在调用过程中把提取结果写回此处。
 */
@Data
public class MetaClawCallContext {

    private String vesselId;
    private String sessionId;
    private long startTime;

    private String content;
    private String reasoningContent;
    private SpiUsage usage;
    private List<SpiToolCall> toolCalls;
}
