/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.grpc.health;

import io.servicetalk.concurrent.PublisherSource.Processor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.api.GrpcStatus;
import io.servicetalk.grpc.api.GrpcStatusCode;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.health.v1.Health;
import io.servicetalk.health.v1.HealthCheckRequest;
import io.servicetalk.health.v1.HealthCheckResponse;
import io.servicetalk.health.v1.HealthCheckResponse.ServingStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

import static io.servicetalk.concurrent.api.Processors.newPublisherProcessorDropHeadOnOverflow;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.grpc.api.GrpcStatusCode.FAILED_PRECONDITION;
import static io.servicetalk.grpc.api.GrpcStatusCode.NOT_FOUND;
import static io.servicetalk.grpc.api.GrpcStatusCode.RESOURCE_EXHAUSTED;
import static io.servicetalk.health.v1.HealthCheckResponse.ServingStatus.NOT_SERVING;
import static io.servicetalk.health.v1.HealthCheckResponse.ServingStatus.SERVICE_UNKNOWN;
import static io.servicetalk.health.v1.HealthCheckResponse.ServingStatus.SERVING;
import static io.servicetalk.health.v1.HealthCheckResponse.newBuilder;
import static io.servicetalk.utils.internal.NumberUtils.ensurePositive;
import static java.util.Objects.requireNonNull;

/**
 * Implementation of {@link Health.HealthService} which targets
 * <a href="https://github.com/grpc/grpc/blob/master/doc/health-checking.md">gRPC health checking</a> that provides
 * accessors to set/clear status for arbitrary services.
 */
public final class DefaultHealthService implements Health.HealthService {
    /**
     * The name of the service corresponding to
     * the <a href="https://github.com/grpc/grpc/blob/master/doc/health-checking.md">overall health status</a>.
     */
    public static final String OVERALL_SERVICE_NAME = "";
    private static final int DEFAULT_MAX_SERVICES = 1000;
    private final Map<String, HealthValue> serviceToStatusMap = new ConcurrentHashMap<>();
    private final Predicate<String> watchAllowed;
    private final int maxServices;
    private final ReentrantLock lock = new ReentrantLock();
    private boolean terminated;

    /**
     * Create a new instance. Service {@link #OVERALL_SERVICE_NAME} state is set to {@link ServingStatus#SERVING}.
     * <p>
     * Watches are permitted for any service name. The number of tracked services is bounded by a default limit of
     * {@value DEFAULT_MAX_SERVICES}; once reached, a {@link #watch(GrpcServiceContext, HealthCheckRequest)} for a
     * service that isn't already known fails with {@link GrpcStatusCode#RESOURCE_EXHAUSTED}. Use
     * {@link #DefaultHealthService(Predicate)} to restrict which service names may be watched, or
     * {@link #DefaultHealthService(Predicate, int)} to also configure the limit.
     */
    public DefaultHealthService() {
        this(service -> true, DEFAULT_MAX_SERVICES);
    }

    /**
     * Create a new instance. Service {@link #OVERALL_SERVICE_NAME} state is set to {@link ServingStatus#SERVING}.
     * @param watchAllowed {@link Predicate} that determines if a {@link #watch(GrpcServiceContext, HealthCheckRequest)}
     * request that doesn't match an existing service will succeed or fail with
     * {@link GrpcStatusCode#FAILED_PRECONDITION}. This can be used to bound memory by restricting watches to expected
     * service names. The number of tracked services is additionally bounded by a default limit of
     * {@value DEFAULT_MAX_SERVICES}; use {@link #DefaultHealthService(Predicate, int)} to configure it.
     */
    public DefaultHealthService(Predicate<String> watchAllowed) {
        this(watchAllowed, DEFAULT_MAX_SERVICES);
    }

