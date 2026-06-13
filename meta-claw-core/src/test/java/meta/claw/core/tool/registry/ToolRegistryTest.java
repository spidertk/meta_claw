package meta.claw.core.tool.registry;

import meta.claw.core.tool.annotation.ToolService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.ApplicationContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ToolRegistryTest {

    @Test
    void scansToolServiceBeans() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBeanDefinitionNames()).thenReturn(new String[]{});

        ToolServiceTool tool = new ToolServiceTool();
        when(ctx.getBeansWithAnnotation(ToolService.class)).thenReturn(Map.of("tool", tool));

        ToolRegistry registry = new ToolRegistry(ctx);
        registry.scanAndRegisterBeans();

        assertEquals(1, registry.toolCount());
        assertTrue(registry.getToolInstances().contains(tool));
    }

    @Test
    void scansSpringAiToolAnnotatedBeans() {
        ApplicationContext ctx = mock(ApplicationContext.class);

        SpringAiTool tool = new SpringAiTool();
        when(ctx.getBeansWithAnnotation(ToolService.class)).thenReturn(Map.of());
        when(ctx.getBeanDefinitionNames()).thenReturn(new String[]{"springAiTool"});
        when(ctx.getBean("springAiTool")).thenReturn(tool);

        ToolRegistry registry = new ToolRegistry(ctx);
        registry.scanAndRegisterBeans();

        assertEquals(1, registry.toolCount());
        assertTrue(registry.getToolInstances().contains(tool));
    }

    @Test
    void doesNotDuplicateBeansWithBothAnnotations() {
        ApplicationContext ctx = mock(ApplicationContext.class);

        BothAnnotationsTool tool = new BothAnnotationsTool();
        when(ctx.getBeansWithAnnotation(ToolService.class)).thenReturn(Map.of("tool", tool));
        when(ctx.getBeanDefinitionNames()).thenReturn(new String[]{"tool"});
        when(ctx.getBean("tool")).thenReturn(tool);

        ToolRegistry registry = new ToolRegistry(ctx);
        registry.scanAndRegisterBeans();

        assertEquals(1, registry.toolCount());
    }

    @Test
    void ignoresBeansWithoutToolAnnotations() {
        ApplicationContext ctx = mock(ApplicationContext.class);

        PlainService plain = new PlainService();
        when(ctx.getBeansWithAnnotation(ToolService.class)).thenReturn(Map.of());
        when(ctx.getBeanDefinitionNames()).thenReturn(new String[]{"plain"});
        when(ctx.getBean("plain")).thenReturn(plain);

        ToolRegistry registry = new ToolRegistry(ctx);
        registry.scanAndRegisterBeans();

        assertEquals(0, registry.toolCount());
    }

    @Test
    void supportsRuntimeRegisterAndUnregister() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBeanDefinitionNames()).thenReturn(new String[]{});
        when(ctx.getBeansWithAnnotation(ToolService.class)).thenReturn(Map.of());

        ToolRegistry registry = new ToolRegistry(ctx);
        registry.scanAndRegisterBeans();

        Object tool = new ToolServiceTool();
        registry.register(tool);
        assertEquals(1, registry.toolCount());

        registry.unregister(tool);
        assertEquals(0, registry.toolCount());
    }

    @ToolService
    static class ToolServiceTool {
        @Tool(description = "tool from service")
        public String run() { return "ok"; }
    }

    static class SpringAiTool {
        @Tool(description = "tool from spring ai annotation")
        public String run() { return "ok"; }
    }

    @ToolService
    static class BothAnnotationsTool {
        @Tool(description = "tool with both annotations")
        public String run() { return "ok"; }
    }

    static class PlainService {
        public String run() { return "ok"; }
    }
}
