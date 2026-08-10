package com.aseubel.yusi.pojo.dto.diary;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A diary attachment anchored to a block in the diary content.
 *
 * <p>The type field is deliberately string based so new attachment kinds can
 * be introduced without changing the diary storage shape.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DiaryAttachmentBinding {

    private String type;

    private String objectKey;

    private String paragraphId;

    private Integer sortOrder;

    /** A short-lived signed URL, populated only in API responses. */
    private String url;
}
