package com.aseubel.yusi.pojo.dto.key;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Browser key exchange request used after email verification.
 */
@Data
public class KeyRecoveryRequest {

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "\\d{6}", message = "验证码格式不正确")
    private String code;

    @NotBlank(message = "恢复公钥不能为空")
    @Size(max = 4096, message = "恢复公钥过长")
    private String recoveryPublicKey;
}
