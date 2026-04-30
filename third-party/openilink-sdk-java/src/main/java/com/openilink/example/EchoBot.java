package com.openilink.example;

import com.openilink.ILinkClient;
import com.openilink.auth.LoginCallbacks;
import com.openilink.model.response.LoginResult;
import com.openilink.monitor.MessageHandler;
import com.openilink.monitor.MonitorOptions;
import com.openilink.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Echo Bot 示例 - 自动回复收到的文本消息。
 *
 * <p>运行步骤：
 * <ol>
 *   <li>启动程序</li>
 *   <li>在控制台查看二维码链接并扫码</li>
 *   <li>在微信上确认登录</li>
 *   <li>发送消息，Bot 会自动回复 "收到: " + 原文</li>
 * </ol>
 */
public class EchoBot {

    private static final Logger log = LoggerFactory.getLogger(EchoBot.class);

    public static void main(String[] args) {
        ILinkClient client = ILinkClient.builder()
                .token("")
                .build();

        // 扫码登录
        LoginResult result = client.loginWithQR(new LoginCallbacks() {
            @Override
            public void onQRCode(String qrCodeUrl) {
                log.info("请扫码: {}", qrCodeUrl);
            }

            @Override
            public void onScanned() {
                log.info("已扫码，请在微信上确认...");
            }

            @Override
            public void onExpired(int attempt, int maxAttempts) {
                log.warn("二维码已过期，正在刷新 ({}/{})", attempt, maxAttempts);
            }
        });

        if (!result.isConnected()) {
            log.error("登录失败: {}", result.getMessage());
            return;
        }
        log.info("已连接 BotID={}", result.getBotId());

        // 监听消息 & 自动回复
        AtomicBoolean stopFlag = new AtomicBoolean(false);

        // Ctrl+C 优雅停止
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("正在停止...");
            stopFlag.set(true);
        }));

        MonitorOptions options = MonitorOptions.builder()
                .onBufUpdate(buf -> {
                    try (FileOutputStream fos = new FileOutputStream("sync_buf.dat")) {
                        fos.write(buf.getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        log.warn("保存 sync_buf 失败", e);
                    }
                })
                .onError(e -> log.warn("监听错误: {}", e.getMessage()))
                .onSessionExpired(() -> log.error("会话已过期，请重新登录"))
                .build();

        client.monitor(msg -> {
            String text = MessageHelper.extractText(msg);
            if (text != null && !text.isEmpty()) {
                log.info("收到 [{}]: {}", msg.getFromUserId(), text);
                client.push(msg.getFromUserId(), "收到: " + text);
            }
        }, options, stopFlag);
    }
}
