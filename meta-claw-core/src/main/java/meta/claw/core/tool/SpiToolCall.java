package meta.claw.core.tool;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Builder
@Getter
@Setter
public class SpiToolCall{
   private String id;
    private String name;
    private Map<String, Object> arguments;
}
