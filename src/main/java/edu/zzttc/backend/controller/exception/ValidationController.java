package edu.zzttc.backend.controller.exception;


import edu.zzttc.backend.domain.entity.RestBean;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class ValidationController {

    @ExceptionHandler(ValidationException.class)
    public RestBean<Void> validateException(ValidationException e) {
        log.warn("Resolve[{}:{}]:", e.getClass().getSimpleName(), e.getMessage());
        return RestBean.failure(400,"请求参数有误");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public RestBean<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.warn("Resolve[{}:{}]:", e.getClass().getSimpleName(), e.getMessage());

        // 获取第一个字段错误信息
        FieldError fieldError = e.getBindingResult().getFieldErrors().get(0);
        String fieldName = fieldError.getField();
        String errorMessage = fieldError.getDefaultMessage();

        // 针对验证码长度提供具体的错误提示
        if ("code".equals(fieldName) && errorMessage != null && errorMessage.contains("长度")) {
            return RestBean.failure(400, "验证码有误");
        }

        // 其他验证错误
        return RestBean.failure(400, "请求参数错误：" + errorMessage);
    }


}
