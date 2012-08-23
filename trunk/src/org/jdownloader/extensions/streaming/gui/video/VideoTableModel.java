package org.jdownloader.extensions.streaming.gui.video;

import java.awt.Point;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.SwingConstants;

import org.appwork.swing.components.tooltips.ExtTooltip;
import org.appwork.swing.components.tooltips.IconLabelToolTip;
import org.appwork.swing.exttable.columns.ExtTextColumn;
import org.appwork.utils.images.IconIO;
import org.jdownloader.extensions.streaming.T;
import org.jdownloader.extensions.streaming.gui.MediaTableModel;
import org.jdownloader.extensions.streaming.mediaarchive.MediaArchiveController;
import org.jdownloader.extensions.streaming.mediaarchive.VideoListController;
import org.jdownloader.extensions.streaming.mediaarchive.VideoMediaItem;

public class VideoTableModel extends MediaTableModel<VideoMediaItem, VideoListController> {

    public VideoTableModel(MediaArchiveController mediaArchiveController) {
        super("VideoTableModel", mediaArchiveController.getVideoController());

    }

    @Override
    protected void initColumns() {
        addColumn(new ExtTextColumn<VideoMediaItem>(T._.gui_video_name()) {

            public ExtTooltip createToolTip(final Point position, final VideoMediaItem obj) {

                try {
                    return new IconLabelToolTip(obj.getName(), new ImageIcon(IconIO.getScaledInstance(ImageIO.read(new File(obj.getThumbnailPath())), 320, 240))) {
                        {
                            label.setHorizontalTextPosition(SwingConstants.CENTER);
                            label.setVerticalTextPosition(SwingConstants.BOTTOM);
                        }
                    };
                } catch (IOException e) {

                }
                return super.createToolTip(position, obj);
            }

            protected Icon getIcon(final VideoMediaItem value) {
                try {

                    // of course this is a performance desaster!
                    return new ImageIcon(IconIO.getScaledInstance(ImageIO.read(new File(value.getThumbnailPath())), 32, 32));
                } catch (Throwable e) {

                }
                return null;
            }

            @Override
            public String getStringValue(VideoMediaItem value) {

                return value.getName();
            }
        });

        addColumn(new ExtTextColumn<VideoMediaItem>(T._.gui_video_format()) {

            @Override
            public String getStringValue(VideoMediaItem value) {
                return value.getContainerFormat();
            }
        });

        addColumn(new ExtTextColumn<VideoMediaItem>(T._.gui_video_status()) {

            @Override
            public String getStringValue(VideoMediaItem value) {
                return "";
            }
        });
    }
}
