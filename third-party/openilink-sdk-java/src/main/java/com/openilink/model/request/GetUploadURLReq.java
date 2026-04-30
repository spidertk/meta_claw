package com.openilink.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openilink.model.BaseInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GetUploadURLReq requests a pre-signed CDN upload URL.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetUploadURLReq {

    @JsonProperty("filekey")
    private String fileKey;

    @JsonProperty("media_type")
    private Integer mediaType;

    @JsonProperty("to_user_id")
    private String toUserId;

    @JsonProperty("rawsize")
    private Long rawSize;

    @JsonProperty("rawfilemd5")
    private String rawFileMd5;

    @JsonProperty("filesize")
    private Long fileSize;

    @JsonProperty("thumb_rawsize")
    private Long thumbRawSize;

    @JsonProperty("thumb_rawfilemd5")
    private String thumbRawMd5;

    @JsonProperty("thumb_filesize")
    private Long thumbFileSize;

    @JsonProperty("no_need_thumb")
    private Boolean noNeedThumb;

    @JsonProperty("aeskey")
    private String aesKey;

    @JsonProperty("base_info")
    private BaseInfo baseInfo;
}
