package meta.claw.core.llm;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import meta.claw.core.prompt.PromptContext;

import java.util.List;
import java.util.Map;

@Builder
@Getter
@Setter
public class SpiChatRequest {
   private String sessionId;
   private PromptContext ctx;
   private List<SpiMessage> messages;
   private Map<String, Object> options;
}
