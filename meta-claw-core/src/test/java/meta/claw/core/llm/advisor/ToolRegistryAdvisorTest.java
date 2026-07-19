package meta.claw.core.llm.advisor;

import meta.claw.core.runtime.TaskContext;
import meta.claw.core.tool.registry.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolRegistryAdvisorTest {

    static class SampleTool {
        @Tool(description = "sample tool")
        public String sample(String input) {
            return input;
        }
    }

    private static ChatClientRequest requestWithContext(Map<String, Object> context) {
        return ChatClientRequest.builder()
                .prompt(new Prompt("hello"))
                .context(context)
                .build();
    }

    private static CallAdvisorChain capturingChain(AtomicReference<ChatClientRequest> captured) {
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return mock(ChatClientResponse.class);
        });
        return chain;
    }

    @Test
    void skipsToolInjectionWhenContextFlagSet() {
        ToolRegistry registry = mock(ToolRegistry.class);
        when(registry.getToolInstances()).thenReturn(List.of(new SampleTool()));
        ToolRegistryAdvisor advisor = new ToolRegistryAdvisor(registry);

        AtomicReference<ChatClientRequest> captured = new AtomicReference<>();
        ChatClientRequest request = requestWithContext(
                Map.of(TaskContext.LlmCallContext.SKIP_TOOL_INJECTION_KEY, Boolean.TRUE));

        advisor.adviseCall(request, capturingChain(captured));

        // 请求原样透传，不注入任何工具 options
        assertSame(request, captured.get());
        assertNull(captured.get().prompt().getOptions());
    }

    @Test
    void injectsToolsWhenFlagAbsent() {
        ToolRegistry registry = mock(ToolRegistry.class);
        when(registry.getToolInstances()).thenReturn(List.of(new SampleTool()));
        ToolRegistryAdvisor advisor = new ToolRegistryAdvisor(registry);

        AtomicReference<ChatClientRequest> captured = new AtomicReference<>();
        advisor.adviseCall(requestWithContext(Map.of()), capturingChain(captured));

        ToolCallingChatOptions options = assertInstanceOf(ToolCallingChatOptions.class,
                captured.get().prompt().getOptions());
        assertEquals(1, options.getToolCallbacks().size());
        assertFalse(options.getInternalToolExecutionEnabled());
    }
}
