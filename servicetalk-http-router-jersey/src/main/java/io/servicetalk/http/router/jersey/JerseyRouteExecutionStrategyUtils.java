/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.router.jersey;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.router.api.NoOffloadsRouteExecutionStrategy;
import io.servicetalk.router.api.RouteExecutionStrategy;
import io.servicetalk.router.api.RouteExecutionStrategyFactory;
import io.servicetalk.router.utils.internal.RouteExecutionStrategyUtils;

import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ExtendedResourceContext;
import org.glassfish.jersey.server.model.HandlerConstructor;
import org.glassfish.jersey.server.model.Invocable;
import org.glassfish.jersey.server.model.MethodHandler;
import org.glassfish.jersey.server.model.Parameter;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceMethod;
import org.glassfish.jersey.server.model.ResourceModel;
import org.glassfish.jersey.server.model.ResourceModelComponent;
import org.glassfish.jersey.server.model.ResourceModelVisitor;
import org.glassfish.jersey.server.model.RuntimeResource;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpExecutionStrategies.customStrategyBuilder;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadAll;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNever;
import static io.servicetalk.router.utils.internal.RouteExecutionStrategyUtils.getRouteExecutionStrategyAnnotation;
import static java.util.Collections.emptyMap;
import static org.glassfish.jersey.model.Parameter.Source.ENTITY;

final class JerseyRouteExecutionStrategyUtils {
    private static final class RouteExecutionStrategyValidator implements ResourceModelVisitor {
        private final RouteExecutionStrategyFactory<HttpExecutionStrategy> strategyFactory;
        private final Map<String, HttpExecutionStrategy> routeStrategies;
        private final Map<Method, HttpExecutionStrategy> methodDefaultStrategies;
        private final Set<String> errors;
        private final Deque<ResourceMethod> resourceMethodDeque;

        RouteExecutionStrategyValidator(final RouteExecutionStrategyFactory<HttpExecutionStrategy> strategyFactory) {
            this.strategyFactory = strategyFactory;

            routeStrategies = new HashMap<>();
            methodDefaultStrategies = new HashMap<>();
            errors = new TreeSet<>();
            resourceMethodDeque = new ArrayDeque<>();
        }

        @Override
        public void visitResource(final Resource resource) {
            processComponents(resource);
        }

        @Override
        public void visitChildResource(final Resource resource) {
            processComponents(resource);
        }

        @Override
        public void visitResourceMethod(final ResourceMethod method) {
            resourceMethodDeque.push(method);
            try {
                processComponents(method);
            } finally {
                resourceMethodDeque.pop();
            }
        }

        @Override
        public void visitInvocable(final Invocable invocable) {
            methodDefaultStrategies.put(invocable.getHandlingMethod(), computeDefaultExecutionStrategy(invocable));

            validateRouteExecutionStrategyAnnotationIfPresent(
                    invocable.getHandlingMethod(), invocable.getHandler().getHandlerClass(),
                    id -> routeStrategies.computeIfAbsent(id, strategyFactory::get), resourceMethodDeque.peek(),
                    errors);
        }

        @Override
        public void visitMethodHandler(final MethodHandler methodHandler) {
            processComponents(methodHandler);
        }

        @Override
        public void visitResourceHandlerConstructor(final HandlerConstructor constructor) {
            processComponents(constructor);
        }

        @Override
        public void visitResourceModel(final ResourceModel resourceModel) {
            processComponents(resourceModel);
        }

        @Override
        public void visitRuntimeResource(final RuntimeResource runtimeResource) {
            processComponents(runtimeResource);
        }

        private void processComponents(final ResourceModelComponent component) {
            final List<? extends ResourceModelComponent> components = component.getComponents();
            if (components == null) {
                return;
            }
            components.forEach(rmc -> rmc.accept(this));
        }
    }

    private static final HttpExecutionStrategy OFFLOAD_RECEIVE_META =
            customStrategyBuilder().offloadReceiveMetadata().build();
    private static final HttpExecutionStrategy OFFLOAD_RECEIVE_META_AND_SEND =
            customStrategyBuilder().offloadReceiveMetadata().offloadSend().build();

    private JerseyRouteExecutionStrategyUtils() {
        // no instances
    }

