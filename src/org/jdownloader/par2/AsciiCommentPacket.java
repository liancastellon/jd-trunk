package org.jdownloader.par2;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class AsciiCommentPacket extends Packet {
    public static final byte[]     MAGIC = new byte[] { 'P', 'A', 'R', ' ', '2', '.', '0', '\0', 'C', 'o', 'm', 'm', 'A', 'S', 'C', 'I' };
    protected final RawPacket      rawPacket;
    protected static final Charset ASCII = Charset.forName("ASCII");

    /**
     * x*4 - ASCII The comment. NB: This is not a null terminated string!
     *
     * @param rawPacket
     */
    public AsciiCommentPacket(RawPacket rawPacket) {
        this.rawPacket = rawPacket;
    }

    @Override
    public String toString() {
        return "AsciiCommentPacket|Comment:" + getComment();
    }

    public ByteBuffer getCommentAsByteBuffer(final boolean ignoreNullTermination) {
        return getByteBuffer(0, getRawPacket().getBody().length, ignoreNullTermination);
    }

    public String getComment() {
        return ASCII.decode(getCommentAsByteBuffer(false)).toString();
    }

    @Override
    public byte[] getType() {
        return MAGIC;
    }

    @Override
    public RawPacket getRawPacket() {
        return rawPacket;
    }
}
