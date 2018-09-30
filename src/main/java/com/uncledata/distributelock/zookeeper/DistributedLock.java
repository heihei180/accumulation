package com.uncledata.distributelock.zookeeper;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;


public class DistributedLock implements Lock, Watcher {
    private ZooKeeper zkConn = null;
    private String ROOT_LOCK = "/locks";
    private String lockName;
    private String WAIT_LOCK;
    private String CURRENT_LOCK;
    private CountDownLatch countDownLatch;
    private int sessionTimeout = 30000;
    private List<Exception> exceptionList = new ArrayList<>();

    DistributedLock(String url, String lockName) {
        this.lockName = lockName;
        try {
            zkConn = new ZooKeeper(url, sessionTimeout, this);
            if (zkConn.exists(ROOT_LOCK, false) == null)
                zkConn.create(ROOT_LOCK, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        } catch (IOException | InterruptedException | KeeperException e) {
            e.printStackTrace();
        }
    }

    public void lock() {
        if (exceptionList.size() > 0) throw new LockException(exceptionList.get(0));
        try {
            if (this.tryLock()) {
                System.out.println(Thread.currentThread().getName() + " " + lockName + " get the lock!");
            } else {
                waitForLock(WAIT_LOCK, sessionTimeout);
            }
        } catch (InterruptedException | KeeperException e) {
            e.printStackTrace();
        }
    }

    public boolean tryLock() {
        try {
            String splitStr = "_lock_";
            CURRENT_LOCK = zkConn.create(ROOT_LOCK + "/" + lockName + splitStr, new byte[0],
                    ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
            System.out.println(CURRENT_LOCK + " is created");

            List<String> subNodes = zkConn.getChildren(ROOT_LOCK, false);

            List<String> lockObjects = new ArrayList<>();
            for (String node : subNodes) {
                String _node = node.split(splitStr)[0];
                if (_node.equals(lockName)) lockObjects.add(node);
            }
            Collections.sort(lockObjects);
            System.out.println(Thread.currentThread().getName() + "'lock is " + CURRENT_LOCK);
            if (CURRENT_LOCK.equals(ROOT_LOCK + "/" + lockObjects.get(0))) {
                return true;
            }
            String prevNode = CURRENT_LOCK.substring(CURRENT_LOCK.lastIndexOf("/") + 1);
            int prevNodePosition = Collections.binarySearch(lockObjects, prevNode);
            WAIT_LOCK = lockObjects.get(prevNodePosition - 1);

        } catch (InterruptedException | KeeperException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean waitForLock(String waitLock, long waitTime) throws KeeperException, InterruptedException {
        Stat stat = zkConn.exists(ROOT_LOCK + "/" + waitLock, new Watcher() {
            @Override
            public void process(WatchedEvent watchedEvent) {
                if (countDownLatch != null) {
                    countDownLatch.countDown();
                }
            }
        });
        if (stat != null) {
            System.out.println(Thread.currentThread().getName() + " is waiting for " + ROOT_LOCK + "/" + waitLock);
            this.countDownLatch = new CountDownLatch(1);
            this.countDownLatch.await(waitTime, TimeUnit.MILLISECONDS);
            this.countDownLatch = null;
            System.out.println(Thread.currentThread().getName() + " get the lock ");
        }
        return true;
    }

    public void unlock() {
        try {
            System.out.println(CURRENT_LOCK+" has release the lock ");
            zkConn.delete(CURRENT_LOCK, -1);
            CURRENT_LOCK = null;
            zkConn.close();
        } catch (InterruptedException | KeeperException e) {
            e.printStackTrace();
        }
    }

    public Condition newCondition() {
        return null;
    }

    public void lockInterruptibly() {
        this.lock();
    }

    @Override
    public void process(WatchedEvent watchedEvent) {

    }


    public class LockException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        LockException(String e) {
            super(e);
        }

        LockException(Exception e) {
            super(e);
        }
    }


    public boolean tryLock(long timeout, TimeUnit unit) {
        try {
            if (this.tryLock()) {
                return true;
            }
            return waitForLock(WAIT_LOCK, timeout);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
