package meta.claw.core.runtime;

import meta.claw.core.model.ProviderConfig;
import meta.claw.core.spi.llm.LlmClientFactoryManager;
import meta.claw.core.spi.llm.SpiChatRequest;
import meta.claw.core.spi.llm.SpiChatResponse;
import meta.claw.core.spi.llm.SpiMessage;
import meta.claw.core.spi.llm.SpiProviderMeta;
import meta.claw.core.spi.llm.SpiStreamingCallback;
import meta.claw.core.spi.llm.openai.OpenAiChatClientFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实流式调用集成测试
 * 使用配置文件中的 Moonshot API 验证真正的流式输出
 */
class SpringAiLlmClientIntegrationTest {

//    @Test
    void testRealStreamingWithMoonshotAPI() throws Exception {
        // 1. 加载配置文件 - 从项目根目录读取
        Path configPath = Paths.get(System.getProperty("user.dir"), ".meta-claw", "config.yaml");
        if (!Files.exists(configPath)) {
            // 如果在子模块中运行，尝试从上级目录查找
            configPath = Paths.get(System.getProperty("user.dir"), "..", ".meta-claw", "config.yaml").normalize();
        }
        assertTrue(Files.exists(configPath), "配置文件不存在: " + configPath);

        ProviderConfig providerConfig;
        try (InputStream input = Files.newInputStream(configPath)) {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(input);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> providers = (Map<String, Object>) config.get("providers");
            @SuppressWarnings("unchecked")
            Map<String, Object> moonshot = (Map<String, Object>) providers.get("moonshot");
            
            providerConfig = new ProviderConfig();
            providerConfig.setApiKey((String) moonshot.get("api_key"));
            providerConfig.setBaseUrl((String) moonshot.get("base_url"));
            providerConfig.setModel((String) moonshot.get("model"));
            providerConfig.setTemperature(((Number) moonshot.get("temperature")).doubleValue());
            providerConfig.setTimeout(((Number) moonshot.get("timeout")).doubleValue());
        }

        assertNotNull(providerConfig.getApiKey(), "API Key 不能为空");
        assertFalse(providerConfig.getApiKey().contains("your-api-key"), "请配置真实的 API Key");

        // 2. 创建 ChatClient
        OpenAiChatClientFactory factory = new OpenAiChatClientFactory();
        String model = providerConfig.getModel() != null && !providerConfig.getModel().isBlank() 
                ? providerConfig.getModel() : "kimi-k2.6";
        providerConfig.setModel(model);
        ChatClient chatClient = factory.create(providerConfig);
        
        SpiProviderMeta meta = SpiProviderMeta.builder()
                .name("moonshot")
                .model(model)
                .baseUrl(providerConfig.getBaseUrl())
                .build();

        SpringAiLlmClient llmClient = new SpringAiLlmClient(chatClient, meta);

        // 3. 准备测试请求 - 要求生成较长的内容以观察流式效果
        SpiChatRequest request = SpiChatRequest.builder()
                .messages(List.of(
                    SpiMessage.system("你是一个有用的AI助手。请用中文回答。"),
                    SpiMessage.user("请详细介绍一下什么是流式输出（streaming），以及它在LLM应用中的优势。请至少写200字。")
                ))
                .build();

        // 4. 执行流式调用并收集数据
        List<String> chunks = new ArrayList<>();
        List<Integer> chunkSizes = new ArrayList<>();
        List<Long> chunkTimestamps = new ArrayList<>();
        AtomicInteger startCount = new AtomicInteger(0);
        AtomicInteger completeCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        
        long startTime = System.currentTimeMillis();

        SpiStreamingCallback callback = new SpiStreamingCallback() {
            @Override
            public void onStart() {
                startCount.incrementAndGet();
                System.out.println("[STREAM START] " + System.currentTimeMillis());
            }

            @Override
            public void onChunk(String chunk) {
                chunks.add(chunk);
                chunkSizes.add(chunk.length());
                long currentTime = System.currentTimeMillis() - startTime;
                chunkTimestamps.add(currentTime);
                                       
                // 使用 System.err 而不是 System.out，因为 Maven 对 stderr 的缓冲策略不同
                // 同时添加时间戳和chunk编号，便于观察
                System.err.printf("[Chunk #%d @%dms, size=%d] %s%n", 
                        chunks.size(), currentTime, chunk.length(), 
                        chunk.replace("\n", "\\n"));
            }                                                               

            @Override
            public void onToolCall(meta.claw.core.spi.llm.SpiToolCall toolCall) {
                // 不需要处理
            }

            @Override
            public void onComplete(SpiChatResponse response) {
                completeCount.incrementAndGet();
                latch.countDown();
                System.out.println("\n[STREAM COMPLETE] Total time: " + (System.currentTimeMillis() - startTime) + "ms");
            }

            @Override
            public void onError(Throwable error) {
                errorCount.incrementAndGet();
                latch.countDown();
                System.err.println("[STREAM ERROR] " + error.getMessage());
                error.printStackTrace();
            }
        };

        llmClient.chatStream(request, callback);

        // 5. 等待完成
        boolean completed = latch.await(60, TimeUnit.SECONDS);
        assertTrue(completed, "流式调用应该在60秒内完成");

        // 6. 验证结果
        assertEquals(1, startCount.get(), "应该调用一次onStart");
        assertEquals(1, completeCount.get(), "应该调用一次onComplete");
        assertEquals(0, errorCount.get(), "不应该有错误");
        
        // 关键验证：应该有多个chunk（证明是流式）
        assertTrue(chunks.size() > 1, 
                "应该收到多个chunk（实际: " + chunks.size() + "），证明是流式调用");
        
        // 7. 分析chunk大小分布
        System.out.println("\n=== Chunk Analysis ===");
        System.out.println("Total chunks: " + chunks.size());
        System.out.println("Total characters: " + chunkSizes.stream().mapToInt(Integer::intValue).sum());
        
        int minSize = chunkSizes.stream().mapToInt(Integer::intValue).min().orElse(0);
        int maxSize = chunkSizes.stream().mapToInt(Integer::intValue).max().orElse(0);
        double avgSize = chunkSizes.stream().mapToInt(Integer::intValue).average().orElse(0);
        
        System.out.println("Min chunk size: " + minSize + " chars");
        System.out.println("Max chunk size: " + maxSize + " chars");
        System.out.println("Avg chunk size: " + String.format("%.2f", avgSize) + " chars");
        
        // 8. 显示前10个chunk的详细信息
        System.out.println("\n=== First 10 Chunks Detail ===");
        for (int i = 0; i < Math.min(10, chunks.size()); i++) {
            String chunk = chunks.get(i);
            System.out.printf("Chunk #%d (size=%d, time=%dms): [%s]%n", 
                    i + 1, chunkSizes.get(i), chunkTimestamps.get(i), 
                    chunk.replace("\n", "\\n").substring(0, Math.min(50, chunk.length())));
        }
        
        // 9. 验证完整内容
        String fullContent = String.join("", chunks);
        System.out.println("\n=== Full Content Length ===");
        System.out.println("Total length: " + fullContent.length() + " characters");
        assertTrue(fullContent.length() > 100, "响应内容应该至少有100个字符");
        
        // 10. 验证时间间隔（证明是逐步返回的）
        if (chunkTimestamps.size() > 1) {
            long firstToLast = chunkTimestamps.get(chunkTimestamps.size() - 1) - chunkTimestamps.get(0);
            System.out.println("\n=== Timing Analysis ===");
            System.out.println("First to last chunk: " + firstToLast + "ms");
//            assertTrue(firstToLast > 100, "第一个和最后一个chunk之间应该有合理的时间间隔");
        }
    }

