package com.lleggieri.concurrent.lock;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Main access class. Holds the queue in a map.
 *
 * @param <K> Key type.
 */
@ThreadSafe final class DefaultChain<K> implements Chain<K> {

  DefaultChain(@Nonnull final ConcurrentMap<K, Entry<?>> map) {
    this.map = map;
  }

  private final ConcurrentMap<K, Entry<?>> map;

  /**
   * Enqueue the promise on the key queue to be executed after the last promise is done,
   * or now if there are no current processes.
   *
   * @param key   the key to lock in.
   * @param block a block of code that returns a promise of a result.
   * @param <A>   The type of the result that the promise will return.
   * @return a new promise that will be fulfilled some time in the future with the result of the block of code.
   */
  @Override public <A> CompletionStage<A> enqueueOn(@Nonnull final K key,
                                            @Nonnull final Type type,
                                            @Nonnull final Supplier<CompletionStage<A>> block) {
    // Remove from the map after the promise completes if it is the last one.
    final Entry<A> entry = new Entry<>(type);
    entry.promise().whenComplete((a, t) -> map.remove(key, entry));
    // Atomically set this callback as the latest one on the queue.
    final Entry<?> previousEntry = map.put(key, entry);
    if (previousEntry == null) {
      // No one is running anything on this key. Run this block and then update the handler with the result.
      block.get().whenComplete(entry.callback());
    } else if (previousEntry.type() == Type.SHARED && entry.type() == Type.SHARED) {
      final BiConsumer<Object, Throwable> barrier = new BiConsumer<Object, Throwable>() {
        private final AtomicInteger counter = new AtomicInteger(0);
        @Override public void accept(final Object ignored1, final Throwable ignored2) {
          if (counter.incrementAndGet() == 2) {
            entry.callback().accept(null, null);
          }
        }
      };
      previousEntry.promise().whenComplete(barrier);
      block.get().whenComplete(barrier);
    } else {
      // Enqueue this block to be run after the current block is done.
      // Run it on the same thread and then update the handler with the result.
      previousEntry.promise().whenComplete((ignored, t) -> block.get().whenComplete(entry.callback()));
    }

    // Return a promise that will be completed with the callback.
    return entry.promise();
  }

  private static final class Entry<A> {
    private final Type type;
    private final CompletableFuture<A> cf;
    private final BiConsumer<A, Throwable> callback;

    public Entry(final Type type) {
      this.type = type;
      this.cf = new CompletableFuture<>();
      this.callback = (a, t) -> {
        if (t == null) {
          this.cf.complete(a);
        } else {
          this.cf.completeExceptionally(t);
        }
      };
    }

    public Type type() {
      return type;
    }
    public BiConsumer<A, Throwable> callback() {
      return callback;
    }

    public CompletionStage<A> promise() {
      return cf;
    }
  }
}

