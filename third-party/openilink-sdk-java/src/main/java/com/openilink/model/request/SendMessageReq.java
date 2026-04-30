package com.openilink.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openilink.model.BaseInfo;
import com.openilink.model.WeixinMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SendMessageReq wraps a single outbound message.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SendMessageReq {

    @JsonProperty("msg")
    private WeixinMessage msg;

    @JsonProperty("base_info")
    private BaseInfo baseInfo;
}
