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
package io.servicetalk.http.router.jersey;

import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

/**
 * Execute every test method a given number of times, this utility helps with use-cases where some behavior is dependent
 * on state, eg. cache or is racy and requires a number of iterations to ensure confidence.
 */
public class Times implements MethodRule {

    private final int times;

    /**
     * Run every test {@code times} times.
     * @param times number of times to repeat every test
     */
    public Times(final int times) {
        this.times = times;
    }

    @Override
    public Statement apply(final Statement base,
                           final FrameworkMethod method,
                           final Object target) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                for (int i = 0; i < times; i++) {
                    base.evaluate();
                }
            }
        };
    }
}