package meta.claw.gateway.weixin;

import meta.claw.core.message.Context;
import meta.claw.core.message.Reply;
import meta.claw.core.message.ReplyType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 微信渠道管理端点
 * <p>
 * 设计约束：协议无法主动发起微信会话，故管理操作全部走本地 HTTP（不鉴权，仅供本机/内网使用，
 * 生产环境需在反向代理层加鉴权）。
 * </p>
 */
@RestController
@RequestMapping("/bot/weixin")
public class WeixinAdminController {

    private final WeixinChannelManager manager;

    /**
     * relogin 异步执行器（扫码最长 8 分钟，不得阻塞 HTTP 线程）
     */
    private final ExecutorService reloginExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "weixin-admin-relogin");
        t.setDaemon(true);
        return t;
    });

    public WeixinAdminController(WeixinChannelManager manager) {
        this.manager = manager;
    }

    /**
     * 全部账号状态
     */
    @GetMapping("/status")
    public List<Map<String, Object>> status() {
        return manager.listChannels().values().stream()
                .map(ch -> {
                    var map = new java.util.LinkedHashMap<String, Object>();
                    map.put("accountId", ch.getAccountId());
                    map.put("channelKey", ch.getChannelKey());
                    map.put("online", ch.isOnline());
                    map.put("botId", ch.getBotId());
                    map.put("pendingQr", ch.getPendingQrUrl() != null);
                    Instant lastInbound = ch.getLastInboundAt();
                    map.put("lastInboundAt", lastInbound != null ? lastInbound.toString() : null);
                    map.put("lastInboundUserId", ch.getLastInboundUserId());
                    return map;
                })
                .map(m -> (Map<String, Object>) m)
                .toList();
    }

    /**
     * 获取当前待确认的登录二维码 URL
     */
    @GetMapping("/qrcode")
    public ResponseEntity<Map<String, Object>> qrcode(@RequestParam String account) {
        WeixinChannel channel = manager.getChannel(account);
        if (channel == null) {
            return ResponseEntity.notFound().build();
        }
        String qrUrl = channel.getPendingQrUrl();
        if (qrUrl == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("accountId", account, "qrUrl", qrUrl));
    }

    /**
     * 触发指定账号重新登录（删除登录态 → 重新扫码）
     */
    @PostMapping("/relogin")
    public ResponseEntity<Map<String, Object>> relogin(@RequestParam String account) {
        WeixinChannel channel = manager.getChannel(account);
        if (channel == null) {
            return ResponseEntity.notFound().build();
        }
        reloginExecutor.submit(channel::relogin);
        return ResponseEntity.accepted().body(Map.of("accountId", account, "accepted", true));
    }

    /**
     * 手动发送媒体消息（出站媒体管线自测入口）
     * <p>
     * to 缺省时使用最近一次入站消息的发送者（需对方先给 Bot 发过消息，
     * 一方面获得收件人，另一方面保证 context_token 存在）。
     * </p>
     *
     * @param account 账号 ID
     * @param to      收件人 userId（可选，缺省为最近入站发送者）
     * @param path    本地文件路径（图片/视频/文件）
     * @param type    媒体类型 IMAGE/VIDEO/FILE（可选，缺省按扩展名推断）
     * @param caption 说明文本（可选，单独先发一条）
     */
    @PostMapping("/send-media")
    public ResponseEntity<Map<String, Object>> sendMedia(@RequestParam String account,
                                                         @RequestParam(required = false) String to,
                                                         @RequestParam String path,
                                                         @RequestParam(required = false) String type,
                                                         @RequestParam(required = false) String caption) {
        WeixinChannel channel = manager.getChannel(account);
        if (channel == null) {
            return ResponseEntity.notFound().build();
        }
        String receiver = (to != null && !to.isBlank()) ? to : channel.getLastInboundUserId();
        if (receiver == null || receiver.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "缺少收件人：请提供 to 参数，或先让目标用户给 Bot 发一条消息"));
        }
        if (!Files.exists(Path.of(path))) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件不存在: " + path));
        }

        Reply reply = new Reply(ReplyType.TEXT, caption != null ? caption : "");
        reply.setMediaPath(path);
        reply.setMediaType(type);
        Context context = new Context();
        context.setReceiver(receiver);
        try {
            channel.send(reply, context);
            return ResponseEntity.ok(Map.of(
                    "accountId", account, "to", receiver, "path", path, "sent", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "发送失败: " + e.getMessage()));
        }
    }
}
