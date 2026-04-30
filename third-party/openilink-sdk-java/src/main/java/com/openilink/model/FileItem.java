package com.openilink.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FileItem holds file attachment fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileItem {

    @JsonProperty("media")
    private CDNMedia media;

    @JsonProperty("file_name")
    private String fileName;

    @JsonProperty("md5")
    private String md5;

    @JsonProperty("len")
    private String len;
}
