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

import io.servicetalk.concurrent.Single;
import io.servicetalk.concurrent.api.Executor;

import org.glassfish.jersey.server.model.ResourceMethod;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.http.router.jersey.ExecutionStrategy.ExecutorSelector.SERVER_EXECUTOR;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Indicates that a resource class or method needs a specific execution strategy.
 */
@Target({ TYPE, METHOD })
@Retention(RUNTIME)
@Documented
@Inherited
public @interface ExecutionStrategy {
    /**
     * The default {@link ExecutionStrategy#executorId()} used
     * when {@link ExecutorSelector#ROUTER_EXECUTOR} is specified
     * but {@link ExecutionStrategy#executorId()} is not specified.
     */
    String DEFAULT_EXECUTOR_ID = "_default";

    /**
     * An {@link ExecutorSelector} represents a strategy for selecting an {@link Executor} when calling a resource.
     */
    enum ExecutorSelector {
        /**
         * {@link ExecutorSelector} that uses the original {@link Executor} used by the server to deliver the request to
         * the Jersey router service.
         */
        SERVER_EXECUTOR {
            @Override
            void validate(final ExecutionStrategy executionStrategy, final Function<String, Executor> executorConfig,
                          @Nullable final ResourceMethod resourceMethod, final Class<?> clazz, final Method method,
                          final Set<String> errors) {
                final String executorId = executionStrategy.executorId();
                if (!executorId.isEmpty()) {
                    errors.add("Executor ID specified with " + this + " on: " + method);
                }
            }

            @Override
            Executor select(final ExecutionStrategy executionStrategy, final Executor currentExecutor,
                            final ExecutorConfig executorConfig) {
                return currentExecutor;
            }
        },
        /**
         * {@link ExecutorSelector} that uses one of the executors optionally configured on the Jersey router service.
         */
        ROUTER_EXECUTOR {
            @Override
            void validate(final ExecutionStrategy executionStrategy, final Function<String, Executor> executorConfig,
                          @Nullable final ResourceMethod resourceMethod, final Class<?> clazz, final Method method,
                          final Set<String> errors) {

                if (resourceMethod != null) {
                    if (resourceMethod.isManagedAsyncDeclared()) {
                        // ManagedAsync is Jersey's executor offloading mechanism: it can't be used conjointly with
                        // our own offloading mechanism
                        errors.add(this + " is not supported on @ManagedAsync: " + method);
                    } else if (resourceMethod.isSuspendDeclared()) {
                        // We use Jersey's async context to suspend/resume request processing when we offload to an
                        // executor. This prevents other JAX-RS resources that use the same mechanism to work properly
                        // like @Suspended AsyncResponse...
                        errors.add(this + " is not supported on AsyncResponse: " + method);
                    } else if (resourceMethod.isSse()) {
                        // ...and Server-Sent Events
                        errors.add(this + " is not supported on Server-Sent Events: " + method);
                    }
                }

                if (CompletionStage.class.isAssignableFrom(method.getReturnType())) {
                    // Jersey suspends/resumes request handling in case the response is a non-realized CompletionStage.
                    // This makes such responses incompatible with our offloading because we use suspend/resume too
                    // and a request can only be suspended once.
                    // Users that need executor offloading with CompletionStage should return Single instead.
                    errors.add(this + " is not supported on CompletionStage returning method: " + method +
                            " Consider using returning " + Single.class.getSimpleName() + " instead.");
                }

                final String effectiveExecutorId = getEffectiveExecutorId(executionStrategy);
                if (executorConfig.apply(effectiveExecutorId) == null) {
                    errors.add("Failed to create executor ID: " + effectiveExecutorId + " specified on: " + method);
                }
            }

            @Override
            Executor select(final ExecutionStrategy executionStrategy, final Executor currentExecutor,
                            final ExecutorConfig executorConfig) {
                return executorConfig.executors.get(getEffectiveExecutorId(executionStrategy));
            }

            private String getEffectiveExecutorId(final ExecutionStrategy executionStrategy) {
                final String ex = executionStrategy.executorId();
                return ex.isEmpty() ? DEFAULT_EXECUTOR_ID : ex;
            }
        };

        abstract void validate(ExecutionStrategy executionStrategy, Function<String, Executor> executorConfig,
                               @Nullable ResourceMethod resourceMethod, Class<?> clazz, Method method,
                               Set<String> errors);

        abstract Executor select(ExecutionStrategy executionStrategy, Executor currentExecutor,
                                 ExecutorConfig executorConfig);
    }

    /**
     * The {@link ExecutorSelector} specified for this {@link ExecutionStrategy}.
     *
     * @return the {@link ExecutorSelector}
     */
    ExecutorSelector value() default SERVER_EXECUTOR;

    /**
     * The executor ID specified for this {@link ExecutionStrategy}.
     *
     * @return the executor ID as {@link String}
     */
    String executorId() default "";
}
