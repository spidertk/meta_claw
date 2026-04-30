package com.openilink.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ImageItem holds image-related fields for a message.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageItem {

    @JsonProperty("media")
    private CDNMedia media;

    @JsonProperty("thumb_media")
    private CDNMedia thumbMedia;

    @JsonProperty("aeskey")
    private String aesKey;

    @JsonProperty("url")
    private String url;

    @JsonProperty("mid_size")
    private Long midSize;

    @JsonProperty("thumb_size")
    private Long thumbSize;

    @JsonProperty("thumb_height")
    private Integer thumbHeight;

    @JsonProperty("thumb_width")
    private Integer thumbWidth;

    @JsonProperty("hd_size")
    private Long hdSize;
}
