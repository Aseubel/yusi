package com.aseubel.yusi.pojo.dto.soulplaza;

import com.aseubel.yusi.pojo.contant.CardType;
import lombok.Data;

/**
 * @author Aseubel
 * @date 2026/1/3 下午10:46
 */
@Data
public class SubmitCardRequest {
    private String content;
    private String originId;
    private CardType type;
}
