package jd.plugins.components;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.jdownloader.plugins.components.config.MediathekProperties;

import jd.plugins.DownloadLink;

public class MediathekHelper {
    /** TODO: Ensure mp3 (audioonly) compatibility, put less info in filename if not required e.g. leave out protocol if we only have one */
    public static String getMediathekFilename(final DownloadLink dl, final MediathekProperties data, final boolean multipleProtocolsAvailable, final boolean sameResolutionWithDifferentBitratePossible) {
        final String title = data.getTitle();
        final String show = data.getShow();
        String channel = data.getChannel();
        if (channel == null) {
            channel = dl.getHost().replace(".", "_");
        }
        String protocol = data.getProtocol();
        if (protocol != null) {
            /* necessary for video player to find correct subtitle */
            protocol = protocol.replace("httpsub", "http");
            protocol = protocol.replace("hlssub", "hls");
        }
        final String videoResolution = data.getResolution();
        final String date_formatted = formatDate(data.getReleaseDate());
        final String type = data.getStreamingType();
        final long bitrateTotal = data.getBitrateTotal();
        final long bandwith = data.getBandwidth();
        /* TODO: value zero is not possible at the moment! 0 = possible, 0 usually means special season/episode */
        final int seasonNumber = data.getSeasonNumber();
        final int episodeNumber = data.getEpisodeNumber();
        String filename = "";
        if (date_formatted != null) {
            filename = date_formatted + "_";
        }
        filename += channel + "_";
        if (show != null) {
            filename += show + " - ";
        }
        /* TODO: value zero is not possible at the moment! */
        if (seasonNumber > 0 && episodeNumber > 0) {
            /* TODO: Check if we can have the case that only episode- or seasonnumber is given! */
            filename += String.format(Locale.US, "S%0" + getPadLength(seasonNumber) + "d" + "E%0" + getPadLength(episodeNumber) + "d", seasonNumber, episodeNumber);
        }
        if (title != null) {
            filename += "_" + title;
        }
        /* Only add protocol to filename if we can have multiple protocols */
        if (multipleProtocolsAvailable) {
            filename += "_" + protocol;
        }
        /* Use either bitrate or bandwidth and only if available- and required */
        if (sameResolutionWithDifferentBitratePossible && bitrateTotal > 0) {
            filename += "_" + bitrateTotal;
        } else if (sameResolutionWithDifferentBitratePossible && bandwith > 0) {
            filename += "_" + bandwith;
        }
        if (videoResolution != null) {
            filename += "_" + videoResolution;
        }
        String ext = data.getFileExtension();
        if (ext == null) {
            /* We need an extension - try to guess the right one! */
            if ("subtitle".equalsIgnoreCase(type)) {
                ext = "srt";
            } else {
                ext = "mp4";
            }
        }
        ext = "." + ext;
        filename += ext;
        filename = encodeUnicode(filename);
        return filename;
    }

    /**
     * 2018-07-19: Replaces chars not ment to be in filenames (at least under Windows) with similar chars. TODO: move this function
     * somewhere else. Function inside "Plugin" class is not static.
     */
    public static String encodeUnicode(final String input) {
        if (input != null) {
            String output = input;
            output = output.replace(":", ";");
            output = output.replace("|", "¦");
            output = output.replace("<", "[");
            output = output.replace(">", "]");
            output = output.replace("/", "⁄");
            output = output.replace("\\", "∖");
            output = output.replace("*", "#");
            output = output.replace("?", "¿");
            // not illegal
            // output = output.replace("!", "¡");
            output = output.replace("!", "¡");
            output = output.replace("\"", "'");
            return output;
        }
        return null;
    }

    public static final int getPadLength(final int size) {
        if (size < 100) {
            return 2;
        } else if (size < 1000) {
            return 3;
        } else if (size < 10000) {
            return 4;
        } else {
            /* This should never happen */
            return 1;
        }
    }

    /** Use this if there is only 1 protocol and only 1 resolution for every bitrate available! */
    public static String getMediathekFilename(final DownloadLink dl) {
        return getMediathekFilename(dl, dl.bindData(MediathekProperties.class), false, false);
    }

    public static String formatDate(final long date) {
        if (date <= 0) {
            return null;
        }
        String formattedDate = null;
        final String targetFormat = "yyyy-MM-dd";
        Date theDate = new Date(date);
        try {
            final SimpleDateFormat formatter = new SimpleDateFormat(targetFormat);
            formattedDate = formatter.format(theDate);
        } catch (Exception e) {
            /* prevent input error killing plugin */
            formattedDate = "date_error";
        }
        return formattedDate;
    }
}
