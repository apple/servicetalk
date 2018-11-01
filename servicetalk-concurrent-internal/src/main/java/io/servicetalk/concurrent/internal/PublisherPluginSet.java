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

import org.reactivestreams.Subscriber;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static io.servicetalk.concurrent.internal.ArrayUtils.indexOf;
import static java.lang.System.arraycopy;
import static java.util.Arrays.copyOf;
import static java.util.Objects.requireNonNull;

final class PublisherPluginSet implements PublisherPlugin {
    private final AtomicReference<ProgressiveSet> setRef = new AtomicReference<>(EmptyPublisherPluginSet.INSTANCE);

    boolean add(PublisherPlugin plugin) {
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

    boolean remove(PublisherPlugin plugin) {
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
        setRef.set(EmptyPublisherPluginSet.INSTANCE);
    }

    @Override
    public void handleSubscribe(final Subscriber<?> subscriber,
                                final SignalOffloader offloader,
                                final BiConsumer<? super Subscriber, SignalOffloader> handleSubscribe) {
        setRef.get().handleSubscribe(subscriber, offloader, handleSubscribe);
    }

    private interface ProgressiveSet extends PublisherPlugin {
        ProgressiveSet add(PublisherPlugin plugin);

        ProgressiveSet remove(PublisherPlugin plugin);
    }

    private static final class EmptyPublisherPluginSet implements ProgressiveSet {
        static final ProgressiveSet INSTANCE = new EmptyPublisherPluginSet();

        private EmptyPublisherPluginSet() {
            // singleton
        }

        @Override
        public ProgressiveSet add(final PublisherPlugin plugin) {
            return new OnePublisherPluginSet(plugin);
        }

        @Override
        public ProgressiveSet remove(final PublisherPlugin plugin) {
            return this;
        }

        @Override
        public void handleSubscribe(final Subscriber<?> subscriber,
                                    final SignalOffloader offloader,
                                    final BiConsumer<? super Subscriber, SignalOffloader> handleSubscribe) {
            handleSubscribe.accept(subscriber, offloader);
        }
    }

    private static final class OnePublisherPluginSet implements ProgressiveSet {
        private final PublisherPlugin plugin;

        OnePublisherPluginSet(PublisherPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public ProgressiveSet add(final PublisherPlugin plugin) {
            return this.plugin.equals(plugin) ? this : new TwoPublisherPluginSet(this.plugin, plugin);
        }

        @Override
        public ProgressiveSet remove(final PublisherPlugin plugin) {
            return this.plugin.equals(plugin) ? EmptyPublisherPluginSet.INSTANCE : this;
        }

        @Override
        public void handleSubscribe(final Subscriber<?> subscriber,
                                    final SignalOffloader offloader,
                                    final BiConsumer<? super Subscriber, SignalOffloader> handleSubscribe) {
            plugin.handleSubscribe(subscriber, offloader, handleSubscribe);
        }
    }

    private static final class TwoPublisherPluginSet implements ProgressiveSet {
        private final PublisherPlugin first;
        private final PublisherPlugin second;

        TwoPublisherPluginSet(PublisherPlugin first, PublisherPlugin second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public ProgressiveSet add(final PublisherPlugin plugin) {
            return first.equals(plugin) || second.equals(plugin) ? this :
                    new ThreeOrMorePublisherPluginSet(first, second, plugin);
        }

        @Override
        public ProgressiveSet remove(final PublisherPlugin plugin) {
            if (first.equals(plugin)) {
                return new OnePublisherPluginSet(second);
            } else if (second.equals(plugin)) {
                return new OnePublisherPluginSet(first);
            }
            return this;
        }

        @Override
        public void handleSubscribe(final Subscriber<?> subscriber,
                                    final SignalOffloader offloader,
                                    final BiConsumer<? super Subscriber, SignalOffloader> handleSubscribe) {
            second.handleSubscribe(subscriber, offloader,
                    (subscriber2, offloader2) -> first.handleSubscribe(subscriber2, offloader2, handleSubscribe));
        }
    }

    private static final class ThreeOrMorePublisherPluginSet implements ProgressiveSet {
        private final PublisherPlugin[] plugins;
        private final TriConsumer<? super Subscriber, SignalOffloader,
                BiConsumer<? super Subscriber, SignalOffloader>> doHandleSubscribe;

        ThreeOrMorePublisherPluginSet(PublisherPlugin... plugins) {
            this.plugins = plugins;
            final PublisherPlugin firstPlugin = plugins[0];
            TriConsumer<? super Subscriber, SignalOffloader, BiConsumer<? super Subscriber, SignalOffloader>>
                    doHandleSubscribe = firstPlugin::handleSubscribe;
            for (int i = plugins.length - 1; i > 0; --i) {
                TriConsumer<? super Subscriber, SignalOffloader, BiConsumer<? super Subscriber, SignalOffloader>>
                        previousConsumer = doHandleSubscribe;
                final PublisherPlugin currentPlugin = plugins[i];
                doHandleSubscribe = (subscriber, offloader, consumer) ->
                        currentPlugin.handleSubscribe(subscriber, offloader,
                            (subscriber2, offloader2) -> previousConsumer.accept(subscriber2, offloader2, consumer));
            }
            this.doHandleSubscribe = doHandleSubscribe;
        }

        @Override
        public ProgressiveSet add(final PublisherPlugin plugin) {
            int i = indexOf(plugin, plugins);
            if (i >= 0) {
                return this;
            }
            PublisherPlugin[] newArray = copyOf(plugins, plugins.length + 1);
            newArray[plugins.length] = plugin;
            return new ThreeOrMorePublisherPluginSet(newArray);
        }

        @Override
        public ProgressiveSet remove(final PublisherPlugin plugin) {
            int i = indexOf(plugin, plugins);
            if (i < 0) {
                return this;
            }
            if (plugins.length == 3) {
                switch (i) {
                    case 0:
                        return new TwoPublisherPluginSet(plugins[1], plugins[2]);
                    case 1:
                        return new TwoPublisherPluginSet(plugins[0], plugins[2]);
                    case 2:
                        return new TwoPublisherPluginSet(plugins[0], plugins[1]);
                    default:
                        throw new RuntimeException("programming error. i: " + i);
                }
            }
            PublisherPlugin[] newArray = new PublisherPlugin[plugins.length - 1];
            arraycopy(plugins, 0, newArray, 0, i);
            arraycopy(plugins, i + 1, newArray, i, plugins.length - i - 1);
            return new ThreeOrMorePublisherPluginSet(newArray);
        }

        @Override
        public void handleSubscribe(final Subscriber<?> subscriber,
                                    final SignalOffloader offloader,
                                    final BiConsumer<? super Subscriber, SignalOffloader> handleSubscribe) {
            doHandleSubscribe.accept(subscriber, offloader, handleSubscribe);
        }
    }
}
