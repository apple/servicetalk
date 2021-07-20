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

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * A chain of {@link HttpExecutionStrategyInfluencer}.
 */
public final class StrategyInfluencerChainBuilder {

    private static final HttpExecutionStrategyInfluencer NO_INFLUENCE = other -> other;

    private final Deque<HttpExecutionStrategyInfluencer> influencers;

    /**
     * Creates a new instance.
     */
    public StrategyInfluencerChainBuilder() {
        influencers = new LinkedList<>();
    }

    /**
     * Creates a new instance.
     *
     * @param influencers {@link List} of {@link HttpExecutionStrategyInfluencer}s.
     */
    private StrategyInfluencerChainBuilder(Deque<HttpExecutionStrategyInfluencer> influencers) {
        this.influencers = new LinkedList<>(influencers);
    }

    /**
     * Adds the passed {@link HttpExecutionStrategyInfluencer} to the head of this chain.
     *
     * @param influencer {@link HttpExecutionStrategyInfluencer} to add.
     */
    public void prepend(HttpExecutionStrategyInfluencer influencer) {
        influencers.addFirst(requireNonNull(influencer));
    }

    /**
     * If the passed {@code mayBeInfluencer} is an {@link HttpExecutionStrategyInfluencer} then add it to the head of
     * this chain.
     *
     * @param mayBeInfluencer An object which may be an {@link HttpExecutionStrategyInfluencer}.
     * @return {@code true} if the passed {@code mayBeInfluencer} was added to the chain.
     */
    public boolean prependIfInfluencer(Object mayBeInfluencer) {
        if (mayBeInfluencer instanceof HttpExecutionStrategyInfluencer) {
            prepend((HttpExecutionStrategyInfluencer) mayBeInfluencer);
            return true;
        }
        return false;
    }

    /**
     * Append another {@link HttpExecutionStrategyInfluencer} to this chain.
     *
     * @param next {@link HttpExecutionStrategyInfluencer} to append.
     */
    public void append(HttpExecutionStrategyInfluencer next) {
        influencers.addLast(requireNonNull(next));
    }

    /**
     * If the passed {@code mayBeInfluencer} is an {@link HttpExecutionStrategyInfluencer} then add it to this chain.
     *
     * @param mayBeInfluencer An object which may be an {@link HttpExecutionStrategyInfluencer}.
     * @return {@code true} if the passed {@code mayBeInfluencer} was added to the chain.
     */
    public boolean appendIfInfluencer(Object mayBeInfluencer) {
        if (mayBeInfluencer instanceof HttpExecutionStrategyInfluencer) {
            append((HttpExecutionStrategyInfluencer) mayBeInfluencer);
            return true;
        }
        return false;
    }

    /**
     * Creates a deep copy of this {@link StrategyInfluencerChainBuilder}.
     *
     * @return A new {@link StrategyInfluencerChainBuilder} containing all the influencers added to this
     * {@link StrategyInfluencerChainBuilder}.
     */
    public StrategyInfluencerChainBuilder copy() {
        return new StrategyInfluencerChainBuilder(influencers);
    }

    /**
     * Builds this chain and returns the head {@link HttpExecutionStrategyInfluencer} for the chain. Invoking
     * {@link HttpExecutionStrategyInfluencer#influenceStrategy(HttpExecutionStrategy)} on the returned
     * {@link HttpExecutionStrategyInfluencer} will invoke the method on the entire chain before returning.
     *
     * @param transportStrategy {@link HttpExecutionStrategy} for the transport, typically specified by the user in the
     * builders.
     * @return {@link HttpExecutionStrategyInfluencer} which is the head of the influencer chain.
     */
    public HttpExecutionStrategyInfluencer build(HttpExecutionStrategy transportStrategy) {
        requireNonNull(transportStrategy);
        HttpExecutionStrategyInfluencer influencer = build0();
        return strategy -> transportStrategy.merge(influencer.influenceStrategy(strategy));
    }

    /**
     * Builds this chain and returns the head {@link HttpExecutionStrategyInfluencer} for the chain. Invoking
     * {@link HttpExecutionStrategyInfluencer#influenceStrategy(HttpExecutionStrategy)} on the returned
     * {@link HttpExecutionStrategyInfluencer} will invoke the method on the entire chain before returning.
     *
     * @return {@link HttpExecutionStrategyInfluencer} which is the head of the influencer chain.
     */
    public HttpExecutionStrategyInfluencer build() {
        return build0();
    }

    private HttpExecutionStrategyInfluencer build0() {
        if (influencers.isEmpty()) {
            return NO_INFLUENCE;
        }
        HttpExecutionStrategyInfluencer head = NO_INFLUENCE;
        for (HttpExecutionStrategyInfluencer influencer : influencers) {
            if (head == NO_INFLUENCE) {
                head = influencer;
            } else {
                HttpExecutionStrategyInfluencer prev = head;
                head = strategy -> influencer.influenceStrategy(prev.influenceStrategy(strategy));
            }
        }
        return head;
    }
}
