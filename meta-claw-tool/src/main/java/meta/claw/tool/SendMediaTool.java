package meta.claw.tool;

import meta.claw.core.infra.ProjectRootFinder;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.VesselContext;
import meta.claw.core.tool.annotation.ToolService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 主动发送媒体文件工具（图片 / 视频 / 文件）。
 * <p>
 * 遵循 send_media 工具模式：工具本身不直接调用渠道 API，只把文件挂载到当前任务上下文，
 * 由渠道层在发送本轮回复时统一携带出站（Reply.mediaPath/mediaType）。
 * </p>
 * <p>
 * 路径解析按以下优先级：绝对路径 → 当前 vessel 目录相对路径
 * （知识库条目 frontmatter 中的 {@code source_asset} 即此类路径，如 {@code assets/xxx/original.jpg}）
 * → 项目根相对路径 → 工作目录相对路径。
 * </p>
 */
@ToolService
public class SendMediaTool {

    @Tool(description = "Send a media file (image, video, or file attachment) to the user. "
            + "Use this when the user asks to see/receive an original image or file, "
            + "e.g. the source_asset path from a knowledge base entry (like 'assets/xxx/original.jpg', "
            + "resolved relative to the current vessel automatically) or any local file path. "
            + "The media will be attached to your current reply; still include a brief text explanation.")
    public String sendMedia(
            @ToolParam(description = "File path: absolute, vessel-relative (e.g. 'assets/...' from knowledge source_asset), "
                    + "or project-relative") String filePath,
            @ToolParam(description = "Media type hint: IMAGE, VIDEO, or FILE (optional; inferred from extension when omitted)",
                    required = false) String mediaType) {
        TaskContext ctx = VesselContext.getTaskContext();
        if (ctx == null) {
            return "Error: no active task context; sendMedia can only be used while handling a user message";
        }
        if (filePath == null || filePath.isBlank()) {
            return "Error: filePath is empty";
        }
        try {
            Path resolved = resolve(filePath, ctx.getVesselId());
            if (resolved == null || !Files.isRegularFile(resolved)) {
                return "Error: file not found: " + filePath;
            }
            String type = normalizeType(mediaType);
            if (type == null) {
                type = inferType(resolved.getFileName().toString());
            }
            ctx.scheduleMedia(resolved.toAbsolutePath().normalize().toString(), type);
            return "Media scheduled: " + resolved + " (type=" + type
                    + "). It will be sent to the user together with your reply.";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * 解析顺序：绝对路径 → vessel 相对路径 → 项目根相对路径 → 工作目录相对路径。
     */
    private Path resolve(String input, String vesselId) {
        Path direct = Path.of(input);
        if (direct.isAbsolute() && Files.exists(direct)) {
            return direct;
        }
        Path metaClawDir = ProjectRootFinder.getMetaClawDir();
        if (vesselId != null && !vesselId.isBlank()) {
            Path vesselRel = metaClawDir.resolve("vessels").resolve(vesselId).resolve(input).normalize();
            if (Files.exists(vesselRel)) {
                return vesselRel;
            }
        }
        Path projectRel = metaClawDir.getParent().resolve(input).normalize();
        if (Files.exists(projectRel)) {
            return projectRel;
        }
        Path cwdRel = Path.of(System.getProperty("user.dir")).resolve(input).toAbsolutePath().normalize();
        if (Files.exists(cwdRel)) {
            return cwdRel;
        }
        return direct.isAbsolute() ? direct : null;
    }

    private String normalizeType(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            return null;
        }
        String upper = mediaType.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "IMAGE", "VIDEO", "FILE" -> upper;
            default -> null;
        };
    }

    private String inferType(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp")) {
            return "IMAGE";
        }
        if (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".m4v")) {
            return "VIDEO";
        }
        return "FILE";
    }
}
