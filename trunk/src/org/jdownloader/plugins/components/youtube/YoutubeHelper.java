package org.jdownloader.plugins.components.youtube;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.regex.Pattern;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.appwork.exceptions.WTFException;
import org.appwork.storage.JSonStorage;
import org.appwork.storage.TypeRef;
import org.appwork.txtresource.TranslationFactory;
import org.appwork.utils.Application;
import org.appwork.utils.Exceptions;
import org.appwork.utils.Hash;
import org.appwork.utils.Regex;
import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.TimeFormatter;
import org.appwork.utils.logging2.LogInterface;
import org.appwork.utils.logging2.LogSource;
import org.appwork.utils.logging2.extmanager.Log;
import org.appwork.utils.net.httpconnection.HTTPProxy;
import org.appwork.utils.net.httpconnection.HTTPProxyStorable;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.logging.LogController;
import org.jdownloader.plugins.components.google.GoogleHelper;
import org.jdownloader.plugins.components.hls.HlsContainer;
import org.jdownloader.plugins.components.youtube.YoutubeReplacer.DataSource;
import org.jdownloader.plugins.components.youtube.itag.AudioCodec;
import org.jdownloader.plugins.components.youtube.itag.VideoCodec;
import org.jdownloader.plugins.components.youtube.itag.VideoFrameRate;
import org.jdownloader.plugins.components.youtube.itag.VideoResolution;
import org.jdownloader.plugins.components.youtube.itag.YoutubeITAG;
import org.jdownloader.plugins.components.youtube.variants.AbstractVariant;
import org.jdownloader.plugins.components.youtube.variants.AudioInterface;
import org.jdownloader.plugins.components.youtube.variants.FileContainer;
import org.jdownloader.plugins.components.youtube.variants.SubtitleVariant;
import org.jdownloader.plugins.components.youtube.variants.VariantBase;
import org.jdownloader.plugins.components.youtube.variants.VideoInterface;
import org.jdownloader.plugins.components.youtube.variants.VideoVariant;
import org.jdownloader.plugins.components.youtube.variants.YoutubeSubtitleStorable;
import org.jdownloader.plugins.config.PluginJsonConfig;
import org.jdownloader.settings.staticreferences.CFG_YOUTUBE;
import org.jdownloader.statistics.StatsManager;
import org.jdownloader.statistics.StatsManager.CollectionName;
import org.jdownloader.updatev2.FilterList;
import org.jdownloader.updatev2.FilterList.Type;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import jd.controlling.AccountController;
import jd.controlling.accountchecker.AccountCheckerThread;
import jd.controlling.proxy.ProxyController;
import jd.controlling.proxy.SingleBasicProxySelectorImpl;
import jd.http.Browser;
import jd.http.Browser.BrowserException;
import jd.http.Cookie;
import jd.http.Cookies;
import jd.http.QueryInfo;
import jd.http.Request;
import jd.nutils.encoding.Encoding;
import jd.parser.html.Form;
import jd.plugins.Account;
import jd.plugins.DownloadLink;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;

public class YoutubeHelper {
    static {
        final YoutubeConfig cfg = PluginJsonConfig.get(YoutubeConfig.class);
        VideoFrameRate.FPS_60.setRating(cfg.getRating60Fps() / 100d);
        FileContainer.MP4.setQualityRating(cfg.getRatingContainerMP4() / 10d);
        FileContainer.WEBM.setQualityRating(cfg.getRatingContainerWEBM() / 10d);
        AudioCodec.AAC.setRating(cfg.getRatingContainerAAC() / 10000d);
        FileContainer.M4A.setQualityRating(cfg.getRatingContainerM4A() / 10000d);
        AudioCodec.MP3.setRating(cfg.getRatingContainerMP3() / 10000d);
        VideoCodec.VP8.setRating(cfg.getRatingCodecVP8());
        final int vp9 = cfg.getRatingCodecVP9();
        VideoCodec.H263.setRating(cfg.getRatingCodecH263());
        VideoCodec.H264.setRating(cfg.getRatingCodecH264());
        VideoCodec.VP9_WORSE_PROFILE_1.setRating(vp9 - 1);
        VideoCodec.VP9.setRating(vp9);
        VideoCodec.VP9_BETTER_PROFILE_1.setRating(vp9 + 1);
        VideoCodec.VP9_BETTER_PROFILE_2.setRating(vp9 + 2);
        String filepattern = cfg.getFilenamePattern();
        if (filepattern != null && !"*videoname* (*quality*).*ext*".equals(filepattern)) {
            // convert old format
            cfg.setVideoFilenamePattern(filepattern);
            cfg.setAudioFilenamePattern(filepattern);

            cfg.setFilenamePattern(null);
        }

        if (cfg.isProxyEnabled()) {

        }
        HTTPProxyStorable proxyStorable = cfg.getProxy();
        if (proxyStorable != null) {
            HTTPProxy proxy = HTTPProxy.getHTTPProxy(proxyStorable);
            SingleBasicProxySelectorImpl selector = new SingleBasicProxySelectorImpl(proxy);
            FilterList filterlist = new FilterList(Type.WHITELIST, new String[] { "youtube.com" });
            selector.setEnabled(cfg.isProxyEnabled());
            selector.setFilter(filterlist);
            ProxyController.getInstance().addProxy(selector);
            cfg.setProxy(null);
        }

    }

    private static final String REGEX_DASHMPD_FROM_JSPLAYER_SETUP       = "\"dashmpd\"\\s*:\\s*(\".*?\")";

    private static final String REGEX_ADAPTIVE_FMTS_FROM_JSPLAYER_SETUP = "\"adaptive_fmts\"\\s*:\\s*(\".*?\")";

    private static final String REGEX_FMT_MAP_FROM_JSPLAYER_SETUP       = "\"url_encoded_fmt_stream_map\"\\s*:\\s*(\".*?\")";

    public static final String  PAID_VIDEO                              = "Paid Video:";

    public static final String  YT_CHANNEL_ID                           = "YT_CHANNEL_ID";

    public static final String  YT_DURATION                             = "YT_DURATION";

    public static final String  YT_DATE_UPDATE                          = "YT_DATE_UPDATE";

    public static final String  YT_GOOGLE_PLUS_ID                       = "YT_GOOGLE_PLUS_ID";

    private Browser             br;

    public Browser getBr() {
        return br;
    }

    public void setBr(Browser br) {
        this.br = br;
    }

    private final YoutubeConfig         cfg;

    private final LogInterface          logger;
    private String                      base;
                                        // private List<YoutubeBasicVariant> variants;

    // public List<YoutubeBasicVariant> getVariants() {
    // return variants;
    // }

    // private Map<String, YoutubeBasicVariant> variantsMap;

    // public Map<String, YoutubeBasicVariant> getVariantsMap() {
    // return variantsMap;
    // }

    public static LogSource             LOGGER   = LogController.getInstance().getLogger(YoutubeHelper.class.getName());
    public static List<YoutubeReplacer> REPLACER = new ArrayList<YoutubeReplacer>();

