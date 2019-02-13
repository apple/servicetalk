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
package io.servicetalk.http.api;

import org.junit.Test;
import org.junit.runner.Runner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters;
import org.junit.runners.parameterized.ParametersRunnerFactory;
import org.junit.runners.parameterized.TestWithParameters;

import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;

/**
 * JUnit supports {@link Test @Test} annotated default methods from v5, this small extension enables it for JUnit 4.
 */
public final class MixinAwareParametersRunnerFactory implements ParametersRunnerFactory {

    @Override
    public Runner createRunnerForTestWithParameters(final TestWithParameters testWithParameters)
            throws InitializationError {
        return new MixinAwareRunnerWithParameters(testWithParameters);
    }

    private static final class MixinAwareRunnerWithParameters extends BlockJUnit4ClassRunnerWithParameters {

        private MixinAwareRunnerWithParameters(final TestWithParameters test) throws InitializationError {
            super(test);
        }

        @Override
        protected List<FrameworkMethod> computeTestMethods() {
            Stream<FrameworkMethod> classMethods = super.computeTestMethods().stream();

            Stream<FrameworkMethod> interfaceMethods = Stream.of(getTestClass().getJavaClass().getInterfaces())
                    .flatMap(c -> Stream.of(c.getMethods()))
                    .filter(m -> m.getAnnotation(Test.class) != null && m.isDefault())
                    .map(FrameworkMethod::new); // in case interface is non-public also call method.setAccessible(true)

            return concat(classMethods, interfaceMethods).collect(toList());
        }
    }
}
