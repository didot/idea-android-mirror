/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:JvmName("FutureUtils")
package com.android.tools.idea.concurrency

import com.google.common.base.Function
import com.google.common.util.concurrent.AsyncFunction
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.JdkFutureAdapters
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListenableFutureTask
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.google.common.util.concurrent.SettableFuture
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.AtomicNotNullLazyValue
import com.intellij.util.Alarm
import com.intellij.util.Alarm.ThreadToUse
import org.jetbrains.ide.PooledThreadExecutor
import java.awt.EventQueue.isDispatchThread
import java.awt.Toolkit
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Wrapper function to apply transform function to ListenableFuture after it get done
 */
//TODO(b/151801197) remove default value for executor.
fun <I, O> ListenableFuture<I>.transform(executor: Executor = directExecutor(), func: (I) -> O): ListenableFuture<O> {
  return Futures.transform(this, Function<I, O> { i -> func(i!!) }, executor)
}

/**
 * @see Futures.transformAsync
 */
fun <I, O> ListenableFuture<I>.transformAsync(executor: Executor, func: (I) -> ListenableFuture<O>): ListenableFuture<O> {
  return Futures.transformAsync(this, AsyncFunction { i -> func(i!!) }, executor)
}

/**
 * Transforms a [ListenableFuture] by throwing out the result.
 */
fun ListenableFuture<*>.ignoreResult(): ListenableFuture<Void?> = transform { null }

/**
 * Wrapper function to convert Future to ListenableFuture
 */
//TODO(b/151801197) remove default value for executor.
fun <I> Future<I>.listenInPoolThread(executor: Executor = directExecutor()): ListenableFuture<I> {
  return JdkFutureAdapters.listenInPoolThread(this, executor)
}

//TODO(b/151801197) remove default value for executor.
fun <I> List<Future<I>>.listenInPoolThread(executor: Executor = directExecutor()): List<ListenableFuture<I>> {
  return this.map { future: Future<I> -> future.listenInPoolThread(executor) }
}

fun <I> List<ListenableFuture<I>>.whenAllComplete(): Futures.FutureCombiner<I?> {
  return Futures.whenAllComplete(this)
}

/**
 * Wrapper function to add callback for a ListenableFuture
 */
//TODO(b/151801197) remove default value for executor.
fun <I> ListenableFuture<I>.addCallback(executor: Executor = directExecutor(), success: (I?) -> Unit, failure: (Throwable?) -> Unit) {
  addCallback(executor, object : FutureCallback<I> {
    override fun onFailure(t: Throwable?) {
      failure(t)
    }

    override fun onSuccess(result: I?) {
      success(result)
    }
  })
}

/**
 * Wrapper function to add callback for a ListenableFuture
 */
//TODO(b/151801197) remove default value for executor.
fun <I> ListenableFuture<I>.addCallback(executor: Executor = directExecutor(), futureCallback: FutureCallback<I>) {
  Futures.addCallback(this, futureCallback, executor)
}

fun <T> executeOnPooledThread(action: ()->T): ListenableFuture<T> {
  val futureTask = ListenableFutureTask.create(action)
  ApplicationManager.getApplication().executeOnPooledThread(futureTask)
  return futureTask
}

/**
 * Converts a [ListenableFuture] to a [CompletionStage].
 */
fun <T> ListenableFuture<T>.toCompletionStage(): CompletionStage<T> = ListenableFutureToCompletionStageAdapter(this)

fun <T> readOnPooledThread(function: () -> T): ListenableFuture<T> {
  return MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE).submit<T> { ReadAction.compute<T, Throwable>(function) }
}

private object MyAlarm : AtomicNotNullLazyValue<Alarm>() {
  override fun compute() = Alarm(ThreadToUse.POOLED_THREAD, ApplicationManager.getApplication())
}

