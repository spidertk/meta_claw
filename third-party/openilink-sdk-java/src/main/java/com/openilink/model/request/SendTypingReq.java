package com.openilink.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openilink.model.BaseInfo;
import com.openilink.model.TypingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SendTypingReq sends or cancels a typing indicator.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SendTypingReq {

    @JsonProperty("ilink_user_id")
    private String ilinkUserId;

    @JsonProperty("typing_ticket")
    private String typingTicket;

    @JsonProperty("status")
    private TypingStatus status;

    @JsonProperty("base_info")
    private BaseInfo baseInfo;
}
