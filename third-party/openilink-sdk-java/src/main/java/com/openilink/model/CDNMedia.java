package com.openilink.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CDNMedia is a reference to encrypted media on the Weixin CDN.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CDNMedia {

    @JsonProperty("encrypt_query_param")
    private String encryptQueryParam;

    @JsonProperty("aes_key")
    private String aesKey;

    @JsonProperty("encrypt_type")
    private Integer encryptType;
}
