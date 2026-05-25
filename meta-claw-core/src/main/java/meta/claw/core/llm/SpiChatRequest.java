package meta.claw.core.llm;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Builder
@Getter
@Setter
public class SpiChatRequest {

   private  String vesselName;
   private List<SpiMessage> messages;
   private Map<String, Object> options;
}
