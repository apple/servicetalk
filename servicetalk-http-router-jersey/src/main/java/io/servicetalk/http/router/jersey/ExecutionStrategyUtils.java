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

import io.servicetalk.concurrent.api.Executor;

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
import java.util.function.Function;

import static io.servicetalk.http.router.jersey.ExecutionStrategy.ExecutorSelector.SERVER_EXECUTOR;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;

final class ExecutionStrategyUtils {
    private static final class ExecutionStrategyValidator implements ResourceModelVisitor {
        private final Function<String, Executor> executorFactory;
        private final Map<String, Executor> executors;
        private final Set<String> errors;
        private final Deque<ResourceMethod> resourceMethodDeque;

        ExecutionStrategyValidator(final Function<String, Executor> executorFactory) {
            this.executorFactory = executorFactory;

            executors = new HashMap<>();
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
            final Method method = invocable.getHandlingMethod();
            final Class<?> clazz = invocable.getHandler().getHandlerClass();
            final ExecutionStrategy executionStrategy = getExecutionStrategy(clazz, method);
            executionStrategy.value().validate(executionStrategy, id -> executors.computeIfAbsent(id, executorFactory),
                    resourceMethodDeque.peek(), clazz, method, errors);
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

    private static final ExecutionStrategy DEFAULT_EXECUTION_STRATEGY = new ExecutionStrategy() {
        @Override
        public Class<? extends Annotation> annotationType() {
            return ExecutionStrategy.class;
        }

        @Override
        public ExecutorSelector value() {
            return SERVER_EXECUTOR;
        }

        @Override
        public String executorId() {
            return "";
        }
    };

    private ExecutionStrategyUtils() {
        // no instances
    }

    static ExecutorConfig validateExecutorConfiguration(final ApplicationHandler applicationHandler,
                                                        final Function<String, Executor> executorFactory) {
        final ExtendedResourceContext resourceContext =
                applicationHandler.getInjectionManager().getInstance(ExtendedResourceContext.class);
        if (resourceContext == null) {
            return new ExecutorConfig(emptyMap());
        }

        final ExecutionStrategyValidator validator = new ExecutionStrategyValidator(executorFactory);
        resourceContext.getResourceModel().accept(validator);
        if (!validator.errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid execution strategy configuration found:\n" + validator.errors);
        }

        return new ExecutorConfig(unmodifiableMap(validator.executors));
    }

    static ExecutionStrategy getExecutionStrategy(final Class<?> clazz, final Method method) {
        ExecutionStrategy executionStrategy = method.getAnnotation(ExecutionStrategy.class);
        if (executionStrategy != null) {
            return executionStrategy;
        }
        executionStrategy = clazz.getAnnotation(ExecutionStrategy.class);
        return executionStrategy != null ? executionStrategy : DEFAULT_EXECUTION_STRATEGY;
    }

    static Executor getResourceExecutor(final Class<?> clazz,
                                        final Method resourceMethod,
                                        final Executor currentExecutor,
                                        final ExecutorConfig executorConfig) {
        final ExecutionStrategy executionStrategy = getExecutionStrategy(clazz, resourceMethod);
        return executionStrategy.value().select(executionStrategy, currentExecutor, executorConfig);
    }
}
