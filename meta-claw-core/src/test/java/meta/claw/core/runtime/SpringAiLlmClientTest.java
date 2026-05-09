package meta.claw.core.runtime;

import meta.claw.core.spi.llm.SpiChatRequest;
import meta.claw.core.spi.llm.SpiChatResponse;
import meta.claw.core.spi.llm.SpiMessage;
import meta.claw.core.spi.llm.SpiProviderMeta;
import meta.claw.core.spi.llm.SpiStreamingCallback;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SpringAiLlmClientTest {

    @Test
    void testChat() {
        ChatClient mockClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec mockSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec mockCall = mock(ChatClient.CallResponseSpec.class);
        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGen = mock(Generation.class);
        AssistantMessage mockMsg = new AssistantMessage("Hello from AI");

        when(mockClient.prompt(any(Prompt.class))).thenReturn(mockSpec);
        when(mockSpec.call()).thenReturn(mockCall);
        when(mockCall.chatResponse()).thenReturn(mockResponse);
        when(mockResponse.getResult()).thenReturn(mockGen);
        when(mockGen.getOutput()).thenReturn(mockMsg);

        SpiProviderMeta meta = SpiProviderMeta.builder()
                .name("kimi").model("moonshot-v1-8k").baseUrl("https://api.moonshot.cn").build();

        SpringAiLlmClient client = new SpringAiLlmClient(mockClient, meta);

        SpiChatRequest request = SpiChatRequest.builder()
                .messages(List.of(SpiMessage.user("hi")))
                .build();

        SpiChatResponse response = client.chat(request);

        assertEquals("Hello from AI", response.content());
        assertEquals("kimi", client.getProviderMeta().name());
    }

    @Test
    void testChatStreamIsRealStreaming() throws InterruptedException {
        // 准备模拟对象
        ChatClient mockClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec mockSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec mockStreamSpec = mock(ChatClient.StreamResponseSpec.class);
        
        // 创建模拟的流式数据 - 分5个chunk返回
        List<String> chunks = List.of("Hello", " ", "from", " ", "AI");
        Flux<String> mockFlux = Flux.fromIterable(chunks);
        
        when(mockClient.prompt(any(Prompt.class))).thenReturn(mockSpec);
        when(mockSpec.stream()).thenReturn(mockStreamSpec);
        when(mockStreamSpec.content()).thenReturn(mockFlux);

        SpiProviderMeta meta = SpiProviderMeta.builder()
                .name("kimi").model("moonshot-v1-8k").baseUrl("https://api.moonshot.cn").build();

        SpringAiLlmClient client = new SpringAiLlmClient(mockClient, meta);

        // 用于跟踪回调调用情况
        AtomicInteger startCount = new AtomicInteger(0);
        AtomicInteger chunkCount = new AtomicInteger(0);
        AtomicInteger completeCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        List<String> receivedChunks = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        
        // 创建流式回调
        SpiStreamingCallback callback = new SpiStreamingCallback() {
            @Override
            public void onStart() {
                startCount.incrementAndGet();
            }

            @Override
            public void onChunk(String chunk) {
                chunkCount.incrementAndGet();
                receivedChunks.add(chunk);
            }

            @Override
            public void onToolCall(meta.claw.core.spi.llm.SpiToolCall toolCall) {
                // 不需要处理
            }

            @Override
            public void onComplete(SpiChatResponse response) {
                completeCount.incrementAndGet();
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                errorCount.incrementAndGet();
                latch.countDown();
            }
        };

        // 执行流式调用
        SpiChatRequest request = SpiChatRequest.builder()
                .messages(List.of(SpiMessage.user("hi")))
                .build();

        client.chatStream(request, callback);

        // 等待异步操作完成
        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "流式调用应该在5秒内完成");

        // 验证结果
        assertEquals(1, startCount.get(), "应该调用一次onStart");
        assertEquals(5, chunkCount.get(), "应该收到5个chunk，证明是流式调用");
        assertEquals(1, completeCount.get(), "应该调用一次onComplete");
        assertEquals(0, errorCount.get(), "不应该有错误");
        
        // 验证chunk的顺序和内容
        assertEquals("Hello", receivedChunks.get(0));
        assertEquals(" ", receivedChunks.get(1));
        assertEquals("from", receivedChunks.get(2));
        assertEquals(" ", receivedChunks.get(3));
        assertEquals("AI", receivedChunks.get(4));
        
        // 验证完整内容
        String fullContent = String.join("", receivedChunks);
        assertEquals("Hello from AI", fullContent, "所有chunk拼接后应该是完整内容");
        
        // 验证mock调用
        verify(mockClient).prompt(any(Prompt.class));
        verify(mockSpec).stream();
        verify(mockStreamSpec).content();
    }
}
