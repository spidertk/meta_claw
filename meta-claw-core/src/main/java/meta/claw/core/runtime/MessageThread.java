package meta.claw.core.runtime;

import meta.claw.core.llm.SpiMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息线程封装（替代裸 List<SpiMessage>）。
 */
public class MessageThread {

    private final List<SpiMessage> messages = new ArrayList<>();

    public void add(SpiMessage message) {
        messages.add(message);
    }

    public List<SpiMessage> snapshot() {
        return List.copyOf(messages);
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }

    public int size() {
        return messages.size();
    }

    public SpiMessage get(int index) {
        return messages.get(index);
    }
}
