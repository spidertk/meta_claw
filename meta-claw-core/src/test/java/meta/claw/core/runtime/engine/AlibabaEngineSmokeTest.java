package meta.claw.core.runtime.engine;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 0 冒烟测试：验证 Spring AI Alibaba 依赖与 Spring AI 1.1.8 的兼容性。
 *
 * <p>本测试不发起真实网络请求，仅验证：</p>
 * <ol>
 *   <li>{@link ReactAgent} 类可加载</li>
 *   <li>{@link ReactAgent} 可使用 mock {@link ChatModel} 构建</li>
 *   <li>一次无工具对话可返回预期结果</li>
 * </ol>
 */
class AlibabaEngineSmokeTest {

    @Test
    void canBuildReactAgentAndCallWithMockModel() throws Exception {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("hello from alibaba")))));

        ReactAgent agent = ReactAgent.builder()
                .name("smoke")
                .description("smoke test agent")
                .model(chatModel)
                .systemPrompt("You are a smoke tester.")
                .build();

        assertNotNull(agent);

        List<Message> messages = List.of(new UserMessage("hi"));
        AssistantMessage result = agent.call(messages);

        assertNotNull(result);
        assertEquals("hello from alibaba", result.getText());
    }
}
