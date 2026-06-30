package meta.claw.core.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaPart {
    private String type;      // image_url, image_base64, audio_url, video_url
    private String mimeType;  // image/png
    private String url;       // http URL or local asset URL
    private byte[] data;      // inline binary (optional)
}
