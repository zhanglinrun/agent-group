package com.linrun.domain.support.tree;

public class StrategyTree<T, D, R> {

    private final StrategyHandler<T, D, R> root;

    public StrategyTree(StrategyHandler<T, D, R> root) {
        this.root = root;
    }

    public R apply(T request, D dynamicContext) {
        return root.apply(request, dynamicContext);
    }
}
