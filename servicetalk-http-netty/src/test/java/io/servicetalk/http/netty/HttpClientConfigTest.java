/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpClientConfigTest {

    @Test
    void expectContinueTimeoutDefaultsToOneSecond() {
        assertThat(new HttpClientConfig().asReadOnly(null).expectContinueTimeout(), is(ofSeconds(1)));
    }

    @Test
    void expectContinueTimeoutZeroIsStored() {
        final HttpClientConfig config = new HttpClientConfig();
        config.expectContinueTimeout(Duration.ZERO);
        assertThat(config.asReadOnly(null).expectContinueTimeout(), is(Duration.ZERO));
    }

    @Test
    void expectContinueTimeoutRejectsNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> new HttpClientConfig().expectContinueTimeout(ofMillis(-1)));
    }

    @Test
    void expectContinueTimeoutRejectsNull() {
        assertThrows(NullPointerException.class, () -> new HttpClientConfig().expectContinueTimeout(null));
    }

    @Test
    void expectContinueTimeoutSurvivesCopy() {
        final HttpClientConfig config = new HttpClientConfig();
        config.expectContinueTimeout(ofSeconds(7));
        assertThat(new HttpClientConfig(config).asReadOnly(null).expectContinueTimeout(), is(ofSeconds(7)));
    }
}
