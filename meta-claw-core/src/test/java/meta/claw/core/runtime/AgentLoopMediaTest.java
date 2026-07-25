package meta.claw.core.runtime;

import meta.claw.core.eventbus.EventBusWrapper;
import meta.claw.core.events.UserMessageReceived;
import meta.claw.core.llm.MediaPart;
import meta.claw.core.message.Context;
import meta.claw.core.message.ContextType;
import meta.claw.core.message.Reply;
import meta.claw.core.message.ReplyType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentLoop 多模态透传测试：Context.kwargs 中的 mediaParts 应传给 VesselRuntime.chat
 */
class AgentLoopMediaTest {

    @Test
    void mediaPartsArePassedToVesselRuntime() {
        EventBusWrapper eventBus = new EventBusWrapper();
        VesselManager vesselManager = mock(VesselManager.class);
        VesselRuntime runtime = mock(VesselRuntime.class);
        when(vesselManager.hasVessel("v1")).thenReturn(true);
        when(vesselManager.getRuntime("v1")).thenReturn(runtime);
        when(runtime.chat(anyString(), anyString(), any())).thenReturn(new Reply(ReplyType.TEXT, "ok"));

        new AgentLoop(eventBus, vesselManager);

        List<MediaPart> mediaParts = List.of(
                MediaPart.builder().type("image_url").mimeType("image/png").url("file:///tmp/x.png").build());
        Context context = new Context(ContextType.TEXT, "看看这张图");
        context.setChannelType("weixin");
        context.getKwargs().put("vesselId", "v1");
        context.getKwargs().put("mediaParts", mediaParts);

        eventBus.post(new UserMessageReceived(context, "session-1", "weixin"));

        verify(runtime, timeout(2000)).chat(eq("session-1"), eq("看看这张图"), eq(mediaParts));
    }

    @Test
    void textOnlyMessagePassesNullMediaParts() {
        EventBusWrapper eventBus = new EventBusWrapper();
        VesselManager vesselManager = mock(VesselManager.class);
        VesselRuntime runtime = mock(VesselRuntime.class);
        when(vesselManager.hasVessel("v1")).thenReturn(true);
        when(vesselManager.getRuntime("v1")).thenReturn(runtime);
        when(runtime.chat(anyString(), anyString(), any())).thenReturn(new Reply(ReplyType.TEXT, "ok"));

        new AgentLoop(eventBus, vesselManager);

        Context context = new Context(ContextType.TEXT, "纯文本");
        context.setChannelType("weixin");
        context.getKwargs().put("vesselId", "v1");

        eventBus.post(new UserMessageReceived(context, "session-2", "weixin"));

        verify(runtime, timeout(2000)).chat(eq("session-2"), eq("纯文本"), eq(null));
    }
}
