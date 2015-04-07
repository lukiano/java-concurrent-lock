package com.lleggieri.concurrent.lock;

import net.javacrumbs.completionstage.CompletableCompletionStage;
import net.javacrumbs.completionstage.CompletionStageFactory;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DefaultChainTest {

  private static CompletionStageFactory factory;
  private static ExecutorService executorService;

  @BeforeClass
  public static void beforeClass() {
    executorService = Executors.newCachedThreadPool();
    factory = new CompletionStageFactory(executorService);
  }

  @AfterClass
  public static void afterClass() {
    executorService.shutdown();
  }

  @Test
  public void testEnqueuing() throws ExecutionException, InterruptedException {
    final Chain<Integer> defaultChain = ChainFactory.create();

    final CompletableCompletionStage<String> CompletionStage1 = factory.createCompletionStage();
    Supplier<CompletionStage<String>> supplier1 = () -> CompletionStage1;

    final CompletableCompletionStage<String> CompletionStage2 = factory.createCompletionStage();
    Supplier<CompletionStage<String>> supplier2 = () -> CompletionStage2;

    final CompletableCompletionStage<String> CompletionStage3 = factory.createCompletionStage();
    Supplier<CompletionStage<String>> supplier3 = () -> CompletionStage3;

    CompletableFuture<String> result1 = defaultChain.enqueueOn(1, Chain.Type.EXCLUSIVE, supplier1).toCompletableFuture();
    CompletableFuture<String> result2 = defaultChain.enqueueOn(1, Chain.Type.EXCLUSIVE, supplier2).toCompletableFuture();
    CompletableFuture<String> result3 = defaultChain.enqueueOn(1, Chain.Type.EXCLUSIVE, supplier3).toCompletableFuture();

    // no one is finished yet.
    Assert.assertFalse(result1.isDone());
    Assert.assertFalse(result2.isDone());
    Assert.assertFalse(result3.isDone());

    CompletionStage1.complete("first");

    // first is finished. Second and third not yet.
    Assert.assertTrue(result1.isDone());
    Assert.assertEquals("first", result1.get());
    Assert.assertFalse(result2.isDone());
    Assert.assertFalse(result3.isDone());

    CompletionStage3.complete("third");

    // third is "finished" but the block should not be called yet.
    Assert.assertFalse(result2.isDone());
    Assert.assertFalse(result3.isDone());

    CompletionStage2.complete("second");

    // now all are finished.
    Assert.assertTrue(result2.isDone());
    Assert.assertTrue(result3.isDone());
    Assert.assertEquals("second", result2.get());
    Assert.assertEquals("third", result3.get());
  }

  private Supplier<Boolean> someWork(final ConcurrentMap<String, Boolean> map, final String key) {
    return () -> {
        Boolean oldValue = map.put(key, Boolean.TRUE);
        if (oldValue == null) {
          try {
            Thread.sleep(500);
          } catch (InterruptedException e) {
            return Boolean.FALSE;
          }
          map.remove(key);
          return Boolean.FALSE;
        } else {
          return Boolean.TRUE;
        }
    };
  }

  @Test public void testExclusion() throws ExecutionException, InterruptedException {
    final ExecutorService executorService = java.util.concurrent.Executors.newCachedThreadPool();
    final ConcurrentMap<String, Boolean> workingOn = new ConcurrentHashMap<>();
    final List<CompletableFuture<Boolean>> Futures = new ArrayList<>();
    final Chain<String> chain = ChainFactory.create();
    try {
      for (int i = 0; i < 30; i++) {
        final String key = Integer.toString(i % 3);
        final Supplier<Boolean> work = someWork(workingOn, key);
        final Supplier<CompletionStage<Boolean>> block = () -> factory.supplyAsync(work);
        Futures.add(chain.enqueueOn(key, Chain.Type.EXCLUSIVE, block).toCompletableFuture());
      }
      CompletableFuture.allOf(Futures.toArray(new CompletableFuture[Futures.size()])).get();
      List<Boolean> results = Futures.stream().map(f -> {
                try {
                  return f.get();
                } catch (Exception e) {
                  return null;
                }
              }
      ).collect(Collectors.toList());
      boolean collision = false;
      for (Boolean b: results) {
        collision |= b;
      }
      Assert.assertFalse(collision);
    } finally {
      executorService.shutdown();
    }
  }
}
