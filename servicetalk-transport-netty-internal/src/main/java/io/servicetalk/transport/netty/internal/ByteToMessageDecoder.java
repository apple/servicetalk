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

package io.servicetalk.transport.netty.internal;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.handler.codec.ByteToMessageDecoder.Cumulator;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.FixedLengthFrameDecoder;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.StringUtil;

import java.net.SocketAddress;
import java.util.List;
import javax.annotation.Nullable;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.handler.codec.ByteToMessageDecoder.MERGE_CUMULATOR;
import static java.util.Objects.requireNonNull;

/**
 * {@link ChannelInboundHandlerAdapter} which decodes bytes in a stream-like fashion from one {@link ByteBuf} to an
 * other Message type.
 *
 * For example here is an implementation which reads all readable bytes from
 * the input {@link ByteBuf} and create a new {@link ByteBuf}.
 *
 * <pre>
 *     public class SquareDecoder extends {@link ByteToMessageDecoder} {
 *         {@code @Override}
 *         public void decode({@link ChannelHandlerContext} ctx, {@link ByteBuf} in, List&lt;Object&gt; out)
 *                 throws {@link Exception} {
 *             out.add(in.readBytes(in.readableBytes()));
 *         }
 *     }
 * </pre>
 *
 * <h3>Frame detection</h3>
 * <p>
 * Generally frame detection should be handled earlier in the pipeline by adding a
 * {@link DelimiterBasedFrameDecoder}, {@link FixedLengthFrameDecoder}, {@link LengthFieldBasedFrameDecoder},
 * or {@link LineBasedFrameDecoder}.
 * <p>
 * If a custom frame decoder is required, then one needs to be careful when implementing
 * one with {@link ByteToMessageDecoder}. Ensure there are enough bytes in the buffer for a
 * complete frame by checking {@link ByteBuf#readableBytes()}. If there are not enough bytes
 * for a complete frame, return without modifying the reader index to allow more bytes to arrive.
 * <p>
 * To check for complete frames without modifying the reader index, use methods like {@link ByteBuf#getInt(int)}.
 * One <strong>MUST</strong> use the reader index when using methods like {@link ByteBuf#getInt(int)}.
 * For example calling <tt>in.getInt(0)</tt> is assuming the frame starts at the beginning of the buffer, which
 * is not always the case. Use <tt>in.getInt(in.readerIndex())</tt> instead.
 * <h3>Pitfalls</h3>
 * <p>
 * Be aware that sub-classes of {@link ByteToMessageDecoder} <strong>MUST NOT</strong>
 * annotated with {@link Sharable}.
 * <p>
 * Some methods such as {@link ByteBuf#readBytes(int)} will cause a memory leak if the returned buffer
 * is not released or added to the <tt>out</tt> {@link List}. Use derived buffers like {@link ByteBuf#readSlice(int)}
 * to avoid leaking memory.
 */
public abstract class ByteToMessageDecoder extends ChannelInboundHandlerAdapter {
    private static final byte STATE_INIT = 0;
    private static final byte STATE_CALLING_CHILD_DECODE = 1;
    private static final byte STATE_HANDLER_REMOVED_PENDING = 2;

    @Nullable
    private ByteBuf cumulation;
    private Cumulator cumulator = MERGE_CUMULATOR;
    private final CtxWrapper ctxWrapper = new CtxWrapper();
    private boolean decodeWasNull;
    private boolean first;
    /**
     * A bitmask where the bits are defined as
     * <ul>
     *     <li>{@link #STATE_INIT}</li>
     *     <li>{@link #STATE_CALLING_CHILD_DECODE}</li>
     *     <li>{@link #STATE_HANDLER_REMOVED_PENDING}</li>
     * </ul>
     */
    private byte decodeState = STATE_INIT;
    private int discardAfterReads = 16;
    private int numReads;

    /**
     * Create a new instance.
     */
    protected ByteToMessageDecoder() {
        ensureNotSharable();
    }

    /**
     * Set the {@link Cumulator} to use for cumulate the received {@link ByteBuf}s.
     */
    final void setCumulator(Cumulator cumulator) {
        this.cumulator = requireNonNull(cumulator);
    }

    /**
     * Set the number of reads after which {@link ByteBuf#discardSomeReadBytes()} are called and so free up memory.
     * The default is {@code 16}.
     * <p>
     * It is assumed this method is only called in the associated {@link Channel}'s {@link EventLoop} thread, otherwise
     * external synchronization must be provided.
     * @param discardAfterReads The number of calls to {@link ChannelHandlerContext#fireChannelRead(Object)} before
     * attempting to discard bytes from the buffer cumulator.
     */
    public final void setDiscardAfterReads(int discardAfterReads) {
        if (discardAfterReads <= 0) {
            throw new IllegalArgumentException("discardAfterReads must be > 0");
        }
        this.discardAfterReads = discardAfterReads;
    }

