package com.lleggieri.concurrent.lock;

import java.util.concurrent.ConcurrentHashMap;

public final class ChainFactory {

  private ChainFactory() {}

  public static <K> Chain<K> create() {
    return new DefaultChain<>(new ConcurrentHashMap<>());
  }
}
