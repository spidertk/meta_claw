package meta.claw.core.runtime.hitl;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存实现的 HITL 网关。
 * <p>适用于单进程内的异步审批：调用方捕获 {@link meta.claw.core.runtime.HitlSuspendedException}
     * 后，通过其他线程调用 {@link #resolve(String, ApprovalResolution)} 完成审批。</p>
 */
@Component
public class InMemoryHitlGate implements HitlGate {

    private final Map<String, CompletableFuture<ApprovalResolution>> pending = new ConcurrentHashMap<>();

    @Override
    public ApprovalResolution await(ApprovalTicket ticket) {
        CompletableFuture<ApprovalResolution> future = new CompletableFuture<>();
        pending.put(ticket.getTicketId(), future);
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HITL await interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("HITL await failed", e);
        } finally {
            pending.remove(ticket.getTicketId());
        }
    }

    @Override
    public void resolve(String ticketId, ApprovalResolution resolution) {
        CompletableFuture<ApprovalResolution> future = pending.get(ticketId);
        if (future == null) {
            throw new IllegalArgumentException("Ticket not found or already resolved: " + ticketId);
        }
        future.complete(resolution);
    }
}
