package com.chat.util;

import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 手写线程池
 */
@Slf4j
public  class ThreadPoolExecutorFactory {

    private ThreadPoolExecutorFactory(){
            if (null == threadPoolExecutor){
                this.getThreadPoolExecutor();
            }
    }
    /**
     * CPU核数:Runtime.getRuntime().availableProcessors()
     * CPU 密集型：核心线程数 = CPU核数 + 1
     * IO 密集型：核心线程数 = CPU核数 * 2
     */

    /**
     * 线程池对象
     * static 关键字表示该变量属于类级别，而不是实例级别。
     * 也就是说，无论创建了多少个类的实例，它们共享同一个 threadPoolExecutor 对象。
     *
     * volatile 关键字用于保证多线程环境下对该变量的可见性和有序性。
     * 当一个线程修改了 volatile 变量的值，其他线程可以立即看到最新的值，而不会使用缓存的旧值。这有助于避免多线程并发访问时的数据不一致性问题。
     */
    private static volatile ThreadPoolExecutor  threadPoolExecutor = null;

    /**
     *线程池核心数
     * corePoolSize 池中所保存的线程数，包括空闲线程。
     *
     * CPU核数:Runtime.getRuntime().availableProcessors()
     * CPU 密集型：核心线程数 = CPU核数 + 1
     * IO 密集型：核心线程数 = CPU核数 * 2
     *
     */
    private static final int CORE_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 2;

    /**
     * maximumPoolSize the maximum number of threads to allow in the pool
     *  池中允许的最大线程数(采用LinkedBlockingQueue时没有作用)
     */
    private static final int MAX_IMUM_POOL_SIZE = 100;

    /**
     * keepAliveTime when the number of threads is greater than
     * the core, this is the maximum time that excess idle threads
     * will wait for new tasks before terminating
     * 当线程数大于核心时，此为终止前多余的空闲线程等待新任务的最长时间，线程池维护线程所允许的空闲时间
     */
    private static final long KEEP_ALIVE_TIME = 1000;

    /**
     * unit the time unit for the {@code keepAliveTime} argument
     * 时间单位(毫秒)
     */
    private static final TimeUnit TIME_UINT = TimeUnit.MILLISECONDS;

    /**
     * 等待队列的大小。默认是无界的，性能损耗的关键
     */
    private static final int QUEUE_SIZE = 200;




    /**
     *
     * 双检锁创建线程安全的单例
     * @return
     */
    public static ThreadPoolExecutor getThreadPoolExecutor(){
        if (null == threadPoolExecutor){
            synchronized (ThreadPoolExecutorFactory.class){
                if (null == threadPoolExecutor){
                    threadPoolExecutor = new ThreadPoolExecutor(
                            CORE_POOL_SIZE,
                            MAX_IMUM_POOL_SIZE,
                            KEEP_ALIVE_TIME,
                            TIME_UINT,
                            new LinkedBlockingQueue<>(QUEUE_SIZE) ,
                            new ThreadPoolExecutor.CallerRunsPolicy()
                    );
                }
            }
        }
        return threadPoolExecutor;
    }

    public static void waitForExecutorFinish(ThreadPoolExecutor threadPoolExecutor) {
        while (threadPoolExecutor.getActiveCount()!=0||
                threadPoolExecutor.getQueue().size() != 0
        ) {
            log.info("活跃线程数:{},阻塞队列数:{}",threadPoolExecutor.getActiveCount(),threadPoolExecutor.getQueue().size());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }





}
