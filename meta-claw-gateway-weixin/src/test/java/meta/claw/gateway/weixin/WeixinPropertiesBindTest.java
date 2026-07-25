package meta.claw.gateway.weixin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WeixinProperties 配置绑定测试：多账号 schema 与 relaxed binding
 */
class WeixinPropertiesBindTest {

    @Test
    void bindsAccountsList() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("meta.claw.weixin.enabled", "true")
                .withProperty("meta.claw.weixin.accounts[0].account-id", "main")
                .withProperty("meta.claw.weixin.accounts[0].token", "tok-1")
                .withProperty("meta.claw.weixin.accounts[0].default-vessel-id", "alibaba")
                .withProperty("meta.claw.weixin.accounts[0].allow-from[0]", "wxid_a")
                .withProperty("meta.claw.weixin.accounts[0].allow-from[1]", "wxid_b")
                .withProperty("meta.claw.weixin.accounts[0].groups[0].group-id", "g1")
                .withProperty("meta.claw.weixin.accounts[0].groups[0].vessel-id", "work")
                .withProperty("meta.claw.weixin.accounts[0].groups[0].trigger-prefix", "@助手")
                .withProperty("meta.claw.weixin.accounts[1].account-id", "work")
                .withProperty("meta.claw.weixin.accounts[1].enabled", "false");

        WeixinProperties props = Binder.get(env)
                .bind("meta.claw.weixin", WeixinProperties.class)
                .orElseThrow(() -> new IllegalStateException("配置绑定失败"));

        assertTrue(props.isEnabled());
        assertEquals(2, props.getAccounts().size());

        WeixinProperties.Account main = props.getAccounts().get(0);
        assertEquals("main", main.getAccountId());
        assertTrue(main.isEnabled());
        assertEquals("tok-1", main.getToken());
        assertEquals("alibaba", main.getDefaultVesselId());
        assertEquals(2, main.getAllowFrom().size());
        assertEquals("wxid_b", main.getAllowFrom().get(1));

        assertEquals(1, main.getGroups().size());
        assertEquals("g1", main.getGroups().get(0).getGroupId());
        assertEquals("work", main.getGroups().get(0).getVesselId());
        assertEquals("@助手", main.getGroups().get(0).getTriggerPrefix());

        WeixinProperties.Account work = props.getAccounts().get(1);
        assertEquals("work", work.getAccountId());
        assertFalse(work.isEnabled());
    }

    @Test
    void defaultsAreSensible() {
        WeixinProperties props = new WeixinProperties();
        assertFalse(props.isEnabled(), "渠道默认关闭，需显式开启");
        assertTrue(props.getAccounts().isEmpty());

        WeixinProperties.Account account = new WeixinProperties.Account();
        assertTrue(account.isEnabled(), "账号默认启用");
        assertEquals("/ai", new WeixinProperties.GroupBinding().getTriggerPrefix());
    }
}
