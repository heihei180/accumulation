package com.uncledata.distributelock.zookeeper;

public class Main {
    private static int n = 500;

    private static void process() {
        System.out.println(--n);
    }

    public static void main(String[] args) {
        Runnable runnable = () -> {
            DistributedLock distributedLock = null;
            try {
                distributedLock = new DistributedLock("192.168.1.1:2181", "test1");
                distributedLock.lock();
                process();
                System.out.println(Thread.currentThread().getName() + " is running");
            } finally {
                if (distributedLock != null) {
                    distributedLock.unlock();
                }
            }
        };

        for (int i = 0; i < 10; i++) {
            Thread t = new Thread(runnable);
            t.start();
        }
    }
}

