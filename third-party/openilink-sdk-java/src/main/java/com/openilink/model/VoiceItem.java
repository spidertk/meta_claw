package com.openilink.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * VoiceItem holds voice message fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VoiceItem {

    @JsonProperty("media")
    private CDNMedia media;

    @JsonProperty("encode_type")
    private Integer encodeType;

    @JsonProperty("bits_per_sample")
    private Integer bitsPerSample;

    @JsonProperty("sample_rate")
    private Integer sampleRate;

    @JsonProperty("playtime")
    private Integer playTime;

    @JsonProperty("text")
    private String text;
}
