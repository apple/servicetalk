package io.servicetalk.loadbalancer;

interface LoadBalancerObserver<ResolvedAddress> {

    HostObserver<ResolvedAddress> hostObserver();

    OutlierObserver outlierEventObserver();

    interface HostObserver<ResolvedAddress> {
        void hostMarkedExpired(ResolvedAddress address, int connectionCount);
        void expiredHostRemoved(ResolvedAddress address);
        void expiredHostRevived(ResolvedAddress address, int connectionCount);

        void unavailableHostRemoved(ResolvedAddress address, int connectionCount);

        void hostCreated(ResolvedAddress address);
    }

    interface OutlierObserver {
        void hostMarkedUnhealthy();

        void hostRevived();
    }
}
