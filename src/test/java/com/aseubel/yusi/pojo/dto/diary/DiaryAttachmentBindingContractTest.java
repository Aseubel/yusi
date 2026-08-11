package com.aseubel.yusi.pojo.dto.diary;

import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiaryAttachmentBindingContractTest {

    @Test
    void diaryWriteRequestsPersistTextRangeAnchor() {
        DiaryAttachmentAnchor anchor = DiaryAttachmentAnchor.builder()
                .kind("TEXT_RANGE")
                .start(3)
                .end(7)
                .quote("正文")
                .prefix("一段")
                .suffix("内容")
                .build();
        DiaryAttachmentBinding binding = DiaryAttachmentBinding.builder()
                .type("IMAGE")
                .objectKey("images/user/a.jpg")
                .paragraphId("p-a")
                .sortOrder(0)
                .anchor(anchor)
                .build();

        WriteDiaryRequest request = new WriteDiaryRequest();
        request.setAttachmentBindings(List.of(binding));

        assertThat(JSONUtil.parseArray(request.toDiary().getAttachmentBindingsJson())
                .getJSONObject(0)
                .getJSONObject("anchor")
                .getStr("quote"))
                .isEqualTo("正文");

        EditDiaryRequest editRequest = new EditDiaryRequest();
        editRequest.setAttachmentBindings(List.of(binding));
        assertThat(JSONUtil.parseArray(editRequest.toDiary().getAttachmentBindingsJson())
                .getJSONObject(0)
                .getJSONObject("anchor")
                .getStr("quote"))
                .isEqualTo("正文");
    }
}
