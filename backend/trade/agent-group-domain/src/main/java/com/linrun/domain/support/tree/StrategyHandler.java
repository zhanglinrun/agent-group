package com.linrun.domain.support.tree;

public interface StrategyHandler<T, D, R> {

    R apply(T request, D dynamicContext);
}















