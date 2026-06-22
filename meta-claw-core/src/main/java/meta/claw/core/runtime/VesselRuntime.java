package meta.claw.core.runtime;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiMessage;
import meta.claw.core.llm.SpiStreamingCallback;
import meta.claw.core.memory.MemoryMessage;
import meta.claw.core.memory.MemoryMessageConverter;
import meta.claw.core.memory.shortterm.ShortMemory;
import meta.claw.core.message.Reply;
import meta.claw.core.runtime.hitl.ApprovalResolution;
import meta.claw.core.runtime.hitl.ApprovalTicket;
import meta.claw.core.tool.SpiToolCall;
import meta.claw.core.prompt.PromptComposer;
import meta.claw.core.prompt.PromptRenderer;
import meta.claw.core.prompt.PromptVars;
import meta.claw.core.runtime.engine.AgentEngine;
import meta.claw.core.runtime.engine.AgentEngineFactory;
import meta.claw.core.runtime.subsystem.MemorySubSystem;
import meta.claw.core.runtime.subsystem.SubSystemRegistry;
import meta.claw.core.runtime.subsystem.VesselAwareSubSystem;
import meta.claw.core.runtime.subsystem.VesselSubSystem;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vessel 核心运行时类 — 子系统编排器。
 * <p>不再直接持有 memory/prompt 依赖，而是通过 SubSystemRegistry 统一编排子系统生命周期。</p>
 */
@Slf4j
@Component
@Scope("prototype")
public class VesselRuntime implements InitializingBean {

    @Autowired
    private PromptComposer promptComposer;
    @Autowired
    private PromptRenderer promptRenderer;
    @Autowired
    private AgentEngineFactory agentEngineFactory;

    /** 所有子系统，Spring 自动收集（含 VesselProfile） */
    @Autowired(required = false)
    private List<VesselSubSystem> subSystems = new ArrayList<>();

    private final SubSystemRegistry registry = new SubSystemRegistry();
    private final String vesselId;

    public VesselRuntime(String vesselId) {
        this.vesselId = vesselId;
    }

    @Override
    public void afterPropertiesSet() {
        // ① 按 priority 排序并注册所有子系统（含 VesselProfile）
        subSystems.stream()
                .sorted(Comparator.comparingInt(VesselSubSystem::priority))
                .forEach(sub -> {
                    registry.register(sub);
                    sub.configure(registry);
                    if (sub instanceof VesselAwareSubSystem aware) {
                        aware.loadForVessel(vesselId);
                    }
                });
    }

    // ========== 子系统查询 ==========

    public SubSystemRegistry getRegistry() {
        return registry;
    }

    @SuppressWarnings("unchecked")
    public <T extends VesselSubSystem> T getSubSystem(String name) {
        return registry.get(name);
    }

    /** 便捷查询：获取 Vessel 配置画像 */
    public VesselProfile getProfile() {
        return registry.get("profile");
    }

    private AgentEngine currentEngine() {
        return agentEngineFactory.getEngine(getProfile().getBundle().getAgentEngine());
    }

    // ========== Prompt 组装与渲染 ==========

    public String renderSystemPrompt() {
        PromptVars allVars = buildPromptVars();
        return promptRenderer.renderSystem(allVars.toMap());
    }

    public PromptVars buildPromptVars() {
        // 静态变量：由 PromptComposer 从 registry 收集所有子系统贡献
        PromptVars staticVars = promptComposer.compose(registry);

        // 动态变量：每次任务实时计算
        PromptVars dynamic = PromptVars.builder()
                .vars(java.util.Map.of(
                        "current_time", formatCurrentTime(),
                        "location", detectLocation()
                ))
                .build();

        return staticVars.merge(dynamic);
    }

    // ========== 对话入口 ==========

    public Reply chat(String sessionId, String userMessage) {
        return execute(newTask(sessionId, userMessage));
    }

    /**
     * 从 HITL 挂起状态恢复，继续完成 ReAct 循环。
     */
    public Reply resume(VesselTask task, ApprovalTicket ticket, ApprovalResolution resolution) {
        String systemPrompt = renderSystemPrompt();
        TaskContext ctx = new TaskContext(task, getProfile(), registry);
        registry.listAll().forEach(sub -> sub.onTaskStart(ctx));
        try {
            List<SpiMessage> messages = buildLlmRequest(task, systemPrompt);
            messages.add(SpiMessage.assistant(null, rebuildToolCalls(ticket)));

            SpiChatRequest request = SpiChatRequest.builder()
                    .vesselId(task.getVesselId())
                    .messages(messages)
                    .sessionId(task.getSessionId())
                    .build();

            Reply reply = currentEngine().resume(ctx, request, ticket, resolution);
            saveAssistantMessage(task, reply.getContent());
            return reply;
        } finally {
            registry.listAll().forEach(sub -> sub.onTaskEnd(ctx));
        }
    }

