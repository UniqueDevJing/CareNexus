package com.hospital.v2.common;

/** 外部模型不可用时的显式失败，禁止用演示数据伪造临床结论。 */
public final class ProviderUnavailableException extends RuntimeException {
    public ProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
