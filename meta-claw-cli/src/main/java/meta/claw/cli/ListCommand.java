package meta.claw.cli;

import meta.claw.core.config.VesselConfig;
import meta.claw.core.runtime.VesselManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;

/**
 * 列出所有已加载的 Vessel。
 * <p>
 * 直接从 {@link VesselManager} 内存中获取已缓存的 Vessel 配置列表，
 * 以表格形式展示 Vessel 的完整信息。
 * </p>
 */
@Component
@Command(name = "list", description = "List all vessels")
public class ListCommand implements Runnable {

    @Autowired
    private VesselManager vesselManager;

    @Option(names = {"--all", "-a"}, description = "Include hidden vessels")
    private boolean includeHidden;

    @Override
    public void run() {
        List<VesselConfig> vessels = vesselManager.listAvailableVessels();

        if (vessels.isEmpty()) {
            System.out.println("No vessels found. Run 'meta-claw init' to create the default vessel.");
            return;
        }

        // 表头
        System.out.println("┌──────────────┬──────────────┬──────────────────────────────┬──────┬────────────────────┬────────┬───────────┬──────────┐");
        System.out.println(String.format("│ %-12s │ %-12s │ %-28s │ %-4s │ %-18s │ %-6s │ %-9s │ %-8s │",
                "ID", "Name", "Description", "Emoji", "Model", "Role", "AutoServe", "Provider"));
        System.out.println("├──────────────┼──────────────┼──────────────────────────────┼──────┼────────────────────┼────────┼───────────┼──────────┤");

        for (VesselConfig meta : vessels) {
            String id = meta.getIdentity().getId();
            if (id != null && id.startsWith(".") && !includeHidden) {
                continue;
            }

            VesselConfig.Identity m = meta.getIdentity();
            String name = m != null && m.getName() != null ? m.getName()
                    : (m != null && m.getDisplayName() != null ? m.getDisplayName() : id);
            String desc = m != null && m.getDescription() != null ? m.getDescription() : "";
            String emoji = m != null && m.getEmoji() != null ? m.getEmoji() : "";

            VesselConfig.LlmConfig llm = meta.getLlm();
            String model = llm != null && llm.getModel() != null ? llm.getModel() : "";
            String provider = llm != null && llm.getProvider() != null ? llm.getProvider() : "";

            VesselConfig.Behavior rt = meta.getBehavior();
            String role = rt != null && rt.getRole() != null ? rt.getRole() : "";
            String autoServe = rt != null && rt.isAutoServe() ? "true" : "false";

            // Truncate for display
            id = truncate(id, 12);
            name = truncate(name, 12);
            desc = truncate(desc, 28);
            emoji = truncate(emoji, 4);
            model = truncate(model, 18);
            role = truncate(role, 6);
            provider = truncate(provider, 8);

            System.out.println(String.format("│ %-12s │ %-12s │ %-28s │ %-4s │ %-18s │ %-6s │ %-9s │ %-8s │",
                    id, name, desc, emoji, model, role, autoServe, provider));
        }
        System.out.println("└──────────────┴──────────────┴──────────────────────────────┴──────┴────────────────────┴────────┴───────────┴──────────┘");
        System.out.println("\nUse 'meta-claw chat <id>' to start chatting.");
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }
}
