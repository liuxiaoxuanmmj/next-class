package edu.zzttc.backend.exception;

import lombok.Getter;

/**
 * 业务层自定义异常
 * 用于主动抛出明确的错误信息
 */
@Getter
public class ServiceException extends RuntimeException {

    private final int code;

    public ServiceException(int code, String message) {
        super(message);
        this.code = code;
    }

    public ServiceException(String message) {
        this(400, message);
    }
}
