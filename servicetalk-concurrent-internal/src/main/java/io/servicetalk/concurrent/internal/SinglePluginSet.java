/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.internal;

import io.servicetalk.concurrent.Executor;
import io.servicetalk.concurrent.Single;
import io.servicetalk.concurrent.Single.Subscriber;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static io.servicetalk.concurrent.internal.ArrayUtils.indexOf;
import static java.lang.System.arraycopy;
import static java.util.Arrays.copyOf;
import static java.util.Objects.requireNonNull;

final class SinglePluginSet implements SinglePlugin {
    private final AtomicReference<ProgressiveSet> setRef = new AtomicReference<>(EmptySinglePluginSet.INSTANCE);

    boolean add(SinglePlugin plugin) {
        requireNonNull(plugin);
        for (;;) {
            ProgressiveSet set = setRef.get();
            ProgressiveSet afterAddSet = set.add(plugin);
            if (set == afterAddSet) {
                return false;
            } else if (setRef.compareAndSet(set, afterAddSet)) {
                return true;
            }
        }
    }

    boolean remove(SinglePlugin plugin) {
        for (;;) {
            ProgressiveSet set = setRef.get();
            ProgressiveSet afterRemoveSet = set.remove(plugin);
            if (set == afterRemoveSet) {
                return false;
            } else if (setRef.compareAndSet(set, afterRemoveSet)) {
                return true;
            }
        }
    }

    void clear() {
        setRef.set(EmptySinglePluginSet.INSTANCE);
    }

    private interface ProgressiveSet extends SinglePlugin {
        ProgressiveSet add(SinglePlugin plugin);

        ProgressiveSet remove(SinglePlugin plugin);
    }

    @Override
    public void handleSubscribe(Subscriber<?> subscriber, SignalOffloader offloader,
                                BiConsumer<? super Subscriber, SignalOffloader> handleSubscribe) {
        setRef.get().handleSubscribe(subscriber, offloader, handleSubscribe);
    }

    @Override
    public <X> CompletionStage<X> toCompletionStage(Single<X> single, Executor executor,
                                                    BiFunction<Single<X>, Executor, CompletionStage<X>> completionStageFactory) {
        return setRef.get().toCompletionStage(single, executor, completionStageFactory);
    }

    @Override
    public <X> CompletionStage<X> fromCompletionStage(final CompletionStage<X> completionStage) {
        return setRef.get().fromCompletionStage(completionStage);
    }

    private static final class EmptySinglePluginSet implements ProgressiveSet {
        static final ProgressiveSet INSTANCE = new EmptySinglePluginSet();

        private EmptySinglePluginSet() {
            // singleton
        }

        @Override
        public void handleSubscribe(Subscriber<?> subscriber, SignalOffloader offloader,
                                    BiConsumer<? super Subscriber, SignalOffloader> handleSubscribe) {
            handleSubscribe.accept(subscriber, offloader);
        }

        @Override
        public <X> CompletionStage<X> toCompletionStage(Single<X> single, Executor executor,
                                                        BiFunction<Single<X>, Executor, CompletionStage<X>> completionStageFactory) {
            return completionStageFactory.apply(single, executor);
        }

        @Override
        public <X> CompletionStage<X> fromCompletionStage(final CompletionStage<X> completionStage) {
            return completionStage;
        }

        @Override
        public ProgressiveSet add(final SinglePlugin plugin) {
            return new OneSinglePluginSet(plugin);
        }

        @Override
        public ProgressiveSet remove(final SinglePlugin plugin) {
            return this;
        }
    }

    private static final class OneSinglePluginSet implements ProgressiveSet {
        private final SinglePlugin plugin;

        OneSinglePluginSet(SinglePlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public void handleSubscribe(Subscriber<?> subscriber, SignalOffloader offloader,
                                    BiConsumer<? super Subscriber, SignalOffloader> handleSubscribe) {
            plugin.handleSubscribe(subscriber, offloader, handleSubscribe);
        }

        @Override
        public <X> CompletionStage<X> toCompletionStage(Single<X> single, Executor executor,
                                                        BiFunction<Single<X>, Executor, CompletionStage<X>> completionStageFactory) {
            return plugin.toCompletionStage(single, executor, completionStageFactory);
        }

        @Override
        public <X> CompletionStage<X> fromCompletionStage(final CompletionStage<X> completionStage) {
            return plugin.fromCompletionStage(completionStage);
        }

        @Override
        public ProgressiveSet add(final SinglePlugin plugin) {
            return this.plugin.equals(plugin) ? this : new TwoSinglePluginSet(this.plugin, plugin);
        }

        @Override
        public ProgressiveSet remove(final SinglePlugin plugin) {
            return this.plugin.equals(plugin) ? EmptySinglePluginSet.INSTANCE : this;
        }
    }

    private static final class TwoSinglePluginSet implements ProgressiveSet {
        private final SinglePlugin first;
        private final SinglePlugin second;

