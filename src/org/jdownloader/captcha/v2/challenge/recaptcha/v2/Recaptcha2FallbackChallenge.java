package org.jdownloader.captcha.v2.challenge.recaptcha.v2;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;

import org.appwork.exceptions.WTFException;
import org.appwork.txtresource.TranslationFactory;
import org.appwork.utils.Application;
import org.appwork.utils.Hash;
import org.appwork.utils.IO;
import org.appwork.utils.Regex;
import org.appwork.utils.net.HTTPHeader;
import org.jdownloader.captcha.v2.AbstractResponse;

import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.html.Form;

public class Recaptcha2FallbackChallenge extends AbstractRecaptcha2FallbackChallenge {

    private final Browser iframe;
    private String        challenge;
    private String        payload;

    public Recaptcha2FallbackChallenge(RecaptchaV2Challenge challenge) {
        super(challenge);

        iframe = owner.getBr().cloneBrowser();
        load();
    }

    protected void load() {
        try {
            final String dataSiteKey = owner.getSiteKey();
            if (round == 1) {
                // iframe.getPage("http://www.google.com/recaptcha/api2/demo");
                iframe.getHeaders().put(new HTTPHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:37.0) Gecko/20100101 Firefox/37.0"));
                iframe.getHeaders().put(new HTTPHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"));

                if (useEnglish) {
                    iframe.getHeaders().put(new HTTPHeader("Accept-Language", "en"));
                } else {
                    iframe.getHeaders().put(new HTTPHeader("Accept-Language", TranslationFactory.getDesiredLanguage()));
                }
                iframe.getPage("http://www.google.com/recaptcha/api/fallback?k=" + dataSiteKey);
            }
            payload = Encoding.htmlDecode(iframe.getRegex("\"(/recaptcha/api2/payload[^\"]+)").getMatch(0));
            String message = Encoding.htmlDecode(iframe.getRegex("<label .*?class=\"fbc-imageselect-message-text\">(.*?)</label>").getMatch(0));
            if (message == null) {
                message = Encoding.htmlDecode(iframe.getRegex("<div .*?class=\"fbc-imageselect-message-error\">(.*?)</div>").getMatch(0));
            }
            highlightedExplain = "#" + round + "/2: " + new Regex(message, "<strong>\\s*(.*?)\\s*</strong>").getMatch(0).replaceAll("<.*?>", "").replaceAll("\\s+", " ");
            if (message != null) {
                setExplain(message.replaceAll("<.*?>", "").replaceAll("\\s+", " "));
            }

            challenge = iframe.getRegex("name=\"c\"\\s+value=\\s*\"([^\"]+)").getMatch(0);
            setImageFile(Application.getResource("rc_" + System.currentTimeMillis() + ".jpg"));

            FileOutputStream fos = null;
            URLConnectionAdapter con = null;
            try {
                con = iframe.cloneBrowser().openGetConnection("http://www.google.com" + payload);
                fos = new FileOutputStream(getImageFile());
                IO.readStreamToOutputStream(-1, con.getInputStream(), fos, true);
            } finally {
                try {
                    fos.close();
                } catch (final Throwable ignore) {
                }
                try {
                    con.disconnect();
                } catch (final Throwable ignore) {
                }
            }
            if (!Application.isJared(null) && getExplainIcon(getExplain()) == null) {
                try {
                    String filename = new Regex(getExplain(), WITH_OF_ALL_THE).getMatch(0).replaceAll("[^\\w]", "");
                    File cache = Application.getResource("tmp/recaptcha/" + filename + "/" + Hash.getMD5(getImageBytes()) + ".jpg");
                    if (!cache.exists()) {
                        cache.getParentFile().mkdirs();
                        IO.writeToFile(cache, getImageBytes());
                    }
                } catch (Throwable e) {
                }
            }
        } catch (Throwable e) {
            throw new WTFException(e);
        }
    }

    public String getChallenge() {
        return challenge;
    }

    // private ArrayList<AbstractResponse<String>> responses = new ArrayList<AbstractResponse<String>>();

    @Override
    public boolean validateResponse(AbstractResponse<String> response) {
        try {
            if (response.getPriority() <= 0) {
                return false;
            }
            final String dataSiteKey = owner.getSiteKey();
            final Form form = iframe.getFormbyKey("c");
            String responses = "";
            final HashSet<String> dupe = new HashSet<String>();
            final String re = response.getValue().replaceAll("[^\\d]+", "");
            for (int i = 0; i < re.length(); i++) {
                final int num = Integer.parseInt(response.getValue().replaceAll("[^\\d]+", "").charAt(i) + "") - 1;
                if (dupe.add(Integer.toString(num))) {
                    responses += "&response=" + num;
                }
            }
            // iframe.getHeaders().put(new HTTPHeader("Origin", "http://www.google.com"));
            iframe.postPageRaw("http://www.google.com/recaptcha/api/fallback?k=" + dataSiteKey, "c=" + Encoding.urlEncode(form.getInputField("c").getValue()) + responses);
            token = iframe.getRegex("\"this\\.select\\(\\)\">(.*?)</textarea>").getMatch(0);
            // this.responses.add(response);
        } catch (Throwable e) {
            throw new WTFException(e);
        }
        // always return true. recaptchav2 fallback requires several captchas. we need to accept all answers. validation will be done
        // later
        return true;
    }

    private int round = 1;

    public void reload(int i, String lastResponse) throws IOException {
        round = i;
        load();
    }
}