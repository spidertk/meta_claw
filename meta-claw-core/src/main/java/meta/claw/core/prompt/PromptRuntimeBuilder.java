package meta.claw.core.prompt;

import meta.claw.core.prompt.PromptAssembler;
import meta.claw.core.prompt.resolver.ResolutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class PromptRuntimeBuilder {

    @Autowired
    private PromptAssembler promptAssembler;

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    public String build(PromptContext context) {
        ResolutionContext ctx = ResolutionContext.builder()
                .vesselMeta(context.getVesselMeta())
                .workspaceDir(context.getWorkspaceDir())
                .currentTime(formatCurrentTime())
                .location(detectLocation())
                .build();
        String systemPart = promptAssembler.assembleSystem(ctx);
        String contextPart = promptAssembler.assembleContext(ctx);
        return systemPart + "\n\n" + contextPart;
    }

    private String formatCurrentTime() {
        return ZonedDateTime.now(ZoneId.systemDefault()).format(TIME_FORMATTER);
    }

    private String detectLocation() {
        return ZoneId.systemDefault().getId();
    }
}
