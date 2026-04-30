package com.openilink.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * QRCodeResponse is returned when requesting a login QR code.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QRCodeResponse {

    @JsonProperty("qrcode")
    private String qrCode;

    @JsonProperty("qrcode_img_content")
    private String qrCodeImgContent;
}
