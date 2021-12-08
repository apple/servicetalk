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

import io.servicetalk.transport.api.ExecutionStrategyInfluencer;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * A chain of {@link ExecutionStrategyInfluencer}.
 *
 * @deprecated Merge {@link io.servicetalk.transport.api.ExecutionStrategy} directly instead.
 */
@Deprecated
public final class StrategyInfluencerChainBuilder implements ExecutionStrategyInfluencer<HttpExecutionStrategy> {

    private final Deque<ExecutionStrategyInfluencer<?>> influencers;

    /**
     * Creates a new instance.
     */
    public StrategyInfluencerChainBuilder() {
        influencers = new LinkedList<>();
    }

    /**
     * Creates a new instance.
     *
     * @param influencers {@link List} of {@link ExecutionStrategyInfluencer}s.
     */
    private StrategyInfluencerChainBuilder(Deque<ExecutionStrategyInfluencer<?>> influencers) {
        this.influencers = new LinkedList<>(influencers);
    }

    /**
     * Adds the passed {@link ExecutionStrategyInfluencer} to the head of this chain.
     *
     * @param influencer {@link HttpExecutionStrategyInfluencer} to add.
     */
    public void prepend(ExecutionStrategyInfluencer<?> influencer) {
        influencers.addFirst(requireNonNull(influencer));
    }

    /**
     * Adds the passed {@link HttpExecutionStrategyInfluencer} to the head of this chain.
     *
     * @param influencer {@link HttpExecutionStrategyInfluencer} to add.
     */
    public void prepend(HttpExecutionStrategyInfluencer influencer) {
        prepend((ExecutionStrategyInfluencer<?>) influencer);
    }

    /**
     * If the passed {@code mayBeInfluencer} is an {@link HttpExecutionStrategyInfluencer} then add it to the head of
     * this chain.
     *
     * @param mayBeInfluencer An object which may be an {@link ExecutionStrategyInfluencer}.
     * @return {@code true} if the passed {@code mayBeInfluencer} was added to the chain.
     */
    public boolean prependIfInfluencer(Object mayBeInfluencer) {
        if (mayBeInfluencer instanceof ExecutionStrategyInfluencer) {
            prepend((ExecutionStrategyInfluencer<?>) mayBeInfluencer);
            return true;
        }
        return false;
    }

    /**
     * Append another {@link ExecutionStrategyInfluencer} to this chain.
     *
     * @param next {@link ExecutionStrategyInfluencer} to append.
     */
    public void append(ExecutionStrategyInfluencer<?> next) {
        influencers.addLast(requireNonNull(next));
    }

    /**
     * Append another {@link HttpExecutionStrategyInfluencer} to this chain.
     *
     * @param next {@link HttpExecutionStrategyInfluencer} to append.
     */
    public void append(HttpExecutionStrategyInfluencer next) {
        append((ExecutionStrategyInfluencer<?>) next);
    }

    /**
     * If the passed {@code mayBeInfluencer} is an {@link ExecutionStrategyInfluencer} then add it to this chain.
     *
     * @param mayBeInfluencer A reference which may be an {@link ExecutionStrategyInfluencer}.
     * @return {@code true} if the passed {@code mayBeInfluencer} was added to the chain.
     */
    public boolean appendIfInfluencer(Object mayBeInfluencer) {
        if (mayBeInfluencer instanceof ExecutionStrategyInfluencer) {
            append((ExecutionStrategyInfluencer<?>) mayBeInfluencer);
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
     * {@link HttpExecutionStrategyInfluencer#requiredOffloads()} on the returned
     * {@link HttpExecutionStrategyInfluencer} will invoke the method on the entire chain before returning.
     *
     * @param transportStrategy {@link HttpExecutionStrategy} for the transport, typically specified by the user in the
     * builders.
     * @return {@link HttpExecutionStrategyInfluencer} which is the head of the influencer chain.
     */
    public HttpExecutionStrategyInfluencer build(HttpExecutionStrategy transportStrategy) {
        requireNonNull(transportStrategy);
        HttpExecutionStrategy influenced = influencers.isEmpty() ?
                transportStrategy :
                transportStrategy.merge(requiredOffloads());
        return newInfluencer(influenced);
    }

    /**
     * Builds this chain and returns a computed {@link HttpExecutionStrategyInfluencer} that reflects the strategy of
     * the entire chain.
     *
     * @return {@link HttpExecutionStrategyInfluencer} which is the head of the influencer chain.
     */
    public HttpExecutionStrategyInfluencer build() {
        HttpExecutionStrategy strategy = influencers.stream()
                .map(ExecutionStrategyInfluencer::requiredOffloads)
                .map(HttpExecutionStrategy::from)
                .reduce(HttpExecutionStrategies.offloadNone(), HttpExecutionStrategy::merge);
        return newInfluencer(strategy);
    }

    /**
     * Builds this chain and returns computed {@link HttpExecutionStrategy} for the entire chain.
     *
     * @return computed {@link HttpExecutionStrategy} of the influencer chain.
     */
    @Override
    public HttpExecutionStrategy requiredOffloads() {
        return influencers.stream()
                .map(ExecutionStrategyInfluencer::requiredOffloads)
                .map(HttpExecutionStrategy::from)
                .reduce(HttpExecutionStrategy::merge)
                .orElse(HttpExecutionStrategies.offloadNone());
    }

    /**
     * Creates an instance of {@link HttpExecutionStrategyInfluencer} that requires the provided strategy.
     *
     * @param requiredStrategy The required strategy of the influencer to be created.
     * @return an instance of {@link HttpExecutionStrategyInfluencer} that requires the provided strategy.
     */
    private static HttpExecutionStrategyInfluencer newInfluencer(HttpExecutionStrategy requiredStrategy) {
        return new HttpExecutionStrategyInfluencer() {

            @Override
            public HttpExecutionStrategy requiredOffloads() {
                return requiredStrategy;
            }
        };
    }
}
