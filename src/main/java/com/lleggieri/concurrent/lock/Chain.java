package com.lleggieri.concurrent.lock;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

public interface Chain<K> {
  enum Type { SHARED, EXCLUSIVE }

  <A> CompletionStage<A> enqueueOn(@Nonnull K key, @Nonnull Type type, @Nonnull Supplier<CompletionStage<A>> block);

  default <A, B> Function<A, CompletionStage<B>> enqueue(@Nonnull final K key,
                                                         @Nonnull final Type type,
                                                         @Nonnull final Function<A, CompletionStage<B>> f) {
    return a -> enqueueOn(key, type, () -> f.apply(a));
  }

}
