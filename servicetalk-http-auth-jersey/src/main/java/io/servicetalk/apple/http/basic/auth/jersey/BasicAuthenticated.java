/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.apple.http.basic.auth.jersey;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.ws.rs.NameBinding;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.SecurityContext;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Indicates that the marked {@link Application}, resource class or resource method is authenticated with HTTP Basic
 * and that a {@link SecurityContext} needs to be established for the in-flight request.
 */
@NameBinding
@Target({TYPE, METHOD})
@Retention(value = RUNTIME)
public @interface BasicAuthenticated {
    // marker annotation
}