    public Reply execute(VesselTask task) {
        // ① 渲染 system prompt
        String systemPrompt = renderSystemPrompt();

        // ② 构造任务上下文
        TaskContext ctx = new TaskContext(task, getProfile(), registry);

        // ③ 任务开始生命周期
        registry.listAll().forEach(sub -> sub.onTaskStart(ctx));

        try {
            // ④ 构建 LLM 请求并执行
            List<SpiMessage> messages = buildLlmRequest(task, systemPrompt);
            SpiChatRequest request = SpiChatRequest.builder()
                    .vesselId(task.getVesselId())
                    .messages(messages)
                    .sessionId(task.getSessionId())
                    .build();

            Reply reply = currentEngine().execute(ctx, request);

            // 保存 assistant 消息到短期记忆
            saveAssistantMessage(task, reply.getContent());

            return reply;
        } finally {
            // ⑤ 任务结束生命周期（finally 中保证调用）
            registry.listAll().forEach(sub -> sub.onTaskEnd(ctx));
        }
    }

    public void chatStream(String sessionId, String userMessage, SpiStreamingCallback callback) {
        String systemPrompt = renderSystemPrompt();
        VesselTask task = newTask(sessionId, userMessage);
        TaskContext ctx = new TaskContext(task, getProfile(), registry);
        registry.listAll().forEach(sub -> sub.onTaskStart(ctx));
        try {
            List<SpiMessage> messages = buildLlmRequest(task, systemPrompt);
            SpiChatRequest request = SpiChatRequest.builder()
                    .vesselId(task.getVesselId())
                    .messages(messages)
                    .sessionId(task.getSessionId())
                    .build();
            Reply reply = currentEngine().executeStream(ctx, request, callback);
            saveAssistantMessage(task, reply.getContent());
        } finally {
            registry.listAll().forEach(sub -> sub.onTaskEnd(ctx));
        }
    }

    // ========== 便捷方法（向后兼容）==========

    public ShortMemory getShortMemory() {
        MemorySubSystem mem = registry.get("memory");
        if (mem != null) {
            return mem.getShortMemory(getProfile().getBundle().getMemoryConfig());
        }
        log.warn("MemorySubSystem not registered, short memory unavailable");
        return null;
    }

    public meta.claw.core.config.VesselConfig getConfig() {
        return getProfile().getBundle().getRuntimeVesselConfig();
    }

    // ========== 内部 ==========

    private VesselTask newTask(String sessionId, String userMessage) {
        return VesselTask.builder()
                .taskId(UUID.randomUUID().toString())
                .vesselId(this.vesselId)
                .sessionId(sessionId)
                .userMessage(userMessage)
                .createdAt(Instant.now())
                .build();
    }

    private List<SpiMessage> buildLlmRequest(VesselTask task, String systemPrompt) {
        List<SpiMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(SpiMessage.system(systemPrompt));
        }

        String sessionId = task.getSessionId();
        ShortMemory shortMem = getShortMemory();
        if (StringUtils.isNotBlank(sessionId) && shortMem != null) {
            int maxRounds = getProfile().getBundle().getMaxHistoryRounds();
            messages.addAll(toSpiMessages(shortMem.loadMessages(vesselId, sessionId, maxRounds)));
        }

        messages.add(SpiMessage.user(task.getUserMessage()));

        if (shortMem != null) {
            shortMem.appendMessage(vesselId, sessionId,
                    MemoryMessageConverter.fromSpiMessage(SpiMessage.user(task.getUserMessage())));
        }

        return messages;
    }

    private void saveAssistantMessage(VesselTask task, String content) {
        ShortMemory shortMem = getShortMemory();
        if (shortMem != null) {
            shortMem.appendMessage(vesselId, task.getSessionId(),
                    MemoryMessageConverter.fromSpiMessage(SpiMessage.assistant(content, null, null)));
        }
    }

    private List<SpiMessage> toSpiMessages(List<MemoryMessage> entries) {
        List<SpiMessage> restored = new ArrayList<>();
        for (MemoryMessage entry : entries) {
            SpiMessage message = MemoryMessageConverter.toSpiMessage(entry);
            if (message.getRole() == null) {
                continue;
            }
            switch (message.getRole().toLowerCase()) {
                case "user" -> restored.add(SpiMessage.user(message.getContent()));
                case "assistant" -> restored.add(
                        SpiMessage.assistant(message.getContent(), message.getReasoningContent(), message.getToolCalls()));
                case "tool" -> restored.add(
                        SpiMessage.tool(message.getContent(), message.getToolCallId(), message.getToolName()));
                default -> {
                    // System prompts are rebuilt from current vessel config when resuming.
                }
            }
        }
        return restored;
    }

    private static String formatCurrentTime() {
        return ZonedDateTime.now(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
    }

    private static String detectLocation() {
        return ZoneId.systemDefault().getId();
    }

    private List<SpiToolCall> rebuildToolCalls(ApprovalTicket ticket) {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        List<SpiToolCall> calls = new ArrayList<>();
        for (meta.claw.core.runtime.hitl.ApprovalItem item : ticket.getItems()) {
            Map<String, Object> args;
            try {
                args = mapper.readValue(item.getArgumentsJson(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
            } catch (Exception e) {
                args = Map.of();
            }
            calls.add(SpiToolCall.builder()
                    .id(item.getToolCallId())
                    .name(item.getToolName())
                    .arguments(args)
                    .build());
        }
        return calls;
    }

    public void shutdown() {
        log.info("VesselRuntime shutdown: {}", vesselId);
    }
}
