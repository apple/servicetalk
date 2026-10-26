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

import io.servicetalk.log4j2.mdc.utils.LoggerStringWriter;
import io.servicetalk.transport.netty.internal.ExpectContinueTimeout;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static io.servicetalk.http.netty.HttpProtocolConfigs.h1;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

class HttpClientConfigTest {

    @Test
    void expectContinueTimeoutDefaultsToThreeSeconds() {
        assertThat(new HttpClientConfig().asReadOnly(null).expectContinueTimeout().timeoutNanos(),
                is(SECONDS.toNanos(3)));
    }

    @Test
    void expectContinueTimeoutZeroIsStored() {
        final HttpClientConfig config = new HttpClientConfig();
        config.expectContinueTimeoutNanos(0);
        assertThat(config.asReadOnly(null).expectContinueTimeout().timeoutNanos(), is(0L));
    }

    @Test
    void expectContinueTimeoutReachesReadOnlyConfig() {
        final HttpClientConfig config = new HttpClientConfig();
        config.expectContinueTimeoutNanos(SECONDS.toNanos(7));
        assertThat(config.asReadOnly(null).expectContinueTimeout().timeoutNanos(), is(SECONDS.toNanos(7)));
    }

    @Test
    void expectContinueTimeoutSurvivesCopy() {
        final HttpClientConfig config = new HttpClientConfig();
        config.expectContinueTimeoutNanos(SECONDS.toNanos(7));
        assertThat(new HttpClientConfig(config).asReadOnly(null).expectContinueTimeout().timeoutNanos(),
                is(SECONDS.toNanos(7)));
    }

    @Test
    void expectContinueTimeoutIsSharedWithinReadOnlyConfig() {
        final ReadOnlyHttpClientConfig readOnly = new HttpClientConfig().asReadOnly(null);
        assertThat(readOnly.expectContinueTimeout(), is(sameInstance(readOnly.expectContinueTimeout())));
    }

    @Test
    void expectContinueTimeoutIsNotSharedAcrossReadOnlyConfigs() {
        final HttpClientConfig config = new HttpClientConfig();
        assertThat(config.asReadOnly(null).expectContinueTimeout(),
                is(not(sameInstance(config.asReadOnly(null).expectContinueTimeout()))));
        assertThat(new HttpClientConfig().asReadOnly(null).expectContinueTimeout(),
                is(not(sameInstance(new HttpClientConfig().asReadOnly(null).expectContinueTimeout()))));
    }

    @Test
    void h1ExpectContinueTimeoutMatchesSharedTimeoutWithoutPipelining() {
        final HttpClientConfig config = new HttpClientConfig();
        config.expectContinueTimeoutNanos(SECONDS.toNanos(7));
        final ReadOnlyHttpClientConfig readOnly = config.asReadOnly(null);
        assertThat(readOnly.h1ExpectContinueTimeout(), is(sameInstance(readOnly.expectContinueTimeout())));
        assertThat(readOnly.h1ExpectContinueTimeout().timeoutNanos(), is(SECONDS.toNanos(7)));
    }

    @Test
    void h1ExpectContinueTimeoutIsDefaultWithoutPipelining() {
        assertThat(new HttpClientConfig().asReadOnly(null).h1ExpectContinueTimeout().timeoutNanos(),
                is(SECONDS.toNanos(3)));
    }

    @Test
    void h1ExpectContinueTimeoutIsNoneWithPipeliningWhileSharedTimeoutIsKept() {
        final HttpClientConfig config = pipelinedConfig();
        config.expectContinueTimeoutNanos(SECONDS.toNanos(7));
        final ReadOnlyHttpClientConfig readOnly = config.asReadOnly(null);
        assertThat(readOnly.h1ExpectContinueTimeout(), is(sameInstance(ExpectContinueTimeout.NONE)));
        assertThat(readOnly.h1ExpectContinueTimeout().timeoutNanos(), is(0L));
        assertThat(readOnly.expectContinueTimeout().timeoutNanos(), is(SECONDS.toNanos(7)));
    }

