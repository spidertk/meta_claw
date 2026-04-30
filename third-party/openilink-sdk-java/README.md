# openilink-sdk-java

微信 [iLink Bot API](https://ilinkai.weixin.qq.com) 的 Java SDK。

```xml
<dependency>
    <groupId>com.openilink</groupId>
    <artifactId>openilink-sdk-java</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## 特性

- 扫码登录，支持扫码/过期回调
- 长轮询消息监听，自动重试与退避
- 主动推送（自动缓存 contextToken）
- 输入状态指示器、Bot 配置、CDN 上传
- Builder 模式配置
- `HttpDoer` 接口，方便自定义传输层和测试
- 结构化异常类型（`APIError`、`HTTPError`）
- 支持 Java 8+

## 快速开始

```java
import com.openilink.ILinkClient;
import com.openilink.auth.LoginCallbacks;
import com.openilink.model.response.LoginResult;
import com.openilink.monitor.MonitorOptions;
import com.openilink.util.MessageHelper;

import java.util.concurrent.atomic.AtomicBoolean;

public class EchoBot {
    public static void main(String[] args) {
        ILinkClient client = ILinkClient.builder()
                .token("")
                .build();

        // 扫码登录
        LoginResult result = client.loginWithQR(new LoginCallbacks() {
            @Override
            public void onQRCode(String url) {
                System.out.println("请扫码: " + url);
            }

            @Override
            public void onScanned() {
                System.out.println("已扫码，请在微信上确认...");
            }
        });

        if (!result.isConnected()) {
            System.err.println("登录失败");
            return;
        }
        System.out.println("已连接 BotID=" + result.getBotId());

        // 监听消息 & 自动回复
        AtomicBoolean stop = new AtomicBoolean(false);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> stop.set(true)));

        client.monitor(msg -> {
            String text = MessageHelper.extractText(msg);
            if (text != null && !text.isEmpty()) {
                client.push(msg.getFromUserId(), "收到: " + text);
            }
        }, null, stop);
    }
}
```

## API

### 创建客户端

```java
// 默认配置
ILinkClient client = ILinkClient.create(token);

// 自定义配置
ILinkClient client = ILinkClient.builder()
    .token(token)
    .baseUrl("https://custom.endpoint.com")
    .httpClient(myHttpDoer)
    .botType("3")
    .version("1.0.0")
    .build();
```

### 扫码登录

```java
LoginResult result = client.loginWithQR(new LoginCallbacks() {
    @Override
    public void onQRCode(String url) { /* 展示二维码 */ }

    @Override
    public void onScanned() { /* 用户已扫码 */ }

    @Override
    public void onExpired(int attempt, int max) { /* 二维码过期，正在刷新 */ }
});
// result.isConnected(), result.getBotId(), result.getBotToken(), result.getUserId()
```

登录成功后，客户端的 Token 和 BaseURL 会自动更新。

### 接收消息

```java
AtomicBoolean stop = new AtomicBoolean(false);

MonitorOptions options = MonitorOptions.builder()
    .initialBuf(savedBuf)                    // 从上次位置恢复
    .onBufUpdate(buf -> { /* 持久化游标 */ })
    .onError(err -> { /* 记录错误 */ })
    .onSessionExpired(() -> { /* 重新登录 */ })
    .build();

client.monitor(msg -> {
    String text = MessageHelper.extractText(msg);
    // msg.getFromUserId(), msg.getContextToken(), msg.getItemList()
}, options, stop);
```

Monitor 会自动缓存每个用户的 contextToken，供 `push` 使用。

### 发送消息

```java
// 回复消息（需要入站消息的 contextToken）
client.sendText(userId, "你好", contextToken);

// 主动推送（使用缓存的 contextToken）
client.push(userId, "这是一条定时通知");
```

### 其他

```java
client.sendTyping(userId, ticket, TypingStatus.TYPING);
client.getConfig(userId, contextToken);
client.getUploadUrl(new GetUploadURLReq(...));
```

## 错误处理

```java
try {
    client.push(userId, "hello");
} catch (NoContextTokenException e) {
    // 该用户尚未发送过消息，无法主动推送
} catch (ILinkException e) {
    if (e.getCause() instanceof APIError) {
        APIError apiErr = (APIError) e.getCause();
        if (apiErr.isSessionExpired()) {
            // 需要重新登录
        }
        System.out.println(apiErr.getErrCode() + " " + apiErr.getErrMsg());
    }
    if (e.getCause() instanceof HTTPError) {
        HTTPError httpErr = (HTTPError) e.getCause();
        System.out.println(httpErr.getStatusCode());
    }
}
```

## 许可证

MIT
# openilink-sdk-java
# openilink-sdk-java2
