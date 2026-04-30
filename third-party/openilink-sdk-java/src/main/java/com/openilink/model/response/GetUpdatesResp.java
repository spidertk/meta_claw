package com.openilink.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openilink.model.WeixinMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GetUpdatesResp is the response from the getUpdates endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetUpdatesResp {

    @JsonProperty("ret")
    private Integer ret;

    @JsonProperty("errcode")
    private Integer errCode;

    @JsonProperty("errmsg")
    private String errMsg;

    @JsonProperty("msgs")
    private List<WeixinMessage> msgs;

    @JsonProperty("get_updates_buf")
    private String getUpdatesBuf;

    @JsonProperty("longpolling_timeout_ms")
    private Long longPollingTimeoutMs;
}