    static {
        REPLACER.add(new YoutubeReplacer("GROUP") {

            @Override
            protected String getValue(DownloadLink link, YoutubeHelper helper, String mod) {

                AbstractVariant variant = AbstractVariant.get(link);
                try {
                    return variant.getGroup().getLabel();
                } catch (Throwable e) {
                    // old variant
                    LOGGER.log(e);
                    return "[INVALID LINK!]";
                }
            }

            @Override
            public String getDescription() {
                return _GUI.T.YoutubeHelper_getDescription_group();
            }

        });

        REPLACER.add(new YoutubeReplacer("ITAG") {

            @Override
            protected String getValue(DownloadLink link, YoutubeHelper helper, String mod) {
                AbstractVariant variant = AbstractVariant.get(link);

                try {
                    if ("audio".equalsIgnoreCase(mod)) {

                        return variant.getiTagAudioOrVideoItagEquivalent().name();
                    } else if ("audioID".equalsIgnoreCase(mod)) {
                        return variant.getiTagAudioOrVideoItagEquivalent().getITAG() + "";
                    } else if ("video".equalsIgnoreCase(mod)) {
                        return variant.getiTagVideo().name();
                    } else if ("videoID".equalsIgnoreCase(mod)) {
                        return variant.getiTagVideo().getITAG() + "";
                    }

                } catch (Throwable e) {
                }
                return "";
            }

            @Override
            public String getDescription() {
                return _GUI.T.YoutubeHelper_getDescription_itag();
            }

        });

        REPLACER.add(new YoutubeReplacer("VARIANT", "V") {

            @Override
            protected String getValue(DownloadLink link, YoutubeHelper helper, String mod) {
                if ("name".equalsIgnoreCase(mod)) {
                    return AbstractVariant.get(link)._getName(link);
                } else {
                    return AbstractVariant.get(link)._getUniqueId();
                }

            }

            @Override
            public String getDescription() {
                return _GUI.T.YoutubeHelper_getDescription_variantid2();
            }

        });
        REPLACER.add(new YoutubeReplacer("QUALITY") {
            @Override
            public String getDescription() {
                return _GUI.T.YoutubeHelper_getDescription_quality();
            }

            @Override
            public String replace(String name, YoutubeHelper helper, DownloadLink link) {
                return super.replace(name, helper, link);
            }

            @Override
            protected String getValue(DownloadLink link, YoutubeHelper helper, String mod) {
                AbstractVariant variant = AbstractVariant.get(link);
                try {
                    return variant.getFileNameQualityTag();
                } catch (Throwable e) {
                    // old variant
                    LOGGER.log(e);
                    return "[INVALID LINK!]";
                }
            }

        });
        REPLACER.add(new YoutubeReplacer("COLLECTION", "COL") {
            @Override
            public String getDescription() {
                return _GUI.T.YoutubeHelper_getDescription_collection();
            }

            @Override
            protected String getValue(DownloadLink link, YoutubeHelper helper, String mod) {
                return link.getStringProperty(YoutubeHelper.YT_COLLECTION, "");
            }

        });
        REPLACER.add(new YoutubeReplacer("VIDEOID", "ID") {
            @Override
            public String getDescription() {
                return _GUI.T.YoutubeHelper_getDescription_id();
            }

            @Override
            protected String getValue(DownloadLink link, YoutubeHelper helper, String mod) {
                return link.getStringProperty(YoutubeHelper.YT_ID, "");
            }

        });
        REPLACER.add(new YoutubeReplacer("360", "SPHERICAL") {
            @Override
            public String getDescription() {
                return _GUI.T.YoutubeHelper_getDescription_spherical();
            }

            @Override
            protected String getValue(DownloadLink link, YoutubeHelper helper, String mod) {
                AbstractVariant variant = AbstractVariant.get(link);
                if (variant != null && variant instanceof VideoVariant) {
                    switch (((VideoVariant) variant).getProjection()) {
                    case SPHERICAL:
                    case SPHERICAL_3D:
                        return "Spherical";
                    }

                }

                return "";
            }

        });
        REPLACER.add(new YoutubeReplacer("THREED", "3D") {
            @Override
            public String getDescription() {
                return _GUI.T.YoutubeHelper_getDescription_3d();
            }

            @Override
            protected String getValue(DownloadLink link, YoutubeHelper helper, String mod) {
                AbstractVariant variant = AbstractVariant.get(link);
                if (variant != null && variant instanceof VideoVariant) {
                    switch (((VideoVariant) variant).getProjection()) {
                    case ANAGLYPH_3D:
                    case SPHERICAL_3D:
                        return "3D";
                    }

                }

                return "";
            }

        });
        REPLACER.add(new YoutubeReplacer("FPS") {
            @Override
            public String getDescription() {
                return _GUI.T.YoutubeHelper_getDescription_fps();
            }

            @Override
            protected String getValue(DownloadLink link, YoutubeHelper helper, String mod) {
                AbstractVariant variant = AbstractVariant.get(link);
                if (variant != null && variant instanceof VideoVariant) {

                    int fps = ((VideoInterface) variant).getVideoFrameRate();
                    if (fps > 0) {
                        return fps + "";
                    }

                }

                return "";
            }

        });

        REPLACER.add(new YoutubeReplacer("EXT", "EXTENSION") {
            @Override
            public String getDescription() {
                return _GUI.T.YoutubeHelper_getDescription_extension();
            }

            @Override
            protected String getValue(DownloadLink link, YoutubeHelper helper, String mod) {
                return link.getStringProperty(YoutubeHelper.YT_EXT, "unknown");
            }

        });

        REPLACER.add(new YoutubeReplacer("HEIGHT", "H") {
            @Override
            public String getDescription() {
                return _GUI.T.YoutubeHelper_getDescription_height();
            }

            @Override
            protected String getValue(DownloadLink link, YoutubeHelper helper, String mod) {
                AbstractVariant variant = AbstractVariant.get(link);
                if (variant != null && variant instanceof VideoVariant) {
                    return ((VideoInterface) variant).getVideoHeight() + "";
                }
                return "";
            }

        });
        REPLACER.add(new YoutubeReplacer("WIDTH", "W") {
            @Override
            public String getDescription() {
                return _GUI.T.YoutubeHelper_getDescription_width();
            }

            @Override
            protected String getValue(DownloadLink link, YoutubeHelper helper, String mod) {
                AbstractVariant variant = AbstractVariant.get(link);
                if (variant != null && variant instanceof VideoVariant) {
                    return ((VideoInterface) variant).getVideoWidth() + "";
                }
                return "";
            }

        });

        REPLACER.add(new YoutubeReplacer("USERNAME", "USER") {

            @Override
            protected String getValue(DownloadLink link, YoutubeHelper helper, String mod) {
                return link.getStringProperty(YoutubeHelper.YT_USER, "");
            }

            @Override
            public String getDescription() {
                return _GUI.T.YoutubeHelper_getDescription_user();
            }

        });
        REPLACER.add(new YoutubeReplacer("CHANNEL_ID") {

            @Override
            protected String getValue(DownloadLink link, YoutubeHelper helper, String mod) {
                return link.getStringProperty(YoutubeHelper.YT_CHANNEL_ID, "");
            }

            @Override
            public String getDescription() {
                return _GUI.T.YoutubeHelper_getDescription_channel_id();
            }

        });

        REPLACER.add(new YoutubeReplacer("GOOGLEPLUS_ID") {

            @Override
            protected String getValue(DownloadLink link, YoutubeHelper helper, String mod) {
                return link.getStringProperty(YoutubeHelper.YT_GOOGLE_PLUS_ID, "");
            }

            public DataSource getDataSource() {
                return DataSource.API_USERS;
            }

            @Override
            public String getDescription() {
                return _GUI.T.YoutubeHelper_getDescription_googleplus_id();
            }

        });
        REPLACER.add(new YoutubeReplacer("DURATION") {

            @Override
            protected String getValue(DownloadLink link, YoutubeHelper helper, String mod) {
                int ms = link.getIntegerProperty(YoutubeHelper.YT_DURATION, -1);
                if (ms <= 0) {
                    return "";
                }
                if (StringUtils.isEmpty(mod)) {

                    return TimeFormatter.formatMilliSeconds(ms, 0);
                } else {
                    return (ms / 1000) + "s";
                }

            }

            @Override
            public String getDescription() {
                return _GUI.T.YoutubeHelper_getDescription_duration();
            }

        });
        REPLACER.add(new YoutubeReplacer("CHANNEL", "CHANNELNAME") {

            @Override
            protected String getValue(DownloadLink link, YoutubeHelper helper, String mod) {
                return link.getStringProperty(YoutubeHelper.YT_CHANNEL, "");
            }

            @Override
            public String getDescription() {
                return _GUI.T.YoutubeHelper_getDescription_channel();
            }

        });

        REPLACER.add(new YoutubeReplacer("VIDEONAME", "VIDEO_NAME", "TITLE") {

            @Override
            protected String getValue(DownloadLink link, YoutubeHelper helper, String mod) {
                return link.getStringProperty(YoutubeHelper.YT_TITLE, "");
            }

            @Override
            public String getDescription() {
                return _GUI.T.YoutubeHelper_getDescription_title();
            }

        });
        REPLACER.add(new YoutubeReplacer("DATE", "DATE_TIME") {
            @Override
            public String getDescription() {
                return _GUI.T.YoutubeHelper_getDescription_date();
            }

            @Override
            protected String getValue(DownloadLink link, YoutubeHelper helper, String mod) {
                // date

                DateFormat formatter = DateFormat.getDateInstance(DateFormat.LONG, TranslationFactory.getDesiredLocale());

                if (StringUtils.isNotEmpty(mod)) {
                    try {
                        formatter = new SimpleDateFormat(mod, TranslationFactory.getDesiredLocale());
                    } catch (Throwable e) {
                        LOGGER.log(e);

                    }
                }
                long timestamp = link.getLongProperty(YoutubeHelper.YT_DATE, -1);
                if (timestamp > 0) {
                    Log.info(" Youtube Replace Date " + mod + " - " + timestamp + " > " + formatter.format(timestamp));
                }
                return timestamp > 0 ? formatter.format(timestamp) : "";
            }

        });

        REPLACER.add(new YoutubeReplacer("DATE_UDPATE") {
            @Override
            public String getDescription() {
                return _GUI.T.YoutubeHelper_getDescription_date();
            }

            public DataSource getDataSource() {
                return DataSource.API_VIDEOS;
            }

            @Override
            protected String getValue(DownloadLink link, YoutubeHelper helper, String mod) {
                // date

                DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG, TranslationFactory.getDesiredLocale());

                if (StringUtils.isNotEmpty(mod)) {
                    try {
                        formatter = new SimpleDateFormat(mod);
                    } catch (Throwable e) {
                        LOGGER.log(e);

                    }
                }
                long timestamp = link.getLongProperty(YoutubeHelper.YT_DATE_UPDATE, -1);
                return timestamp > 0 ? formatter.format(timestamp) : "";
            }

        });
        REPLACER.add(new YoutubeReplacer("VIDEO_CODEC") {
            @Override
            public String getDescription() {
                return _GUI.T.YoutubeHelper_getDescription_videoCodec();
            }

            public DataSource getDataSource() {
                return DataSource.WEBSITE;
            }

            @Override
            protected String getValue(DownloadLink link, YoutubeHelper helper, String mod) {

                AbstractVariant variant = AbstractVariant.get(link);
                if (variant != null && variant instanceof VideoVariant) {
                    VideoCodec v = ((VideoInterface) variant).getVideoCodec();
                    if (v != null) {
                        return v.getLabel();
                    }
                }
                return "";
            }

        });
        REPLACER.add(new YoutubeReplacer("RESOLUTION") {
            @Override
            public String getDescription() {
                return _GUI.T.YoutubeHelper_getDescription_resolution();
            }

            public DataSource getDataSource() {
                return DataSource.WEBSITE;
            }

            @Override
            protected String getValue(DownloadLink link, YoutubeHelper helper, String mod) {
                AbstractVariant variant = AbstractVariant.get(link);
                if (variant != null && variant instanceof VideoVariant) {
                    VideoResolution v = ((VideoInterface) variant).getVideoResolution();
                    if (v != null) {
                        return v.getLabel();
                    }
                }
                return "";
            }

        });
        REPLACER.add(new YoutubeReplacer("BESTRESOLUTION") {
            @Override
            public String getDescription() {
                return _GUI.T.YoutubeHelper_getDescription_resolution_best();
            }

            public DataSource getDataSource() {
                return DataSource.WEBSITE;
            }

            @Override
            protected String getValue(DownloadLink link, YoutubeHelper helper, String mod) {
                // date
                String var = link.getStringProperty(YoutubeHelper.YT_BEST_VIDEO, "");
                if (var == null) {
                    return "";
                }
                try {
                    return YoutubeITAG.valueOf(var).getVideoResolution().getLabel();

                } catch (Throwable e) {
                    // old variant
                    LOGGER.log(e);
                    return "[INVALID LINK!]";
                }
            }

        });
        REPLACER.add(new YoutubeReplacer("AUDIO_CODEC") {
            @Override
            public String getDescription() {
                return _GUI.T.YoutubeHelper_getDescription_audioCodec();
            }

            public DataSource getDataSource() {
                return DataSource.WEBSITE;
            }

            @Override
            protected String getValue(DownloadLink link, YoutubeHelper helper, String mod) {

                AbstractVariant variant = AbstractVariant.get(link);

                if (variant != null) {
                    if (variant instanceof AudioInterface) {
                        return ((AudioInterface) variant).getAudioCodec().getLabel();
                    }

                }

                return "";

            }

        });
        REPLACER.add(new YoutubeReplacer("LNG") {
            @Override
            public String getDescription() {
                return _GUI.T.YoutubeHelper_getDescription_language();
            }

            public DataSource getDataSource() {
                return DataSource.WEBSITE;
            }

            @Override
            protected String getValue(DownloadLink link, YoutubeHelper helper, String mod) {
                AbstractVariant variant = AbstractVariant.get(link);

                if (variant != null) {
                    if (variant instanceof SubtitleVariant) {
                        if ("display".equalsIgnoreCase(mod)) {
                            return ((SubtitleVariant) variant).getGenericInfo()._getLocale().getDisplayLanguage();
                        } else {
                            return ((SubtitleVariant) variant).getGenericInfo().getLanguage();
                        }

                    }

                }

                return "";
            }

        });
        REPLACER.add(new YoutubeReplacer("AUDIO_BITRATE") {
            @Override
            public String getDescription() {
                return _GUI.T.YoutubeHelper_getDescription_audioQuality();
            }

            public DataSource getDataSource() {
                return DataSource.WEBSITE;
            }

            @Override
            protected String getValue(DownloadLink link, YoutubeHelper helper, String mod) {
                AbstractVariant variant = AbstractVariant.get(link);

                if (variant != null) {
                    if (variant instanceof AudioInterface) {
                        return ((AudioInterface) variant).getAudioBitrate().getKbit() + "";
                    }

                }

                return "";
            }

        });

