package org.jdownloader.extensions.shutdown;

import jd.utils.JDUtilities;

import org.appwork.shutdown.ShutdownController;
import org.appwork.shutdown.ShutdownEvent;
import org.appwork.shutdown.ShutdownRequest;
import org.appwork.utils.logging2.LogSource;
import org.jdownloader.updatev2.ForcedShutdown;
import org.jdownloader.updatev2.RestartController;
import org.jdownloader.updatev2.SmartRlyExitRequest;

public class UnixShutdownInterface extends ShutdownInterface {
    private final LogSource logger;

    public UnixShutdownInterface(ShutdownExtension shutdownExtension) {
        logger = shutdownExtension.getLogger();
    }

    @Override
    public Mode[] getSupportedModes() {
        return new Mode[] { Mode.SHUTDOWN, Mode.HIBERNATE, Mode.STANDBY, Mode.CLOSE };
    }

    @Override
    public void requestMode(Mode mode, boolean force) {
        switch (mode) {
        case SHUTDOWN:
            stopActivity();
            ShutdownController.getInstance().addShutdownEvent(new ShutdownEvent() {
                @Override
                public int getHookPriority() {
                    return Integer.MIN_VALUE;
                }

                @Override
                public void onShutdown(ShutdownRequest shutdownRequest) {
                    try {
                        dbusPowerState("Shutdown");
                    } catch (Throwable e) {
                        logger.log(e);
                    }
                    try {
                        JDUtilities.runCommand("dcop", new String[] { "--all-sessions", "--all-users", "ksmserver", "ksmserver", "logout", "0", "2", "0" }, null, 0);
                    } catch (Throwable e) {
                        logger.log(e);
                    }
                    try {
                        JDUtilities.runCommand("poweroff", new String[] {}, null, 0);
                    } catch (Throwable e) {
                        logger.log(e);
                    }
                    try {
                        JDUtilities.runCommand("sudo", new String[] { "shutdown", "-P", "now" }, null, 0);
                    } catch (Throwable e) {
                        logger.log(e);
                    }
                    try {
                        // shutdown: -H and -P flags can only be used along with -h flag.
                        JDUtilities.runCommand("sudo", new String[] { "shutdown", "-Ph", "now" }, null, 0);
                    } catch (Throwable e) {
                        logger.log(e);
                    }
                }
            });
            RestartController.getInstance().exitAsynch(new ForcedShutdown());
            break;
        case HIBERNATE:
            stopActivity();
            try {
                dbusPowerState("Hibernate");
            } catch (Throwable e) {
            }
            break;
        case STANDBY:
            stopActivity();
            try {
                dbusPowerState("Suspend");
            } catch (Throwable e) {
            }
            break;
        case CLOSE:
            stopActivity();
            RestartController.getInstance().exitAsynch(new SmartRlyExitRequest(true));
            break;
        default:
            break;
        }
    }

    private void dbusPowerState(String command) {
        JDUtilities.runCommand("dbus-send", new String[] { "--session", "--dest=org.freedesktop.PowerManagement", "--type=method_call", "--print-reply", "--reply-timeout=2000", "/org/freedesktop/PowerManagement", "org.freedesktop.PowerManagement." + command }, null, 0);
    }

    @Override
    public void prepareMode(Mode mode) {
    }
}
