package com.openilink.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openilink.model.BaseInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GetUpdatesReq is the request body for the getUpdates long-poll endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetUpdatesReq {

    @JsonProperty("get_updates_buf")
    private String getUpdatesBuf;

    @JsonProperty("base_info")
    private BaseInfo baseInfo;
}