    @Override
    public final void handlerRemoved(ChannelHandlerContext ctx) {
        if (decodeState == STATE_CALLING_CHILD_DECODE) {
            decodeState = STATE_HANDLER_REMOVED_PENDING;
            return;
        }
        ByteBuf buf = cumulation;
        if (buf != null) {
            // Directly set this to null so we are sure we not access it in any other method here anymore.
            cumulation = null;
            cumulationReset();

            int readable = buf.readableBytes();
            if (readable > 0) {
                ByteBuf bytes = buf.readBytes(readable);
                buf.release();
                ctx.fireChannelRead(bytes);
            } else {
                buf.release();
            }

            numReads = 0;
            ctx.fireChannelReadComplete();
        }
        handlerRemoved0(ctx);
    }

    /**
     * Gets called after the {@link ByteToMessageDecoder} was removed from the actual context and
     * it doesn't handle events anymore.
     * @param ctx the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder}
     * belongs to
     */
    protected void handlerRemoved0(@SuppressWarnings("unused") ChannelHandlerContext ctx) { }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof ByteBuf) {
            ctxWrapper.setDelegate(ctx);
            final int firedChannelReadCount = ctxWrapper.getFireChannelReadCount();
            try {
                ByteBuf data = (ByteBuf) msg;
                first = cumulation == null;
                if (first) {
                    cumulation = data;
                } else {
                    cumulation = cumulator.cumulate(ctx.alloc(), cumulation, data);
                }
                callDecode(ctxWrapper, cumulation);
            } catch (DecoderException e) {
                throw e;
            } catch (Exception e) {
                throw new DecoderException(e);
            } finally {
                if (cumulation != null && !cumulation.isReadable()) {
                    numReads = 0;
                    releaseCumulation();
                } else if (++numReads >= discardAfterReads) {
                    // We did enough reads already try to discard some bytes so we not risk to see a OOME.
                    // See https://github.com/netty/netty/issues/4275
                    numReads = 0;
                    tryDiscardSomeReadBytes();
                }
                decodeWasNull = firedChannelReadCount == ctxWrapper.getFireChannelReadCount();
                ctxWrapper.resetFireChannelReadCount();
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        numReads = 0;
        tryDiscardSomeReadBytes();
        if (decodeWasNull) {
            decodeWasNull = false;
            if (!ctx.channel().config().isAutoRead()) {
                ctx.read();
            }
        }
        ctx.fireChannelReadComplete();
    }

    private void tryDiscardSomeReadBytes() {
        if (cumulation != null && !first && cumulation.refCnt() == 1) {
            // discard some bytes if possible to make more room in the
            // buffer but only if the refCnt == 1 as otherwise the user may have
            // used slice().retain() or duplicate().retain().
            //
            // See:
            // - https://github.com/netty/netty/issues/2327
            // - https://github.com/netty/netty/issues/1764
            discardSomeReadBytes();
        }
    }

    /**
     * May discard some, all, or none of read bytes depending on its internal implementation to reduce overall memory
     * bandwidth consumption at the cost of potentially additional memory consumption.
     */
    protected void discardSomeReadBytes() {
        assert cumulation != null;
        cumulation.discardSomeReadBytes();
    }

    /**
     * Returns cumulation reader index.
     *
     * @return reader index of cumulation.
     */
    protected final int getCumulationReaderIndex() {
        assert cumulation != null;
        return cumulation.readerIndex();
    }

    /**
     * Resets cumulation.
     */
    protected void cumulationReset() {
    }

    private void releaseCumulation() {
        if (cumulation != null) {
            cumulation.release();
            cumulation = null;
            cumulationReset();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        ctxWrapper.setDelegate(ctx);
        channelInputClosed(ctxWrapper, true);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof ChannelInputShutdownEvent) {
            ctxWrapper.setDelegate(ctx);
            // The decodeLast method is invoked when a channelInactive event is encountered.
            // This method is responsible for ending requests in some situations and must be called
            // when the input has been shutdown.
            channelInputClosed(ctxWrapper, false);
        }
        super.userEventTriggered(ctx, evt);
    }

    private void channelInputClosed(CtxWrapper ctx, boolean callChannelInactive) {
        final int firedChannelReadCount = ctxWrapper.getFireChannelReadCount();
        try {
            channelInputClosed(ctx);
        } catch (DecoderException e) {
            throw e;
        } catch (Exception e) {
            throw new DecoderException(e);
        } finally {
            try {
                releaseCumulation();
                if (firedChannelReadCount != ctxWrapper.getFireChannelReadCount()) {
                    // Something was read, call fireChannelReadComplete()
                    ctx.fireChannelReadComplete();
                }
                if (callChannelInactive) {
                    ctx.fireChannelInactive();
                }
            } finally {
                ctxWrapper.resetFireChannelReadCount();
            }
        }
    }

    /**
     * Called when the input of the channel was closed which may be because it changed to inactive or because of
     * {@link ChannelInputShutdownEvent}.
     * @param ctx the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     */
    private void channelInputClosed(CtxWrapper ctx) throws Exception {
        if (cumulation != null) {
            callDecode(ctx, cumulation);
            decodeLast(ctx, cumulation);
        } else {
            decodeLast(ctx, EMPTY_BUFFER);
        }
    }

    /**
     * Called once data should be decoded from the given {@link ByteBuf}. This method will call
     * {@link #decode(ChannelHandlerContext, ByteBuf)} as long as decoding should take place.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param in            the {@link ByteBuf} from which to read data
     */
    private void callDecode(CtxWrapper ctx, ByteBuf in) {
        try {
            while (in.isReadable() && !ctx.isRemoved()) {
                int fireChannelReadCount = ctx.getFireChannelReadCount();
                int oldInputLength = in.readableBytes();
                decodeRemovalReentryProtection(ctx, in);
                if (ctx.isRemoved()) {
                    break;
                }
                if (fireChannelReadCount == ctx.getFireChannelReadCount()) {
                    if (oldInputLength == in.readableBytes()) {
                        break;
                    }
                } else if (oldInputLength == in.readableBytes()) {
                    throw new DecoderException(
                            StringUtil.simpleClassName(getClass()) +
                                    ".decode() did not read anything but decoded a message.");
                }
            }
        } catch (DecoderException e) {
            throw e;
        } catch (Exception cause) {
            throw new DecoderException(cause);
        }
    }

    /**
     * Decode the from one {@link ByteBuf} to an other. This method will be called till either the input
     * {@link ByteBuf} has nothing to read when return from this method or till nothing was read from the input
     * {@link ByteBuf}.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param in            the {@link ByteBuf} from which to read data
     * @throws Exception    is thrown if an error occurs
     */
    protected abstract void decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception;

    /**
     * Decode the from one {@link ByteBuf} to an other. This method will be called till either the input
     * {@link ByteBuf} has nothing to read when return from this method or till nothing was read from the input
     * {@link ByteBuf}.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param in            the {@link ByteBuf} from which to read data
     * @throws Exception    is thrown if an error occurs
     */
    private void decodeRemovalReentryProtection(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        decodeState = STATE_CALLING_CHILD_DECODE;
        try {
            decode(ctx, in);
        } finally {
            boolean removePending = decodeState == STATE_HANDLER_REMOVED_PENDING;
            decodeState = STATE_INIT;
            if (removePending) {
                handlerRemoved(ctx);
            }
        }
    }

    /**
     * Is called one last time when the {@link ChannelHandlerContext} goes in-active. Which means the
     * {@link #channelInactive(ChannelHandlerContext)} was triggered.
     *
     * By default this will just call {@link #decode(ChannelHandlerContext, ByteBuf)} but sub-classes may
     * override this for some special cleanup operation.
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param in            the {@link ByteBuf} from which to read data
     * @throws Exception    is thrown if an error occurs
     */
    protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        if (in.isReadable()) {
            // Only call decode() if there is something left in the buffer to decode.
            // See https://github.com/netty/netty/issues/4386
            decodeRemovalReentryProtection(ctx, in);
        }
    }

    private static final class CtxWrapper implements ChannelHandlerContext {
        @Nullable
        private ChannelHandlerContext delegate;
        private int fireChannelReadCount;

        void setDelegate(ChannelHandlerContext delegate) {
            this.delegate = delegate;
        }

        int getFireChannelReadCount() {
            return fireChannelReadCount;
        }

        void resetFireChannelReadCount() {
            fireChannelReadCount = 0;
        }

        @Override
        public Channel channel() {
            assert delegate != null;
            return delegate.channel();
        }

        @Override
        public EventExecutor executor() {
            assert delegate != null;
            return delegate.executor();
        }

        @Override
        public String name() {
            assert delegate != null;
            return delegate.name();
        }

        @Override
        public ChannelHandler handler() {
            assert delegate != null;
            return delegate.handler();
        }

        @Override
        public boolean isRemoved() {
            assert delegate != null;
            return delegate.isRemoved();
        }

        @Override
        public ChannelHandlerContext fireChannelRegistered() {
            assert delegate != null;
            delegate.fireChannelRegistered();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelUnregistered() {
            assert delegate != null;
            delegate.fireChannelUnregistered();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelActive() {
            assert delegate != null;
            delegate.fireChannelActive();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelInactive() {
            assert delegate != null;
            delegate.fireChannelInactive();
            return this;
        }

        @Override
        public ChannelHandlerContext fireExceptionCaught(Throwable cause) {
            assert delegate != null;
            delegate.fireExceptionCaught(cause);
            return this;
        }

        @Override
        public ChannelHandlerContext fireUserEventTriggered(Object evt) {
            assert delegate != null;
            delegate.fireUserEventTriggered(evt);
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelRead(Object msg) {
            assert delegate != null;
            ++fireChannelReadCount;
            delegate.fireChannelRead(msg);
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelReadComplete() {
            assert delegate != null;
            delegate.fireChannelReadComplete();
            return this;
        }

        @Override
        public ChannelHandlerContext fireChannelWritabilityChanged() {
            assert delegate != null;
            delegate.fireChannelWritabilityChanged();
            return this;
        }

        @Override
        public ChannelFuture bind(SocketAddress localAddress) {
            assert delegate != null;
            return delegate.bind(localAddress);
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress) {
            assert delegate != null;
            return delegate.connect(remoteAddress);
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
            assert delegate != null;
            return delegate.connect(remoteAddress, localAddress);
        }

        @Override
        public ChannelFuture disconnect() {
            assert delegate != null;
            return delegate.disconnect();
        }

        @Override
        public ChannelFuture close() {
            assert delegate != null;
            return delegate.close();
        }

        @Override
        public ChannelFuture deregister() {
            assert delegate != null;
            return delegate.deregister();
        }

        @Override
        public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
            assert delegate != null;
            return delegate.bind(localAddress, promise);
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
            assert delegate != null;
            return delegate.connect(remoteAddress, promise);
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            assert delegate != null;
            return delegate.connect(remoteAddress, localAddress, promise);
        }

        @Override
        public ChannelFuture disconnect(ChannelPromise promise) {
            assert delegate != null;
            return delegate.disconnect(promise);
        }

        @Override
        public ChannelFuture close(ChannelPromise promise) {
            assert delegate != null;
            return delegate.close(promise);
        }

        @Override
        public ChannelFuture deregister(ChannelPromise promise) {
            assert delegate != null;
            return delegate.deregister(promise);
        }

        @Override
        public ChannelHandlerContext read() {
            assert delegate != null;
            delegate.read();
            return this;
        }

        @Override
        public ChannelFuture write(Object msg) {
            assert delegate != null;
            return delegate.write(msg);
        }

        @Override
        public ChannelFuture write(Object msg, ChannelPromise promise) {
            assert delegate != null;
            return delegate.write(msg, promise);
        }

        @Override
        public ChannelHandlerContext flush() {
            assert delegate != null;
            delegate.flush();
            return this;
        }

        @Override
        public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
            assert delegate != null;
            return delegate.writeAndFlush(msg, promise);
        }

        @Override
        public ChannelFuture writeAndFlush(Object msg) {
            assert delegate != null;
            return delegate.writeAndFlush(msg);
        }

        @Override
        public ChannelPromise newPromise() {
            assert delegate != null;
            return delegate.newPromise();
        }

        @Override
        public ChannelProgressivePromise newProgressivePromise() {
            assert delegate != null;
            return delegate.newProgressivePromise();
        }

        @Override
        public ChannelFuture newSucceededFuture() {
            assert delegate != null;
            return delegate.newSucceededFuture();
        }

        @Override
        public ChannelFuture newFailedFuture(Throwable cause) {
            assert delegate != null;
            return delegate.newFailedFuture(cause);
        }

        @Override
        public ChannelPromise voidPromise() {
            assert delegate != null;
            return delegate.voidPromise();
        }

        @Override
        public ChannelPipeline pipeline() {
            assert delegate != null;
            return delegate.pipeline();
        }

        @Override
        public ByteBufAllocator alloc() {
            assert delegate != null;
            return delegate.alloc();
        }

        @Override
        public <T> Attribute<T> attr(AttributeKey<T> key) {
            assert delegate != null;
            return delegate.attr(key);
        }

        @Override
        public <T> boolean hasAttr(AttributeKey<T> key) {
            assert delegate != null;
            return delegate.hasAttr(key);
        }
    }
}
