package io.servicetalk.loadbalancer;

public interface PrioritizedHost {

    int priority();

    boolean isHealthy();

    double intrinsicWeight();

    void loadBalancedWeight(final double weight);
}
