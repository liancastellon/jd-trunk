package org.jdownloader.captcha.v2;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jd.controlling.captcha.SkipException;

import org.jdownloader.captcha.v2.solver.jac.SolverException;
import org.jdownloader.captcha.v2.solverjob.SolverJob;

public abstract class ChallengeSolver<T> {

    private ThreadPoolExecutor threadPool;
    private Class<T>           resultType;
    private SolverService      service;

    /**
     * 
     * @param i
     *            size of the threadpool. if i<=0 there will be no threadpool. each challenge will get a new thread in this case
     */
    @SuppressWarnings("unchecked")
    public ChallengeSolver(SolverService service, int i) {
        this.service = service;
        if (service == null) {
            this.service = (SolverService) this;
        }
        service.addSolver(this);
        initThreadPool(i);

        final Type superClass = this.getClass().getGenericSuperclass();
        if (superClass instanceof Class) {
            throw new IllegalArgumentException("Wrong Construct");
        }
        resultType = (Class<T>) ((ParameterizedType) superClass).getActualTypeArguments()[0];
    }

    public ChallengeSolver(int i) {
        this(null, i);
    }

    protected HashMap<SolverJob<T>, JobRunnable<T>> map = new HashMap<SolverJob<T>, JobRunnable<T>>();

    public SolverService getService() {
        return service;
    }

    public boolean isEnabled() {
        return getService().getConfig().isEnabled();
    }

    public void setEnabled(boolean b) {
        getService().getConfig().setEnabled(b);
    }

    public List<SolverJob<T>> listJobs() {
        synchronized (map) {
            return new ArrayList<SolverJob<T>>(map.keySet());

        }

    }

    public boolean isJobDone(SolverJob<?> job) {
        synchronized (map) {
            return !map.containsKey(job);
        }

    }

    public void enqueue(SolverJob<T> job) {

        JobRunnable<T> jr;
        jr = new JobRunnable<T>(this, job);
        synchronized (map) {
            map.put(job, jr);
            if (threadPool == null) {
                new Thread(jr, "ChallengeSolverThread").start();
            } else {
                threadPool.execute(jr);
            }
        }
    }

    protected static void checkInterruption() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
    }

    public void kill(SolverJob<T> job) {
        synchronized (map) {
            JobRunnable<T> jr = map.remove(job);

            if (jr != null) {
                job.getLogger().info("Cancel " + jr);
                jr.cancel();

            } else {
                job.getLogger().info("Could not kill " + job + " in " + this);
            }
        }
    }

    public String toString() {
        return getClass().getSimpleName();
    }

    private void initThreadPool(int i) {
        if (i <= 0) {
            return;
        }
        threadPool = new ThreadPoolExecutor(0, i, 30000, TimeUnit.MILLISECONDS, new LinkedBlockingDeque<Runnable>(), new ThreadFactory() {

            public Thread newThread(final Runnable r) {
                return new Thread(r, "SolverThread:" + ChallengeSolver.this.toString());
            }

        }, new ThreadPoolExecutor.AbortPolicy()) {
            protected void afterExecute(Runnable r, Throwable t) {
                super.afterExecute(r, t);
                if (r instanceof JobRunnable) {
                    synchronized (map) {
                        map.remove(((JobRunnable<?>) r).getJob());
                    }
                }

            }

            @Override
            protected void beforeExecute(Thread t, Runnable r) {
                super.beforeExecute(t, r);

                /*
                 * WORKAROUND for stupid SUN /ORACLE way of "how a threadpool should work" !
                 */
                int working = threadPool.getActiveCount();
                int active = threadPool.getPoolSize();
                int max = threadPool.getMaximumPoolSize();
                if (active < max) {
                    if (working == active) {
                        /*
                         * we can increase max pool size so new threads get started
                         */
                        threadPool.setCorePoolSize(Math.min(max, active + 1));
                    }
                }
            }

        };
        threadPool.allowCoreThreadTimeOut(true);
    }

    public abstract void solve(SolverJob<T> solverJob) throws InterruptedException, SolverException, SkipException;

    public Class<T> getResultType() {
        return resultType;
    }

    public boolean canHandle(Challenge<?> c) {
        return getResultType().isAssignableFrom(c.getResultType());
    }

    public long getTimeout() {
        return -1;
    }

    public int getWaitForByID(String solverID) {
        Integer obj = getWaitForMap().get(solverID);
        return obj == null ? 0 : obj.intValue();
    }

    private Map<String, Integer> waitForMap = null;

    public synchronized Map<String, Integer> getWaitForMap() {
        if (waitForMap != null) {
            return waitForMap;
        }
        Map<String, Integer> map = getService().getConfig().getWaitForMap();
        if (map == null || map.size() == 0) {
            map = getService().getWaitForOthersDefaultMap();
            getService().getConfig().setWaitForMap(map);
        }
        waitForMap = Collections.synchronizedMap(map);
        return waitForMap;
    }

}
