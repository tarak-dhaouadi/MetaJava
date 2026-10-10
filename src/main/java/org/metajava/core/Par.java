package org.metajava.core;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * Shared worker pool for parallel workflows. Default 4 threads, capped at the number of cores;
 * override with -Dmetajava.threads=N or {@link #setThreads(int)}.
 * The pool is a work-stealing {@link ForkJoinPool}: a loop started from inside a pool task (for instance the
 * GLMM likelihood inside a leave-one-out refit that itself runs in the pool) is split further and the waiting
 * thread helps with the work, so nested parallel loops use idle threads and can never deadlock.
 * A cancellation condition can be attached to the calling thread with {@link #withCancel}; loops started under it
 * (and tasks submitted under it) stop at the next iteration with a CancellationException once it is true.
 */
public final class Par {
    private static final ThreadLocal<BooleanSupplier> CANCEL = new ThreadLocal<>();
    private static final ThreadLocal<DoubleConsumer> PROGRESS = new ThreadLocal<>();   // progress sink of the calling thread (outermost loops only)
    private static final ThreadLocal<Boolean> IN_LOOP = new ThreadLocal<>();          // set while a loop body runs: nested loops do not report
    private static volatile int threads = defaultThreads();   // volatile: read on every loop, written rarely
    private static volatile ForkJoinPool pool;

    private Par() {}

    public static int maxThreads() { return Boolean.getBoolean("metajava.threads.nocap") ? 64 : Math.max(1, Runtime.getRuntime().availableProcessors()); }   // nocap: testing only

    static int defaultThreads() {
        int req = 4;
        String p = System.getProperty("metajava.threads");
        if (p != null) try { req = Integer.parseInt(p.trim()); } catch (NumberFormatException e) { }
        return Math.max(1, Math.min(req, maxThreads()));
    }

    public static int threads() { return threads; }

    public static synchronized void setThreads(int n) {
        n = Math.max(1, Math.min(n, maxThreads()));
        if (n == threads && pool != null) return;
        threads = n;
        ForkJoinPool old = pool; pool = null;
        if (old != null) old.shutdown();
    }

    private static ForkJoinPool pool() {
        ForkJoinPool p = pool;
        if (p == null) synchronized (Par.class) {
            p = pool;
            if (p == null) {
                p = new ForkJoinPool(threads, pl -> { ForkJoinWorkerThread t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pl); t.setName("metajava-worker-" + t.getPoolIndex()); t.setDaemon(true); return t; }, null, false);
                pool = p;
            }
        }
        return p;
    }

    /** Runs r with a cancellation condition attached to this thread; parallel loops and submitted tasks started inside inherit it. */
    public static void withCancel(BooleanSupplier cancelled, Runnable r) {
        BooleanSupplier old = CANCEL.get(); CANCEL.set(cancelled);
        try { r.run(); } finally { if (old == null) CANCEL.remove(); else CANCEL.set(old); }
    }

    /** Runs r with a progress sink attached to this thread: the outermost parallel loop started inside r reports the fraction of its iterations completed (0..1). */
    public static void withProgress(DoubleConsumer sink, Runnable r) {
        DoubleConsumer old = PROGRESS.get(); PROGRESS.set(sink);
        try { r.run(); } finally { if (old == null) PROGRESS.remove(); else PROGRESS.set(old); }
    }

    /** Throws CancellationException when the condition attached to this thread is true (also usable inside long sequential loops). */
    public static void checkCancel() { BooleanSupplier c = CANCEL.get(); if (c != null && c.getAsBoolean()) throw new CancellationException("analysis superseded"); }

    private static <T> Callable<T> inherit(Callable<T> c) {
        final BooleanSupplier tok = CANCEL.get(); if (tok == null) return c;
        return () -> { BooleanSupplier old = CANCEL.get(); CANCEL.set(tok); try { return c.call(); } finally { if (old == null) CANCEL.remove(); else CANCEL.set(old); } };
    }

    /** Runs body(0..n-1); results must be written to per-index slots by the caller. Rethrows the first failure. */
    public static void forRange(int n, IntConsumer body0) {
        if (n <= 0) return;
        final BooleanSupplier tok = CANCEL.get();
        final DoubleConsumer pg = IN_LOOP.get() == null ? PROGRESS.get() : null; final AtomicInteger cnt = new AtomicInteger(); final boolean outer = IN_LOOP.get() == null;
        final IntConsumer body = pg == null ? body0 : i -> { body0.accept(i); pg.accept(cnt.incrementAndGet() / (double) n); };
        if (n == 1 || threads <= 1) {
            if (outer) IN_LOOP.set(Boolean.TRUE);
            try { for (int i = 0; i < n; i++) { if (tok != null && tok.getAsBoolean()) throw new CancellationException("analysis superseded"); body.accept(i); } }
            finally { if (outer) IN_LOOP.remove(); }
            return;
        }
        final int chunk = Math.max(1, (int) Math.ceil(n / (threads * 4.0)));
        final AtomicBoolean abort = new AtomicBoolean();
        final RecursiveAction root = new Range(0, n, chunk, body, tok, abort);
        if (ForkJoinTask.inForkJoinPool()) { root.invoke(); return; }   // nested: the waiting worker helps, no deadlock
        try { pool().submit(root).get(); }
        catch (InterruptedException e) { abort.set(true); Thread.currentThread().interrupt(); throw new CancellationException("interrupted"); }
        catch (ExecutionException e) { abort.set(true); throw rethrow(e.getCause()); }
    }

    private static RuntimeException rethrow(Throwable c) { if (c instanceof RuntimeException) throw (RuntimeException) c; if (c instanceof Error) throw (Error) c; throw new RuntimeException(c); }

    private static final class Range extends RecursiveAction {
        final int lo, hi, chunk; final IntConsumer body; final BooleanSupplier tok; final AtomicBoolean abort;
        Range(int lo, int hi, int chunk, IntConsumer body, BooleanSupplier tok, AtomicBoolean abort) { this.lo = lo; this.hi = hi; this.chunk = chunk; this.body = body; this.tok = tok; this.abort = abort; }
        @Override protected void compute() {
            if (hi - lo <= chunk) {
                BooleanSupplier old = CANCEL.get(); CANCEL.set(tok);   // inner loops started by body see the same condition
                Boolean oldLoop = IN_LOOP.get(); IN_LOOP.set(Boolean.TRUE);   // inner loops do not report progress
                try {
                    for (int i = lo; i < hi; i++) {
                        if (abort.get() || tok != null && tok.getAsBoolean()) throw new CancellationException("analysis superseded");
                        body.accept(i);
                    }
                } catch (RuntimeException | Error e) { abort.set(true); throw e;
                } finally { if (old == null) CANCEL.remove(); else CANCEL.set(old); if (oldLoop == null) IN_LOOP.remove(); else IN_LOOP.set(oldLoop); }
                return;
            }
            int mid = (lo + hi) >>> 1; invokeAll(new Range(lo, mid, chunk, body, tok, abort), new Range(mid, hi, chunk, body, tok, abort));
        }
    }

    /** Runs independent tasks concurrently (one task each, in the pool) and waits for all. */
    public static void all(java.util.List<? extends Runnable> tasks) { forRange(tasks.size(), i -> tasks.get(i).run()); }

    /** Submits a long-running task (e.g. one analysis section) to the pool without blocking the caller. */
    public static <T> Future<T> submit(Callable<T> c) { return pool().submit(inherit(c)); }

    public static <T> T[] map(int n, IntFunction<T> f, T[] out) { forRange(n, i -> out[i] = f.apply(i)); return out; }
}
