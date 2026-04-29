package com.example.agent.tools.params;

import com.example.agent.tools.ToolExecutionException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工具参数基类，提供统一的验证能力
 * 所有工具参数类都应该继承此类
 */
public abstract class BaseToolParams {

    private static volatile Validator validator;

    /**
     * 懒加载获取验证器实例
     */
    private static Validator getValidator() {
        if (validator == null) {
            synchronized (BaseToolParams.class) {
                if (validator == null) {
                    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
                    validator = factory.getValidator();
                }
            }
        }
        return validator;
    }

    /**
     * 验证参数是否有效
     *
     * @throws ToolExecutionException 如果验证失败
     */
    public void validate() throws ToolExecutionException {
        Validator validator = getValidator();
        Set<ConstraintViolation<BaseToolParams>> violations = validator.validate(this);
        
        if (!violations.isEmpty()) {
            String errorMessage = violations.stream()
                    .map(v -> String.format("'%s': %s", v.getPropertyPath(), v.getMessage()))
                    .collect(Collectors.joining("; "));
            
            throw new ToolExecutionException("参数验证失败：" + errorMessage);
        }
    }

    /**
     * 获取参数类型名称（用于错误报告）
     *
     * @return 参数类简单名称
     */
    public String getParamType() {
        return this.getClass().getSimpleName();
    }
}
