package com.ignacioramirez.itemDetailService.dto.items.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public record PictureRS(
        String url,
        boolean main,
        String alt
) { }
