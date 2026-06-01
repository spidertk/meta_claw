package meta.claw.core.runtime.prompt;

import meta.claw.core.runtime.prompt.resolver.MetaSectionResolver;
import meta.claw.core.runtime.prompt.resolver.ResolutionContext;
import meta.claw.core.user.VesselMeta;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptAssemblerTest {

    @Test
    void assemble_replacesMetaSection() throws Exception {
        PromptAssembler assembler = new PromptAssembler();
        java.lang.reflect.Field f = PromptAssembler.class.getDeclaredField("resolvers");
        f.setAccessible(true);
        f.set(assembler, List.of(new MetaSectionResolver()));

        VesselMeta meta = new VesselMeta();
        meta.getMeta().setName("TestBot");
        meta.getMeta().setDescription("A test bot");

        ResolutionContext ctx = ResolutionContext.builder().vesselMeta(meta).build();
        String result = assembler.assemble("<SECTION id=\"meta\"/>", SectionRegistry.Target.SYSTEM, ctx);
        assertTrue(result.contains("TestBot"));
    }

    @Test
    void assemble_removesUnresolvedTags() throws Exception {
        PromptAssembler assembler = new PromptAssembler();
        java.lang.reflect.Field f = PromptAssembler.class.getDeclaredField("resolvers");
        f.setAccessible(true);
        f.set(assembler, List.of());

        String result = assembler.assemble("<SECTION id=\"unknown\"/>", SectionRegistry.Target.SYSTEM,
                ResolutionContext.builder().build());
        assertFalse(result.contains("<SECTION"));
    }
}
