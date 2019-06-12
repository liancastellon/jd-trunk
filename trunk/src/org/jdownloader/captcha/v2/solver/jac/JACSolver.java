package org.jdownloader.captcha.v2.solver.jac;

import java.awt.Image;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;

import jd.captcha.JACMethod;
import jd.captcha.JAntiCaptcha;
import jd.captcha.LetterComperator;
import jd.captcha.pixelgrid.Captcha;
import jd.plugins.Plugin;
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginForHost;

import org.appwork.shutdown.ShutdownController;
import org.appwork.shutdown.ShutdownEvent;
import org.appwork.shutdown.ShutdownRequest;
import org.appwork.storage.config.JsonConfig;
import org.appwork.utils.StringUtils;
import org.appwork.utils.ImageProvider.ImageProvider;
import org.appwork.utils.logging2.LogSource;
import org.jdownloader.captcha.v2.AbstractResponse;
import org.jdownloader.captcha.v2.Challenge;
import org.jdownloader.captcha.v2.ChallengeSolver;
import org.jdownloader.captcha.v2.challenge.stringcaptcha.BasicCaptchaChallenge;
import org.jdownloader.captcha.v2.challenge.stringcaptcha.CaptchaResponse;
import org.jdownloader.captcha.v2.solverjob.SolverJob;
import org.jdownloader.logging.LogController;

public class JACSolver extends ChallengeSolver<String> {
    private static final double              _0_85             = 0.85;
    private final JACSolverConfig            config;
    private static final JACSolver           INSTANCE          = new JACSolver();
    private final HashMap<String, Integer>   jacMethodTrustMap = new HashMap<String, Integer>();
    private final HashMap<String, AutoTrust> threshold;
    private final LogSource                  logger;

    /**
     * get the only existing instance of JACSolver. This is a singleton
     *
     * @return
     */
    public static JACSolver getInstance() {
        return JACSolver.INSTANCE;
    }

    /**
     * Create a new instance of JACSolver. This is a singleton class. Access the only existing instance by using {@link #getInstance()}.
     */
    @Override
    public JacSolverService getService() {
        return (JacSolverService) super.getService();
    }

    private JACSolver() {
        super(new JacSolverService(), 5);
        config = JsonConfig.create(JACSolverConfig.class);
        logger = LogController.getInstance().getLogger(JACSolver.class.getName());
        final HashMap<String, AutoTrust> threshold = config.getJACThreshold();
        if (threshold == null) {
            this.threshold = new HashMap<String, AutoTrust>();
        } else {
            this.threshold = new HashMap<String, AutoTrust>(threshold);
        }
        ShutdownController.getInstance().addShutdownEvent(new ShutdownEvent() {
            @Override
            public void onShutdown(ShutdownRequest shutdownRequest) {
                config.setJACThreshold(JACSolver.this.threshold);
            }
        });
    }

    @Override
    public long getTimeout() {
        return 30000;
    }

    public boolean canHandle(Challenge<?> c) {
        // JAC is too old/unsafe to use for account logins!
        return !c.isAccountLogin() && super.canHandle(c);
    }

    @Override
    public void enqueue(SolverJob<String> job) {
        if (isEnabled() && canHandle(job.getChallenge())) {
            super.enqueue(job);
        }
    }

