package org.jdownloader.captcha.v2.challenge.oauth;

import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import jd.controlling.captcha.ChallengeDialogHandler;
import jd.controlling.captcha.HideAllCaptchasException;
import jd.controlling.captcha.HideCaptchasByHostException;
import jd.controlling.captcha.HideCaptchasByPackageException;
import jd.controlling.captcha.RefreshException;
import jd.controlling.captcha.StopCurrentActionException;
import jd.gui.swing.dialog.DialogType;

import org.appwork.uio.CloseReason;
import org.appwork.utils.swing.EDTHelper;
import org.appwork.utils.swing.EDTRunner;
import org.appwork.utils.swing.dialog.Dialog;
import org.appwork.utils.swing.dialog.DialogCanceledException;
import org.appwork.utils.swing.dialog.DialogClosedException;
import org.appwork.utils.swing.dialog.InternDialog;
import org.appwork.utils.swing.windowmanager.WindowManager;
import org.appwork.utils.swing.windowmanager.WindowManager.FrameState;

public class OAuthDialogHandler extends ChallengeDialogHandler<OAuthChallenge> {
    private OAuthDialog dialog;

    public OAuthDialogHandler(OAuthChallenge captchaChallenge) {
        super(captchaChallenge.getDomainInfo(), captchaChallenge);
    }

    @Override
    protected int getTimeoutInMS() {
        return captchaChallenge.getTimeout();
    }

    @Override
    protected void showDialog(DialogType dialogType, int flag) throws DialogClosedException, DialogCanceledException, HideCaptchasByHostException, HideCaptchasByPackageException, StopCurrentActionException, HideAllCaptchasException, RefreshException {
        OAuthDialog d = new OAuthDialog(flag, dialogType, getHost(), captchaChallenge) {
            public void dispose() {
                super.dispose();
                synchronized (OAuthDialogHandler.this) {
                    OAuthDialogHandler.this.notifyAll();
                }
            }
        };
        d.setTimeout(getTimeoutInMS());
        if (!captchaChallenge.keepAlive()) {
            // no reason to let the user stop the countdown if the result cannot be used after the countdown anyway
            d.setCountdownPausable(false);
        }
        dialog = d;
        showDialog(dialog);
        new EDTHelper<Object>() {
            @Override
            public Object edtRun() {
                dialog.getDialog().addWindowListener(new WindowListener() {
                    @Override
                    public void windowOpened(WindowEvent e) {
                    }

                    @Override
                    public void windowIconified(WindowEvent e) {
                    }

                    @Override
                    public void windowDeiconified(WindowEvent e) {
                    }

                    @Override
                    public void windowDeactivated(WindowEvent e) {
                    }

                    @Override
                    public void windowClosing(WindowEvent e) {
                        synchronized (OAuthDialogHandler.this) {
                            OAuthDialogHandler.this.notifyAll();
                        }
                    }

                    @Override
                    public void windowClosed(WindowEvent e) {
                        synchronized (OAuthDialogHandler.this) {
                            boolean v = dialog.getDialog().isVisible();
                            OAuthDialogHandler.this.notifyAll();
                        }
                    }

                    @Override
                    public void windowActivated(WindowEvent e) {
                    }
                });
                return null;
            }
        }.waitForEDT();
        try {
            while (dialog.getDialog().isDisplayable()) {
                synchronized (this) {
                    this.wait(1000);
                }
            }
        } catch (InterruptedException e) {
            throw new DialogClosedException(Dialog.RETURN_INTERRUPT);
        } finally {
            try {
                dialog.dispose();
            } catch (Exception e) {
            }
        }
        dialog.getReturnValue();
        try {
            if (dialog.getCloseReason() != CloseReason.OK) {
                // if (dialog.isHideCaptchasForHost()) {
                // throw new HideCaptchasByHostException();
                // }
                // if (dialog.isHideCaptchasForPackage()) {
                // throw new HideCaptchasByPackageException();
                // }
                // if (dialog.isStopDownloads()) {
                // throw new StopCurrentActionException();
                // }
                // if (dialog.isHideAllCaptchas()) {
                // throw new HideAllCaptchasException();
                // }
                // if (dialog.isStopCrawling()) {
                // throw new StopCurrentActionException();
                // }
                // if (dialog.isStopShowingCrawlerCaptchas()) {
                // //
                // throw new HideAllCaptchasException();
                // }
                // if (dialog.isRefresh()) {
                // throw new RefreshException();
                // }
                dialog.throwCloseExceptions();
                throw new DialogClosedException(Dialog.RETURN_CLOSED);
            }
        } catch (IllegalStateException e) {
            // Captcha has been solved externally
        }
    }

    public void requestFocus() {
        new EDTRunner() {
            @Override
            protected void runInEDT() {
                OAuthDialog d = dialog;
                if (d != null) {
                    InternDialog<Boolean> win = d.getDialog();
                    if (win != null) {
                        WindowManager.getInstance().setZState(win, FrameState.TO_FRONT_FOCUSED);
                    }
                }
            }
        };
    }
    // public void setResponse(CaptchaResult resp) {
    // externalSet = true;
    // this.resp = resp;
    // new EDTRunner() {
    // @Override
    // protected void runInEDT() {
    // try {
    // if (textDialog != null) textDialog.dispose();
    // } catch (final Throwable e) {
    // LogSource.exception(getLogger(), e);
    // }
    // }
    // };
    // }
}