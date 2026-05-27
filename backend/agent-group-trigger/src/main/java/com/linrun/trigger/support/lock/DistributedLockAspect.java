package com.linrun.trigger.support.lock;

import com.linrun.types.exception.AppException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

@Aspect
@Component
public class DistributedLockAspect {

    private static final String LOCK_PREFIX = "agent-group:lock:";

    private final RedissonClient redissonClient;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public DistributedLockAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        String lockKey = LOCK_PREFIX + parseKey(joinPoint, distributedLock);
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = lock.tryLock(distributedLock.waitTime(), distributedLock.leaseTime(), distributedLock.timeUnit());
        if (!locked) {
            throw new AppException("LOCK_0001", "distributed lock acquire failed");
        }
        try {
            return joinPoint.proceed();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private String parseKey(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) {
        String keyExpression = distributedLock.key();
        if (!StringUtils.hasText(keyExpression)) {
            return joinPoint.getSignature().toShortString();
        }
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            StandardEvaluationContext context = new StandardEvaluationContext();
            String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
            Object[] args = joinPoint.getArgs();
            for (int i = 0; i < args.length; i++) {
                context.setVariable("p" + i, args[i]);
                context.setVariable("a" + i, args[i]);
            }
            if (parameterNames != null) {
                for (int i = 0; i < parameterNames.length && i < args.length; i++) {
                    context.setVariable(parameterNames[i], args[i]);
                }
            }
            Object value = expressionParser.parseExpression(keyExpression).getValue(context);
            return value == null ? method.getName() : value.toString();
        } catch (Exception ignored) {
            return keyExpression;
        }
    }
}
