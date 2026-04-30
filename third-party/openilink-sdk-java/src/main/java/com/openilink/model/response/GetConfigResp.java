package com.openilink.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GetConfigResp contains bot config including typing_ticket.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetConfigResp {

    @JsonProperty("ret")
    private Integer ret;

    @JsonProperty("errmsg")
    private String errMsg;

    @JsonProperty("typing_ticket")
    private String typingTicket;
}
