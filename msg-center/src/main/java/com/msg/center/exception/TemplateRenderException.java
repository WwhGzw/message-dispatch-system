package com.msg.center.exception;

/**
 * 模板渲染异常
 * <p>
 * 模板不存在、未启用、变量缺失、渲染结果包含未替换占位符等场景抛出。
 */
public class TemplateRenderException extends RuntimeException {

    public TemplateRenderException(String message) {
        super(message);
    }

    public TemplateRenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
