package com.openilink.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openilink.model.BaseInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GetConfigReq requests bot config for a given user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetConfigReq {

    @JsonProperty("ilink_user_id")
    private String ilinkUserId;

    @JsonProperty("context_token")
    private String contextToken;

    @JsonProperty("base_info")
    private BaseInfo baseInfo;
}