    /**
     * Create a new instance. Service {@link #OVERALL_SERVICE_NAME} state is set to {@link ServingStatus#SERVING}.
     * @param watchAllowed {@link Predicate} that determines if a {@link #watch(GrpcServiceContext, HealthCheckRequest)}
     * request that doesn't match an existing service will succeed or fail with
     * {@link GrpcStatusCode#FAILED_PRECONDITION}. This can be used to bound memory by restricting watches to expected
     * service names.
     * @param maxServices Upper bound on the number of services tracked, counting both services registered via
     * {@link #setStatus(String, ServingStatus)} and services created by {@link #watch(GrpcServiceContext,
     * HealthCheckRequest)}. A {@link #watch(GrpcServiceContext, HealthCheckRequest)} request for a service that isn't
     * already known will fail with {@link GrpcStatusCode#RESOURCE_EXHAUSTED} once this limit is reached. Must be
     * positive, and should be sized above the number of services the server registers.
     */
    public DefaultHealthService(Predicate<String> watchAllowed, int maxServices) {
        this.watchAllowed = requireNonNull(watchAllowed);
        this.maxServices = ensurePositive(maxServices, "maxServices");
        lock.lock(); // to satisfy the assert in newRegisteredHealthValue()
        try {
            final HealthValue overall = newRegisteredHealthValue();
            overall.next(newBuilder().setStatus(SERVING).build());
            serviceToStatusMap.put(OVERALL_SERVICE_NAME, overall);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Single<HealthCheckResponse> check(final GrpcServiceContext ctx, final HealthCheckRequest request) {
        HealthValue health = serviceToStatusMap.get(request.getService());
        if (health == null) {
            return Single.failed(new GrpcStatusException(
                    new GrpcStatus(NOT_FOUND, "unknown service: " + request.getService())));
        }
        return health.publisher.takeAtMost(1).firstOrError();
    }

    @Override
    public Publisher<HealthCheckResponse> watch(final GrpcServiceContext ctx, final HealthCheckRequest request) {
        final String service = request.getService();
        return Publisher.defer(() -> {
            lock.lock();
            try {
                if (terminated) {
                    return Publisher.from(newBuilder().setStatus(NOT_SERVING).build());
                }
                HealthValue healthValue = serviceToStatusMap.get(service);
                if (healthValue == null) {
                    if (!watchAllowed.test(service)) {
                        return Publisher.failed(new GrpcStatusException(new GrpcStatus(FAILED_PRECONDITION,
                                "watch not allowed for service " + service)));
                    }
                    if (serviceToStatusMap.size() >= maxServices) {
                        return Publisher.failed(new GrpcStatusException(new GrpcStatus(RESOURCE_EXHAUSTED,
                                "max number of watched services reached")));
                    }
                    healthValue = newWatchOnlyHealthValue();
                    serviceToStatusMap.put(service, healthValue);
                }
                healthValue.incrementWatches();
                final HealthValue watched = healthValue;
                return watched.publisher.beforeFinally(() -> watched.watcherTerminated(service));
            } finally {
                lock.unlock();
            }
        });
    }

    // Visible for testing: number of services currently tracked, used to assert watch-only entries are evicted.
    int trackedServices() {
        return serviceToStatusMap.size();
    }

    /**
     * Updates the status of the server.
     * @param service the name of some aspect of the server that is associated with a health status.
     * This name can have no relation with the gRPC services that the server is running with.
     * It can also be an empty String {@code ""} per the gRPC specification.
     * @param status is one of the values {@link ServingStatus#SERVING}, {@link ServingStatus#NOT_SERVING},
     * and {@link ServingStatus#UNKNOWN}.
     * @return {@code true} if this change was applied. {@code false} if it was not due to {@link #terminate()}.
     */
    public boolean setStatus(String service, ServingStatus status) {
        HealthValue healthValue;
        lock.lock();
        try {
            if (terminated) {
                return false;
            }
            healthValue = serviceToStatusMap.get(service);
            if (healthValue == null) {
                healthValue = newRegisteredHealthValue();
                serviceToStatusMap.put(service, healthValue);
            } else {
                // Promote a watch-only entry so it is retained independent of active watchers.
                healthValue.register();
            }
        } finally {
            lock.unlock();
        }
        HealthCheckResponse resp = newBuilder().setStatus(status).build();
        healthValue.next(resp);
        return true;
    }

    /**
     * Clears the health status record of a service. The health service will respond with NOT_FOUND
     * error on checking the status of a cleared service.
     * @param service the name of some aspect of the server that is associated with a health status.
     * This name can have no relation with the gRPC services that the server is running with.
     * It can also be an empty String {@code ""} per the gRPC specification.
     * @return {@code true} if this call removed a service. {@code false} if service wasn't found.
     */
    public boolean clearStatus(String service) {
        final HealthValue healthValue = serviceToStatusMap.remove(service);
        if (healthValue != null) {
            healthValue.completeMultipleTerminalSafe(SERVICE_UNKNOWN);
            return true;
        }
        return false;
    }

    /**
     * All services will be marked as {@link ServingStatus#NOT_SERVING}, and
     * future updates to services will be prohibited. This method is meant to be called prior to server shutdown as a
     * way to indicate that clients should redirect their traffic elsewhere.
     * @return {@code true} if this call terminated this service. {@code false} if it was not due to previous call to
     * this method.
     */
    public boolean terminate() {
        lock.lock();
        try {
            if (terminated) {
                return false;
            }
            terminated = true;
        } finally {
            lock.unlock();
        }
        for (final HealthValue healthValue : serviceToStatusMap.values()) {
            healthValue.completeMultipleTerminalSafe(NOT_SERVING);
        }
        return true;
    }

    private HealthValue newRegisteredHealthValue() {
        HealthValue value = new HealthValue();
        value.register();
        return value;
    }

    private HealthValue newWatchOnlyHealthValue() {
        HealthValue value = new HealthValue();
        value.next(newBuilder().setStatus(SERVICE_UNKNOWN).build());
        return value;
    }

    private final class HealthValue {
        private final Processor<HealthCheckResponse, HealthCheckResponse> processor;
        private final Publisher<HealthCheckResponse> publisher;
        // Both guarded by DefaultHealthService.lock.
        private int watcherCount;
        private boolean registered;

        HealthValue() {
            this.processor = newPublisherProcessorDropHeadOnOverflow(4);
            this.publisher = fromSource(processor)
                    // Allow multiple subscribers to Subscribe to the resulting Publisher, use a history of 1
                    // so each new subscriber gets the latest state.
                    .replay(1);
        }

        /**
         * Marks this value as server-registered and maintains a Subscriber so the latest signal is retained for late
         * subscribers even when no client is currently watching. Idempotent.
         * Must be called while holding the lock
         */
        void register() {
            assert lock.isHeldByCurrentThread();
            if (!registered) {
                registered = true;
                publisher.ignoreElements().subscribe();
            }
        }

        void watcherTerminated(final String service) {
            lock.lock();
            try {
                // Only watch-only entries are evicted; server-registered services persist for check() and late watchers
                watcherCount--;
                if (watcherCount == 0 && !registered) {
                    serviceToStatusMap.remove(service, this);
                }
            } finally {
                lock.unlock();
            }
        }

        void incrementWatches() {
            assert lock.isHeldByCurrentThread();
            watcherCount++;
        }

        void next(HealthCheckResponse response) {
            processor.onNext(response);
        }

        /**
         * This method is safe to invoke multiple times. Safety is currently provided by default {@link Processor}
         * implementations.
         * @param status The last status to set.
         */
        void completeMultipleTerminalSafe(ServingStatus status) {
            try {
                next(newBuilder().setStatus(status).build());
            } catch (Throwable cause) {
                processor.onError(cause);
                return;
            }
            processor.onComplete();
        }
    }
}
