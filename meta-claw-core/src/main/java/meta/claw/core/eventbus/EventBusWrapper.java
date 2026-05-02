package meta.claw.core.eventbus;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Guava AsyncEventBus 封装类
 * 提供异步事件总线的统一入口，支持事件的发布、订阅与取消订阅
 */
@Slf4j
public class EventBusWrapper {

    /** 内部持有的 Guava 异步事件总线实例 */
    private final EventBus eventBus;

    /**
     * 默认构造函数
     * 初始化异步事件总线：固定 10 线程的线程池 + 自定义异常处理器
     */
    public EventBusWrapper() {
        // 创建固定大小为 10 的线程池，用于异步分发事件
        ExecutorService executor = Executors.newFixedThreadPool(10);
        // 初始化 AsyncEventBus，并绑定异常处理器，防止订阅者异常导致总线崩溃
        this.eventBus = new AsyncEventBus(executor, (throwable, subscriberExceptionContext) -> {
            String eventType = subscriberExceptionContext.getEvent() != null
                    ? subscriberExceptionContext.getEvent().getClass().getName()
                    : "null";
            log.error("【EventBus 异常】订阅者: {}, 事件类型: {}, 异常信息: {}",
                    subscriberExceptionContext.getSubscriberMethod(),
                    eventType,
                    throwable.getMessage(),
                    throwable);
        });
    }

    /**
     * 发布事件到总线
     * 所有订阅了该事件类型的监听者都会收到通知（异步执行）
     *
     * @param event 待发布的事件对象
     */
    public void post(Object event) {
        eventBus.post(event);
    }

    /**
     * 注册订阅者到事件总线
     * 订阅者需要使用 @Subscribe 注解标注事件处理方法
     *
     * @param subscriber 订阅者实例
     */
    public void register(Object subscriber) {
        eventBus.register(subscriber);
    }

    /**
     * 从事件总线注销订阅者
     * 注销后该实例将不再接收任何事件通知
     *
     * @param subscriber 订阅者实例
     */
    public void unregister(Object subscriber) {
        eventBus.unregister(subscriber);
    }
}
