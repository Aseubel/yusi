package com.aseubel.yusi.pojo.dto.diary;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A location inside a diary block that can host an attachment marker.
 *
 * <p>The quote and its surrounding context allow the client to relocate an
 * anchor after text is inserted before it without relying on stale offsets.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DiaryAttachmentAnchor {

    private String kind;

    private Integer start;

    private Integer end;

    private String quote;

    private String prefix;

    private String suffix;
}