    @Override
    public void solve(SolverJob<String> job) throws InterruptedException, SolverException {
        try {
            if (job.getChallenge() instanceof BasicCaptchaChallenge && isEnabled()) {
                BasicCaptchaChallenge captchaChallenge = (BasicCaptchaChallenge) job.getChallenge();
                String host = null;
                if (captchaChallenge.getPlugin() instanceof PluginForHost) {
                    host = ((PluginForHost) captchaChallenge.getPlugin()).getHost();
                } else if (captchaChallenge.getPlugin() instanceof PluginForDecrypt) {
                    host = ((PluginForDecrypt) captchaChallenge.getPlugin()).getHost();
                }
                String trustID = (host + "_" + captchaChallenge.getTypeID()).toLowerCase(Locale.ENGLISH);
                if (StringUtils.isEmpty(captchaChallenge.getTypeID())) {
                    return;
                }
                job.getLogger().info("JACSolver handles " + job);
                job.getLogger().info("JAC: enabled: " + config.isEnabled() + " Has Method: " + JACMethod.hasMethod(captchaChallenge.getTypeID()));
                if (!config.isEnabled() || !JACMethod.hasMethod(captchaChallenge.getTypeID())) {
                    return;
                }
                job.getChallenge().sendStatsSolving(this);
                checkInterruption();
                final JAntiCaptcha jac = new JAntiCaptcha(captchaChallenge.getTypeID());
                checkInterruption();
                Image captchaImage;
                captchaImage = ImageProvider.read(captchaChallenge.getImageFile());
                checkInterruption();
                final Captcha captcha = jac.createCaptcha(captchaImage);
                checkInterruption();
                String captchaCode = jac.checkCaptcha(captchaChallenge.getImageFile(), captcha);
                if (StringUtils.isEmpty(captchaCode)) {
                    return;
                }
                if (jac.isExtern()) {
                    /* external captchaCode Response */
                    job.addAnswer(new CaptchaResponse(captchaChallenge, this, captchaCode, 100));
                } else {
                    /* internal captchaCode Response */
                    final LetterComperator[] lcs = captcha.getLetterComperators();
                    double vp = 0.0;
                    if (lcs == null) {
                    } else {
                        for (final LetterComperator element : lcs) {
                            if (element == null) {
                                vp = 0;
                                break;
                            }
                            vp += element.getValityPercent();
                        }
                        vp /= lcs.length;
                    }
                    int trust = 120 - (int) vp;
                    int orgTrust = trust;
                    // StatsManager.I().
                    synchronized (jacMethodTrustMap) {
                        Integer trustMap = jacMethodTrustMap.get(trustID);
                        if (trustMap != null) {
                            if (trust > trustMap) {
                                trust = 100;
                            }
                        }
                        synchronized (threshold) {
                            final AutoTrust trustValue = threshold.get(trustID);
                            if (trustValue != null) {
                                if (trust > trustValue.getValue() * _0_85) {
                                    trust = 100;
                                }
                            }
                        }
                    }
                    // we need to invert th
                    job.addAnswer(new JACCaptchaResponse(captchaChallenge, this, captchaCode, trust, orgTrust));
                }
            }
        } catch (IOException e) {
            job.getChallenge().sendStatsError(this, e);
            throw new SolverException(e);
        }
    }

    public void setMethodTrustThreshold(PluginForHost plugin, String method, int threshold) {
        final String trustID = (plugin.getHost() + "_" + method).toLowerCase(Locale.ENGLISH);
        synchronized (jacMethodTrustMap) {
            if (threshold < 0 || threshold > 100) {
                jacMethodTrustMap.remove(trustID);
            } else {
                jacMethodTrustMap.put(trustID, threshold);
            }
        }
    }

    @Override
    public boolean setValid(AbstractResponse<?> response) {
        if (response.getSolver() != this) {
            return false;
        }
        if (response instanceof JACCaptchaResponse) {
            final int priority = ((JACCaptchaResponse) response).getUnmodifiedTrustValue();
            final Challenge<?> challenge = response.getChallenge();
            if (challenge instanceof BasicCaptchaChallenge) {
                final Plugin plugin = ((BasicCaptchaChallenge) challenge).getPlugin();
                final String trustID = (plugin.getHost() + "_" + challenge.getTypeID()).toLowerCase(Locale.ENGLISH);
                synchronized (threshold) {
                    AutoTrust trustValue = threshold.get(trustID);
                    if (trustValue == null) {
                        trustValue = new AutoTrust(priority);
                        threshold.put(trustID, trustValue);
                    }
                    trustValue.add(priority);
                    logger.info("New JAC Threshold for " + trustID + " : " + trustValue.getValue() + "(" + trustValue.getCounter() + ")");
                }
            }
        }
        return true;
    }

    @Override
    public boolean setInvalid(AbstractResponse<?> response) {
        if (response instanceof JACCaptchaResponse) {
            final int priority = ((JACCaptchaResponse) response).getUnmodifiedTrustValue();
            final Challenge<?> challenge = response.getChallenge();
            if (challenge instanceof BasicCaptchaChallenge) {
                final Plugin plugin = ((BasicCaptchaChallenge) challenge).getPlugin();
                final String trustID = (plugin.getHost() + "_" + challenge.getTypeID()).toLowerCase(Locale.ENGLISH);
                synchronized (threshold) {
                    final AutoTrust trustValue = threshold.get(trustID);
                    if (trustValue != null) {
                        logger.info("JAC Failure for " + trustID + "; : TrustValue " + priority + "; Dynamic Trust: " + trustValue.getValue() + "(" + trustValue.getCounter() + ") Detected: " + response.getValue());
                        // increase trustValue!
                        trustValue.add((int) (priority * (1d + (1d - _0_85) * 2)));
                        logger.info("New JAC Threshold for " + trustID + " : " + trustValue.getValue() + "(" + trustValue.getCounter() + ")");
                    }
                }
            }
            return true;
        }
        return false;
    }
}
