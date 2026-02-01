package com.aseubel.yusi.common.jackson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 用于解决 Page 接口反序列化问题的辅助类
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RestPage<T> extends PageImpl<T> {

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public RestPage(
            @JsonProperty("content") List<T> content,
            @JsonProperty("page") JsonNode page,
            @JsonProperty("number") Integer number,
            @JsonProperty("size") Integer size,
            @JsonProperty("totalElements") Long totalElements) {
        super(
                content == null ? Collections.emptyList() : content,
                PageRequest.of(resolveNumber(number, page), resolveSize(size, page)),
                resolveTotalElements(totalElements, page, content));
    }

    public RestPage(List<T> content, Pageable pageable, long total) {
        super(content, pageable, total);
    }

    public RestPage(List<T> content) {
        super(content);
    }

    public RestPage() {
        super(new ArrayList<>());
    }

    private static int resolveNumber(Integer number, JsonNode page) {
        if (number != null) {
            return number;
        }
        if (page != null && page.has("number") && page.get("number").canConvertToInt()) {
            return page.get("number").asInt();
        }
        return 0;
    }

    private static int resolveSize(Integer size, JsonNode page) {
        if (size != null && size > 0) {
            return size;
        }
        if (page != null && page.has("size") && page.get("size").canConvertToInt()) {
            int resolved = page.get("size").asInt();
            if (resolved > 0) {
                return resolved;
            }
        }
        return 10;
    }

    private static long resolveTotalElements(Long totalElements, JsonNode page, List<?> content) {
        if (totalElements != null) {
            return totalElements;
        }
        if (page != null && page.has("totalElements") && page.get("totalElements").canConvertToLong()) {
            return page.get("totalElements").asLong();
        }
        return content == null ? 0L : content.size();
    }
}
