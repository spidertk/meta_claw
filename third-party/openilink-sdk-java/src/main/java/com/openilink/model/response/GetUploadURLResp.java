package com.openilink.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GetUploadURLResp contains pre-signed upload parameters from the CDN.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetUploadURLResp {

    @JsonProperty("upload_param")
    private String uploadParam;

    @JsonProperty("thumb_upload_param")
    private String thumbUploadParam;
}
