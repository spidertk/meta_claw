package meta.claw.tool.schema;

import meta.claw.core.llm.SpiJsonSchema;
import meta.claw.core.tool.schema.JsonSchemaGenerator;
import meta.claw.core.tool.annotation.Tool;
import meta.claw.core.tool.annotation.ToolParam;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonSchemaGeneratorTest {

    private final JsonSchemaGenerator generator = new JsonSchemaGenerator();

    @Tool(name = "demo", description = "Demo tool")
    public void demoMethod(
            @ToolParam(name = "str", description = "A string param") String str,
            @ToolParam(name = "num", description = "An int param") int num,
            @ToolParam(name = "flag", description = "A bool param") boolean flag) {
    }

    @Tool(name = "skip", description = "Skip param without annotation")
    public void skipMethod(
            @ToolParam(name = "a", description = "annotated") String a,
            String notAnnotated) {
    }

    @Tool(name = "empty", description = "No params")
    public void emptyMethod() {
    }

    @Test
    void generate_shouldMapParamTypes() throws NoSuchMethodException {
        Method method = getClass().getMethod("demoMethod", String.class, int.class, boolean.class);
        SpiJsonSchema schema = generator.generate(method);

        assertEquals("object", schema.type());
        Map<String, SpiJsonSchema> props = schema.properties();
        assertEquals(3, props.size());
        assertEquals("string", props.get("str").type());
        assertEquals("integer", props.get("num").type());
        assertEquals("boolean", props.get("flag").type());
    }

    @Test
    void generate_shouldIncludeDescriptions() throws NoSuchMethodException {
        Method method = getClass().getMethod("demoMethod", String.class, int.class, boolean.class);
        SpiJsonSchema schema = generator.generate(method);

        Map<String, SpiJsonSchema> props = schema.properties();
        assertEquals("A string param", props.get("str").description());
        assertEquals("An int param", props.get("num").description());
    }

    @Test
    void generate_shouldSkipParamsWithoutToolParam() throws NoSuchMethodException {
        Method method = getClass().getMethod("skipMethod", String.class, String.class);
        SpiJsonSchema schema = generator.generate(method);

        Map<String, SpiJsonSchema> props = schema.properties();
        assertEquals(1, props.size());
        assertTrue(props.containsKey("a"));
        assertFalse(props.containsKey("notAnnotated"));
    }

    @Test
    void generate_shouldHandleNoParams() throws NoSuchMethodException {
        Method method = getClass().getMethod("emptyMethod");
        SpiJsonSchema schema = generator.generate(method);

        assertNotNull(schema);
        assertEquals("object", schema.type());
        assertTrue(schema.properties() == null || schema.properties().isEmpty());
    }
}
