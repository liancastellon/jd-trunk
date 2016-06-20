package org.jdownloader.captcha.v2.solver.solver9kw;

import java.util.HashMap;

import org.appwork.storage.config.annotations.AboutConfig;
import org.appwork.storage.config.annotations.DefaultBooleanValue;
import org.appwork.storage.config.annotations.DefaultIntValue;
import org.appwork.storage.config.annotations.DefaultJsonObject;
import org.appwork.storage.config.annotations.DefaultStringValue;
import org.appwork.storage.config.annotations.DescriptionForConfigEntry;
import org.appwork.storage.config.annotations.RequiresRestart;
import org.appwork.storage.config.annotations.SpinnerValidator;
import org.jdownloader.captcha.v2.ChallengeSolverConfig;

public interface Captcha9kwSettings extends ChallengeSolverConfig {
    @AboutConfig
    @DefaultStringValue("")
    @DescriptionForConfigEntry("Your (User) ApiKey from 9kw.eu")
    String getApiKey();

    void setApiKey(String jser);

    @AboutConfig
    @DefaultBooleanValue(false)
    @DescriptionForConfigEntry("Activate the debugmode for 9kw.eu service")
    boolean isDebug();

    void setDebug(boolean b);

    @AboutConfig
    @DefaultBooleanValue(false)
    @DescriptionForConfigEntry("Activate the Mouse Captchas")
    boolean ismouse();

    void setmouse(boolean b);

    @AboutConfig
    @DefaultBooleanValue(true)
    boolean isEnabledGlobally();

    void setEnabledGlobally(boolean b);

    @AboutConfig
    @DefaultBooleanValue(false)
    @DescriptionForConfigEntry("Activate the Puzzle Captchas")
    boolean ispuzzle();

    void setpuzzle(boolean b);

    @AboutConfig
    @DefaultBooleanValue(false)
    @DescriptionForConfigEntry("Activate the Slider Captchas")
    boolean isslider();

    void setslider(boolean b);

    @AboutConfig
    @DefaultStringValue("")
    @DescriptionForConfigEntry("Hosteroptions for 9kw.eu")
    String gethosteroptions();

    void sethosteroptions(String jser);

    @AboutConfig
    @DefaultBooleanValue(false)
    @DescriptionForConfigEntry("Confirm option for captchas (Cost +6)")
    boolean isconfirm();

    void setconfirm(boolean b);

    @AboutConfig
    @DefaultBooleanValue(false)
    @DescriptionForConfigEntry("Confirm option for mouse captchas (Cost +6)")
    boolean ismouseconfirm();

    void setmouseconfirm(boolean b);

    @AboutConfig
    @DefaultIntValue(0)
    @SpinnerValidator(min = 0, max = 20)
    @DescriptionForConfigEntry("More priority for captchas (Cost +1-20)")
    int getprio();

    void setprio(int seconds);

    @AboutConfig
    @DefaultStringValue("")
    @DescriptionForConfigEntry("Captcha whitelist for hoster with prio")
    String getwhitelistprio();

    void setwhitelistprio(String jser);

    @AboutConfig
    @DefaultStringValue("")
    @DescriptionForConfigEntry("Captcha blacklist for hoster with prio")
    String getblacklistprio();

    void setblacklistprio(String jser);

    @AboutConfig
    @DefaultBooleanValue(true)
    @DescriptionForConfigEntry("Activate the blacklist with prio")
    boolean getblacklistpriocheck();

    void setblacklistpriocheck(boolean b);

    @AboutConfig
    @DefaultBooleanValue(true)
    @DescriptionForConfigEntry("Activate the whitelist with prio")
    boolean getwhitelistpriocheck();

    void setwhitelistpriocheck(boolean b);

    @AboutConfig
    @DefaultIntValue(0)
    @SpinnerValidator(min = 0, max = 9999)
    @DescriptionForConfigEntry("Max. Captchas per hour")
    int gethour();

    void sethour(int seconds);

    @AboutConfig
    @DefaultIntValue(0)
    @SpinnerValidator(min = 0, max = 9999)
    @DescriptionForConfigEntry("Max. Captchas per minute")
    int getminute();

    void setminute(int seconds);

    @AboutConfig
    @DefaultBooleanValue(true)
    @DescriptionForConfigEntry("Only https requests to 9kw.eu")
    boolean ishttps();

    void sethttps(boolean b);

    @AboutConfig
    @DefaultStringValue("")
    @DescriptionForConfigEntry("Captcha whitelist for hoster")
    String getwhitelist();

    void setwhitelist(String jser);

    @AboutConfig
    @DefaultStringValue("")
    @DescriptionForConfigEntry("Captcha blacklist for hoster")
    String getblacklist();

    void setblacklist(String jser);

    @AboutConfig
    @DefaultBooleanValue(true)
    @DescriptionForConfigEntry("Activate the blacklist")
    boolean getblacklistcheck();

    void setblacklistcheck(boolean b);

    @AboutConfig
    @DefaultBooleanValue(true)
    @DescriptionForConfigEntry("Activate the whitelist")
    boolean getwhitelistcheck();