fun <V> delayedValue(value: V, delayMillis: Int): ListenableFuture<V> {
  val result = SettableFuture.create<V>()
  MyAlarm.value.addRequest({ result.set(value) }, delayMillis)
  return result
}

fun <V> delayedOperation(callable: Callable<V>, delayMillis: Int): ListenableFuture<V> {
  val result = SettableFuture.create<V>()
  MyAlarm.value.addRequest(
    Runnable {
      try {
        result.set(callable.call())
      }
      catch (t: Throwable) {
        result.setException(t)
      }
    },
    delayMillis
  )
  return result
}

fun <V> delayedError(t: Throwable, delayMillis: Int): ListenableFuture<V> {
  val result = SettableFuture.create<V>()
  MyAlarm.value.addRequest({ result.setException(t) }, delayMillis)
  return result
}

/**
 * Waits on the dispatch thread for a [Future] to complete.
 * Calling this method instead of [Future.get] is required for
 * [Future] that have callbacks executing on the
 * [com.intellij.util.concurrency.EdtExecutorService].
 */
@Throws(ExecutionException::class, InterruptedException::class, TimeoutException::class)
fun <V> pumpEventsAndWaitForFuture(future: Future<V>, timeout: Long, unit: TimeUnit): V {

  assert(Toolkit.getDefaultToolkit().systemEventQueue is IdeEventQueue)
  assert(isDispatchThread())

  val nano = unit.toNanos(timeout)
  val startNano = System.nanoTime()
  val endNano = startNano + nano

  while (System.nanoTime() <= endNano) {
    IdeEventQueue.getInstance().flushQueue()
    ApplicationManager.getApplication().invokeAndWait(
      Runnable {
        try {
          future.get(50, TimeUnit.MILLISECONDS)
        }
        catch (e: InterruptedException) {
          // Ignore exceptions since we will retry (or rethrow) later on
        }
        catch (e: ExecutionException) {
        }
        catch (e: TimeoutException) {
        }
        catch (e: CancellationException) {
        }
      },
      ModalityState.any()
    )

    if (future.isDone) {
      return future.get()
    }
  }

  throw TimeoutException()
}

/**
 * Similar to [transform], but executes [finallyBlock] in both success and error completion.
 * The returned future fails if:
 * 1. The original future fails.
 * 2. The [finallyBlock] fails.
 *
 * If they both fail, the Throwable from the original future is returned,
 * with the error from [finallyBlock] available through [Throwable.getSuppressed].
 */
fun <I> ListenableFuture<I>.finallySync(executor: Executor, finallyBlock: () -> Unit): ListenableFuture<I> {
  val futureResult = SettableFuture.create<I>()

  addCallback(executor, object : FutureCallback<I> {
    override fun onSuccess(result: I?) {
      try {
        finallyBlock()
        futureResult.set(result)
      }
      catch (finallyError: Throwable) {
        futureResult.setException(finallyError)
      }
    }

    override fun onFailure(t: Throwable) {
      try {
        finallyBlock()
        futureResult.setException(t)
      }
      catch (finallyError: Throwable) {
        // Prefer original error over error from finally block
        t.addSuppressed(finallyError)
        futureResult.setException(t)
      }
    }
  })
  return futureResult
}

/**
 * @see [Futures.catching]
 */
fun <V, X : Throwable> ListenableFuture<V>.catching(executor: Executor, exceptionType: Class<X>, fallback: (X) -> V): ListenableFuture<V> {
  return Futures.catching(this, exceptionType, Function<X, V> { t -> fallback(t!!) }, executor)
}

/**
 * Submits a [function] in this executor queue, and returns a [ListenableFuture]
 * that completes with the [function] result or the exception thrown from the [function].
 */
fun <V> Executor.executeAsync(function: () -> V): ListenableFuture<V> {
  // Should be migrated to Futures.submit(), once guava will be updated to version >= 28.2
  return Futures.immediateFuture(Unit).transform(this) { function() }
}