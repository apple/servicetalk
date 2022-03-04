/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

import org.junit.jupiter.api.Test;

import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadAll;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

class HttpExecutionStrategyTest {

    private static final HttpExecutionStrategy MAGIC_REQUIRED_STRATEGY = new HttpExecutionStrategy() {

        @Override
        public boolean isCloseOffloaded() {
            return false;
        }

        @Override
        public boolean isMetadataReceiveOffloaded() {
            return false;
        }

        @Override
        public boolean isDataReceiveOffloaded() {
            return false;
        }

        @Override
        public boolean isSendOffloaded() {
            return false;
        }

        @Override
        public boolean isEventOffloaded() {
            return false;
        }

        @Override
        public HttpExecutionStrategy merge(final HttpExecutionStrategy other) {
            return this;
        }
    };

    private static final HttpExecutionStrategy MAGIC_INFLUENCE_STRATEGY = new HttpExecutionStrategy() {

        @Override
        public boolean isCloseOffloaded() {
            return false;
        }

        @Override
        public boolean isMetadataReceiveOffloaded() {
            return false;
        }

        @Override
        public boolean isDataReceiveOffloaded() {
            return false;
        }

        @Override
        public boolean isSendOffloaded() {
            return false;
        }

        @Override
        public boolean isEventOffloaded() {
            return false;
        }

        @Override
        public HttpExecutionStrategy merge(final HttpExecutionStrategy other) {
            return this;
        }
    };

    @Test
    void bothDefaults() {
        class BothDefaults implements HttpExecutionStrategyInfluencer {
        }

        BothDefaults bothDefaults = new BothDefaults();
        assertThat(bothDefaults.requiredOffloads(), sameInstance(offloadAll()));
        assertThat(bothDefaults.influenceStrategy(defaultStrategy()), sameInstance(offloadAll()));
    }

    @Test
    void onlyRequiredOffloadsImplemented() {
        class RequiredOnly implements HttpExecutionStrategyInfluencer {
            @Override
            public HttpExecutionStrategy requiredOffloads() {
                return MAGIC_REQUIRED_STRATEGY;
            }
        }

        RequiredOnly requiredOnly = new RequiredOnly();
        assertThat(requiredOnly.requiredOffloads(), sameInstance(MAGIC_REQUIRED_STRATEGY));
    }

    private interface MyOwnDefaultRequiredOffloads extends HttpExecutionStrategyInfluencer {
        @Override
        default HttpExecutionStrategy requiredOffloads() {
            return MAGIC_REQUIRED_STRATEGY;
        }
    }

    @Test
    void requiredOffloadsSubInterfaceDefault() {
        class RequiredDefault implements MyOwnDefaultRequiredOffloads { }

        RequiredDefault requiredOnly = new RequiredDefault();
        assertThat(requiredOnly.requiredOffloads(), sameInstance(MAGIC_REQUIRED_STRATEGY));
    }

    @Test
    void onlyInfluenceStrategyImplemented() {
        class InfluenceOnly implements HttpExecutionStrategyInfluencer {
            @Override
            public HttpExecutionStrategy influenceStrategy(HttpExecutionStrategy strategy) {
                return MAGIC_INFLUENCE_STRATEGY;
            }
        }

        InfluenceOnly influenceOnly = new InfluenceOnly();
        assertThat(influenceOnly.requiredOffloads(), sameInstance(MAGIC_INFLUENCE_STRATEGY));
        assertThat(influenceOnly.influenceStrategy(defaultStrategy()), sameInstance(MAGIC_INFLUENCE_STRATEGY));
    }

    @Test
    void onlyNoInfluenceStrategyImplemented() {
        class NoInfluence implements HttpExecutionStrategyInfluencer {
            @Override
            public HttpExecutionStrategy influenceStrategy(HttpExecutionStrategy strategy) {
                return strategy;
            }
        }

        NoInfluence noInfluence = new NoInfluence();
        assertThat(noInfluence.requiredOffloads(), sameInstance(HttpExecutionStrategies.offloadNone()));
        assertThat(noInfluence.influenceStrategy(defaultStrategy()), sameInstance(defaultStrategy()));
    }

    @Test
    void onlyInfluencingInfluenceStrategyImplemented() {
        final HttpExecutionStrategy offloadSend = HttpExecutionStrategies.customStrategyBuilder().offloadSend().build();
        class Influence implements HttpExecutionStrategyInfluencer {
            @Override
            public HttpExecutionStrategy influenceStrategy(HttpExecutionStrategy strategy) {
                return offloadSend.merge(strategy);
            }
        }

        Influence influence = new Influence();
        assertThat(influence.requiredOffloads(), is(offloadSend));
        assertThat(influence.influenceStrategy(offloadNone()), is(offloadSend));
        assertThat(influence.influenceStrategy(offloadSend), is(offloadSend));
    }

    @Test
    void bothRequiredOffloadsAndInfluenceStrategyImplemented() {
        class BothImplemented implements HttpExecutionStrategyInfluencer {
            @Override
            public HttpExecutionStrategy requiredOffloads() {
                return MAGIC_REQUIRED_STRATEGY;
            }

            @Override
            public HttpExecutionStrategy influenceStrategy(HttpExecutionStrategy strategy) {
                return MAGIC_INFLUENCE_STRATEGY;
            }
        }

        BothImplemented bothImplemented = new BothImplemented();
        assertThat(bothImplemented.requiredOffloads(), sameInstance(MAGIC_REQUIRED_STRATEGY));
        assertThat(bothImplemented.influenceStrategy(defaultStrategy()), sameInstance(MAGIC_INFLUENCE_STRATEGY));
    }
}
