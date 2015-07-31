package org.jdownloader.controlling.filter;

import java.util.ArrayList;
import java.util.regex.Pattern;

import org.appwork.utils.logging.Log;
import org.jdownloader.controlling.filter.FiletypeFilter.TypeMatchType;
import org.jdownloader.gui.translate._GUI;

public class CompiledFiletypeFilter {
    private Pattern[]     list = new Pattern[0];
    private TypeMatchType matchType;

    public TypeMatchType getMatchType() {
        return matchType;
    }

    public static interface ExtensionsFilterInterface {
        public Pattern compiledAllPattern();

        public String getDesc();

        public String getIconID();

        public Pattern getPattern();

        public String name();

        public boolean isSameExtensionGroup(ExtensionsFilterInterface extension);

    }

    public static ExtensionsFilterInterface getExtensionsFilterInterface(final String fileExtension) {
        if (fileExtension != null) {
            for (final ExtensionsFilterInterface[] extensions : new ExtensionsFilterInterface[][] { HashExtensions.values(), AudioExtensions.values(), ArchiveExtensions.values(), ImageExtensions.values(), VideoExtensions.values() }) {
                for (final ExtensionsFilterInterface extension : extensions) {
                    if (extension.getPattern().matcher(fileExtension).matches()) {
                        return extension;
                    }
                }
            }
        }
        return null;
    }

    public static enum HashExtensions implements ExtensionsFilterInterface {
        SFV,
        MD5,
        SHA,
        SHA256,
        SHA512;

        private final Pattern  pattern;
        private static Pattern allPattern;

        public Pattern getPattern() {
            return pattern;
        }

