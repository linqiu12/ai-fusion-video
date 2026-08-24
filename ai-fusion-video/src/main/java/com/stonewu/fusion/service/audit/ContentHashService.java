package com.stonewu.fusion.service.audit;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 为版本、审核和发布凭证生成稳定的 SHA-256 内容指纹。 */
@Component
public class ContentHashService {

    public String sha256(String content) {
        try {
            byte[] bytes = content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("当前 Java 运行时不支持 SHA-256", impossible);
        }
    }
}