    @Test
    void testStreamingChunkSizeDistribution() throws Exception {
        // 这个测试专门用于分析chunk大小的分布情况
        
        Path configPath = Paths.get(System.getProperty("user.dir"), ".meta-claw", "config.yaml");
        if (!Files.exists(configPath)) {
            // 如果在子模块中运行，尝试从上级目录查找
            configPath = Paths.get(System.getProperty("user.dir"), "..", ".meta-claw", "config.yaml").normalize();
        }
        if (!Files.exists(configPath)) {
            System.out.println("跳过测试：配置文件不存在");
            return;
        }

        ProviderConfig providerConfig;
        try (InputStream input = Files.newInputStream(configPath)) {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(input);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> providers = (Map<String, Object>) config.get("providers");
            @SuppressWarnings("unchecked")
            Map<String, Object> moonshot = (Map<String, Object>) providers.get("moonshot");
            
            providerConfig = new ProviderConfig();
            providerConfig.setApiKey((String) moonshot.get("api_key"));
            providerConfig.setBaseUrl((String) moonshot.get("base_url"));
            providerConfig.setModel((String) moonshot.get("model"));
        }

        if (providerConfig.getApiKey().contains("your-api-key")) {
            System.out.println("跳过测试：请配置真实的API Key");
            return;
        }

        OpenAiChatClientFactory factory = new OpenAiChatClientFactory();
        String model = providerConfig.getModel() != null && !providerConfig.getModel().isBlank() 
                ? providerConfig.getModel() : "kimi-k2.6";
        providerConfig.setModel(model);
        ChatClient chatClient = factory.create(providerConfig);
        
        SpiProviderMeta meta = SpiProviderMeta.builder()
                .name("moonshot")
                .model(model)
                .baseUrl(providerConfig.getBaseUrl())
                .build();

        SpringAiLlmClient llmClient = new SpringAiLlmClient(chatClient, meta);

        SpiChatRequest request = SpiChatRequest.builder()
                .messages(List.of(
                    SpiMessage.user("请写一篇关于人工智能发展的短文，大约300字。")
                ))
                .build();

        List<Integer> chunkSizes = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        llmClient.chatStream(request, new SpiStreamingCallback() {
            @Override
            public void onStart() {}

            @Override
            public void onChunk(String chunk) {
                chunkSizes.add(chunk.length());
            }

            @Override
            public void onToolCall(meta.claw.core.spi.llm.SpiToolCall toolCall) {}

            @Override
            public void onComplete(SpiChatResponse response) {
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                latch.countDown();
            }
        });

        latch.await(60, TimeUnit.SECONDS);

        // 统计chunk大小分布
        System.out.println("\n=== Chunk Size Distribution ===");
        System.out.println("Total chunks: " + chunkSizes.size());
        
        // 按大小分组统计
        Map<String, Integer> sizeGroups = new java.util.HashMap<>();
        sizeGroups.put("1-5 chars", 0);
        sizeGroups.put("6-10 chars", 0);
        sizeGroups.put("11-20 chars", 0);
        sizeGroups.put("21-50 chars", 0);
        sizeGroups.put("50+ chars", 0);
        
        for (int size : chunkSizes) {
            if (size <= 5) sizeGroups.put("1-5 chars", sizeGroups.get("1-5 chars") + 1);
            else if (size <= 10) sizeGroups.put("6-10 chars", sizeGroups.get("6-10 chars") + 1);
            else if (size <= 20) sizeGroups.put("11-20 chars", sizeGroups.get("11-20 chars") + 1);
            else if (size <= 50) sizeGroups.put("21-50 chars", sizeGroups.get("21-50 chars") + 1);
            else sizeGroups.put("50+ chars", sizeGroups.get("50+ chars") + 1);
        }
        
        sizeGroups.forEach((group, count) -> 
                System.out.println(group + ": " + count + " chunks"));
        
        // 验证有多个chunk
        assertTrue(chunkSizes.size() > 1, "应该有多个chunk");
    }
}
