# Lab 01 — Multithreading in Java

JDK 21.0.7, `pom.xml` compiles for 21.

## Task 1 — Creating threads

apollo = Thread subclass, gemini = Runnable, mercury = lambda, artemis = virtual thread.

With `start()`, countdowns mix and "all launched" prints early. After `join()`, all are TERMINATED; only artemis is virtual.

With `--sequential`, `run()` stays on main, so they finish one by one. gemini/mercury print "main"; artemis prints nothing (`run()` on a virtual thread does not start it).

## Task 2 — Shared counter

8 threads × 1,000,000 increments. Expected: 8,000,000.

Unsafe lost updates (~3–5M). synchronized, ReentrantLock, AtomicLong, and LongAdder all hit 8,000,000. LongAdder was fastest (~11 ms).

`count++` is not atomic — two threads can write the same `n+1`. If only `increment()` is synchronized, `get()` can still read a stale value.

## Task 3 — Producer / consumer

2 producers × 50 orders, 3 consumers, buffer size 5. Both BoundedBuffer and ArrayBlockingQueue got `processed=100` on 20/20 runs.

Use `while` + `wait`, not `if` — waking up does not mean the condition is still true. Prefer `notifyAll()` over `notify()`, or the wrong thread may wake and the right one sleeps forever.

## Task 4 — Pools and virtual threads

Primes under 4,000,000:

- 1 thread: 283,146 primes, 210 ms
- 16 threads: 283,146 primes, 50 ms

500 fake downloads (200 ms each, every 50th fails):

- pool of 20: 5095 ms, ok=490 failed=10
- virtual threads: 217 ms, ok=490 failed=10

Virtual threads win here because they all sleep at once. Slow download used `get(1, SECONDS)` then `cancel(true)`.

## Task 5 — Deadlock

Opposite lock order deadlocks (`deadlock.txt`). Watchdog saw transfer-a-to-b and transfer-b-to-a.

8 threads × 100,000 transfers on 20 accounts. Both OrderedBank and TryLockBank kept total at 20,000,000. tryLock had 3,573 retries.

OrderedBank locks smaller id first. TryLockBank drops the first lock if the second fails. After interrupt + `join(1000)`, all 8 workers were `alive=false`.

## Challenge — Word count

Scanner puts paths on a queue; workers count words; reporter prints every 500 ms.

200 files × 50,000 words (reporter off):

| workers | mode | time |
|---:|---|---:|
| 1 | platform | 789 ms |
| 2 | platform | 473 ms |
| 4 | platform | 246 ms |
| 8 | platform | 199 ms |
| 200 | virtual | 209 ms |

More workers help until about 8. Virtual threads do not help much here (CPU-bound). Tests pass with `mvn verify`.
