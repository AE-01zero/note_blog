package com.aezer0.initialization.config.exception;


import com.aezer0.initialization.result.BizResponseCode;
import lombok.Getter;

/**
 * 业务异常，这种异常一般是可预知的
 *
 * @author aezer0
 */
@Getter
public class BizException extends RuntimeException {

    private final BizResponseCode code;
    private final String customMsg;

    public BizException(BizResponseCode bizResponseCode) {
        super(bizResponseCode.getMsg());
        this.code = bizResponseCode;
        this.customMsg = bizResponseCode.getMsg();
    }

    public BizException(BizResponseCode bizResponseCode, String msg) {
        super(msg);
        this.code = bizResponseCode;
        this.customMsg = msg;
    }

    public BizException(String msg) {
        super(msg);
        this.code = null; // 枚举中没有 code = -1
        this.customMsg = msg;
    }

    public int getCodeValue() {
        return code != null ? code.getCode() : -1;
    }

    @Override
    public String getMessage() {
        return customMsg != null ? customMsg : (code != null ? code.getMsg() : super.getMessage());
    }
}