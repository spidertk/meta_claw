package meta.claw.core.runtime;

import meta.claw.core.llm.SpiMessage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * 验证 LlmClientManager 将 SpiMessage 转换为 Spring AI Message 时，
 * tool 消息保留正确的 toolCallId 与 toolName，避免 LLM 后端报
 * "tool_call_id is not found" 错误。
 */
class LlmClientManagerToolMessageTest {

    @Test
    void convertsToolMessageWithIdAndName() {
        LlmClientManager manager = new LlmClientManager();
        SpiMessage toolMsg = SpiMessage.tool("2", "calculate:0", "calculate");

        Message springMsg = (Message) ReflectionTestUtils.invokeMethod(manager, "toSpringMessage", toolMsg);

        assertInstanceOf(ToolResponseMessage.class, springMsg);
        ToolResponseMessage trm = (ToolResponseMessage) springMsg;
        assertEquals(1, trm.getResponses().size());
        assertEquals("calculate:0", trm.getResponses().get(0).id());
        assertEquals("calculate", trm.getResponses().get(0).name());
        assertEquals("2", trm.getResponses().get(0).responseData());
    }

    @Test
    void convertsLegacyToolMessageWithoutIdAndName() {
        LlmClientManager manager = new LlmClientManager();
        SpiMessage toolMsg = SpiMessage.tool("legacy result");

        Message springMsg = (Message) ReflectionTestUtils.invokeMethod(manager, "toSpringMessage", toolMsg);

        assertInstanceOf(ToolResponseMessage.class, springMsg);
        ToolResponseMessage trm = (ToolResponseMessage) springMsg;
        assertEquals("tool", trm.getResponses().get(0).id());
        assertEquals("tool", trm.getResponses().get(0).name());
        assertEquals("legacy result", trm.getResponses().get(0).responseData());
    }

    @Test
    void convertsLegacyWrappedToolMessageJson() {
        LlmClientManager manager = new LlmClientManager();
        SpiMessage toolMsg = SpiMessage.tool(
                "{\"toolCallId\":\"calculate:0\",\"toolName\":\"calculate\",\"result\":\"2\"}");

        Message springMsg = (Message) ReflectionTestUtils.invokeMethod(manager, "toSpringMessage", toolMsg);

        assertInstanceOf(ToolResponseMessage.class, springMsg);
        ToolResponseMessage trm = (ToolResponseMessage) springMsg;
        assertEquals("calculate:0", trm.getResponses().get(0).id());
        assertEquals("calculate", trm.getResponses().get(0).name());
        assertEquals("2", trm.getResponses().get(0).responseData());
    }
}
