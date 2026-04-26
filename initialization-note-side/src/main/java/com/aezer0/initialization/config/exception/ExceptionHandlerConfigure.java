package com.aezer0.initialization.config.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;

import com.aezer0.initialization.result.BizResponseCode;
import com.aezer0.initialization.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 异常处理
 *
 * @author aezer0
 */
@RestControllerAdvice
@Slf4j
public class ExceptionHandlerConfigure {

    /**
     * 处理未登录异常
     *
     * @param exception NotLoginException
     * @return 返回 401 状态码和响应体
     */
    @ExceptionHandler(NotLoginException.class)
    @ResponseBody
    public ResponseEntity<Result<String>> handle(NotLoginException exception) {
        log.debug("未登录异常", exception);
        Result<String> result = Result.error(new BizException("未登录"));
        result.setCode(HttpStatus.UNAUTHORIZED.value()); // 401
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
    }

    /**
     * 处理业务异常
     *
     * @param exception BizException
     * @return 返回 200 状态码，body 描述异常信息
     */
    @ExceptionHandler(BizException.class)
    @ResponseBody
    public ResponseEntity<Result<Object>> handle(BizException exception) {
        return ResponseEntity.ok(Result.error(exception));
    }

    /**
     * 处理参数错误的异常
     *
     * @param exception BadRequestException
     * @return 返回 400 状态码
     */
    @ExceptionHandler(BadRequestException.class)
    @ResponseBody
    public ResponseEntity<Result<String>> handle(BadRequestException exception) {
        Result<String> result = Result.error(new BizException(BizResponseCode.ERR_400, exception.getMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    /**
     * 处理角色无权操作异常
     *
     * @param exception NotRoleException
     * @return 返回 200 状态码，body 描述无权限信息
     */
    @ExceptionHandler(NotRoleException.class)
    @ResponseBody
    public ResponseEntity<Result<String>> handle(NotRoleException exception) {
        log.debug("当前用户角色无权操作", exception);
        Result<String> result = Result.error(new BizException(BizResponseCode.ERR_11003));
        return ResponseEntity.ok(result);
    }

    /**
     * 处理参数校验异常
     *
     * @param exception MethodArgumentNotValidException
     * @return 返回 400 状态码，body 描述参数错误信息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public ResponseEntity<Result<String>> handle(MethodArgumentNotValidException exception) {
        ObjectError error = exception.getBindingResult().getAllErrors().get(0);
        String defaultMessage = error.getDefaultMessage();
        Result<String> result = Result.error(new BizException(BizResponseCode.ERR_400, defaultMessage));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    /**
     * 处理未捕获的其他运行时异常
     *
     * @param exception RuntimeException
     * @return 返回 200 状态码，body 描述通用错误
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseBody
    public ResponseEntity<Result<Object>> handle(RuntimeException exception) {
        log.error("未捕获的运行时异常", exception);
        Result<Object> result = Result.error(new BizException(exception.getMessage()));
        return ResponseEntity.ok(result);
    }

}
