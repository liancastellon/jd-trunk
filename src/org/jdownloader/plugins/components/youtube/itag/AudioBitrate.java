package org.jdownloader.plugins.components.youtube.itag;

import org.appwork.storage.config.annotations.IntegerInterface;
import org.appwork.storage.config.annotations.LabelInterface;
import org.jdownloader.plugins.components.youtube.YT_STATICS;
import org.jdownloader.plugins.components.youtube.variants.AbstractVariant;
import org.jdownloader.plugins.components.youtube.variants.AudioInterface;

public enum AudioBitrate implements IntegerInterface,LabelInterface {
    KBIT_256(256),
    KBIT_192(192),
    KBIT_160(160),
    KBIT_152(152),
    KBIT_128(128),
    KBIT_96(96),
    KBIT_64(64),
    KBIT_48(48),
    KBIT_32(32),
    KBIT_32_ESTIMATED(31),
    KBIT_24(24),
    KBIT_12(12);

    private int kbit;

    private AudioBitrate(int kbit) {
        this.kbit = kbit;
    }

    public int getKbit() {
        return kbit;
    }

    public String getLabel() {

        return kbit + " kbit/s";
    }

    @Override
    public int getInt() {
        return kbit;
    }

    public static AudioBitrate getByVariant(AbstractVariant o1) {
        if (o1 instanceof AudioInterface) {
            return ((AudioInterface) o1).getAudioBitrate();
        }
        return null;
    }

    public static int getSortId(AbstractVariant v) {

        AudioBitrate res = getByVariant(v);
        if (res == null) {
            return -1;
        }
        Object intObj = YT_STATICS.SORTIDS_AUDIO_BITRATE.get(res);
        if (intObj == null) {
            return -1;
        }
        return ((Number) intObj).intValue();
    }

}
