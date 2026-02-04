package com.aseubel.yusi.common.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * 压缩工具类
 * 用于缓存前压缩数据，减少 Redis 内存占用
 * 
 * 使用 DEFLATE 算法（与 gzip 相同的压缩算法，但无头部）
 * 
 * @author Aseubel
 * @date 2026/2/3
 */
@Slf4j
public final class CompressUtils {

    private CompressUtils() {
    }

    /**
     * 压缩阈值：只有超过此大小的数据才压缩
     * 避免小数据压缩后反而变大
     */
    private static final int COMPRESS_THRESHOLD = 256;

    /**
     * 压缩标记前缀：用于区分压缩和未压缩数据
     */
    private static final String COMPRESSED_PREFIX = "COMPRESSED:";

    /**
     * 压缩字符串
     * 如果数据小于阈值，直接返回原始数据
     * 
     * @param data 原始字符串
     * @return 压缩后的 Base64 字符串（带前缀）或原始字符串
     */
    public static String compress(String data) {
        if (data == null || data.isEmpty()) {
            return data;
        }

        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);

        // 小数据不压缩
        if (bytes.length < COMPRESS_THRESHOLD) {
            return data;
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (DeflaterOutputStream dos = new DeflaterOutputStream(baos, new Deflater(Deflater.BEST_COMPRESSION))) {
                dos.write(bytes);
            }

            byte[] compressed = baos.toByteArray();

            // 如果压缩后没有变小，返回原始数据
            if (compressed.length >= bytes.length) {
                return data;
            }

            return COMPRESSED_PREFIX + Base64.getEncoder().encodeToString(compressed);
        } catch (IOException e) {
            log.warn("压缩失败，返回原始数据: {}", e.getMessage());
            return data;
        }
    }

    /**
     * 解压字符串
     * 自动检测是否为压缩数据
     * 
     * @param data 可能是压缩后的字符串或原始字符串
     * @return 解压后的字符串
     */
    public static String decompress(String data) {
        if (data == null || data.isEmpty()) {
            return data;
        }

        // 检查是否为压缩数据
        if (!data.startsWith(COMPRESSED_PREFIX)) {
            return data;
        }

        String base64Data = data.substring(COMPRESSED_PREFIX.length());

        try {
            byte[] compressed = Base64.getDecoder().decode(base64Data);

            ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            try (InflaterInputStream iis = new InflaterInputStream(bais, new Inflater())) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = iis.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
            }

            return baos.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("解压失败，返回原始数据: {}", e.getMessage());
            // 返回去掉前缀的数据（可能是损坏的压缩数据）
            return data;
        }
    }

    /**
     * 计算压缩率
     * 用于监控和调优
     * 
     * @param original   原始数据
     * @param compressed 压缩后数据
     * @return 压缩率（0-1，越小越好）
     */
    public static double compressionRatio(String original, String compressed) {
        if (original == null || original.isEmpty()) {
            return 1.0;
        }
        return (double) compressed.length() / original.length();
    }
}
