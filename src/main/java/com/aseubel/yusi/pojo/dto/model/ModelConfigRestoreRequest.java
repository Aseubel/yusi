package com.aseubel.yusi.pojo.dto.model;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 模型配置恢复请求：mode=FACTORY 出厂恢复；mode=VERSION 需携带 version。 */
@Data
public class ModelConfigRestoreRequest {

    /** 恢复模式：FACTORY | VERSION */
    @NotNull
    private String mode;

    /** 历史回滚目标版本（mode=VERSION 时必填） */
    private Long version;

    /** 乐观锁：当前配置版本 */
    @NotNull
    private Long expectedVersion;
}
