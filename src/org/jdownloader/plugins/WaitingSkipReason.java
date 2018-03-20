package org.jdownloader.plugins;

import javax.swing.Icon;

import jd.controlling.downloadcontroller.HistoryEntry;
import jd.controlling.packagecontroller.AbstractNode;
import jd.nutils.Formatter;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackageView;

import org.jdownloader.api.downloads.ChannelCollector;
import org.jdownloader.api.downloads.DownloadControllerEventPublisher;
import org.jdownloader.api.downloads.v2.DownloadsAPIV2Impl;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.views.downloads.columns.ETAColumn;
import org.jdownloader.gui.views.downloads.columns.TaskColumn;
import org.jdownloader.images.AbstractIcon;
import org.jdownloader.images.NewTheme;
import org.jdownloader.translate._JDT;

public class WaitingSkipReason implements ConditionalSkipReason, TimeOutCondition, ValidatableConditionalSkipReason {
    public static enum CAUSE {
        IP_BLOCKED(_JDT.T.downloadlink_status_error_download_limit()),
        FILE_TEMP_UNAVAILABLE(_JDT.T.downloadlink_status_error_temp_unavailable()),
        CONNECTION_TEMP_UNAVAILABLE(_JDT.T.download_error_message_networkreset()),
        HOST_TEMP_UNAVAILABLE(_JDT.T.downloadlink_status_error_hoster_temp_unavailable()),
        RETRY_IN(null);
        private final String exp;

        private CAUSE(String exp) {
            this.exp = exp;
        }

        public String getExplanation() {
            return exp;
        }
    }

    private final CAUSE cause;

    public CAUSE getCause() {
        return cause;
    }

    private final long   timeOutTimeStamp;
    private final String message;
    private final Icon   icon;
    private boolean      valid = true;

    public long getTimeOutTimeStamp() {
        return timeOutTimeStamp;
    }

    public WaitingSkipReason(CAUSE cause, long timeOut, String message) {
        this.cause = cause;
        this.timeOutTimeStamp = System.currentTimeMillis() + timeOut;
        this.message = message;
        switch (cause) {
        case FILE_TEMP_UNAVAILABLE:
            icon = new AbstractIcon(IconKey.ICON_WARNING_GREEN, 16);
            break;
        case HOST_TEMP_UNAVAILABLE:
            icon = new AbstractIcon(IconKey.ICON_WARNING_RED, 16);
            break;
        case IP_BLOCKED:
            icon = NewTheme.I().getIcon(IconKey.ICON_AUTO_RECONNECT, 16);
            break;
        default:
            icon = new AbstractIcon(IconKey.ICON_WAIT, 16);
            break;
        }
    }

    public WaitingSkipReason(CAUSE cause, long timeOut) {
        this(cause, timeOut, null);
    }

    public String getMessage() {
        if (message == null) {
            if (cause == CAUSE.RETRY_IN) {
                long left = getTimeOutLeft();
                if (left > 0) {
                    return _JDT.T.gui_download_waittime_status2(Formatter.formatSeconds(left / 1000));
                } else {
                    return _JDT.T.gui_download_waittime_status2("");
                }
            }
            return cause.getExplanation();
        }
        return message;
    }

    @Override
    public String toString() {
        return getCause() + ":" + getMessage() + "|Timeout:" + getTimeOutLeft();
    }

    public long getTimeOutLeft() {
        return Math.max(0, getTimeOutTimeStamp() - System.currentTimeMillis());
    }

    @Override
    public boolean isConditionReached() {
        final long left = getTimeOutLeft();
        return left == 0;
    }

    @Override
    public void finalize(DownloadLink link) {
    }

    @Override
    public String getMessage(Object requestor, AbstractNode node) {
        long left = getTimeOutLeft();
        if (left > 0) {
            if (requestor instanceof HistoryEntry) {
                return getMessage();
            }
            if (requestor instanceof TaskColumn) {
                return getMessage();
            }
            if (requestor instanceof DownloadControllerEventPublisher) {
                return getMessage();
            }
            if (requestor instanceof ChannelCollector) {
                return getMessage();
            }
            if (requestor instanceof DownloadsAPIV2Impl) {
                return getMessage();
            }
            if (requestor instanceof FilePackageView) {
                return getMessage();
            }
            if (requestor instanceof ETAColumn) {
                return Formatter.formatSeconds(left / 1000);
            }
        }
        return null;
    }

    @Override
    public Icon getIcon(Object requestor, AbstractNode node) {
        return icon;
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    @Override
    public void invalidate() {
        valid = false;
    }
}