        REPLACER.add(new YoutubeReplacer("VIDEONUMBER") {
            @Override
            public String getDescription() {
                return _GUI.T.YoutubeHelper_getDescription_videonumber();
            }

            @Override
            protected String getValue(DownloadLink link, YoutubeHelper helper, String mod) {

                // playlistnumber

                if (StringUtils.isEmpty(mod)) {
                    mod = "0000";
                }
                DecimalFormat df;
                try {
                    df = new DecimalFormat(mod);
                } catch (Throwable e) {
                    LOGGER.log(e);
                    df = new DecimalFormat("0000");
                }
                int playlistNumber = link.getIntegerProperty(YoutubeHelper.YT_PLAYLIST_INT, -1);
                return playlistNumber >= 0 ? df.format(playlistNumber) : "";
            }

        });
    }

    public static final String  YT_EXT                           = "YT_EXT";
    public static final String  YT_TITLE                         = "YT_TITLE";
    public static final String  YT_PLAYLIST_INT                  = "YT_PLAYLIST_INT";
    public static final String  YT_ID                            = "YT_ID";

    public static final String  YT_CHANNEL                       = "YT_CHANNEL";
    public static final String  YT_USER                          = "YT_USER";
    public static final String  YT_DATE                          = "YT_DATE";
    public static final String  YT_VARIANTS                      = "YT_VARIANTS";
    public static final String  YT_VARIANT                       = "YT_VARIANT";
    /**
     * @deprecated use {@link #YT_VARIANT_INFO}
     */
    public static final String  YT_STREAMURL_VIDEO               = "YT_STREAMURL_VIDEO";
    /**
     * @deprecated use {@link #YT_VARIANT_INFO}
     */
    public static final String  YT_STREAMURL_AUDIO               = "YT_STREAMURL_AUDIO";
    /**
     * @deprecated use {@link #YT_VARIANT_INFO}
     */
    public static final String  YT_STREAMURL_VIDEO_SEGMENTS      = "YT_STREAMURL_VIDEO_SEGMENTS";
    /**
     * @deprecated use {@link #YT_VARIANT_INFO}
     */
    public static final String  YT_STREAMURL_AUDIO_SEGMENTS      = "YT_STREAMURL_AUDIO_SEGMENTS";

    private static final String REGEX_HLSMPD_FROM_JSPLAYER_SETUP = "\"hlsvp\"\\s*:\\s*(\".*?\")";

    private static String handleRule(String s, final String line) throws PluginException {

        final String method = new Regex(line, "\\.([\\w\\d]+?)\\(\\s*\\)").getMatch(0);
        if ("reverse".equals(method)) {
            //
            s = new StringBuilder(s).reverse().toString();

            return s;
        }
        // slice
        final String i = new Regex(line, "\\.slice\\((\\d+)\\)").getMatch(0);
        if (i != null) {
            //
            s = s.substring(Integer.parseInt(i));

            return s;
        }

        final String idx = new Regex(line, "=..\\([^,]+\\,(\\d+)\\)").getMatch(0);
        if (idx != null) {
            final int idxI = Integer.parseInt(idx);
            s = YoutubeHelper.pk(s, idxI);

            return s;

        }

        if (new Regex(line, "\\.split\\(\"\"\\)").matches()) {
            return s;
        }
        if (new Regex(line, "\\.join\\(\"\"\\)").matches()) {
            return s;
        }
        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Unknown Signature Rule: " + line);

    }

    protected static String pk(final String s, final int idxI) {
        final char c = s.charAt(0);
        final StringBuilder sb = new StringBuilder();
        sb.append(s.charAt(idxI % s.length()));
        sb.append(s.substring(1, idxI));
        sb.append(c);
        sb.append(s.substring(idxI + 1));
        return sb.toString();
    }

    public YoutubeHelper(final Browser br, final LogInterface logger) {
        this.br = br;
        this.logger = logger;

        this.cfg = CFG_YOUTUBE.CFG;

        this.base = "https://www.youtube.com";

    }

    public class StreamMap {
        public StreamMap(String map, String src2) {
            this.mapData = map;
            this.src = src2;
        }

        @Override
        public String toString() {
            return src;
        }

        @Override
        public int hashCode() {
            return mapData.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof StreamMap)) {
                return false;
            }

            return ((StreamMap) obj).mapData.equals(mapData);
        }

        private String mapData;
        private String src;
    }

    private HashMap<String, HashMap<String, String>> jsCache             = new HashMap<String, HashMap<String, String>>();
    private HashSet<String>                          subtitleUrls;
    private HashSet<StreamMap>                       fmtMaps;

    private LinkedHashSet<StreamMap>                 mpdUrls;

    private HashMap<String, String>                  videoInfo;

    private boolean                                  hlsEnabled          = true;

    private boolean                                  dashMpdEnabled      = true;

    private boolean                                  adaptiveFmtsEnabled = true;

    private boolean                                  fmtMapEnabled       = true;

    private String                                   html5PlayerJs;

    private YoutubeClipData                          vid;

    String descrambleSignature(final String sig, String jsUrl, final String id) throws IOException, PluginException {
        if (sig == null) {
            return null;
        }
        String ret = descrambleSignatureNew(sig, jsUrl, id);
        if (StringUtils.isNotEmpty(ret)) {
            return ret;
        }
        return descrambleSignatureOld(sig, jsUrl, id);

    }

    String descrambleSignatureNew(final String sig, String jsUrl, final String id) throws IOException, PluginException {

        if (sig == null) {
            return null;
        }

        String all = null;
        String descrambler = null;
        String des = null;
        String jsContent = null;
        Object result = null;

        HashMap<String, String> cache = jsCache.get(id);
        if (cache != null && !cache.isEmpty()) {
            all = cache.get("all");
            descrambler = cache.get("descrambler");
            des = cache.get("des");
        }
        if (all == null || descrambler == null || des == null) {
            cache = new HashMap<String, String>();
            jsContent = br.cloneBrowser().getPage(jsUrl);
            descrambler = new Regex(jsContent, "set\\(\"signature\",([\\$\\w]+)\\([\\w]+\\)").getMatch(0);

            final String func = Pattern.quote(descrambler) + "=function\\(([^)]+)\\)\\{(.+?return.*?)\\}";
            des = new Regex(jsContent, Pattern.compile(func, Pattern.DOTALL)).getMatch(1);
            all = new Regex(jsContent, Pattern.compile(Pattern.quote(descrambler) + "=function\\(([^)]+)\\)\\{(.+?return.*?)\\}.*?", Pattern.DOTALL)).getMatch(-1);

            String requiredObjectName = new Regex(des, "([\\w\\d]+)\\.([\\w\\d]{2})\\(").getMatch(0);
            String requiredObject = new Regex(jsContent, Pattern.compile("var " + Pattern.quote(requiredObjectName) + "=\\{.*?\\}\\};", Pattern.DOTALL)).getMatch(-1);
            all += ";";
            all += requiredObject;
        }
        while (true) {
            try {
                final ScriptEngineManager manager = org.jdownloader.scripting.JavaScriptEngineFactory.getScriptEngineManager(this);
                final ScriptEngine engine = manager.getEngineByName("javascript");
                result = engine.eval(all + " " + descrambler + "(\"" + sig + "\")");
                if (result != null) {
                    if (cache.isEmpty()) {
                        cache.put("all", all);
                        cache.put("descrambler", descrambler);
                        // not used by js but the failover.
                        cache.put("des", des);
                        jsCache.put(id, cache);
                    }
                    return result.toString();
                }
            } catch (final Throwable e) {
                if (e.getMessage() != null) {
                    // do not use language components of the error message. Only static identifies, otherwise other languages will fail!
                    // -raztoki
                    final String ee = new Regex(e.getMessage(), "ReferenceError: \"([\\$\\w]+)\".+<Unknown source>").getMatch(0);
                    // should only be needed on the first entry, then on after 'cache' should get result the first time!
                    if (ee != null) {
                        if (jsContent == null) {
                            jsContent = br.cloneBrowser().getPage(jsUrl);
                        }
                        // lets look for missing reference
                        final String ref = new Regex(jsContent, "var\\s+" + Pattern.quote(ee) + "\\s*=\\s*\\{.*?\\};").getMatch(-1);
                        if (ref != null) {
                            all = ref + "\r\n" + all;
                            continue;
                        } else {
                            logger.warning("Could not find missing var/function");
                        }
                    } else {
                        logger.warning("Could not find reference Error");
                    }
                }
                logger.log(e);
            }
            break;
        }
        String s = sig;
        try {
            logger.info("Des: " + des);
            final String[] t = new Regex(des, "[^;]+").getColumn(-1);
            logger.info("Des " + Arrays.toString(t));
            for (final String line : t) {
                s = YoutubeHelper.handleRule(s, line);
            }
            return s;
        } catch (final PluginException e) {
            logger.log(e);
            throw e;
        }

    }

    /**
     * *
     *
     * @param html5PlayerJs
     *            TODO
     * @param br
     * @param s
     *
     * @return
     * @throws IOException
     * @throws PluginException
     */
    String descrambleSignatureOld(final String sig, String jsUrl, final String id) throws IOException, PluginException {
        if (sig == null) {
            return null;
        }

        String all = null;
        String descrambler = null;
        String des = null;
        String jsContent = null;
        Object result = null;

        HashMap<String, String> cache = jsCache.get(id);
        if (cache != null && !cache.isEmpty()) {
            all = cache.get("all");
            descrambler = cache.get("descrambler");
            des = cache.get("des");
        }
        if (all == null || descrambler == null || des == null) {
            cache = new HashMap<String, String>();
            jsContent = br.cloneBrowser().getPage(jsUrl);
            descrambler = new Regex(jsContent, "\\.sig\\|\\|([\\$\\w]+)\\(").getMatch(0);
            if (descrambler == null) {
                descrambler = new Regex(jsContent, "\\w+\\.signature\\=([\\$\\w]+)\\([\\w]+\\)").getMatch(0);
                if (descrambler == null) {
                    return sig;
                }
            }
            final String func = "function " + Pattern.quote(descrambler) + "\\(([^)]+)\\)\\{(.+?return.*?)\\}";
            des = new Regex(jsContent, Pattern.compile(func)).getMatch(1);
            all = new Regex(jsContent, Pattern.compile("function " + Pattern.quote(descrambler) + "\\(([^)]+)\\)\\{(.+?return.*?)\\}.*?")).getMatch(-1);
            if (all == null) {
                all = new Regex(jsContent, Pattern.compile("var " + Pattern.quote(descrambler) + "=function\\(([^)]+)\\)\\{(.+?return.*?)\\}.*?")).getMatch(-1);
                // pleaseee...
                if (all != null) {
                    final String[] a = new Regex(all, "var (" + Pattern.quote(descrambler) + ")=function(.+)").getRow(0);
                    if (a != null) {
                        all = "function " + a[0] + a[1];
                    }
                }
                if (des == null) {
                    des = all;
                }
            }
        }
        while (true) {
            try {
                final ScriptEngineManager manager = org.jdownloader.scripting.JavaScriptEngineFactory.getScriptEngineManager(this);
                final ScriptEngine engine = manager.getEngineByName("javascript");
                result = engine.eval(all + " " + descrambler + "(\"" + sig + "\")");
                if (result != null) {
                    if (cache.isEmpty()) {
                        cache.put("all", all);
                        cache.put("descrambler", descrambler);
                        // not used by js but the failover.
                        cache.put("des", des);
                        jsCache.put(id, cache);
                    }
                    return result.toString();
                }
            } catch (final Throwable e) {
                if (e.getMessage() != null) {
                    // do not use language components of the error message. Only static identifies, otherwise other languages will fail!
                    // -raztoki
                    final String ee = new Regex(e.getMessage(), "ReferenceError: \"([\\$\\w]+)\".+<Unknown source>").getMatch(0);
                    // should only be needed on the first entry, then on after 'cache' should get result the first time!
                    if (ee != null) {
                        if (jsContent == null) {
                            jsContent = br.cloneBrowser().getPage(jsUrl);
                        }
                        // lets look for missing reference
                        final String ref = new Regex(jsContent, "var\\s+" + Pattern.quote(ee) + "\\s*=\\s*\\{.*?\\};").getMatch(-1);
                        if (ref != null) {
                            all = ref + "\r\n" + all;
                            continue;
                        } else {
                            logger.warning("Could not find missing var/function");
                        }
                    } else {
                        logger.warning("Could not find reference Error");
                    }
                }
                logger.log(e);
            }
            break;
        }
        String s = sig;
        try {
            logger.info("Des: " + des);
            final String[] t = new Regex(des, "[^;]+").getColumn(-1);
            logger.info("Des " + Arrays.toString(t));
            for (final String line : t) {
                s = YoutubeHelper.handleRule(s, line);
            }
            return s;
        } catch (final PluginException e) {
            logger.log(e);
            throw e;
        }

    }

    protected void extractData() {
        if (StringUtils.isEmpty(vid.title) && this.br.containsHTML("&title=")) {
            final String match = this.br.getRegex("&title=([^&$]+)").getMatch(0);
            if (StringUtils.isNotEmpty(match)) {
                vid.title = Encoding.htmlDecode(match.replaceAll("\\+", " ").trim());
            }

        }
        if (StringUtils.isEmpty(vid.description)) {
            String match = br.getRegex("<div id=\"watch-description-text\".*?><p id=\"eow-description\"\\s*>(.*?)</p\\s*>\\s*</div>\\s*<div id=\"watch-description-extras\"\\s*>").getMatch(0);
            if (StringUtils.isNotEmpty(match)) {
                match = Encoding.htmlDecode(match.replaceAll("\\+", " ").trim().replaceAll("<br\\s*/>", "\r\n"));
                match = match.replaceAll("<a.*?href=\"([^\"]*)\".*?>(.*?)</a\\s*>", "$1");
                vid.description = match;

            } else {
                // video has no description
                vid.description = "";
            }
        }
        if (StringUtils.isEmpty(vid.title)) {
            final String match = this.br.getRegex("<title>(.*?) - YouTube</title>").getMatch(0);
            if (StringUtils.isNotEmpty(match)) {
                vid.title = Encoding.htmlDecode(match.replaceAll("\\+", " ").trim());

            }
        }

        if (vid.length <= 0) {
            final String match = this.br.getRegex("\"length_seconds\"\\\\s*:\\s*(\\d+)").getMatch(0);
            if (StringUtils.isNotEmpty(match)) {
                vid.length = Integer.parseInt(match);

            }
        }

        if (StringUtils.isEmpty(vid.title)) {
            final String match = this.br.getRegex("<meta name=\"title\" content=\"(.*?)\">").getMatch(0);
            if (StringUtils.isNotEmpty(match)) {
                vid.title = Encoding.htmlDecode(match.trim());

            }
        }

        if (vid.date <= 0) {

            // dd MMM yyyy - old
            final Locale locale = Locale.ENGLISH;
            SimpleDateFormat formatter = new SimpleDateFormat("dd MMM yyyy", locale);
            formatter.setTimeZone(TimeZone.getDefault());
            String date = this.br.getRegex("class=\"watch-video-date\" >([ ]+)?(\\d{1,2} [A-Za-z]{3} \\d{4})</span>").getMatch(1);
            if (date == null) {
                date = this.br.getRegex("<strong[^>]*>Published on (\\d{1,2} [A-Za-z]{3} \\d{4})</strong>").getMatch(0);
            }

            // MMM dd, yyyy (20150508)
            if (date == null) {
                formatter = new SimpleDateFormat("MMM dd, yyyy", locale);
                formatter.setTimeZone(TimeZone.getDefault());
                date = this.br.getRegex("<strong[^>]*>Published on ([A-Za-z]{3} \\d{1,2}, \\d{4})</strong>").getMatch(0);
                logger.info("Formatter " + "MMM dd, yyyy " + locale + " on " + date);
            }
            // yyyy-MM-dd (20150508)
            if (date == null) {
                formatter = new SimpleDateFormat("yyyy-MM-dd", locale);
                formatter.setTimeZone(TimeZone.getDefault());
                date = this.br.getRegex("<meta itemprop=\"datePublished\" content=\"(\\d{4}-\\d{2}-\\d{2})\">").getMatch(0);

                logger.info("Formatter " + "yyyy-MM-dd " + locale + " on " + date);
            }
            if (date != null) {
                try {
                    vid.date = formatter.parse(date).getTime();

                    logger.info("Date result " + vid.date + " " + new Date(vid.date));
                } catch (final Exception e) {
                    final LogSource log = LogController.getInstance().getPreviousThreadLogSource();
                    log.log(e);

                }
            }
        }
        if (StringUtils.isEmpty(vid.channelID)) {
            String match = this.br.getRegex("\"channel_id\"\\: \"([^\"]+)\"").getMatch(0);
            if (StringUtils.isEmpty(match)) {
                match = this.br.getRegex("<meta itemprop=\"channelId\" content=\"([^\"]+)\">").getMatch(0);
            }
            if (StringUtils.isNotEmpty(match)) {
                vid.channelID = Encoding.htmlDecode(match.trim());

            }
        }
        if (vid.duration <= 0) {
            final String[] match = this.br.getRegex("<meta itemprop=\"duration\" content=\"PT(\\d*)M(\\d*)S\">").getRow(0);
            if (match != null) {
                int dur = 0;
                if (match[0].length() > 0) {
                    dur += Integer.parseInt(match[0]) * 60 * 1000;
                }
                if (match[1].length() > 0) {
                    dur += Integer.parseInt(match[1]) * 1000;
                }
                vid.duration = dur;

            }
        }
        if (StringUtils.isEmpty(vid.channel)) {
            String match = this.br.getRegex("<div class=\"yt-user-info\"><a [^>]*data-name[^>]*>(.*?)</a>").getMatch(0);
            if (StringUtils.isEmpty(match) && StringUtils.isNotEmpty(vid.channelID)) {
                // content warning regex.
                match = this.br.getRegex("<div class=\"yt-user-info\">\\s*<a [^>]+ data-ytid=\"" + Pattern.quote(vid.channelID) + "\"[^>]*>(.*?)</a>").getMatch(0);
            }
            if (StringUtils.isEmpty(match)) {
                // in the html5 json info
                match = this.br.getRegex("\"author\":\\s*\"(.*?)\"").getMatch(0);
            }
            if (StringUtils.isNotEmpty(match)) {
                vid.channel = Encoding.htmlDecode(match.trim());

            }
        }
        if (StringUtils.isEmpty(vid.user)) {
            final String match = this.br.getRegex("temprop=\"url\" href=\"http://(www\\.)?youtube\\.com/user/([^<>\"]+)\"").getMatch(1);
            // getVideoInfoWorkaroundUsed
            final String vidWorkAround = this.br.getRegex("&author=(.*?)&").getMatch(0);
            if (StringUtils.isNotEmpty(match)) {
                vid.user = Encoding.htmlDecode(match.trim());
            } else if (vid.channel != null) {
                vid.user = vid.channel;
            } else if (StringUtils.isNotEmpty(vidWorkAround)) {
                vid.user = vidWorkAround;
            }
        }
    }

    protected void handleContentWarning(final Browser ibr) throws Exception {
        // not necessarily age related but violence/disturbing content?
        // https://www.youtube.com/watch?v=Wx9GxXYKx_8 gets you
        // <script>window.location = "http:\/\/www.youtube.com\/verify_controversy?next_url=\/watch%3Fv%3DWx9GxXYKx_8"</script>
        // then from verify_controversy page
        // <form action="/verify_controversy?action_confirm=1" method="POST">
        // <p>
        // <input type="hidden" name="session_token" value="UYIQVFoBkQKGHKCionXaE-OXh4Z8MTM5MzE2NTcyNUAxMzkzMDc5MzI1"/>
        // <input type="hidden" name="referrer" value="">
        // <input type="hidden" name="next_url" value="/watch?v=Wx9GxXYKx_8">
        // <button onclick=";return true;" type="submit" class=" yt-uix-button yt-uix-button-primary yt-uix-button-size-default"
        // role="button"><span class="yt-uix-button-content">Continue </span></button>
        // or <a href="/">Cancel</a>
        // </p>
        // </form>
        String vc = ibr.getRegex("\"([^\"]+verify_controversy\\?next_url[^\"]+)\"").getMatch(0);
        if (vc != null) {
            vc = JSonStorage.restoreFromString("\"" + vc + "\"", TypeRef.STRING);
            ibr.getPage(vc);
        }

        // nsfw testlink https://www.youtube.com/watch?v=p7S_u5TzI-I
        // youtube shows an extra screen the first time a user wants to see a age-protected video.
        // <div class="content">
        // <h1 id="unavailable-message" class="message">
        // Content Warning
        //
        // </h1>
        // <div id="unavailable-submessage" class="submessage">
        // <div id="watch7-player-age-gate-content">
        // <p>This video may be inappropriate for some users.</p>
        //
        // <p>By confirming, you agree that this warning will no longer be shown in the future.</p>
        // <form action="/verify_age?action_confirm=1" method="POST">
        // <input type="hidden" name="next_url" value="/watch?v=p7S_u5TzI-I">
        // <input type="hidden" name="set_racy" value="true">
        // <input type="hidden" name="session_token" value="d5tNczUUbnz7-G160SZlqEFiM798MTM4OTE3NzA4M0AxMzg5MDkwNjgz"/>
        // <button onclick=";return true;" class=" yt-uix-button yt-uix-button-primary yt-uix-button-size-default" type="submit"
        // role="button"><span class="yt-uix-button-content">I understand and wish to proceed </span></button>
        // </form>
        //
        // <p class="safety-mode-message">If you would instead prefer to avoid potentially inappropriate content, consider
        // activating YouTube's <a href="//support.google.com/youtube/bin/answer.py?answer=174084&amp;hl=en-GB">Safety Mode</a>.</p>
        // </div>

        final Form forms[] = ibr.getForms();
        if (forms != null) {
            for (final Form form : forms) {
                if (form.getAction() != null && form.getAction().contains("verify_age")) {
                    this.logger.info("Verify Age");
                    ibr.submitForm(form);
                    break;
                }
                if (form.getAction() != null && form.getAction().contains("verify_controversy")) {
                    this.logger.info("Verify Controversy");
                    ibr.submitForm(form);
                    break;
                }
            }
        }

    }

    public void loadVideo(final YoutubeClipData vid) throws Exception {
        // TODO: add Cache
        refreshVideo(vid);
    }

    public void refreshVideo(final YoutubeClipData vid) throws Exception {

        this.vid = vid;
        final Map<YoutubeITAG, List<YoutubeStreamData>> ret = new HashMap<YoutubeITAG, List<YoutubeStreamData>>();
        final YoutubeConfig cfg = PluginJsonConfig.get(YoutubeConfig.class);
        boolean loggedIn = br.getCookie("https://youtube.com", "LOGIN_INFO") != null;
        this.br.setFollowRedirects(true);

        /* this cookie makes html5 available and skip controversy check */

        this.br.setCookie("youtube.com", "PREF", "f1=50000000&hl=en");
        this.br.getHeaders().put("User-Agent", "Wget/1.12");

        br.getPage(base + "/watch?v=" + vid.videoID + "&gl=US&hl=en&has_verified=1&bpctr=9999999999");

        vid.approxThreedLayout = br.getRegex("\"approx_threed_layout\"\\s*\\:\\s*\"([^\"]*)").getMatch(0);
        String[][] keyWordsGrid = br.getRegex("<meta\\s+property=\"([^\"]*)\"\\s+content=\"yt3d\\:([^\"]+)=([^\"]+)\">").getMatches();
        vid.keywords3D = new HashMap<String, String>();
        if (keyWordsGrid != null) {
            for (String[] keyValue : keyWordsGrid) {
                vid.keywords3D.put(keyValue[1], keyValue[2]);
            }
        }

        String keywords = br.getRegex("<meta name=\"keywords\" content=\"([^\"]*)").getMatch(0);
        vid.keywords = new HashSet<String>();
        if (keywords != null) {
            for (String s : keywords.split("[,]+")) {
                vid.keywords.add(s);
            }

        }
        handleRentalVideos();

        html5PlayerJs = this.br.getMatch("\"js\"\\s*:\\s*\"(.+?)\"");
        if (html5PlayerJs != null) {
            html5PlayerJs = html5PlayerJs.replace("\\/", "/");
            html5PlayerJs = "http:" + html5PlayerJs;
        }
        if (html5PlayerJs == null) {
            html5PlayerJs = br.getMatch("src=\"//([^\"<>]*?/base.js)\" name=\"player\\/base\"");

            if (html5PlayerJs != null) {
                html5PlayerJs = "https://" + html5PlayerJs;
            }
        }
        String unavailableReason = this.br.getRegex("<div id=\"player-unavailable\" class=\"[^\"]*\">.*?<h. id=\"unavailable-message\"[^>]*?>([^<]+)").getMatch(0);
        fmtMaps = new HashSet<StreamMap>();
        subtitleUrls = new HashSet<String>();
        mpdUrls = new LinkedHashSet<StreamMap>();
        videoInfo = new HashMap<String, String>();

        vid.ageCheck = br.containsHTML("age-gate");
        this.handleContentWarning(br);

        collectMapsFormHtmlSource(br.getRequest().getHtmlCode(), "base");

        Browser apiBrowser = null;

        boolean apiRequired = br.getRegex(REGEX_FMT_MAP_FROM_JSPLAYER_SETUP).getMatch(0) == null;

        apiBrowser = this.br.cloneBrowser();
        apiBrowser.getPage(this.base + "/get_video_info?&video_id=" + vid.videoID + "&el=info&ps=default&eurl=&gl=US&hl=en");

        collectMapsFromVideoInfo(apiBrowser.toString(), apiBrowser.getURL());
        if (apiRequired) {
            apiBrowser = this.br.cloneBrowser();
            apiBrowser.getPage(this.base + "/embed/" + vid.videoID);
            apiBrowser.getPage(this.base + "/get_video_info?video_id=" + vid.videoID + "&eurl=" + Encoding.urlEncode("https://youtube.googleapis.com/v/" + vid.videoID) + "&sts=16511");
            if (!apiBrowser.containsHTML("url_encoded_fmt_stream_map")) {
                // StatsManager.I().track("youtube/vInfo1");
                apiBrowser.getPage(this.base + "/get_video_info?video_id=" + vid.videoID + "&hl=en&gl=US&el=detailpage&ps=default&eurl=&gl=US&hl=en");
                if (!apiBrowser.containsHTML("url_encoded_fmt_stream_map")) {
                    // StatsManager.I().track("youtube/vInfo2");
                    // example https://www.youtube.com/v/p7S_u5TzI-I
                    // age protected
                    apiBrowser.getPage(this.base + "/get_video_info?video_id=" + vid.videoID + "&hl=en&gl=US&el=embedded&ps=default&eurl=&gl=US&hl=en");
                }

            }
            collectMapsFromVideoInfo(apiBrowser.toString(), apiBrowser.getURL());
            if (apiBrowser.containsHTML("requires_purchase=1")) {
                logger.warning("Download not possible: You have to pay to watch this video");
                throw new Exception("Paid Video");
            }
            final String errorcode = apiBrowser.getRegex("errorcode=(\\d+)").getMatch(0);
            String reason = apiBrowser.getRegex("reason=([^\\&]+)").getMatch(0);
            if ("150".equals(errorcode)) {
                // http://www.youtube.com/watch?v=xxWHMmiOTVM
                // reason=This video contains content from WMG. It is restricted from playback on certain sites.<br/><u><a
                // href='...>Watch on YouTube</a>

                // the next error handling below will catch from the original browser and give correct feedback!

                logger.warning(" failed due to been restricted content. " + reason);

            }
            if (reason != null) {
                reason = Encoding.urlDecode(reason, false);
                // remove all tags
                reason = reason.replaceAll("<.*?>", "");
                if (reason != null && reason.contains("Watch this video on YouTube") && !loggedIn) {
                    reason = "Account required. Add your Youtube Account to JDownloader";
                }
                vid.error = reason;
            }
        }

        if (unavailableReason != null) {
            final String copyrightClaim = "This video is no longer available due to a copyright claim";
            unavailableReason = Encoding.htmlDecode(unavailableReason.replaceAll("\\+", " ").trim());
            /*
             * If you consider using !unavailableReason.contains("this video is unavailable), you need to also ignore content warning
             */
            if (br.containsHTML("This video is private")) {
                // check if video is private
                String subError = br.getRegex("<div id=\"unavailable-submessage\" class=\"[^\"]*\">(.*?)</div>").getMatch(0);
                if (subError != null && !subError.matches("\\s*")) {
                    subError = subError.trim();
                    logger.warning("Private Video");
                    logger.warning(unavailableReason + " :: " + subError);
                    vid.error = "This Video is Private";
                    return;
                }
            } else if (unavailableReason.startsWith("This video has been removed")) {
                // currently covering
                // This video has been removed by the user. .:. ab4U0RwrOTI
                // This video has been removed because its content violated YouTube&#39;s Terms of Service. .:. 7RA4A-4QqHU
                logger.warning(unavailableReason);
                vid.error = unavailableReason;
                return;
            } else if (unavailableReason.contains("account associated with this video has been")) {
                // currently covering
                // This video is no longer available because the YouTube account associated with this video has been closed.
                // id=wBVhciYW9Og, date=20141222, author=raztoki
                logger.warning(unavailableReason);
                vid.error = unavailableReason;
                return;
            } else if ("This live event has ended.".equalsIgnoreCase(unavailableReason)) {
                // currently covering
                // This live event has ended.
                // id=qEJwOuvDf7I, date=20150412, author=raztoki
                logger.warning(unavailableReason);
                vid.error = unavailableReason;
                return;
            } else if (unavailableReason.contains(copyrightClaim)) {
                // currently covering
                // "One Monkey saves another Mo..."
                // This video is no longer available due to a copyright claim by ANI Media Pvt Ltd.
                // id=l8nBcj8ul7s, date=20141224, author=raztoki
                // filename is shown in error.
                vid.title = new Regex(unavailableReason, "\"(.*?(?:\\.\\.\\.)?)\"\n").getMatch(0);
                logger.warning(copyrightClaim);
                vid.error = copyrightClaim;
                return;
            } else if (unavailableReason.equals("This video is unavailable.") || unavailableReason.equals(/* 15.12.2014 */"This video is not available.")) {
                // be aware that this is always present, only when there is a non whitespace suberror is it valid.
                // currently covering
                // Sorry about that. .:. 7BN5H7AVHUIE8 invalid uid.
                String subError = br.getRegex("<div id=\"unavailable-submessage\" class=\"[^\"]*\">(.*?)</div>").getMatch(0);
                if (subError != null && !subError.matches("\\s*")) {
                    subError = subError.trim();
                    logger.warning(unavailableReason + " :: " + subError);
                    vid.error = unavailableReason;
                    return;
                }
            }
        }
        this.extractData();
        doFeedScan();
        doUserAPIScan();

        // String html5_fmt_map;
        // String dashFmt;
        // String dashmpd;
        // if (apiRequired) {
        // // age check bypass active
        //
        // html5_fmt_map = apiBrowser.getRegex("url_encoded_fmt_stream_map\\s*=\\s*(.*?)(&|$)").getMatch(0);
        // html5_fmt_map = Encoding.htmlDecode(html5_fmt_map);
        //
        // dashFmt = apiBrowser.getRegex("adaptive_fmts\\s*=\\s*(.*?)(&|$)").getMatch(0);
        // dashFmt = Encoding.htmlDecode(dashFmt);
        //
        // dashmpd = apiBrowser.getRegex("dashmpd\\s*=\\s*(.*?)(&|$)").getMatch(0);
        // dashmpd = Encoding.htmlDecode(dashmpd);
        //
        // } else {
        // regular url testlink: http://www.youtube.com/watch?v=4om1rQKPijI

        // }

        fmtLoop: for (StreamMap fmt : fmtMaps) {

            boolean fmtChecked = false;
            for (final String line : fmt.mapData.split(",")) {
                try {
                    final YoutubeStreamData match = this.parseLine(Request.parseQuery(line));
                    if (match != null) {

                        if (!cfg.isExternMultimediaToolUsageEnabled() && match.getItag().name().contains("DASH_")) {
                            continue;
                        }

                        List<YoutubeStreamData> lst = ret.get(match.getItag());
                        if (lst == null) {
                            lst = new ArrayList<YoutubeStreamData>();
                            ret.put(match.getItag(), lst);
                        }
                        lst.add(match);

                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
        if (cfg.isExternMultimediaToolUsageEnabled()) {
            mpd: for (StreamMap mpdUrl : mpdUrls) {
                try {
                    if (StringUtils.isEmpty(mpdUrl.mapData)) {
                        continue;
                    }
                    Browser clone = br.cloneBrowser();
                    clone.getPage(mpdUrl.mapData);
                    String xml = clone.getRequest().getHtmlCode();
                    if (!clone.getHttpConnection().isOK()) {
                        logger.severe("Bad Request: ");
                        logger.severe(clone.getHttpConnection() + "");
                        continue;
                    }
                    if (xml.trim().startsWith("#EXTM3U")) {
                        ArrayList<HlsContainer> containers = HlsContainer.getHlsQualities(clone);
                        for (HlsContainer c : containers) {
                            System.out.println(1);
                            String[][] params = new Regex(c.downloadurl, "/([^/]+)/([^/]+)").getMatches();
                            final QueryInfo query = Request.parseQuery(c.downloadurl);
                            if (params != null) {
                                for (int i = 1; i < params.length; i++) {
                                    query.addAndReplace(params[i][0], Encoding.htmlDecode(params[i][1]));
                                }
                            }

                            query.addIfNoAvailable("codecs", c.codecs);
                            query.addIfNoAvailable("type", query.get("codecs") + "-" + query.get("mime"));

                            query.addIfNoAvailable("fps", query.get("frameRate"));
                            query.addIfNoAvailable("width", c.width + "");
                            query.addIfNoAvailable("height", c.height + "");
                            if (query.containsKey("width") && query.containsKey("height")) {
                                query.addIfNoAvailable("size", query.get("width") + "x" + query.get("height"));
                            }
                            String fps = query.get("fps");

                            int projectionType = -1;

                            try {
                                String v = query.get("projection_type");
                                projectionType = v == null ? -1 : Integer.parseInt(v);
                            } catch (Throwable e) {
                                logger.log(e);
                            }
                            final YoutubeITAG itag = YoutubeITAG.get(Integer.parseInt(query.get("itag")), c.width, c.height, StringUtils.isEmpty(fps) ? -1 : Integer.parseInt(fps), query.getDecoded("type"), query, vid.date);

                            List<YoutubeStreamData> lst = ret.get(itag);
                            if (lst == null) {
                                lst = new ArrayList<YoutubeStreamData>();
                                ret.put(itag, lst);
                            }
                            YoutubeStreamData vsd;
                            lst.add(vsd = new YoutubeStreamData(vid, c.downloadurl, itag, query));

                            try {
                                vsd.setHeight(Integer.parseInt(query.get("height")));
                            } catch (Throwable e) {
                            }
                            try {
                                vsd.setWidth(Integer.parseInt(query.get("width")));
                            } catch (Throwable e) {
                            }
                            try {
                                vsd.setFps(query.get("fps"));
                            } catch (Throwable e) {
                            }

                        }
                    } else {
                        DocumentBuilder docBuilder = createXMLParser();
                        Document doc = docBuilder.parse(new InputSource(new StringReader(xml)));
                        NodeList representations = doc.getElementsByTagName("Representation");
                        for (int r = 0; r < representations.getLength(); r++) {
                            Element representation = (Element) representations.item(r);
                            Element baseUrlElement = (Element) representation.getElementsByTagName("BaseURL").item(0);
                            String contentLength = baseUrlElement.getAttribute("yt:contentLength");
                            String url = baseUrlElement.getTextContent();

                            String[][] params = new Regex(url, "/([^/]+)/([^/]+)").getMatches();
                            final QueryInfo query = Request.parseQuery(url);
                            if (params != null) {
                                for (int i = 1; i < params.length; i++) {
                                    query.addAndReplace(params[i][0], Encoding.htmlDecode(params[i][1]));
                                }
                            }
                            handleQuery(representation, ret, url, query);

                        }
                    }

                } catch (BrowserException e) {
                    logger.log(e);
                } catch (Throwable e) {
                    logger.log(e);
                    Map<String, String> infos = new HashMap<String, String>();
                    infos.put("name", e.getMessage());
                    infos.put("stacktrace", Exceptions.getStackTrace(e));
                    StatsManager.I().track(0, null, "loadVideo/Exception", infos, CollectionName.PLUGINS);
                }
            }
        }
        for (YoutubeStreamData sd : loadThumbnails()) {
            List<YoutubeStreamData> lst = ret.get(sd.getItag());
            if (lst == null) {
                lst = new ArrayList<YoutubeStreamData>();
                ret.put(sd.getItag(), lst);
            }
            lst.add(sd);

        }
        vid.streams = ret;
        vid.subtitles = loadSubtitles();

    }

    private DocumentBuilder createXMLParser() throws ParserConfigurationException {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        docFactory.setValidating(false);

        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        docBuilder.setEntityResolver(new EntityResolver() {

            @Override
            public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
                return null;
            }
        });

        docBuilder.setErrorHandler(new ErrorHandler() {

            @Override
            public void warning(SAXParseException exception) throws SAXException {
                logger.log(exception);
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
                logger.log(exception);
            }

            @Override
            public void error(SAXParseException exception) throws SAXException {
                logger.log(exception);
            }
        });
        return docBuilder;
    }

    private void collectFmtMap(String htmlCode, String regex, String src) {
        String map = new Regex(htmlCode, regex).getMatch(0);
        if (map == null) {
            return;
        }
        map = JSonStorage.restoreFromString(map, TypeRef.STRING);

        if (StringUtils.isNotEmpty(map)) {
            // map = Encoding.urlDecode(map, false);
            if (!fmtMaps.contains(map)) {
                System.out.println("Add FMT Map html " + regex + "- " + map);
                fmtMaps.add(new StreamMap(map, src));
            }
        }
    }

    private void collectMapsFormHtmlSource(String html, String src) {

        collectSubtitleUrls(html, "['\"]TTS_URL['\"]\\s*:\\s*(['\"][^'\"]+['\"])");
        if (fmtMapEnabled) {
            collectFmtMap(html, REGEX_FMT_MAP_FROM_JSPLAYER_SETUP, "fmtMapJSPlayer." + src);
        }
        if (adaptiveFmtsEnabled) {
            collectFmtMap(html, REGEX_ADAPTIVE_FMTS_FROM_JSPLAYER_SETUP, "adaptiveFmtsJSPlayer." + src);
        }
        if (dashMpdEnabled) {
            collectMpdMap(html, REGEX_DASHMPD_FROM_JSPLAYER_SETUP, "dashMpdJSPlayer." + src);
        }
        if (hlsEnabled) {
            collectMpdMap(html, REGEX_HLSMPD_FROM_JSPLAYER_SETUP, "hlsJSPlayer." + src);
        }
    }

    private void collectSubtitleUrls(String html, String pattern) {
        String json = new Regex(html, pattern).getMatch(0);
        if (json == null) {
            return;
        }
        json = JSonStorage.restoreFromString(json, TypeRef.STRING);
        if (json != null) {
            subtitleUrls.add(handleSignature(json));
        }
    }

    private void collectMapsFromVideoInfo(String queryString, String src) throws MalformedURLException {
        QueryInfo map = Request.parseQuery(queryString);
        for (Entry<String, String> es : map.toMap().entrySet()) {
            videoInfo.put(es.getKey(), Encoding.urlDecode(es.getValue(), false));
        }
        String ttsurl = videoInfo.get("ttsurl");
        if (StringUtils.isNotEmpty(ttsurl)) {
            subtitleUrls.add(handleSignature(ttsurl));
        }

        if (adaptiveFmtsEnabled) {
            String adaptive_fmts = videoInfo.get("adaptive_fmts");
            if (StringUtils.isNotEmpty(adaptive_fmts)) {
                // adaptive_fmts = Encoding.urlDecode(adaptive_fmts, false);
                System.out.println("Add adaptive_fmts VI - " + adaptive_fmts);
                fmtMaps.add(new StreamMap(adaptive_fmts, "adaptive_fmts." + src));

            }
        }
        if (fmtMapEnabled) {
            String url_encoded_fmt_stream_map = videoInfo.get("url_encoded_fmt_stream_map");
            if (StringUtils.isNotEmpty(url_encoded_fmt_stream_map)) {
                // url_encoded_fmt_stream_map = Encoding.urlDecode(url_encoded_fmt_stream_map, false);
                System.out.println("Add url_encoded_fmt_stream_map VI - " + url_encoded_fmt_stream_map);
                fmtMaps.add(new StreamMap(url_encoded_fmt_stream_map, "url_encoded_fmt_stream_map." + src));
            }
        }
        if (dashMpdEnabled) {
            String dashmpd = videoInfo.get("dashmpd");
            if (StringUtils.isNotEmpty(dashmpd)) {
                final String url = handleSignature(dashmpd);
                if (url != null) {
                    mpdUrls.add(new StreamMap(url, "dashmpd." + src));
                }
            }
        }
        if (hlsEnabled) {
            String hlsvp = videoInfo.get("hlsvp");
            if (StringUtils.isNotEmpty(hlsvp)) {
                final String url = handleSignature(hlsvp);
                if (url != null) {
                    mpdUrls.add(new StreamMap(url, "hlsvp." + src));
                }
            }
        }
    }

    private void collectMpdMap(String htmlCode, String regex, String src) {
        String map = new Regex(htmlCode, regex).getMatch(0);
        if (map == null) {
            return;
        }
        map = JSonStorage.restoreFromString(map, TypeRef.STRING);
        if (StringUtils.isNotEmpty(map)) {
            final String url = handleSignature(map);
            if (url != null) {
                mpdUrls.add(new StreamMap(url, src));
            }
        }
    }

    private String handleSignature(String map) {
        String s = new Regex(map, "/s/(.*?\\..*?)/").getMatch(0);
        if (s != null) {
            // the website does not load these urls neither
            return null;
        }
        return map;
    }

    /**
     * @param vid
     * @param ret
     * @param html5PlayerJs
     * @param r
     * @param url
     * @param query
     * @throws IOException
     * @throws PluginException
     */
    private void handleQuery(Element representation, final Map<YoutubeITAG, List<YoutubeStreamData>> ret, String url, final QueryInfo query) throws IOException, PluginException {
        String r = null;

        r = xmlNodeToString(representation);

        NamedNodeMap ats = representation.getAttributes();
        for (int a = 0; a < ats.getLength(); a++) {
            Attr at = (Attr) ats.item(a);
            query.addAndReplace(at.getName(), at.getValue());

        }

        query.addIfNoAvailable("type", query.get("codecs") + "-" + query.get("mime"));

        query.addIfNoAvailable("fps", query.get("frameRate"));

        if (query.containsKey("width") && query.containsKey("height")) {
            query.addIfNoAvailable("size", query.get("width") + "x" + query.get("height"));
        }
        String signature = new Regex(url, "(sig|signature)=(.*?)(\\&|$)").getMatch(1);

        if (StringUtils.isEmpty(signature)) {
            // verified 7.1.24
            // non dash?
            signature = query.get("sig");
        }
        if (StringUtils.isEmpty(signature)) {
            signature = query.get("signature");
        }
        if (StringUtils.isEmpty(signature)) {
            // verified 7.1.213
            signature = this.descrambleSignature(query.get("s"), html5PlayerJs, vid.videoID);
        }

        if (url != null && !url.contains("sig")) {

            url = url + "&signature=" + signature;
        }
        String size = query.get("size");
        int width = -1;
        int height = -1;

        if (StringUtils.isNotEmpty(size)) {
            String[] splitted = size.split("\\s*x\\s*");
            if (splitted != null && splitted.length == 2) {
                width = Integer.parseInt(splitted[0]);
                height = Integer.parseInt(splitted[1]);
            }
        }
        int projectionType = -1;

        try {
            String v = query.get("projection_type");
            projectionType = v == null ? -1 : Integer.parseInt(v);
        } catch (Throwable e) {
            logger.log(e);
        }
        String fps = query.get("fps");
        String type = query.get("type");
        if (StringUtils.isNotEmpty(type)) {
            type = Encoding.urlDecode(type, false);
        }
        int itagId = Integer.parseInt(query.get("itag"));
        final YoutubeITAG itag = YoutubeITAG.get(itagId, width, height, StringUtils.isEmpty(fps) ? -1 : Integer.parseInt(fps), type, query, vid.date);

        logger.info(Encoding.urlDecode(JSonStorage.toString(query.list()), false));
        NodeList segmentListNodes = representation.getElementsByTagName("SegmentList");
        ArrayList<String> segmentsList = new ArrayList<String>();
        if (segmentListNodes != null && segmentListNodes.getLength() > 0) {
            // we have segments

            Node segments = segmentListNodes.item(0);
            NodeList childs = segments.getChildNodes();
            for (int c = 0; c < childs.getLength(); c++) {
                String seg = ((Element) childs.item(c)).getAttribute("sourceURL");
                if (StringUtils.isEmpty(seg)) {
                    seg = ((Element) childs.item(c)).getAttribute("media");
                    String testUrl = url + seg;
                    // try {
                    // getBr().openHeadConnection(testUrl);
                    // System.out.println(getBr().getHttpConnection());
                    // getBr().getHttpConnection().disconnect();
                    // if (getBr().getHttpConnection().getResponseCode() != 200) {
                    // System.out.println("ERROR");
                    // System.out.println(getBr().getHttpConnection());
                    // }
                    // } catch (Throwable e) {
                    // e.printStackTrace();
                    // }
                }

                segmentsList.add(seg);
            }

        }
        if (url != null && itag != null) {

            YoutubeStreamData vsd;

            List<YoutubeStreamData> lst = ret.get(itag);
            if (lst == null) {
                lst = new ArrayList<YoutubeStreamData>();
                ret.put(itag, lst);
            }
            lst.add(vsd = new YoutubeStreamData(vid, url, itag, query));
            vsd.setHeight(height);
            vsd.setWidth(width);
            vsd.setFps(fps);
            if (segmentsList.size() > 0) {
                vsd.setSegments(segmentsList.toArray(new String[] {}));
            }

        } else {

            this.logger.info("Unknown Line: " + r);

            this.logger.info("Unknown ITAG: " + query.get("itag"));
            this.logger.info(url + "");
            this.logger.info(query + "");
            try {

                if (!Application.isJared(null)) {
                    new ItagHelper(vid, br, query, url).run();
                }
            } catch (Exception e) {
                logger.log(e);
            }
        }
    }

    private String xmlNodeToString(Node representation) {
        try {
            String r;
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer;

            transformer = transformerFactory.newTransformer();
            transformer.setURIResolver(new URIResolver() {

                @Override
                public Source resolve(String href, String base) throws TransformerException {
                    return null;
                }
            });

            DOMSource source = new DOMSource(representation);

            ByteArrayOutputStream bao;
            StreamResult result = new StreamResult(bao = new ByteArrayOutputStream());
            transformer.transform(source, result);
            // r = result.getWriter().toString();
            r = new String(bao.toByteArray(), "ASCII");
            return r;
        } catch (Throwable e1) {

            return representation + "";
        }
    }

    private void handleRentalVideos() throws Exception {
        String rentalText = br.getRegex("\"ypc_video_rental_bar_text\"\\s*\\:\\s*\"([^\"]+)").getMatch(0);

        if (StringUtils.isNotEmpty(rentalText)) {
            logger.warning("Download not possible: " + rentalText);
            throw new Exception(PAID_VIDEO + rentalText);
        }
        if (br.containsHTML("<meta itemprop=\"paid\" content=\"True\">")) {
            logger.warning("Download not possible: You have to pay to watch this video");
            throw new Exception(PAID_VIDEO + " Download not possible");
        }
        if (br.containsHTML("watch-checkout-offers")) {
            logger.warning("Download not possible: You have to pay to watch this video");
            throw new Exception(PAID_VIDEO + "Download not possible");
        }
    }

    private void doUserAPIScan() throws IOException {
        String checkName = cfg.getPackagePattern() + cfg.getVideoFilenamePattern() + cfg.getAudioFilenamePattern() + cfg.getSubtitleFilenamePattern() + cfg.getImageFilenamePattern() + cfg.getDescriptionFilenamePattern();

        boolean extended = false;
        // only load extra page, if we need the properties
        for (YoutubeReplacer r : REPLACER) {
            if (r.getDataSource() == DataSource.API_USERS && r.matches(checkName)) {
                extended = true;
                break;
            }
        }
        if (!extended) {
            return;
        }
        if (StringUtils.isEmpty(vid.user)) {
            return;
        }
        Browser clone = br.cloneBrowser();
        // if (cfg.isPreferHttpsEnabled()) {
        clone.getPage("https://gdata.youtube.com/feeds/api/users/" + vid.user + "?v=2");
        // } else {
        // clone.getPage("http://gdata.youtube.com/feeds/api/users/" + vid.user + "?v=2");
        // }

        String googleID = clone.getRegex("<yt\\:googlePlusUserId>(.*?)</yt\\:googlePlusUserId>").getMatch(0);
        if (StringUtils.isNotEmpty(googleID)) {
            vid.userGooglePlusID = googleID;
        }

    }

    public static void main(String[] args) {
        for (VariantBase tag : VariantBase.values()) {
            System.out.println("<li>" + tag.getType() + " : " + tag.name() + "</li>");
        }
    }

    /**
     * this method calls an API which has been deprecated by youtube. TODO: Find new API!
     *
     * @deprecated
     * @param vid
     * @throws IOException
     */
    private void doFeedScan() throws IOException {
        if (true) {
            return;
        }
        String checkName = cfg.getFilenamePattern() + cfg.getPackagePattern() + cfg.getVideoFilenamePattern() + cfg.getAudioFilenamePattern() + cfg.getSubtitleFilenamePattern() + cfg.getImageFilenamePattern();
        boolean extended = false;
        // only load extra page, if we need the properties
        for (YoutubeReplacer r : REPLACER) {
            if (r.getDataSource() == DataSource.API_VIDEOS && r.matches(checkName)) {
                extended = true;
                break;
            }
        }
        if (!extended) {
            return;
        }
        Browser clone = br.cloneBrowser();
        // if (cfg.isPreferHttpsEnabled()) {
        clone.getPage("https://gdata.youtube.com/feeds/api/videos/" + vid.videoID + "?v=2");
        // } else {
        // clone.getPage("http://gdata.youtube.com/feeds/api/videos/" + vid.videoID + "?v=2");
        // }
        try {
            // dd.MM.yyyy_HH-mm-ss
            // 2014-01-06T00:01:01.000Z
            String date = clone.getRegex("<published>(.*?)</published>").getMatch(0);
            if (StringUtils.isNotEmpty(date)) {
                DatatypeFactory f = DatatypeFactory.newInstance();
                XMLGregorianCalendar xgc = f.newXMLGregorianCalendar(date);

                vid.date = xgc.toGregorianCalendar().getTime().getTime();

            }

        } catch (DatatypeConfigurationException e) {
            e.printStackTrace();
        }

        try {
            // dd.MM.yyyy_HH-mm-ss
            // 2014-01-06T00:01:01.000Z
            String date = clone.getRegex("<updated>(.*?)</updated>").getMatch(0);
            if (StringUtils.isNotEmpty(date)) {
                DatatypeFactory f = DatatypeFactory.newInstance();
                XMLGregorianCalendar xgc = f.newXMLGregorianCalendar(date);

                vid.dateUpdated = xgc.toGregorianCalendar().getTime().getTime();

            }

        } catch (DatatypeConfigurationException e) {
            e.printStackTrace();
        }
        vid.category = clone.getRegex("<media:category.*?>(.*?)</media:category>").getMatch(0);

        // duration
        String duration = clone.getRegex("duration=\"(\\d+)\"").getMatch(0);
        if (StringUtils.isEmpty(duration)) {
            duration = clone.getRegex("<yt\\:duration seconds=\"(\\d+)\" />").getMatch(0);
        }
        if (StringUtils.isNotEmpty(duration)) {

            vid.duration = Integer.parseInt(duration);

        }

    }

    private List<YoutubeStreamData> loadThumbnails() {
        ArrayList<YoutubeStreamData> ret = new ArrayList<YoutubeStreamData>();
        String best = br.getRegex("<meta property=\"og\\:image\" content=\".*?/(\\w+\\.jpg)\">").getMatch(0);
        ret.add(new YoutubeStreamData(vid, "http://img.youtube.com/vi/" + vid.videoID + "/default.jpg", YoutubeITAG.IMAGE_LQ, null));
        if (best != null && best.equals("default.jpg")) {
            return ret;
        }
        ret.add(new YoutubeStreamData(vid, "http://img.youtube.com/vi/" + vid.videoID + "/mqdefault.jpg", YoutubeITAG.IMAGE_MQ, null));
        if (best != null && best.equals("mqdefault.jpg")) {
            return ret;
        }
        ret.add(new YoutubeStreamData(vid, "http://img.youtube.com/vi/" + vid.videoID + "/hqdefault.jpg", YoutubeITAG.IMAGE_HQ, null));
        if (best != null && best.equals("hqdefault.jpg")) {
            return ret;
        }

        ret.add(new YoutubeStreamData(vid, "http://img.youtube.com/vi/" + vid.videoID + "/maxresdefault.jpg", YoutubeITAG.IMAGE_MAX, null));

        return ret;
    }

    public void login(final Account account, final boolean refresh, final boolean showDialog) throws Exception {

        try {
            this.br.setDebug(true);
            this.br.setCookiesExclusive(true);
            // delete all cookies
            this.br.clearCookies(null);
            Thread thread = Thread.currentThread();
            boolean forceUpdateAndBypassCache = thread instanceof AccountCheckerThread && ((AccountCheckerThread) thread).getJob().isForce();
            br.setCookie("http://youtube.com", "PREF", "hl=en-GB");
            if (account.getProperty("cookies") != null && !forceUpdateAndBypassCache) {
                @SuppressWarnings("unchecked")
                HashMap<String, String> cookies = (HashMap<String, String>) account.getProperty("cookies");
                // cookies = null;
                if (cookies != null) {
                    if (cookies.containsKey("SSID")) {
                        for (final Map.Entry<String, String> cookieEntry : cookies.entrySet()) {
                            final String key = cookieEntry.getKey();
                            final String value = cookieEntry.getValue();
                            this.br.setCookie("youtube.com", key, value);
                        }
                        if (!refresh) {
                            return;
                        } else {
                            this.br.getPage("https://www.youtube.com");
                            if (this.br.containsHTML("<span.*?>\\s*Sign out\\s*</span>")) {
                                return;
                            }
                        }

                    }
                }
            }

            this.br.setFollowRedirects(true);
            GoogleHelper helper = new GoogleHelper(br) {
                @Override
                protected boolean validateSuccess() {
                    return br.getCookie("http://youtube.com", "SID") != null;
                }

                protected String breakRedirects(String url) throws IOException {

                    String sidt = new Regex(url, "accounts\\/SetSID\\?ssdc\\=1\\&sidt=([^\\&]+)").getMatch(0);
                    if (sidt != null) {
                        String jsonUrl = br.getRegex("uri\\:\\s*\\'(.*?)\\'\\,").getMatch(0);
                        jsonUrl = Encoding.unescape(jsonUrl);
                        br.getPage(jsonUrl);
                        return null;
                    }
                    if (br.getURL() != null && br.getURL().contains("/accounts/SetSID")) {
                        return null;
                    }
                    return url;
                }

            };
            helper.setLogger(logger);
            helper.setCacheEnabled(false);
            if (helper.login(account)) {

                final HashMap<String, String> cookies = new HashMap<String, String>();
                final Cookies cYT = this.br.getCookies("youtube.com");
                for (final Cookie c : cYT.getCookies()) {
                    cookies.put(c.getKey(), c.getValue());
                }
                // set login cookie of the account.
                account.setProperty("cookies", cookies);
            } else {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
        } catch (final PluginException e) {
            account.setProperty("cookies", null);
            throw e;
        }

    }

    private void getPageFollowRedirectsDuringLogin(Browser br, String string) throws IOException {
        boolean before = br.isFollowingRedirects();
        br.setFollowRedirects(false);
        int max = 20;
        try {
            while (max-- > 0) {

                br.getPage(string);
                if (br.getRedirectLocation() != null) {
                    if (br.getCookie("youtube.com", "LOGIN_INFO") != null) {
                        break;
                    }
                    string = br.getRedirectLocation();
                    continue;
                }
                String redirect = br.getRegex("<meta http-equiv=\"refresh\" content=\"0\\; url=\\&\\#39\\;(.+)\\&\\#39\\;").getMatch(0);
                if (redirect != null) {
                    string = Encoding.htmlDecode(redirect);
                } else {
                    break;
                }
            }
        } finally {
            br.setFollowRedirects(before);
        }
    }

    public void login(final boolean refresh, final boolean showDialog) {

        ArrayList<Account> accounts = AccountController.getInstance().getAllAccounts("youtube.com");
        if (accounts != null && accounts.size() != 0) {
            final Iterator<Account> it = accounts.iterator();
            while (it.hasNext()) {
                final Account n = it.next();
                if (n.isEnabled() && n.isValid()) {

                    try {

                        this.login(n, refresh, showDialog);
                        if (n.isValid()) {
                            return;
                        }
                    } catch (final Exception e) {

                        n.setValid(false);
                        return;
                    }

                }
            }
        }

        // debug

        accounts = AccountController.getInstance().getAllAccounts("google.com");
        if (accounts != null && accounts.size() != 0) {
            final Iterator<Account> it = accounts.iterator();
            while (it.hasNext()) {
                final Account n = it.next();
                if (n.isEnabled() && n.isValid()) {

                    try {

                        this.login(n, refresh, showDialog);
                        if (n.isValid()) {
                            return;
                        }
                    } catch (final Exception e) {

                        n.setValid(false);
                        return;
                    }

                }
            }
        }
        accounts = AccountController.getInstance().getAllAccounts("youtube.jd");
        if (accounts != null && accounts.size() != 0) {
            final Iterator<Account> it = accounts.iterator();
            while (it.hasNext()) {
                final Account n = it.next();
                if (n.isEnabled() && n.isValid()) {

                    try {

                        this.login(n, refresh, showDialog);
                        if (n.isValid()) {
                            return;
                        }
                    } catch (final Exception e) {

                        n.setValid(false);
                        return;
                    }

                }
            }
        }
        return;
    }

    public static final String YT_LENGTH_SECONDS     = "YT_LENGTH_SECONDS";

    /**
     * @deprecated use {@link #YT_VARIANT_INFO}
     */
    public static final String YT_STREAMURL_DATA     = "YT_STREAMURL_DATA";
    @Deprecated
    public static final String YT_SUBTITLE_CODE      = "YT_SUBTITLE_CODE";     // Update YoutubeSubtitleName
    @Deprecated
    public static final String YT_SUBTITLE_CODE_LIST = "YT_SUBTITLE_CODE_LIST";

    public static final String YT_BEST_VIDEO         = "YT_BEST_VIDEO";

    public static final String YT_DESCRIPTION        = "YT_DESCRIPTION";

    // public static final String YT_VARIANT_INFO = "YT_VARIANT_INFO";

    public static final String YT_STREAM_DATA_VIDEO  = "YT_STREAM_DATA_VIDEO";
    public static final String YT_STREAM_DATA_AUDIO  = "YT_STREAM_DATA_AUDIO";
    public static final String YT_STREAM_DATA_DATA   = "YT_STREAM_DATA_DATA";

    public static final String YT_3D                 = "YT_3D";

    public static final String YT_COLLECTION         = "YT_COLLECTION";

    public String createFilename(DownloadLink link) {

        AbstractVariant variant = AbstractVariant.get(link);

        String formattedFilename = variant.getFileNamePattern();
        // validate the pattern
        if (!formattedFilename.toLowerCase(Locale.ENGLISH).contains("*ext*")) {
            formattedFilename = null;
        }
        if (formattedFilename == null || formattedFilename.equals("")) {
            formattedFilename = "*videoname* (*quality*) *ext*";
        }

        formattedFilename = replaceVariables(link, formattedFilename);
        return formattedFilename;
    }

    public String replaceVariables(DownloadLink link, String formattedFilename) {
        LogInterface logger = LogController.getInstance().getPreviousThreadLogSource();
        if (logger == null) {
            logger = Log.DF;
        }
        AbstractVariant variant = AbstractVariant.get(link);
        try {
            formattedFilename = variant.modifyFileName(formattedFilename, link);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        // channelname

        for (YoutubeReplacer r : REPLACER) {
            formattedFilename = r.replace(formattedFilename, this, link);
        }
        try {
            formattedFilename = variant.modifyFileName(formattedFilename, link);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return formattedFilename;
    }

    protected YoutubeStreamData parseLine(final QueryInfo query) throws MalformedURLException, IOException, PluginException {

        if (StringUtils.equalsIgnoreCase(query.get("conn"), "rtmp")) {
            logger.info("Stream is not supported: " + query);
            vid.error = "RTMP(E) Stream not supported";
            return null;
        }
        String url = query.getDecoded("url");
        if (url == null) {
            String fallback_host = query.getDecoded("fallback_host");
            if (fallback_host != null) {
                url = new Regex(fallback_host, "url=(.+)").getMatch(0);

            }
        }
        if (StringUtils.isEmpty(url)) {
            throw new WTFException("No Url found " + query);
        }
        String signature = new Regex(url, "(sig|signature)=(.*?)(\\&|$)").getMatch(1);

        if (StringUtils.isEmpty(signature)) {
            // verified 7.1.24
            // non dash?
            signature = query.get("sig");
        }
        if (StringUtils.isEmpty(signature)) {
            signature = query.get("signature");
        }

        if (StringUtils.isEmpty(signature) && query.get("s") != null) {
            // verified 7.1.213
            signature = this.descrambleSignature(query.get("s"), html5PlayerJs, vid.videoID);

        }

        if (url != null && !url.contains("sig")) {
            url = url + "&signature=" + signature;
        }
        int bitrate = -1;
        String bitrateString = query.get("bitrate");
        if (StringUtils.isNotEmpty(bitrateString)) {
            bitrate = Integer.parseInt(bitrateString);
        }
        String size = query.get("size");
        int width = -1;
        int height = -1;

        if (StringUtils.isNotEmpty(size)) {
            String[] splitted = size.split("\\s*x\\s*");
            if (splitted != null && splitted.length == 2) {
                width = Integer.parseInt(splitted[0]);
                height = Integer.parseInt(splitted[1]);
            }
        }
        String fps = query.get("fps");
        String type = query.get("type");
        if (StringUtils.isNotEmpty(type)) {
            type = Encoding.urlDecode(type, false);
        }
        String itagString = query.get("itag");

        try {
            final YoutubeITAG itag = YoutubeITAG.get(Integer.parseInt(query.get("itag")), width, height, StringUtils.isEmpty(fps) ? -1 : Integer.parseInt(fps), type, query, vid.date);

            final String quality = Encoding.urlDecode(query.get("quality"), false);
            logger.info(Encoding.urlDecode(JSonStorage.toString(query.list()), false));
            if (url != null && itag != null) {
                YoutubeStreamData vsd;

                vsd = new YoutubeStreamData(vid, url, itag, query);
                vsd.setHeight(height);
                vsd.setWidth(width);
                vsd.setFps(fps);
                return vsd;
            } else {
                this.logger.info("Unknown Line: " + query);
                this.logger.info(url + "");
                this.logger.info(query + "");
                try {

                    if (!Application.isJared(null)) {
                        new ItagHelper(vid, br, query, url).run();
                    }
                } catch (Exception e) {
                    logger.log(e);
                }
            }
            return null;
        } catch (NumberFormatException e) {
            e.printStackTrace();
            throw e;
        }
    }

    private void itagWarning(YoutubeITAG itag, String string, Object size) {
        this.logger.warning("Youtube WARNING! Bad Itag choosen: " + itag + " does not support " + string + " of " + size);
    }

    private String replaceHttps(final String s) {

        // final boolean prefers = this.cfg.isPreferHttpsEnabled();
        //
        // if (prefers) {
        return s.replaceFirst("http://", "https://");
        // } else {
        // return s.replaceFirst("https://", "http://");
        // }
    }

    public void setupProxy() {
        if (br == null) {
            return;
        }
        if (this.cfg.isProxyEnabled()) {
            final HTTPProxyStorable proxy = this.cfg.getProxy();

            // int PROXY_PORT = cfg.getProxyPort();
            // if (StringUtils.isEmpty(PROXY_ADDRESS) || PROXY_PORT < 0) return;
            // PROXY_ADDRESS = new Regex(PROXY_ADDRESS, "^[0-9a-zA-Z]+://").matches() ? PROXY_ADDRESS : "http://" + PROXY_ADDRESS;
            // org.appwork.utils.net.httpconnection.HTTPProxy proxy =
            // org.appwork.utils.net.httpconnection.HTTPProxy.parseHTTPProxy(PROXY_ADDRESS + ":" + PROXY_PORT);
            if (proxy != null) {
                HTTPProxy prxy = HTTPProxy.getHTTPProxy(proxy);
                if (prxy != null) {
                    this.br.setProxy(prxy);
                } else {

                }
                return;
            }

        }
        this.br.setProxy(this.br.getThreadProxy());
    }

    private ArrayList<YoutubeSubtitleStorable> loadSubtitles() throws IOException, ParserConfigurationException, SAXException {
        HashMap<String, YoutubeSubtitleStorable> urls = new HashMap<String, YoutubeSubtitleStorable>();

        for (String ttsUrl : subtitleUrls) {
            String xml = br.getPage(replaceHttps(ttsUrl + "&asrs=1&fmts=1&tlangs=1&ts=" + System.currentTimeMillis() + "&type=list"));

            DocumentBuilder docBuilder = createXMLParser();
            InputSource is = new InputSource(new StringReader(xml));

            Document doc = docBuilder.parse(is);

            NodeList tracks = doc.getElementsByTagName("track");
            YoutubeSubtitleStorable defaultLanguage = null;
            for (int trackIndex = 0; trackIndex < tracks.getLength(); trackIndex++) {
                Element track = (Element) tracks.item(trackIndex);

                String trackID = track.getAttribute("id");
                String lang = track.getAttribute("lang_code");
                String name = track.getAttribute("name");
                String kind = track.getAttribute("kind");
                String langOrg = track.getAttribute("lang_original");
                String langTrans = track.getAttribute("lang_translated");
                if (name == null) {
                    name = "";
                }
                if (kind == null) {
                    kind = "";
                }

                if (StringUtils.isNotEmpty(langTrans)) {
                    langOrg = langTrans;
                }
                if (StringUtils.isEmpty(langOrg)) {

                    langOrg = TranslationFactory.stringToLocale(lang).getDisplayLanguage(Locale.ENGLISH);
                }
                YoutubeSubtitleStorable old = urls.get(lang);
                if (old != null) {
                    // speech recognition
                    // if ("asr".equalsIgnoreCase(old.getKind())) {
                    // urls.put(lang, new YoutubeSubtitleInfo(ttsUrl, lang, name, kind, langOrg));
                    //
                    // }
                    continue;
                }
                YoutubeSubtitleStorable info;
                info = new YoutubeSubtitleStorable(ttsUrl, lang, null, kind);
                if (info._getLocale() == null) {
                    // unknown language
                    logger.info("Unknown Subtitle Language: " + JSonStorage.serializeToJson(info));
                    continue;
                }
                urls.put(lang, info);
                if ("true".equalsIgnoreCase(track.getAttribute("lang_default"))) {
                    defaultLanguage = info;
                }
                // System.out.println(lang);
            }
            if (defaultLanguage != null) {
                NodeList targets = doc.getElementsByTagName("target");
                for (int targetIndex = 0; targetIndex < targets.getLength(); targetIndex++) {
                    Element target = (Element) targets.item(targetIndex);

                    String targetID = target.getAttribute("id");
                    String lang = target.getAttribute("lang_code");
                    String name = target.getAttribute("name");
                    String kind = target.getAttribute("kind");
                    String langOrg = target.getAttribute("lang_original");
                    String langTrans = target.getAttribute("lang_translated");
                    String urlfrag = target.getAttribute("urlfrag");
                    if (name == null) {
                        name = "";
                    }
                    if (kind == null) {
                        kind = "";
                    }

                    if (StringUtils.isNotEmpty(langTrans)) {
                        langOrg = langTrans;
                    }
                    if (StringUtils.isEmpty(langOrg)) {

                        langOrg = TranslationFactory.stringToLocale(lang).getDisplayLanguage(Locale.ENGLISH);
                    }
                    YoutubeSubtitleStorable old = urls.get(lang);
                    if (old != null) {
                        // speech recognition
                        // if ("asr".equalsIgnoreCase(old.getKind())) {
                        // urls.put(lang, new YoutubeSubtitleInfo(ttsUrl, lang, name, kind, langOrg));
                        //
                        // }
                        continue;
                    }

                    YoutubeSubtitleStorable info = new YoutubeSubtitleStorable(ttsUrl, lang, defaultLanguage.getLanguage(), defaultLanguage.getKind());
                    if (info._getLocale() == null) {
                        // unknown language
                        logger.info("Unknown Subtitle Language: " + JSonStorage.serializeToJson(info));
                        continue;
                    }
                    urls.put(lang, info);
                    // System.out.println("->" + lang);
                }
            }
        }
        return new ArrayList<YoutubeSubtitleStorable>(urls.values());
    }

    // public List<YoutubeBasicVariant> getVariantByIds(String... extra) {
    // ArrayList<YoutubeBasicVariant> ret = new ArrayList<YoutubeBasicVariant>();
    // if (extra != null) {
    // for (String s : extra) {
    // YoutubeBasicVariant v = getVariantById(s);
    // if (v != null) {
    // ret.add(v);
    // }
    // }
    // }
    // return ret;
    // }

    public YoutubeConfig getConfig() {
        return cfg;
    }

    public static List<VariantIDStorable> readExtraList() {
        YoutubeConfig cf = PluginJsonConfig.get(YoutubeConfig.class);
        List<VariantIDStorable> list = new ArrayList<VariantIDStorable>();
        // List<VariantIDStorable> configList = cf.getExtra();
        // if (configList != null) {
        // for (VariantIDStorable obj : configList) {
        // if (obj != null) {
        // list.add(obj);
        // }
        // }
        // }
        // String[] strList = cf.getExtraVariants();
        // if (strList != null) {
        // for (String b : strList) {
        //
        // list.add(new VariantIDStorable(b));
        // }
        // cf.setExtra(list);
        // cf.setExtraVariants(null);
        // }

        return list;
    }

    public static List<VariantIDStorable> readBlacklist() {
        YoutubeConfig cf = PluginJsonConfig.get(YoutubeConfig.class);
        List<VariantIDStorable> list = new ArrayList<VariantIDStorable>();
        // List<VariantIDStorable> configList = cf.getDisabledVariants();
        // if (configList != null) {
        // for (VariantIDStorable obj : configList) {
        // if (obj != null) {
        // list.add(obj);
        // }
        // }
        // }
        // String[] strList = cf.getBlacklistedVariants();
        // if (strList != null) {
        // for (String b : strList) {
        //
        // list.add(new VariantIDStorable(b));
        // }
        // cf.setDisabledVariants(list);
        // cf.setBlacklistedVariants(null);
        // }

        return list;
    }

    public static String createLinkID(String videoID, AbstractVariant variant, List<String> variants) {
        return "youtubev2://" + videoID + "/" + Encoding.urlEncode(variant._getUniqueId()) + "/" + Encoding.urlEncode(Hash.getMD5(JSonStorage.serializeToJson(variants)));
    }

    public static void writeVariantToDownloadLink(DownloadLink downloadLink, AbstractVariant v) {
        downloadLink.getTempProperties().setProperty(YoutubeHelper.YT_VARIANT, v);
        downloadLink.setProperty(YoutubeHelper.YT_VARIANT, v.getStorableString());
        downloadLink.setProperty(YoutubeHelper.YT_EXT, v.getContainer().getExtension());
    }

}