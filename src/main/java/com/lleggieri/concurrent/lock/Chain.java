package com.lleggieri.concurrent.lock;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class Chain<K> {
  public enum Type { SHARED, EXCLUSIVE }

  public static <K> Chain<K> create() {
    return new DefaultChain<>(new ConcurrentHashMap<>());
  }

  public abstract <A> CompletionStage<A> enqueueOn(@Nonnull K key, @Nonnull Type type, @Nonnull Supplier<CompletionStage<A>> block);

  public final <A, B> Function<A, CompletionStage<B>> enqueue(@Nonnull final K key,
                                                              @Nonnull final Type type,
                                                              @Nonnull final Function<A, CompletionStage<B>> f) {
    return a -> enqueueOn(key, type, () -> f.apply(a));
  }

  protected interface Entry<A> {
    Type type();
    BiConsumer<A, Throwable> callback();
    CompletionStage<A> future();
  }

}