    @Test
    void h1ExpectContinueTimeoutIsNoneWithPipeliningAndDefaultTimeout() {
        final ReadOnlyHttpClientConfig readOnly = pipelinedConfig().asReadOnly(null);
        assertThat(readOnly.h1ExpectContinueTimeout().timeoutNanos(), is(0L));
        assertThat(readOnly.expectContinueTimeout().timeoutNanos(), is(SECONDS.toNanos(3)));
    }

    @Test
    void pipeliningStateSurvivesCopy() {
        final HttpClientConfig config = pipelinedConfig();
        config.expectContinueTimeoutNanos(SECONDS.toNanos(7));
        final ReadOnlyHttpClientConfig readOnly = new HttpClientConfig(config).asReadOnly(null);
        assertThat(readOnly.h1ExpectContinueTimeout().timeoutNanos(), is(0L));
        assertThat(readOnly.expectContinueTimeout().timeoutNanos(), is(SECONDS.toNanos(7)));
    }

    @Test
    void warnsOncePerReadOnlyConfigWhenExplicitWithPipelining() {
        final HttpClientConfig config = pipelinedConfig();
        config.expectContinueTimeoutNanos(SECONDS.toNanos(7));
        assertThat(warnLinesFrom(config::asReadOnly), hasSize(1));
        assertThat(warnLinesFrom(config::asReadOnly), hasSize(1));
    }

    @Test
    void warnMentionsBothOptions() {
        final HttpClientConfig config = pipelinedConfig();
        config.expectContinueTimeoutNanos(SECONDS.toNanos(7));
        final String line = warnLinesFrom(config::asReadOnly).get(0);
        assertThat(line, containsString("[WARN ]"));
        assertThat(line, containsString("expectContinueTimeout"));
        assertThat(line, containsString("maxPipelinedRequests"));
        assertThat(line, containsString("keeps waiting for the server's response"));
    }

    @Test
    void warnsAfterCopyOfExplicitConfigWithPipelining() {
        final HttpClientConfig config = pipelinedConfig();
        config.expectContinueTimeoutNanos(SECONDS.toNanos(7));
        assertThat(warnLinesFrom(owner -> new HttpClientConfig(config).asReadOnly(owner)), hasSize(1));
    }

    @Test
    void doesNotWarnWhenTimeoutIsDefaultWithPipelining() {
        assertThat(warnLinesFrom(pipelinedConfig()::asReadOnly), is(empty()));
    }

    @Test
    void doesNotWarnWithoutPipelining() {
        final HttpClientConfig config = new HttpClientConfig();
        config.expectContinueTimeoutNanos(SECONDS.toNanos(7));
        assertThat(warnLinesFrom(config::asReadOnly), is(empty()));
    }

    @Test
    void doesNotWarnWhenExplicitTimeoutIsZeroWithPipelining() {
        final HttpClientConfig config = pipelinedConfig();
        config.expectContinueTimeoutNanos(0);
        assertThat(warnLinesFrom(config::asReadOnly), is(empty()));
    }

    private static HttpClientConfig pipelinedConfig() {
        final HttpClientConfig config = new HttpClientConfig();
        config.protocolConfigs().protocols(h1().maxPipelinedRequests(2).build());
        return config;
    }

    // The log appender is JVM-wide and test classes run in parallel; keep only lines logged by this thread.
    private static List<String> warnLinesFrom(final Consumer<Object> action) {
        final LoggerStringWriter loggerStringWriter = new LoggerStringWriter();
        loggerStringWriter.reset();
        try {
            action.accept(null);
            final String thread = Thread.currentThread().getName();
            final List<String> lines = new ArrayList<>();
            for (final String line : loggerStringWriter.accumulated().split("\\R")) {
                if (line.contains("maxPipelinedRequests is greater than 1") && line.contains(thread)) {
                    lines.add(line);
                }
            }
            return lines;
        } finally {
            loggerStringWriter.remove();
        }
    }
}
