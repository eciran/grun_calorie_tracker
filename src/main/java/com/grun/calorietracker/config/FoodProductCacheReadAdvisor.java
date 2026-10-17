package com.grun.calorietracker.config;

import com.grun.calorietracker.service.support.FoodProductCacheMissCoordinator;
import com.grun.calorietracker.service.support.FoodProductSnapshotCache;
import io.micrometer.core.instrument.MeterRegistry;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cache.annotation.AnnotationCacheOperationSource;
import org.springframework.cache.interceptor.CacheableOperation;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;

public final class FoodProductCacheReadAdvisor extends AbstractPointcutAdvisor {
    private final AnnotationCacheOperationSource operations = new AnnotationCacheOperationSource();
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameters = new DefaultParameterNameDiscoverer();
    private final Pointcut pointcut = new StaticMethodMatcherPointcut() {
        @Override public boolean matches(Method method, Class<?> type) { return operation(method, type) != null; }
    };
    private final MethodInterceptor advice;

    public FoodProductCacheReadAdvisor(BeanFactory beans, FoodProductCacheProperties properties,
                                      MeterRegistry meters, boolean enabled) {
        var coordinator = new FoodProductCacheMissCoordinator(properties.getCoalescing(), meters);
        setOrder(Ordered.HIGHEST_PRECEDENCE + 200);
        advice = invocation -> {
            if (!enabled || !properties.getCoalescing().isEnabled()
                    || TransactionSynchronizationManager.isActualTransactionActive()) return invocation.proceed();
            Method method = AopUtils.getMostSpecificMethod(invocation.getMethod(), invocation.getThis().getClass());
            CacheableOperation operation = operation(method, invocation.getThis().getClass());
            if (operation == null) return invocation.proceed();
            Object key;
            FoodProductSnapshotCache cache;
            try {
                var evaluation = new MethodBasedEvaluationContext(invocation.getThis(), method, invocation.getArguments(), parameters);
                evaluation.setBeanResolver(new BeanFactoryResolver(beans));
                key = parser.parseExpression(operation.getKey()).getValue(evaluation);
                var context = new Invocation(operation, invocation.getThis(), method, invocation.getArguments());
                var resolved = beans.getBean("foodProductCacheResolver", CacheResolver.class).resolveCaches(context);
                cache = resolved.size() == 1 && resolved.iterator().next() instanceof FoodProductSnapshotCache snapshot
                        ? snapshot : null;
            } catch (RuntimeException unavailable) {
                meters.counter("grun.product.cache.coalescing", "result", "key_bypass").increment();
                return invocation.proceed();
            }
            if (cache == null) return invocation.proceed();
            return coordinator.load(cache, key, invocation::proceed);
        };
    }

    private CacheableOperation operation(Method method, Class<?> type) {
        var found = operations.getCacheOperations(method, type);
        if (found == null || found.size() != 1) return null;
        var only = found.iterator().next();
        return only instanceof CacheableOperation cacheable
                && "foodProductCacheResolver".equals(cacheable.getCacheResolver())
                && !cacheable.getKey().isBlank() && cacheable.getCondition().isBlank() ? cacheable : null;
    }
    @Override public Pointcut getPointcut() { return pointcut; }
    @Override public MethodInterceptor getAdvice() { return advice; }

    private record Invocation(CacheableOperation operation, Object target, Method method, Object[] args)
            implements CacheOperationInvocationContext<CacheableOperation> {
        @Override public CacheableOperation getOperation() { return operation; }
        @Override public Object getTarget() { return target; }
        @Override public Method getMethod() { return method; }
        @Override public Object[] getArgs() { return args; }
    }
}
