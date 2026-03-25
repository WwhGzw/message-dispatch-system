package com.msg.access.exception;

import com.msg.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GlobalExceptionHandler 单元测试
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleValidationException_shouldReturn400WithDetails() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "bizType", "不能为空"));
        bindingResult.addError(new FieldError("request", "channel", "不能为空"));

        MethodParameter param = new MethodParameter(
                this.getClass().getDeclaredMethod("setUp"), -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, bindingResult);

        Result<Void> result = handler.handleValidationException(ex);

        assertEquals(400, result.getCode());
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("参数校验失败"));
        assertTrue(result.getMessage().contains("不能为空"));
    }

    @Test
    void handleMessageNotReadable_shouldReturn400() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("JSON parse error");

        Result<Void> result = handler.handleMessageNotReadable(ex);

        assertEquals(400, result.getCode());
        assertFalse(result.isSuccess());
        assertEquals("请求体格式错误", result.getMessage());
    }

    @Test
    void handleIllegalArgument_shouldReturn400WithMessage() {
        IllegalArgumentException ex = new IllegalArgumentException("无效的渠道类型");

        Result<Void> result = handler.handleIllegalArgument(ex);

        assertEquals(400, result.getCode());
        assertFalse(result.isSuccess());
        assertEquals("无效的渠道类型", result.getMessage());
    }

    @Test
    void handleGenericException_shouldReturn500() {
        Exception ex = new RuntimeException("unexpected error");

        Result<Void> result = handler.handleGenericException(ex);

        assertEquals(500, result.getCode());
        assertFalse(result.isSuccess());
        assertEquals("系统异常", result.getMessage());
    }
}
