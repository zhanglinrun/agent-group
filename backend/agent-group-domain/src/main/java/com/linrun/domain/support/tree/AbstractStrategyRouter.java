package com.linrun.domain.support.tree;

public abstract class AbstractStrategyRouter<T, D, R> implements StrategyHandler<T, D, R> {

    @Override
    public R apply(T request, D dynamicContext) {
        StrategyHandler<T, D, R> next = router(request, dynamicContext);
        if (next == null) {
            throw new IllegalStateException("strategy router returned null");
        }
        return next.apply(request, dynamicContext);
    }

    protected abstract StrategyHandler<T, D, R> router(T request, D dynamicContext);
}
