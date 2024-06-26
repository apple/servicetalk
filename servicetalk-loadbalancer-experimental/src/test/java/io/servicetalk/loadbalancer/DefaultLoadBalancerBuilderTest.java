/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.loadbalancer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.startsWith;

final class DefaultLoadBalancerBuilderTest {

    @Test
    void isTheResultOfLoadBalancersBuilder() {
        LoadBalancerBuilder<String, TestLoadBalancedConnection> builder = LoadBalancers.builder("builder_id");
        assertThat(builder, instanceOf(DefaultLoadBalancerBuilder.class));
    }

    @Test
    void toStringWorks() {
        DefaultLoadBalancerBuilder<String, TestLoadBalancedConnection> builder =
                new DefaultLoadBalancerBuilder<>("builder_id");
        // Make sure the `.toString()` method doesn't throw due to being recursive or something similar.
        assertThat(builder.build().toString(), startsWith(
        "DefaultLoadBalancerFactory{" +
                "id='builder_id', "));
    }
}
