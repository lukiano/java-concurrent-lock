package com.lleggieri.concurrent.lock;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public final class Callback<A> extends CompletableFuture<A> implements BiConsumer<A, Throwable> {
  @Override public void accept(final A a, final Throwable t) {
    if (t == null) {
      this.complete(a);
    } else {
      this.completeExceptionally(t);
    }
  }
}
