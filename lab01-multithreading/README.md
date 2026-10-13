# lab01-multithreading

JDK 21.0.7 on this laptop (11 cores). `pom.xml` compiles for 21.

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
mvn -q verify
java -cp target/classes edu.itc.se.lab01.task1.Main
java -cp target/classes edu.itc.se.lab01.task2.Main
java -cp target/classes edu.itc.se.lab01.task3.Main
java -cp target/classes edu.itc.se.lab01.task3.Main2
java -cp target/classes edu.itc.se.lab01.task4.Main
java -cp target/classes edu.itc.se.lab01.task5.Main
```

Task 3 is slow because of the sleeps. `task5.Main deadlock` hangs until you kill the process.

## task 1

apollo is a Thread subclass, gemini is a Runnable, mercury is a lambda, artemis is a virtual thread.

Without the flag, start() returns immediately so the countdowns mix and "all launched" shows up early. After join() they are all TERMINATED and only artemis is virtual.

With --sequential, run() happens on main, so each countdown finishes before the next and "all launched" is at the end. apollo still prints its own name. gemini and mercury print "main" because CountdownTask uses currentThread(). artemis prints nothing, since calling run() on a virtual thread does not start it, and the states stay NEW.

## task 2

8 threads x 1_000_000 increments. expected 8000000.

unsafe runs (wrong, as expected):
- 3272318
- 5120781
- 4928610

correct ones all hit 8000000. rough times: synchronized 306 ms, ReentrantLock 75 ms, AtomicLong 328 ms, LongAdder 11 ms.

count++ is not one step. two threads can read the same number and both write n+1, so an update gets lost.

LongAdder is quicker here because threads mostly increment their own cell instead of all hitting one AtomicLong. sum() only adds the cells when you read the total.

If only increment() is synchronized, the ++ itself is safe, but get() still has no lock, so another thread can read a stale count. A plain long read is not guaranteed to be atomic either. Bench gets away with it only because get() runs after join(), and join() makes the worker's writes visible to main. A thread that peeks at the counter while the workers are still going would not.

## task 3

2 producers x 50 orders, 3 consumers, buffer size 5. main puts one poison pill (id -1) per consumer after the producers finish. 20/20 runs got processed=100 for both BoundedBuffer and ArrayBlockingQueue. Producer and Consumer did not change between the two; they only see the Buffer interface.

wait() is in a while, not an if. waking up does not mean the condition is still true. it might be a spurious wakeup, or another consumer already took the item. with an if, take() can remove from an empty deque and put() can go past the capacity.

notify() only wakes one thread, and it might be the wrong kind. a producer wakes up, sees the buffer is full again, and waits, without waking the consumer that actually needed the signal. that one sleeps forever. it does not happen every run, which is why I looped 20 times. notifyAll() wakes everyone and each thread checks its own condition again.

## task 4

primes under 4_000_000, 16 chunks:

- one thread: 283146 primes, 210 ms
- 16 threads: 283146 primes, 50 ms

same count, and the parallel one was faster.

500 fake downloads, 200 ms each, every 50th throws IOException("boom"):

- fixed pool of 20: 5095 ms, ok=490 failed=10
- virtual threads: 217 ms, ok=490 failed=10

The pool of 20 can only sleep 20 at a time, so it is about 5 seconds. Virtual threads all sleep together, so it is about one 200 ms sleep. Failures are counted from ExecutionException and the program keeps going. The extra 5 s download uses get(1, SECONDS) then cancel(true): `timed out cancelled=true`. The CompletableFuture version with handle() also got ok=490 failed=10. Executors are in try-with-resources so the JVM exits.

## task 5

`Main deadlock` locks two accounts in opposite order. The watchdog printed transfer-a-to-b and transfer-b-to-a. jstack output is in deadlock.txt. I killed the process after saving that.

Then 8 threads x 100_000 transfers (800_000 total) on 20 accounts. starting total 20_000_000, still 20_000_000 after both fixes. tryLock had 3573 retries.

OrderedBank always locks the smaller id first, so the two threads cannot wait on each other. TryLockBank drops the first lock if the second tryLock fails, then sleeps a bit and tries again. if you kept the first lock, you would be back to hold-and-wait.

For shutdown I let the tryLock workers run for 2 seconds, then interrupt and join(1000). all 8 printed alive=false. they did 151954 transfers and the total was still 20_000_000. when sleep or tryLock throws InterruptedException I set the interrupt flag again and return. otherwise the flag gets cleared and the loop would keep going.

## challenge

Scanner thread puts file paths on an ArrayBlockingQueue of size 16. workers count words. reporter prints every 500 ms. it only reads atomic counters and the map's entrySet(), which is weakly consistent, so the top 3 can be a bit off and the workers are not blocked on a lock. one line from a 1-worker run:

```
files 79/200 words=3898180 rate=7598791/s top3=transfer:131342, the:132313, producer:132831
```

--timeout and the shutdown hook both call the same shutdown(): interrupt scanner and workers, wait up to 2 s, print [PARTIAL]. tests check that parallel and single-threaded counts match on 20 files, and that a timeout run comes back within 3 s.

200 files, 50_000 words each (10_000_000 words), reporter off:

| workers | mode | time |
|---:|---|---:|
| 1 | platform | 789 ms |
| 2 | platform | 473 ms |
| 4 | platform | 246 ms |
| 8 | platform | 199 ms |
| 200 | virtual | 209 ms |

More platform workers help until about 8, then it levels off. this job is mostly CPU once the file is in memory, so a pile of virtual threads is not faster than 8 normal ones. virtual threads helped in task 4 because those tasks were just sleeping.

merge(word, 1L, Long::sum) keeps CAS-ing the same map entry for a popular word, so threads step on each other. computeIfAbsent + LongAdder.increment() makes one adder per word and the increments spread across cells. a synchronized map would be worse, one lock for everything. sum() is the slow read, and it only happens at the end.

The word-count queue can block, but it does not deadlock. put/take release the queue lock while they wait, and workers do not wait on each other. the scanner waiting for space and the workers waiting for files looks like a circle, but the poison pills (one per worker after the scan) and interrupt from --timeout / Ctrl+C break it. task 5 is the one that actually deadlocks: each thread holds one account and waits for the other, the locks are exclusive, nobody steals a monitor, and the order is circular.