    void setwhitelistcheck(boolean b);

    @AboutConfig
    @DefaultBooleanValue(true)
    @DescriptionForConfigEntry("Activate the Captcha Feedback")
    boolean isfeedback();

    void setfeedback(boolean b);

    @AboutConfig
    @DefaultBooleanValue(false)
    @DescriptionForConfigEntry("Activate the Captcha Feedback for Recaptchav2")
    boolean isfeedbackrecaptchav2();

    void setfeedbackrecaptchav2(boolean b);

    @AboutConfig
    @DefaultBooleanValue(false)
    @DescriptionForConfigEntry("Activate the option selfsolve")
    boolean isSelfsolve();

    void setSelfsolve(boolean b);

    @AboutConfig
    @RequiresRestart("A JDownloader Restart is required after changes")
    @DefaultIntValue(1)
    @SpinnerValidator(min = 0, max = 10)
    @DescriptionForConfigEntry("Max. Captchas Parallel")
    int getThreadpoolSize();

    void setThreadpoolSize(int size);

    @AboutConfig
    @DefaultIntValue(600000)
    @SpinnerValidator(min = 60000, max = 3999000)
    @org.appwork.storage.config.annotations.DescriptionForConfigEntry("Other max. timeout only for 9kw Service")
    int getCaptchaOther9kwTimeout();

    void setCaptchaOther9kwTimeout(int ms);

    @AboutConfig
    @DefaultStringValue("")
    @DescriptionForConfigEntry("Captcha whitelist for hoster with timeout")
    String getwhitelisttimeout();

    void setwhitelisttimeout(String jser);

    @AboutConfig
    @DefaultStringValue("")
    @DescriptionForConfigEntry("Captcha blacklist for hoster with timeout")
    String getblacklisttimeout();

    void setblacklisttimeout(String jser);

    @AboutConfig
    @DefaultBooleanValue(true)
    @DescriptionForConfigEntry("Activate the blacklist with timeout")
    boolean getblacklisttimeoutcheck();

    void setblacklisttimeoutcheck(boolean b);

    @AboutConfig
    @DefaultBooleanValue(true)
    @DescriptionForConfigEntry("Activate the whitelist with timeout")
    boolean getwhitelisttimeoutcheck();

    void setwhitelisttimeoutcheck(boolean b);

    @AboutConfig
    @DefaultBooleanValue(true)
    @DescriptionForConfigEntry("Activate the lowcredits dialog")
    boolean getlowcredits();

    void setlowcredits(boolean b);

    @AboutConfig
    @DefaultBooleanValue(false)
    @DescriptionForConfigEntry("Activate the high queue dialog")
    boolean gethighqueue();

    void sethighqueue(boolean b);

    @AboutConfig
    @DefaultBooleanValue(true)
    @DescriptionForConfigEntry("Activate the badfeedbacks dialog")
    boolean getbadfeedbacks();

    void setbadfeedbacks(boolean b);

    @AboutConfig
    @DefaultBooleanValue(false)
    @DescriptionForConfigEntry("Activate the badnofeedbacks dialog")
    boolean getbadnofeedbacks();

    void setbadnofeedbacks(boolean b);

    @AboutConfig
    @DefaultBooleanValue(true)
    @DescriptionForConfigEntry("Activate the badtimeout dialog")
    boolean getbadtimeout();

    void setbadtimeout(boolean b);

    @AboutConfig
    @DefaultBooleanValue(false)
    @DescriptionForConfigEntry("Activate the errors/uploads dialog")
    boolean getbaderrorsanduploads();

    void setbaderrorsanduploads(boolean b);

    @AboutConfig
    @DefaultIntValue(600000)
    @SpinnerValidator(min = 60000, max = 3999000)
    @DescriptionForConfigEntry("Default Timeout in ms")
    int getDefaultTimeout();

    void setDefaultTimeout(int ms);

    @AboutConfig
    @DefaultIntValue(180)
    @SpinnerValidator(min = 5, max = 1800)
    @DescriptionForConfigEntry("Interval for the notifications")
    int getDefaultTimeoutNotification();

    void setDefaultTimeoutNotification(int ms);

    @AboutConfig
    @DefaultJsonObject("{\"jdownloader.org\":60000}")
    @DescriptionForConfigEntry("Host bound Waittime before using CES. Use CaptchaExchangeChanceToSkipBubbleTimeout for a global timeout")
    HashMap<String, Integer> getBubbleTimeoutByHostMap();

    void setBubbleTimeoutByHostMap(HashMap<String, Integer> map);

    @AboutConfig
    @DefaultIntValue(4)
    @SpinnerValidator(min = 3, max = 50)
    @DescriptionForConfigEntry("Max. captchas per download")
    int getmaxcaptchaperdl();

    void setmaxcaptchaperdl(int size);

    @AboutConfig
    @DefaultBooleanValue(false)
    @DescriptionForConfigEntry("Activate max. captchas per download")
    boolean getmaxcaptcha();

    void setmaxcaptcha(boolean b);
}
