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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionStrategy;

import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ExtendedResourceContext;
import org.glassfish.jersey.server.model.HandlerConstructor;
import org.glassfish.jersey.server.model.Invocable;
import org.glassfish.jersey.server.model.MethodHandler;
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
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyMap;

final class RouteExecutionStrategyUtils {
    private static final class RouteExecutionStrategyValidator implements ResourceModelVisitor {
        private final Function<String, HttpExecutionStrategy> routeStrategyFactory;
        private final Map<String, HttpExecutionStrategy> routeStrategies;
        private final Set<String> errors;
        private final Deque<ResourceMethod> resourceMethodDeque;

        RouteExecutionStrategyValidator(final Function<String, HttpExecutionStrategy> routeStrategyFactory) {
            this.routeStrategyFactory = routeStrategyFactory;

            routeStrategies = new HashMap<>();
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
            validate(id -> routeStrategies.computeIfAbsent(id, routeStrategyFactory), resourceMethodDeque.peek(),
                    invocable.getHandler().getHandlerClass(), invocable.getHandlingMethod(), errors);
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

    private RouteExecutionStrategyUtils() {
        // no instances
    }

    static RouteStrategiesConfig validateRouteStrategies(final ApplicationHandler applicationHandler,
                                                         final Function<String,
                                                                 HttpExecutionStrategy> routeStrategyFactory) {
        final ExtendedResourceContext resourceContext =
                applicationHandler.getInjectionManager().getInstance(ExtendedResourceContext.class);
        if (resourceContext == null) {
            return new RouteStrategiesConfig(emptyMap());
        }

        final RouteExecutionStrategyValidator validator = new RouteExecutionStrategyValidator(routeStrategyFactory);
        resourceContext.getResourceModel().accept(validator);
        if (!validator.errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid execution strategy configuration found:\n" + validator.errors);
        }

        return new RouteStrategiesConfig(validator.routeStrategies);
    }

    @Nullable
    static HttpExecutionStrategy getRouteExecutionStrategy(final Class<?> clazz,
                                                           final Method method,
                                                           final RouteStrategiesConfig routeStrategiesConfig) {

        Annotation annotation = getRouteExecutionStrategyAnnotation(clazz, method);
        if (annotation == null) {
            return null;
        }

        if (annotation instanceof NoOffloadsRouteExecutionStrategy) {
            return noOffloadsStrategy();
        }

        return routeStrategiesConfig.routeStrategies.get(((RouteExecutionStrategy) annotation).id());
    }

    @Nullable
    private static Annotation getRouteExecutionStrategyAnnotation(final Class<?> clazz,
                                                                  final Method method) {
        Annotation annotation = method.getAnnotation(NoOffloadsRouteExecutionStrategy.class);
        if (annotation != null) {
            return annotation;
        }
        annotation = method.getAnnotation(RouteExecutionStrategy.class);
        if (annotation != null) {
            return annotation;
        }
        annotation = clazz.getAnnotation(NoOffloadsRouteExecutionStrategy.class);
        if (annotation != null) {
            return annotation;
        }
        return clazz.getAnnotation(RouteExecutionStrategy.class);
    }

    private static void validate(final Function<String, HttpExecutionStrategy> routeStrategyFactory,
                                 @Nullable final ResourceMethod resourceMethod,
                                 final Class<?> clazz,
                                 final Method method,
                                 final Set<String> errors) {

        final Annotation annotation;

        if (stream(method.getAnnotations())
                .filter(RouteExecutionStrategyUtils::isRouteExecutionStrategyAnnotation).count() > 1) {
            errors.add("More than one execution strategy annotation found on: " + clazz);
            annotation = null;
        } else if (stream(clazz.getAnnotations())
                .filter(RouteExecutionStrategyUtils::isRouteExecutionStrategyAnnotation).count() > 1) {
            errors.add("More than one execution strategy annotation found on: " + clazz);
            annotation = null;
        } else {
            annotation = getRouteExecutionStrategyAnnotation(clazz, method);
        }

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

        if (annotation instanceof NoOffloadsRouteExecutionStrategy) {
            return;
        }

        RouteExecutionStrategy routeExecutionStrategy = (RouteExecutionStrategy) annotation;
        final String id = routeExecutionStrategy.id();
        if (id.isEmpty()) {
            errors.add("Route execution strategy with empty ID specified on: " + method);
        } else if (routeStrategyFactory.apply(id) == null) {
            errors.add("Failed to create execution strategy ID: " + id + " specified on: " + method);
        }
    }

    private static boolean isRouteExecutionStrategyAnnotation(final Annotation annotation) {
        return annotation instanceof NoOffloadsRouteExecutionStrategy || annotation instanceof RouteExecutionStrategy;
    }
}