    static RouteStrategiesConfig validateRouteStrategies(
            final ApplicationHandler applicationHandler,
            final RouteExecutionStrategyFactory<HttpExecutionStrategy> strategyFactory) {

        final ExtendedResourceContext resourceContext =
                applicationHandler.getInjectionManager().getInstance(ExtendedResourceContext.class);
        if (resourceContext == null) {
            return new RouteStrategiesConfig(emptyMap(), emptyMap());
        }

        final RouteExecutionStrategyValidator validator = new RouteExecutionStrategyValidator(strategyFactory);
        resourceContext.getResourceModel().accept(validator);
        if (!validator.errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid execution strategy configuration found:\n" + validator.errors);
        }

        return new RouteStrategiesConfig(validator.routeStrategies, validator.methodDefaultStrategies);
    }

    static HttpExecutionStrategy getRouteExecutionStrategy(final Method method,
                                                           final Class<?> clazz,
                                                           final RouteStrategiesConfig routeStrategiesConfig) {

        final Annotation annotation = getRouteExecutionStrategyAnnotation(method, clazz);
        if (annotation == null) {
            // This can never be null because all methods known to Jersey have been introspected
            return routeStrategiesConfig.methodDefaultStrategies.get(method);
        }

        if (annotation instanceof NoOffloadsRouteExecutionStrategy) {
            return offloadNever();
        }

        // This can never be null because we have pre-validated that all route strategy IDs exist at startup
        return routeStrategiesConfig.routeStrategies.get(((RouteExecutionStrategy) annotation).id());
    }

    private static HttpExecutionStrategy computeDefaultExecutionStrategy(final Invocable invocable) {
        final Parameter entityParam = invocable.getParameters().stream().filter(p -> p.getSource() == ENTITY)
                .findFirst()
                .orElse(null);

        final boolean consumesStreaming = entityParam != null &&
                Publisher.class.isAssignableFrom(entityParam.getRawType());
        final boolean consumesAsync = consumesStreaming ||
                (entityParam != null && Single.class.isAssignableFrom(entityParam.getRawType()));
        final boolean producesStreaming = Publisher.class.isAssignableFrom(invocable.getRawResponseType());
        final boolean producesAsync = producesStreaming ||
                Single.class.isAssignableFrom(invocable.getRawResponseType()) ||
                Completable.class.isAssignableFrom(invocable.getRawResponseType());

        if (!consumesAsync && !producesAsync) {
            // blocking-aggregated
            // HttpApiConversions/BlockingToStreamingService uses OFFLOAD_RECEIVE_DATA_STRATEGY because we are invoking
            // the service within a callback of Single<HttpRequest>, which may call the service on the eventloop.
            // Here we are not invoking the service as part of collecting the request payload,
            // so OFFLOAD_RECEIVE_META is sufficient.
            return OFFLOAD_RECEIVE_META;
        } else if (consumesAsync && !consumesStreaming && producesAsync && !producesStreaming) {
            // async-aggregated
            return OFFLOAD_RECEIVE_META_AND_SEND;
        } else {
            // default to async-streaming, as it has the most aggressive offloading strategy
            return offloadAll();
        }
    }

    private static void validateRouteExecutionStrategyAnnotationIfPresent(
            final Method method,
            final Class<?> clazz,
            final RouteExecutionStrategyFactory<HttpExecutionStrategy> strategyFactory,
            @Nullable final ResourceMethod resourceMethod,
            final Set<String> errors) {

        final Annotation annotation = RouteExecutionStrategyUtils
                .validateRouteExecutionStrategyAnnotationIfPresent(method, clazz, strategyFactory, errors);

        if (annotation == null) {
            return;
        }

        if (resourceMethod != null) {
            if (resourceMethod.isManagedAsyncDeclared()) {
                // ManagedAsync is Jersey's executor offloading mechanism: it can't be used conjointly with
                // our own offloading mechanism
                errors.add("Execution strategy annotations are not supported on @ManagedAsync: " + method);
            } else if (resourceMethod.isSuspendDeclared()) {
                // We use Jersey's async context to suspend/resume request processing when we offload to an
                // executor. This prevents other JAX-RS resources that use the same mechanism to work properly
                // like @Suspended AsyncResponse...
                errors.add("Execution strategy annotations are not supported on AsyncResponse: " + method);
            } else if (resourceMethod.isSse()) {
                // ...and Server-Sent Events
                errors.add("Execution strategy annotations are not supported on Server-Sent Events: " + method);
            }
        }

        if (CompletionStage.class.isAssignableFrom(method.getReturnType())) {
            // Jersey suspends/resumes request handling in case the response is a non-realized CompletionStage.
            // This makes such responses incompatible with our offloading because we use suspend/resume too
            // and a request can only be suspended once.
            // Users that need executor offloading with CompletionStage should return Single instead.
            errors.add("Execution strategy annotations are not supported on CompletionStage returning method: " +
                    method + " Consider returning " + Single.class.getSimpleName() + " instead.");
        }
    }
}
