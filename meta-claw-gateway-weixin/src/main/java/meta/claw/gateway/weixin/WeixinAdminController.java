package meta.claw.gateway.weixin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