        TwoSinglePluginSet(SinglePlugin first, SinglePlugin second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void handleSubscribe(Subscriber<?> subscriber, SignalOffloader offloader,
                                    BiConsumer<? super Subscriber, SignalOffloader> handleSubscribe) {
            second.handleSubscribe(subscriber, offloader,
                    (subscriber2, offloader2) -> first.handleSubscribe(subscriber2, offloader2, handleSubscribe));
        }

        @Override
        public <X> CompletionStage<X> toCompletionStage(Single<X> single, Executor executor,
                                                        BiFunction<Single<X>, Executor, CompletionStage<X>> completionStageFactory) {
            return second.toCompletionStage(single, executor,
                    (single2, executor2) -> first.toCompletionStage(single2, executor2, completionStageFactory));
        }

        @Override
        public <X> CompletionStage<X> fromCompletionStage(final CompletionStage<X> completionStage) {
            return second.fromCompletionStage(first.fromCompletionStage(completionStage));
        }

        @Override
        public ProgressiveSet add(final SinglePlugin plugin) {
            return first.equals(plugin) || second.equals(plugin) ? this :
                    new ThreeOrMoreSinglePluginSet(first, second, plugin);
        }

        @Override
        public ProgressiveSet remove(final SinglePlugin plugin) {
            if (first.equals(plugin)) {
                return new OneSinglePluginSet(second);
            } else if (second.equals(plugin)) {
                return new OneSinglePluginSet(first);
            }
            return this;
        }
    }

    private static final class ThreeOrMoreSinglePluginSet implements ProgressiveSet {
        private final SinglePlugin[] plugins;
        private final TriConsumer<? super Subscriber, SignalOffloader,
                BiConsumer<? super Subscriber, SignalOffloader>> doHandleSubscribe;

        ThreeOrMoreSinglePluginSet(SinglePlugin... plugins) {
            this.plugins = plugins;
            final SinglePlugin firstPlugin = plugins[0];
            TriConsumer<? super Subscriber, SignalOffloader, BiConsumer<? super Subscriber, SignalOffloader>>
                    doHandleSubscribe = firstPlugin::handleSubscribe;
            for (int i = plugins.length - 1; i > 0; --i) {
                TriConsumer<? super Subscriber, SignalOffloader, BiConsumer<? super Subscriber, SignalOffloader>>
                        previousConsumer = doHandleSubscribe;
                final SinglePlugin currentPlugin = plugins[i];
                doHandleSubscribe = (subscriber, offloader, consumer) ->
                        currentPlugin.handleSubscribe(subscriber, offloader,
                            (subscriber2, offloader2) -> previousConsumer.accept(subscriber2, offloader2, consumer));
            }
            this.doHandleSubscribe = doHandleSubscribe;
        }

        @Override
        public void handleSubscribe(Subscriber<?> subscriber, SignalOffloader offloader,
                                    BiConsumer<? super Subscriber, SignalOffloader> handleSubscribe) {
            doHandleSubscribe.accept(subscriber, offloader, handleSubscribe);
        }

        @Override
        public <X> CompletionStage<X> toCompletionStage(Single<X> single, Executor executor,
                                                        BiFunction<Single<X>, Executor, CompletionStage<X>> completionStageFactory) {
            // TODO: the generic types make it so this can't be cached. Is there an alternative way to allow unwrapping
            // the Executor, and wrapping the returned CompletionStage?
            final SinglePlugin firstPlugin = plugins[0];
            TriFunction<Single<X>, Executor, BiFunction<Single<X>, Executor, CompletionStage<X>>, CompletionStage<X>>
                    singleToCompletionStage = firstPlugin::toCompletionStage;
            for (int i = plugins.length - 1; i > 0; --i) {
                TriFunction<Single<X>, Executor,
                        BiFunction<Single<X>, Executor, CompletionStage<X>>, CompletionStage<X>>
                        previousFunction = singleToCompletionStage;
                final SinglePlugin currentPlugin = plugins[i];
                singleToCompletionStage = (single2, executor2, function) ->
                        currentPlugin.toCompletionStage(single2, executor2,
                                (single3, executor3) -> previousFunction.accept(single3, executor3, function));
            }
            return singleToCompletionStage.accept(single, executor, completionStageFactory);
        }

        @Override
        public <X> CompletionStage<X> fromCompletionStage(final CompletionStage<X> completionStage) {
            CompletionStage<X> result = completionStage;
            for (int i = plugins.length - 1; i > 0; --i) {
                result = plugins[i].fromCompletionStage(result);
            }
            return result;
        }

        @Override
        public ProgressiveSet add(final SinglePlugin plugin) {
            int i = indexOf(plugin, plugins);
            if (i >= 0) {
                return this;
            }
            SinglePlugin[] newArray = copyOf(plugins, plugins.length + 1);
            newArray[plugins.length] = plugin;
            return new ThreeOrMoreSinglePluginSet(newArray);
        }

        @Override
        public ProgressiveSet remove(final SinglePlugin plugin) {
            int i = indexOf(plugin, plugins);
            if (i < 0) {
                return this;
            }
            if (plugins.length == 3) {
                switch (i) {
                    case 0:
                        return new TwoSinglePluginSet(plugins[1], plugins[2]);
                    case 1:
                        return new TwoSinglePluginSet(plugins[0], plugins[2]);
                    case 2:
                        return new TwoSinglePluginSet(plugins[0], plugins[1]);
                    default:
                        throw new RuntimeException("programming error. i: " + i);
                }
            }
            SinglePlugin[] newArray = new SinglePlugin[plugins.length - 1];
            arraycopy(plugins, 0, newArray, 0, i);
            arraycopy(plugins, i + 1, newArray, i, plugins.length - i - 1);
            return new ThreeOrMoreSinglePluginSet(newArray);
        }
    }
}
