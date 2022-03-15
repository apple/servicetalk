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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.grpc.api.GrpcServiceContext;
import io.servicetalk.grpc.api.GrpcStatus;
import io.servicetalk.grpc.api.GrpcStatusCode;
import io.servicetalk.health.v1.Health;
import io.servicetalk.health.v1.HealthCheckRequest;
import io.servicetalk.health.v1.HealthCheckResponse;
import io.servicetalk.health.v1.HealthCheckResponse.ServingStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

import static io.servicetalk.concurrent.api.Processors.newPublisherProcessorDropHeadOnOverflow;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.grpc.api.GrpcStatusCode.FAILED_PRECONDITION;
import static io.servicetalk.grpc.api.GrpcStatusCode.NOT_FOUND;
import static io.servicetalk.health.v1.HealthCheckResponse.ServingStatus.NOT_SERVING;
import static io.servicetalk.health.v1.HealthCheckResponse.ServingStatus.SERVICE_UNKNOWN;
import static io.servicetalk.health.v1.HealthCheckResponse.ServingStatus.SERVING;
import static io.servicetalk.health.v1.HealthCheckResponse.newBuilder;
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
    private final Map<String, HealthValue> serviceToStatusMap = new ConcurrentHashMap<>();
    private final Predicate<String> watchAllowed;
    private final Lock lock = new ReentrantLock();
    private boolean terminated;

    /**
     * Create a new instance. Service {@link #OVERALL_SERVICE_NAME} state is set to {@link ServingStatus#SERVING}.
     */
    public DefaultHealthService() {
        this(service -> true);
    }

    /**
     * Create a new instance. Service {@link #OVERALL_SERVICE_NAME} state is set to {@link ServingStatus#SERVING}.
     * @param watchAllowed {@link Predicate} that determines if a {@link #watch(GrpcServiceContext, HealthCheckRequest)}
     * request that doesn't match an existing service will succeed or fail with
     * {@link GrpcStatusCode#FAILED_PRECONDITION}. This can be used to bound memory by restricting watches to expected
     * service names.
     */
    public DefaultHealthService(Predicate<String> watchAllowed) {
        this.watchAllowed = requireNonNull(watchAllowed);
        serviceToStatusMap.put(OVERALL_SERVICE_NAME, new HealthValue(SERVING));
    }

    @Override
    public Single<HealthCheckResponse> check(final GrpcServiceContext ctx, final HealthCheckRequest request) {
        HealthValue health = serviceToStatusMap.get(request.getService());
        if (health == null) {
            return Single.failed(new GrpcStatus(NOT_FOUND, null, "unknown service: " + request.getService())
                    .asException());
        }
        return Single.succeeded(health.last);
    }

    @Override
    public Publisher<HealthCheckResponse> watch(final GrpcServiceContext ctx, final HealthCheckRequest request) {
        // Try a get first to avoid locking with the assumption that most requests will be to watch existing services.
        HealthValue healthValue = serviceToStatusMap.get(request.getService());
        if (healthValue == null) {
            if (!watchAllowed.test(request.getService())) {
                return Publisher.failed(new GrpcStatus(FAILED_PRECONDITION, null, "watch not allowed for service " +
                        request.getService()).asException());
            }
            lock.lock();
            try {
                if (terminated) {
                    return Publisher.from(newBuilder().setStatus(NOT_SERVING).build());
                }
                healthValue = serviceToStatusMap.computeIfAbsent(request.getService(),
                        __ -> new HealthValue(SERVICE_UNKNOWN));
            } finally {
                lock.unlock();
            }
        }

        return Publisher.from(healthValue.last).concat(healthValue.publisher);
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
        final HealthCheckResponse resp;
        final HealthValue healthValue;
        lock.lock();
        try {
            if (terminated) {
                return false;
            }
            resp = newBuilder().setStatus(status).build();
            healthValue = serviceToStatusMap.computeIfAbsent(service, __ -> new HealthValue(resp));
        } finally {
            lock.unlock();
        }
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
            healthValue.complete(SERVICE_UNKNOWN);
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
            healthValue.complete(NOT_SERVING);
        }
        serviceToStatusMap.clear();
        return true;
    }

    private static final class HealthValue {
        private final PublisherSource.Processor<HealthCheckResponse, HealthCheckResponse> processor;
        private final Publisher<HealthCheckResponse> publisher;
        private volatile HealthCheckResponse last;

        private HealthValue(final HealthCheckResponse initialState) {
            this.processor = newPublisherProcessorDropHeadOnOverflow(4);
            this.publisher = fromSource(processor)
                    // Allow multiple subscribers to Subscribe to the resulting Publisher.
                    .multicast(1, false);
            this.last = initialState;
        }

        private HealthValue(final ServingStatus status) {
            this(newBuilder().setStatus(status).build());
        }

        void next(HealthCheckResponse response) {
            // Set the status here instead of in an operator because we need the status to be updated regardless if
            // anyone is consuming the status.
            last = response;
            processor.onNext(response);
        }

        void complete(ServingStatus status) {
            next(newBuilder().setStatus(status).build());
            processor.onComplete();
        }
    }
}
