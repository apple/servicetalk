/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.router.utils.internal;

import io.servicetalk.router.api.NoOffloadsRouteExecutionStrategy;
import io.servicetalk.router.api.RouteExecutionStrategy;
import io.servicetalk.router.api.RouteExecutionStrategyFactory;
import io.servicetalk.transport.api.ExecutionStrategy;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Set;
import javax.annotation.Nullable;

import static java.util.Arrays.stream;

/**
 * Utilities to handle {@link RouteExecutionStrategy} annotation.
 */
public final class RouteExecutionStrategyUtils {

    private RouteExecutionStrategyUtils() {
        // no instances
    }

    /**
     * Validates configuration of {@link RouteExecutionStrategy} annotation is correct if present.
     *
     * @param method {@link Method} to validate
     * @param clazz {@link Class} to validate
     * @param strategyFactory a {@link RouteExecutionStrategyFactory} that creates a specific{@link ExecutionStrategy}
     * @param errors collection to track all errors related to misconfiguration
     * @return an instance of {@link RouteExecutionStrategy} annotation if present
     */
    @Nullable
    public static Annotation validateRouteExecutionStrategyAnnotationIfPresent(
            final Method method,
            final Class<?> clazz,
            final RouteExecutionStrategyFactory<? extends ExecutionStrategy> strategyFactory,
            final Set<String> errors) {

        final Annotation annotation = validateAnnotation(method, clazz, errors);
        if (annotation == null) {
            return null;
        }

        if (annotation instanceof NoOffloadsRouteExecutionStrategy) {
            return annotation;
        }

        validateId(annotation, method, strategyFactory, errors);
        return annotation;
    }

    /**
     * Returns {@link ExecutionStrategy} for the specified {@link Method} and validates that configuration of
     * {@link RouteExecutionStrategy} annotation is correct if present.
     *
     * @param method {@link Method} to validate
     * @param clazz {@link Class} to validate
     * @param strategyFactory a {@link RouteExecutionStrategyFactory} that creates a specific{@link ExecutionStrategy}
     * @param errors collection to track all errors related to misconfiguration
     * @param noOffloadsExecutionStrategy an {@link ExecutionStrategy} for {@link NoOffloadsRouteExecutionStrategy}
     * @param <T> specific implementation type of {@link ExecutionStrategy}
     * @return a defined {@link ExecutionStrategy} or {@code null} if not defined
     */
    @Nullable
    public static <T extends ExecutionStrategy> T getAndValidateRouteExecutionStrategyAnnotationIfPresent(
            final Method method,
            final Class<?> clazz,
            final RouteExecutionStrategyFactory<T> strategyFactory,
            final Set<String> errors,
            final T noOffloadsExecutionStrategy) {

        final Annotation annotation = validateAnnotation(method, clazz, errors);
        if (annotation == null) {
            return null;
        }

        if (annotation instanceof NoOffloadsRouteExecutionStrategy) {
            return noOffloadsExecutionStrategy;
        }

        return validateId(annotation, method, strategyFactory, errors);
    }

    @Nullable
    private static Annotation validateAnnotation(final Method method, final Class<?> clazz, final Set<String> errors) {
        final Annotation annotation;

        if (stream(method.getAnnotations())
                .filter(RouteExecutionStrategyUtils::isRouteExecutionStrategyAnnotation).count() > 1) {
            errors.add("More than one execution strategy annotation found on method: " + method);
            annotation = null;
        } else if (stream(clazz.getAnnotations())
                .filter(RouteExecutionStrategyUtils::isRouteExecutionStrategyAnnotation).count() > 1) {
            errors.add("More than one execution strategy annotation found on: " + clazz);
            annotation = null;
        } else {
            annotation = getRouteExecutionStrategyAnnotation(method, clazz);
        }
        return annotation;
    }

    @Nullable
    private static <T extends ExecutionStrategy> T validateId(final Annotation annotation, final Method method,
                                                              final RouteExecutionStrategyFactory<T> strategyFactory,
                                                              final Set<String> errors) {
        final String id = ((RouteExecutionStrategy) annotation).id();
        if (id.isEmpty()) {
            errors.add("Route execution strategy with empty ID specified on: " + method);
            return null;
        }
        final T routeExecutionStrategy = strategyFactory.get(id);
        if (routeExecutionStrategy == null) {
            errors.add("Failed to create execution strategy ID \"" + id + "\" specified on: " + method);
        }
        return routeExecutionStrategy;
    }

    /**
     * Returns {@link RouteExecutionStrategy} annotation if exists on {@link Method} or {@link Class}.
     *
     * @param method an endpoint method
     * @param clazz an endpoint class
     * @return {@link RouteExecutionStrategy} annotation if exists on {@link Method} or {@link Class}
     */
    @Nullable
    public static Annotation getRouteExecutionStrategyAnnotation(final Method method, final Class<?> clazz) {
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

    private static boolean isRouteExecutionStrategyAnnotation(final Annotation annotation) {
        return annotation instanceof NoOffloadsRouteExecutionStrategy || annotation instanceof RouteExecutionStrategy;
    }
}
