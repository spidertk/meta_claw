package com.openilink.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * QRStatusResponse is returned when polling QR code scan status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QRStatusResponse {

    /** Status: wait, scaned, confirmed, expired */
    @JsonProperty("status")
    private String status;

    @JsonProperty("bot_token")
    private String botToken;

    @JsonProperty("ilink_bot_id")
    private String ilinkBotId;

    @JsonProperty("baseurl")
    private String baseUrl;

    @JsonProperty("ilink_user_id")
    private String ilinkUserId;
}
