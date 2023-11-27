package io.servicetalk.loadbalancer;

interface LoadBalancerObserver {

    void hostMarkedExpired();
    void hostRemovedViaExpired();

    void hostRemovedViaUnavailable();

    void hostAvailable();

    OutlierObserver outlierEvent();

    interface OutlierObserver {
        void hostMarkedUnhealthy();

        void hostRevived();
    }
}
