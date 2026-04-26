package com.aezer0.initialization.result;


import com.aezer0.initialization.config.exception.BizException;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 统一响应body
 *
 * @author aezer0
 */
@Data
@Accessors(chain = true)
public class Result<T> {

    /**
     * 数据
     */
    private T data;

    /**
     * 响应码
     */
    private int code;

    /**
     * 描述
     */
    private String message;

    // 构造方法（私有，强制使用静态方法构建）
    private Result() {
    }

    /**
     * 无数据的成功响应
     *
     * @param <T> 类型
     * @return 响应体
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 成功的响应，附带数据
     *
     * @param data 数据
     * @param <T>  数据类型
     * @return 响应体
     */
    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.data = data;
        r.code = 0;
        r.message = "成功";
        return r;
    }

    /**
     * 构建业务异常的响应
     *
     * @param exception 业务异常
     * @param <T>       类型
     * @return 响应体
     */
    public static <T> Result<T> error(BizException exception) {
        Result<T> r = new Result<>();
        r.data = null;
        r.code = exception.getCodeValue(); // 使用 getCodeValue 处理 code = -1 的情况
        r.message = exception.getMessage();
        return r;
    }

    /**
     * 构建通用异常的响应
     *
     * @param exception 异常
     * @param <T>       类型
     * @return 响应体
     */
    public static <T> Result<T> error(RuntimeException exception) {
        Result<T> r = new Result<>();
        r.data = null;
        r.code = -1;
        r.message = exception.getMessage();
        return r;
    }

    /**
     * 构建错误响应
     *
     * @param message 错误消息
     * @param <T>     类型
     * @return 响应体
     */
    public static <T> Result<T> error(String message) {
        Result<T> r = new Result<>();
        r.data = null;
        r.code = -1;
        r.message = message;
        return r;
    }

}