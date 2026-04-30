package com.openilink.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LoginResult holds the outcome of a QR login flow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResult {

    private boolean connected;
    private String botToken;
    private String botId;
    private String baseUrl;
    private String userId;
    private String message;
}
