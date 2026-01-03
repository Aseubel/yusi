package com.aseubel.yusi.pojo.dto.diary;

import com.aseubel.yusi.pojo.entity.Diary;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * @author Aseubel
 * @date 2025/5/7 上午10:11
 */
@Data
public class EditDiaryRequest implements Serializable {

    private String userId;

    private String diaryId;

    private String title;

    private String content;

    private Boolean visibility;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @JsonSerialize(using = LocalDateSerializer.class)
    @JsonDeserialize(using = LocalDateDeserializer.class)
    private LocalDate entryDate;

    public Diary toDiary() {
        return Diary.builder()
               .userId(userId)
                .diaryId(diaryId)
               .title(title)
               .content(content)
               .visibility(visibility)
               .entryDate(entryDate)
               .build();
    }
}