        private HashExtensions() {
            pattern = Pattern.compile(name(), Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        }

        private HashExtensions(String id) {
            this.pattern = Pattern.compile(id, Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        }

        public String getDesc() {
            return _GUI._.FilterRuleDialog_createTypeFilter_mime_checksums();
        }

        public String getIconID() {
            return "hashsum";
        }

        public Pattern compiledAllPattern() {
            if (allPattern == null) {
                allPattern = compileAllPattern(HashExtensions.values());
            }
            return allPattern;
        }

        @Override
        public boolean isSameExtensionGroup(ExtensionsFilterInterface extension) {
            return extension instanceof HashExtensions;
        }

    }

    public static enum AudioExtensions implements ExtensionsFilterInterface {
        MP3,
        WMA,
        AAC,
        WAV,
        FLAC,
        MID,
        MOD,
        OGG,
        S3M,
        FourMP("4MP"),
        AA,
        AIF,
        AIFF,
        AU,
        M3U,
        M4a,
        M4b,
        M4P,
        MKA,
        MP1,
        MP2,
        MPA,
        OMG,
        OMF,
        SND;

        private final Pattern  pattern;
        private static Pattern allPattern;

        public Pattern getPattern() {
            return pattern;
        }

        private AudioExtensions() {
            pattern = Pattern.compile(name(), Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        }

        private AudioExtensions(String id) {
            this.pattern = Pattern.compile(id, Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        }

        public String getDesc() {
            return _GUI._.FilterRuleDialog_createTypeFilter_mime_audio();
        }

        public String getIconID() {
            return "audio";
        }

        public Pattern compiledAllPattern() {
            if (allPattern == null) {
                allPattern = compileAllPattern(AudioExtensions.values());
            }
            return allPattern;
        }

        @Override
        public boolean isSameExtensionGroup(ExtensionsFilterInterface extension) {
            return extension instanceof AudioExtensions;
        }

    }

    public static enum VideoExtensions implements ExtensionsFilterInterface {
        ThreeGP("3GP"),
        ASF,
        AVI,
        DIVX,
        XVID,
        FLV,
        MP4,
        H264,
        H265,
        M4U,
        M4V,
        MOV,
        MKV,
        MPEG,
        MPEG4,
        MPG,
        OGM,
        OGV,
        VOB,
        WMV,
        GP3,
        WEBM;

        private final Pattern  pattern;
        private static Pattern allPattern;

        private VideoExtensions() {
            pattern = Pattern.compile(name(), Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        }

        public Pattern getPattern() {
            return pattern;
        }

        @Override
        public boolean isSameExtensionGroup(ExtensionsFilterInterface extension) {
            return extension instanceof VideoExtensions;
        }

        private VideoExtensions(String id) {
            this.pattern = Pattern.compile(id, Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        }

        public String getDesc() {
            return _GUI._.FilterRuleDialog_createTypeFilter_mime_video();
        }

        public Pattern compiledAllPattern() {
            if (allPattern == null) {
                allPattern = compileAllPattern(AudioExtensions.values());
            }
            return allPattern;
        }

        public String getIconID() {
            return "video";
        }
    }

    private static Pattern compileAllPattern(ExtensionsFilterInterface[] filters) {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        boolean or = false;
        for (ExtensionsFilterInterface value : filters) {
            if (or) {
                sb.append("|");
            }
            sb.append(value.getPattern());
            or = true;
        }
        sb.append(")");
        return Pattern.compile(sb.toString(), Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    }

    public static enum ArchiveExtensions implements ExtensionsFilterInterface {
        REV,
        RAR,
        ZIP,
        SevenZIP("7ZIP"),
        R_NUM("r\\d+"),
        NUM("\\d+"),
        MultiZip("z\\d+"),
        ACE,
        TAR,
        GZ,
        AR,
        BZ2,
        ARJ,
        CPIO,
        SevenZ("7Z"),
        S7Z,
        DMG,
        SFX,
        XZ,
        TGZ,
        LZH,
        LHA,
        PAR2("(vol\\d+\\.par2|vol\\d+\\+\\d+\\.par2|par2)"),
        PAR("(p\\d+|par)");

        private final Pattern  pattern;
        private static Pattern allPattern;

        public Pattern getPattern() {
            return pattern;
        }

        private ArchiveExtensions() {
            pattern = Pattern.compile(name(), Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        }

        private ArchiveExtensions(String id) {
            this.pattern = Pattern.compile(id, Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        }

        public String getDesc() {
            return _GUI._.FilterRuleDialog_createTypeFilter_mime_archives();
        }

        public Pattern compiledAllPattern() {
            if (allPattern == null) {
                allPattern = compileAllPattern(AudioExtensions.values());
            }
            return allPattern;
        }

        @Override
        public boolean isSameExtensionGroup(ExtensionsFilterInterface extension) {
            return extension instanceof ArchiveExtensions;
        }

        public String getIconID() {
            return org.jdownloader.gui.IconKey.ICON_COMPRESS;
        }
    }

    public static enum ImageExtensions implements ExtensionsFilterInterface {
        JPG,
        JPEG,
        GIF,
        PNG,
        BMP,
        TIFF,
        RAW,
        SVG,
        ICO;

        private final Pattern  pattern;
        private static Pattern allPattern;

        public Pattern getPattern() {
            return pattern;
        }

        private ImageExtensions() {
            pattern = Pattern.compile(name(), Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        }

        private ImageExtensions(String id) {
            this.pattern = Pattern.compile(id, Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        }

        public String getDesc() {
            return _GUI._.FilterRuleDialog_createTypeFilter_mime_images();
        }

        public Pattern compiledAllPattern() {
            if (allPattern == null) {
                allPattern = compileAllPattern(AudioExtensions.values());
            }
            return allPattern;
        }

        @Override
        public boolean isSameExtensionGroup(ExtensionsFilterInterface extension) {
            return extension instanceof ImageExtensions;
        }

        public String getIconID() {
            return "image";
        }
    }

    public CompiledFiletypeFilter(FiletypeFilter filetypeFilter) {
        java.util.List<Pattern> list = new ArrayList<Pattern>();
        if (filetypeFilter.isArchivesEnabled()) {
            for (ArchiveExtensions ae : ArchiveExtensions.values()) {
                list.add(ae.getPattern());
            }
        }

        if (filetypeFilter.isHashEnabled()) {
            for (HashExtensions ae : HashExtensions.values()) {
                list.add(ae.getPattern());
            }
        }

        if (filetypeFilter.isAudioFilesEnabled()) {
            for (AudioExtensions ae : AudioExtensions.values()) {
                list.add(ae.getPattern());
            }
        }

        if (filetypeFilter.isImagesEnabled()) {
            for (ImageExtensions ae : ImageExtensions.values()) {
                list.add(ae.getPattern());
            }
        }
        if (filetypeFilter.isVideoFilesEnabled()) {
            for (VideoExtensions ae : VideoExtensions.values()) {
                list.add(ae.getPattern());
            }
        }
        try {
            if (filetypeFilter.getCustoms() != null) {
                if (filetypeFilter.isUseRegex()) {
                    list.add(Pattern.compile(filetypeFilter.getCustoms(), Pattern.DOTALL | Pattern.CASE_INSENSITIVE));
                } else {

                    for (String s : filetypeFilter.getCustoms().split("\\,")) {
                        list.add(LinkgrabberFilterRuleWrapper.createPattern(s, false));
                    }
                }
            }
        } catch (final IllegalArgumentException e) {
            /* custom regex may contain errors */
            Log.exception(e);
        }
        matchType = filetypeFilter.getMatchType();
        this.list = list.toArray(new Pattern[list.size()]);
    }

    public boolean matches(String extension) {
        switch (matchType) {
        case IS:
            for (Pattern o : this.list) {
                try {
                    if (o.matcher(extension).matches()) {
                        return true;
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
            return false;
        case IS_NOT:
            for (Pattern o : this.list) {
                try {
                    if (o.matcher(extension).matches()) {
                        return false;
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
            return true;

        }

        return false;
    }

    public Pattern[] getList() {
        return list;
    }

}
