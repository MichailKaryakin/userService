package org.example.user.aspect;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class MetricsAspect {

    private final MeterRegistry meterRegistry;

    @Around("execution(* org.example.user.service.*.*(..))")
    public Object measureMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            Object result = joinPoint.proceed();
            sample.stop(Timer.builder("user.service.method.duration")
                    .tag("class", className)
                    .tag("method", methodName)
                    .tag("success", "true")
                    .register(meterRegistry));
            return result;
        } catch (Throwable e) {
            sample.stop(Timer.builder("user.service.method.duration")
                    .tag("class", className)
                    .tag("method", methodName)
                    .tag("success", "false")
                    .register(meterRegistry));
            meterRegistry.counter("user.service.method.errors",
                    "class", className,
                    "method", methodName,
                    "exception", e.getClass().getSimpleName()
            ).increment();
            throw e;
        }
    }
}
