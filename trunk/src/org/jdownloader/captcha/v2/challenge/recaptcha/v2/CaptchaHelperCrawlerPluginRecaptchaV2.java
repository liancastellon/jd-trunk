package org.jdownloader.captcha.v2.challenge.recaptcha.v2;

import java.io.IOException;

import org.appwork.utils.logging2.LogSource;
import org.jdownloader.captcha.blacklist.BlacklistEntry;
import org.jdownloader.captcha.blacklist.BlockAllCrawlerCaptchasEntry;
import org.jdownloader.captcha.blacklist.BlockCrawlerCaptchasByHost;
import org.jdownloader.captcha.blacklist.BlockCrawlerCaptchasByPackage;
import org.jdownloader.captcha.blacklist.CaptchaBlackList;
import org.jdownloader.captcha.v2.AbstractResponse;
import org.jdownloader.captcha.v2.ChallengeResponseController;
import org.jdownloader.captcha.v2.solverjob.SolverJob;

import jd.controlling.captcha.SkipException;
import jd.controlling.downloadcontroller.SingleDownloadController;
import jd.controlling.linkcollector.LinkCollector;
import jd.controlling.linkcrawler.LinkCrawlerThread;
import jd.http.Browser;
import jd.plugins.CaptchaException;
import jd.plugins.DecrypterException;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;

public class CaptchaHelperCrawlerPluginRecaptchaV2 extends AbstractCaptchaHelperRecaptchaV2<PluginForDecrypt> {

    public CaptchaHelperCrawlerPluginRecaptchaV2(PluginForDecrypt plugin, Browser br, String siteKey) {
        super(plugin, br, siteKey);

    }

    public CaptchaHelperCrawlerPluginRecaptchaV2(PluginForDecrypt plugin, Browser br) {
        this(plugin, br, null);
    }

    public String getToken() throws PluginException, InterruptedException, DecrypterException {
        runDdosPrevention();
        if (Thread.currentThread() instanceof SingleDownloadController) {
            logger.severe("PluginForDecrypt.getCaptchaCode inside SingleDownloadController!?");
        }
        String apiKey = siteKey;
        if (apiKey == null) {
            apiKey = getRecaptchaV2ApiKey();
            if (apiKey == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "RecaptchaV2 API Key can not be found");
            }
        }
        final PluginForDecrypt plugin = getPlugin();
        final RecaptchaV2Challenge c = new RecaptchaV2Challenge(apiKey, plugin, getSiteDomain(), getSiteUrl());
        c.setTimeout(plugin.getCaptchaTimeout());
        plugin.invalidateLastChallengeResponse();
        final BlacklistEntry<?> blackListEntry = CaptchaBlackList.getInstance().matches(c);
        if (blackListEntry != null) {
            logger.warning("Cancel. Blacklist Matching");
            throw new CaptchaException(blackListEntry);
        }
        try {
            SolverJob<String> firstJob = ChallengeResponseController.getInstance().handle(c);
            if (c.getResult() != null) {

                if (c.getResult().size() == 1 && c.getResult().get(0).getChallenge() instanceof Recaptcha2FallbackChallenge) {
                    logger.info("2 Step Recaptcha v2 round #2");
                    final Recaptcha2FallbackChallenge challenge = ((Recaptcha2FallbackChallenge) c.getResult().get(0).getChallenge());
                    try {
                        challenge.reload(2, c.getResult().get(0).getValue());
                        SolverJob<String> secondJob = ChallengeResponseController.getInstance().handle(challenge);
                        if (challenge.getToken() != null) {
                            firstJob.validate();
                            secondJob.validate();
                            return challenge.getToken();
                            // challenge.evaluate()
                        } else {
                            firstJob.invalidate();
                            secondJob.invalidate();
                            throw new DecrypterException(DecrypterException.CAPTCHA);
                        }
                    } catch (IOException e) {
                        LogSource.exception(logger, e);
                        throw new DecrypterException(DecrypterException.CAPTCHA);
                    }
                }
            }
            if (!c.isSolved()) {
                throw new DecrypterException(DecrypterException.CAPTCHA);
            }

            if (c.getResult() != null) {
                for (AbstractResponse<String> r : c.getResult()) {
                    if (r.getChallenge() instanceof AbstractRecaptcha2FallbackChallenge) {
                        return ((AbstractRecaptcha2FallbackChallenge) r.getChallenge()).getToken();
                    }

                }
            }
            if (!c.isCaptchaResponseValid()) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Captcha reponse value did not validate!");
            }
            return c.getResult().getValue();
        } catch (InterruptedException e) {
            LogSource.exception(logger, e);
            throw e;
        } catch (SkipException e) {
            LogSource.exception(logger, e);
            switch (e.getSkipRequest()) {
            case BLOCK_ALL_CAPTCHAS:
                CaptchaBlackList.getInstance().add(new BlockAllCrawlerCaptchasEntry(plugin.getCrawler()));
                break;
            case BLOCK_HOSTER:
                CaptchaBlackList.getInstance().add(new BlockCrawlerCaptchasByHost(plugin.getCrawler(), plugin.getHost()));
                break;
            case BLOCK_PACKAGE:
                CaptchaBlackList.getInstance().add(new BlockCrawlerCaptchasByPackage(plugin.getCrawler(), plugin.getCurrentLink()));
                break;
            case REFRESH:
                break;
            case STOP_CURRENT_ACTION:
                if (Thread.currentThread() instanceof LinkCrawlerThread) {
                    LinkCollector.getInstance().abort();
                    // Just to be sure
                    CaptchaBlackList.getInstance().add(new BlockAllCrawlerCaptchasEntry(plugin.getCrawler()));
                }
                break;
            default:
                break;
            }
            throw new CaptchaException(e.getSkipRequest());
        }
    }

}
