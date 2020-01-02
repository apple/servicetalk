/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.router.api;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Indicates that a resource class or method needs a specific execution strategy.
 * <p>
 * To disable offloading of the user code to a different thread pool use {@link NoOffloadsRouteExecutionStrategy}.
 */
@Target({TYPE, METHOD})
@Retention(RUNTIME)
@Documented
@Inherited
public @interface RouteExecutionStrategy {
    /**
     * The execution strategy ID specified for this {@link RouteExecutionStrategy}.
     *
     * @return the execution strategy ID as {@link String}
     */
    String id();
}
