package org.example.user.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class LoggingAspect {

    @Around("execution(* org.example.user.service.*.*(..))")
    public Object logMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();

        log.debug("[{}] {}: entering", className, methodName);

        try {
            Object result = joinPoint.proceed();
            log.debug("[{}] {}: completed", className, methodName);
            return result;
        } catch (Exception e) {
            log.error("[{}] {}: threw {} — {}", className, methodName,
                    e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }
}
