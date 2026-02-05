package com.aseubel.yusi.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import com.aseubel.yusi.common.exception.ErrorCode;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
public final class Response<T> implements Serializable {

    private static final long serialVersionUID = 7000723935764546321L;

    private Integer code;
    private String info;
    private T data;

    public static <T> Response<T> success() {
        return Response.<T>builder().code(200).info("success").build();
    }

    public static <T> Response<T> success(T data) {
        return Response.<T>builder().code(200).info("success").data(data).build();
    }

    public static <T> Response<T> success(String info) {
        return Response.<T>builder().code(200).info(info).build();
    }

    public static <T> Response<T> success(Integer code, String info) {
        return Response.<T>builder().code(code).info(info).build();
    }

    public static <T> Response<T> fail(String info) {
        return Response.<T>builder().code(500).info(info).build();
    }

    public static <T> Response<T> authFail(String info) {
        return Response.<T>builder().code(401).info(info).build();
    }

}
