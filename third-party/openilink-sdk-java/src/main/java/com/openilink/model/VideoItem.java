package com.openilink.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * VideoItem holds video message fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VideoItem {

    @JsonProperty("media")
    private CDNMedia media;

    @JsonProperty("video_size")
    private Long videoSize;

    @JsonProperty("play_length")
    private Integer playLength;

    @JsonProperty("video_md5")
    private String videoMd5;

    @JsonProperty("thumb_media")
    private CDNMedia thumbMedia;

    @JsonProperty("thumb_size")
    private Long thumbSize;

    @JsonProperty("thumb_height")
    private Integer thumbHeight;

    @JsonProperty("thumb_width")
    private Integer thumbWidth;
}
