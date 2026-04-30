package com.openilink.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * WeixinMessage is the unified message structure from getUpdates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WeixinMessage {

    @JsonProperty("seq")
    private Long seq;

    @JsonProperty("message_id")
    private Long messageId;

    @JsonProperty("from_user_id")
    private String fromUserId;

    @JsonProperty("to_user_id")
    private String toUserId;

    @JsonProperty("client_id")
    private String clientId;

    @JsonProperty("create_time_ms")
    private Long createTimeMs;

    @JsonProperty("update_time_ms")
    private Long updateTimeMs;

    @JsonProperty("delete_time_ms")
    private Long deleteTimeMs;

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("group_id")
    private String groupId;

    @JsonProperty("message_type")
    private MessageType messageType;

    @JsonProperty("message_state")
    private MessageState messageState;

    @JsonProperty("item_list")
    private List<MessageItem> itemList;

    @JsonProperty("context_token")
    private String contextToken;
}
