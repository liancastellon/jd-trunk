package org.jdownloader.extensions.eventscripter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.appwork.utils.StringUtils;
import org.appwork.utils.logging2.LogSource;

public class IntervalController {
    private final EventScripterExtension                  extension;
    private final AtomicReference<Thread>                 scheduler     = new AtomicReference<Thread>(null);
    private final AtomicReference<ArrayList<ScriptEntry>> scriptEntries = new AtomicReference<ArrayList<ScriptEntry>>(null);
    private final LogSource                               logger;

    public IntervalController(EventScripterExtension eventScripterExtension) {
        this.extension = eventScripterExtension;
        logger = extension.getLogger();
        update();
    }

    public synchronized void update() {
        final ArrayList<ScriptEntry> scriptEntries = new ArrayList<ScriptEntry>();
        for (ScriptEntry e : extension.getEntries()) {
            if (e.getEventTrigger() == EventTrigger.INTERVAL && e.isEnabled()) {
                scriptEntries.add(e);
            }
        }
        this.scriptEntries.set(scriptEntries);
        Thread thread = scheduler.get();
        if (scriptEntries.size() > 0) {
            if (thread == null || thread.isAlive() == false) {
                thread = new Thread("EventScripterScheduler") {
                    public void run() {
                        try {
                            while (Thread.currentThread() == scheduler.get()) {
                                final ArrayList<ScriptEntry> scriptEntries = IntervalController.this.scriptEntries.get();
                                if (scriptEntries != null && scriptEntries.size() > 0) {
                                    long minwait = Long.MAX_VALUE;
                                    for (final ScriptEntry scriptEntry : scriptEntries) {
                                        if (scriptEntry.getEventTrigger() == EventTrigger.INTERVAL && scriptEntry.isEnabled()) {
                                            try {
                                                Map<String, Object> settings = scriptEntry.getEventTriggerSettings();
                                                long interval = 1000;
                                                if (settings.get("interval") != null) {
                                                    interval = Math.max(1000, ((Number) settings.get("interval")).longValue());
                                                }
                                                Object last = settings.get("lastFire");
                                                long lastTs = 0l;
                                                if (last != null) {
                                                    lastTs = ((Number) last).longValue();
                                                }
                                                long waitFor = interval - (System.currentTimeMillis() - lastTs);
                                                if (waitFor <= 0) {
                                                    if (fire(scriptEntry, interval)) {
                                                        settings.put("lastFire", System.currentTimeMillis());
                                                    }
                                                    waitFor = interval;
                                                }
                                                minwait = Math.max(500, Math.min(minwait, waitFor));
                                            } catch (Throwable e2) {
                                                logger.log(e2);
                                                minwait = 5000;
                                            }
                                        }
                                    }
                                    try {
                                        Thread.sleep(minwait);
                                    } catch (InterruptedException e1) {
                                        break;
                                    }
                                } else {
                                    break;
                                }
                            }
                        } finally {
                            scheduler.compareAndSet(Thread.currentThread(), null);
                        }
                    };
                };
                thread.setDaemon(true);
                scheduler.set(thread);
                thread.start();
            }
        } else {
            scheduler.set(null);
            if (thread != null) {
                thread.interrupt();
            }
        }
    }

    private final WeakHashMap<ScriptThread, Long> scriptThreads = new WeakHashMap<ScriptThread, Long>();

    protected synchronized boolean fire(final ScriptEntry scriptEntry, long interval) {
        if (scriptEntry.isEnabled() && StringUtils.isNotEmpty(scriptEntry.getScript())) {
            try {
                final boolean isSynchronous = scriptEntry.getEventTrigger().isSynchronous(scriptEntry.getEventTriggerSettings());
                if (isSynchronous) {
                    for (final Entry<ScriptThread, Long> scriptThread : scriptThreads.entrySet()) {
                        if (scriptEntry.getID() == scriptThread.getValue() && scriptThread.getKey().isAlive()) {
                            return false;
                        }
                    }
                }
                final HashMap<String, Object> props = new HashMap<String, Object>();
                props.put("interval", interval);
                final ScriptThread thread = new ScriptThread(extension, scriptEntry, props, logger) {
                    @Override
                    public boolean isSynchronous() {
                        return isSynchronous;
                    }

                    @Override
                    protected void finalizeEnvironment() throws IllegalAccessException {
                        super.finalizeEnvironment();
                        try {
                            final Map<String, Object> settings = scriptEntry.getEventTriggerSettings();
                            settings.put("interval", Math.max(1000, ((Number) props.get("interval")).longValue()));
                        } catch (final Throwable e) {
                            logger.log(e);
                        }
                    };
                };
                if (isSynchronous) {
                    scriptThreads.put(thread, scriptEntry.getID());
                }
                thread.start();
                return true;
            } catch (Throwable e) {
                logger.log(e);
            }
        }
        return false;
    }
}
