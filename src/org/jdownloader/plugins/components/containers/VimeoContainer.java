package org.jdownloader.plugins.components.containers;

import org.appwork.storage.TypeRef;
import org.jdownloader.plugins.components.hls.HlsContainer;

/**
 *
 * custom video container to support vimeo.
 *
 * @author raztoki
 *
 */
public class VimeoContainer extends VideoContainer {
    public static final TypeRef<VimeoContainer> TYPE_REF = new TypeRef<VimeoContainer>() {
    };
    private Quality                             quality;
    private String                              lang;

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    private Source source;
    private String codec;
    private Long   estimatedSize = null;

    public VimeoContainer() {
    }

    public Long getEstimatedSize() {
        return estimatedSize;
    }

    public void setEstimatedSize(Long estimatedSize) {
        this.estimatedSize = estimatedSize;
    }

    private Long id = null;

    // order is important, worst to best
    public enum Quality {
        MOBILE,
        SD,
        HD,
        ORIGINAL;
    }

    // order is important, methods that can resume vs not resume.
    public enum Source {
        HLS, // hls. (currently can't resume)
        DASH, // dash in segments. (think this can resume)
        WEB, // standard mp4. (can resume)
        DOWNLOAD, // from download button. (can resume)
        SUBTITLE;
    }

    /**
     * @return the quality
     */
    public final Quality getQuality() {
        return quality;
    }

    /**
     * @return the codec
     */
    public final String getCodec() {
        return codec;
    }

    /**
     * @return the id
     */
    public final Long getId() {
        return id;
    }

    /**
     * @param quality
     *            the quality to set
     */
    public final void setQuality(Quality quality) {
        this.quality = quality;
    }

    /**
     * sets quality reference based on current Height value
     */
    public final void setQuality() {
        setQuality(getQuality(getHeight()));
    }

    /**
     * @param codec
     *            the codec to set
     */
    public final void setCodec(String codec) {
        this.codec = codec;
    }

    /**
     * @param id
     *            the id to set
     */
    public final void setId(Long id) {
        this.id = id;
    }

    /**
     * @return the source
     */
    public Source getSource() {
        return source;
    }

    /**
     * @param source
     *            the source to set
     */
    public void setSource(Source source) {
        this.source = source;
    }

    /**
     * determines from input value
     *
     * @param i
     * @return
     */
    private static Quality getQuality(final int i) {
        if (i == -1) {
            return null;
        } else {
            return i >= 720 ? Quality.HD : Quality.SD;
        }
    }

    /**
     * create VimeoVideoContainer from HLSContainer
     *
     * @param container
     * @return
     */
    public static VimeoContainer createVimeoVideoContainer(HlsContainer container) {
        final VimeoContainer vvm = new VimeoContainer();
        vvm.setCodec(container.getCodecs());
        vvm.setWidth(container.getWidth());
        vvm.setHeight(container.getHeight());
        vvm.setBitrate(container.getBandwidth());
        vvm.setFramerate(container.getFramerate());
        vvm.setSource(Source.HLS);
        vvm.setQuality(getQuality(container.getHeight()));
        vvm.setDownloadurlAndExtension(container.getDownloadurl(), ".mp4");
        return vvm;
    }

    /**
     * create specific linkid to allow multiple entries (can be annoying if you never want to download dupe).
     *
     * @param id
     * @return
     */
    public String createLinkID(final String id) {
        final String linkid;
        if (Source.SUBTITLE.equals(getSource())) {
            linkid = id.concat("_").concat(toString(getId()).concat(toString(getSource())).concat(toString(getLang())));
        } else {
            linkid = id.concat("_").concat(getQuality().toString()).concat("_").concat(String.valueOf(getWidth())).concat("x").concat(String.valueOf(getHeight())).concat((getId() != null ? "_" + String.valueOf(getId()) : "")).concat((getSource() != null ? "_" + getSource().toString() : "")).concat((getLang() != null ? "_" + getLang().toString() : ""));
        }
        return linkid;
    }

    private String toString(Object object) {
        if (object == null) {
            return "";
        } else {
            return "_".concat(object.toString());
        }
    }

    @Override
    public String toString() {
        return super.toString().concat(getQuality() != null ? "|" + getQuality().toString() : "").concat(getSource() != null ? "|" + getSource().toString() : "");
    }

    public String bestString() {
        if (Source.SUBTITLE.equals(getSource())) {
            return getLang();
        } else {
            return String.valueOf(getWidth()).concat("x").concat(String.valueOf(getHeight()));
        }
    }
}
