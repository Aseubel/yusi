package com.aseubel.yusi.pojo.dto.ai;

import lombok.Data;

import java.io.Serializable;

/**
 * @author Aseubel
 * @date 2025/5/9 下午11:50
 */
@Data
public class DiaryChatRequest implements Serializable {

    String userId;

    String query;
}
