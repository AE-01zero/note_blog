package com.aezer0.initialization.config.exception;

/**
 * 参数错误的异常
 * 对于http来说，会返回400的状态码
 *
 * @author aezer0
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }

}
