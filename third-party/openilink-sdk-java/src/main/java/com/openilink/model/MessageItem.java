package com.openilink.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MessageItem is a single content item within a message (text, image, etc.).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageItem {

    @JsonProperty("type")
    private MessageItemType type;

    @JsonProperty("create_time_ms")
    private Long createTimeMs;

    @JsonProperty("update_time_ms")
    private Long updateTimeMs;

    @JsonProperty("is_completed")
    private Boolean isCompleted;

    @JsonProperty("msg_id")
    private String msgId;

    @JsonProperty("ref_msg")
    private RefMessage refMsg;

    @JsonProperty("text_item")
    private TextItem textItem;

    @JsonProperty("image_item")
    private ImageItem imageItem;

    @JsonProperty("voice_item")
    private VoiceItem voiceItem;

    @JsonProperty("file_item")
    private FileItem fileItem;

    @JsonProperty("video_item")
    private VideoItem videoItem;
}
