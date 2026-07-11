package meta.claw.core.llm.advisor;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.tool.registry.ToolRegistry;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.List;

/**
 * 工具注入 Advisor。
 * <p>
 * 从 {@link ToolRegistry} 拉取所有已注册的工具实例，或从请求上下文中读取显式传入的
 * {@link ToolCallback} 数组，构造 {@link ToolCallingChatOptions} 并注入到 outgoing {@link Prompt} 中。
 * 由于该 Advisor 位于 Spring AI {@code ToolCallAdvisor} 内侧，ToolCallAdvisor 能看到这些工具
 * 但不会执行它们（因为 {@code internalToolExecutionEnabled=false}），最终由 meta-claw 自己的
 * ReAct 循环负责工具执行。
 * </p>
 */
@Slf4j
public class ToolRegistryAdvisor implements CallAdvisor, StreamAdvisor {

    private final ToolRegistry toolRegistry;

    public ToolRegistryAdvisor(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        return chain.nextCall(addTools(request));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return chain.nextStream(addTools(request));
    }

    private ChatClientRequest addTools(ChatClientRequest request) {
        ToolCallback[] explicitCallbacks = (ToolCallback[]) request.context().get(TaskContext.LlmCallContext.EXPLICIT_TOOL_CALLBACKS_KEY);

        List<ToolCallback> toolCallbacks;
        if (explicitCallbacks != null && explicitCallbacks.length > 0) {
            toolCallbacks = Arrays.asList(explicitCallbacks);
            log.debug("[ToolRegistryAdvisor] injecting {} explicit tool callback(s)", toolCallbacks.size());
        } else {
            List<Object> toolInstances = toolRegistry.getToolInstances();
            log.debug("[ToolRegistryAdvisor] injecting {} tool(s) from registry", toolInstances.size());
            if (toolInstances.isEmpty()) {
                return request;
            }
            toolCallbacks = Arrays.asList(ToolCallbacks.from(toolInstances.toArray()));
        }

        org.springframework.ai.chat.prompt.ChatOptions options = request.prompt().getOptions();
        if (options instanceof ToolCallingChatOptions toolOptions) {
            // 直接修改原始 options，避免丢失 OpenAiChatOptions 的 streamUsage 等专属参数
            toolOptions.setToolCallbacks(toolCallbacks);
            toolOptions.setInternalToolExecutionEnabled(false);
            return request;
        }

        // fallback：原始 options 不是 ToolCallingChatOptions 时整体替换
        ToolCallingChatOptions toolOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(toolCallbacks)
                .internalToolExecutionEnabled(false)
                .build();

        return ChatClientRequest.builder()
                .prompt(new Prompt(request.prompt().getInstructions(), toolOptions))
                .context(request.context())
                .build();
    }

    @Override
    public String getName() {
        return "ToolRegistryAdvisor";
    }

    @Override
    public int getOrder() {
        // 位于 ToolCallAdvisor 内侧、响应提取 Advisor 外侧
        return 0;
    }
}
