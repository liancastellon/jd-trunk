package org.jdownloader.extensions.extraction.split;

import java.io.File;
import java.util.List;

import org.appwork.utils.IO;
import org.appwork.utils.Regex;
import org.jdownloader.extensions.extraction.Archive;
import org.jdownloader.extensions.extraction.ArchiveFactory;
import org.jdownloader.extensions.extraction.ArchiveFile;
import org.jdownloader.extensions.extraction.DummyArchive;
import org.jdownloader.extensions.extraction.DummyArchiveFile;
import org.jdownloader.extensions.extraction.ExtractionController;
import org.jdownloader.extensions.extraction.ExtractionControllerConstants;
import org.jdownloader.extensions.extraction.ExtractionControllerException;
import org.jdownloader.extensions.extraction.IExtraction;
import org.jdownloader.extensions.extraction.MissingArchiveFile;
import org.jdownloader.extensions.extraction.multi.ArchiveException;
import org.jdownloader.extensions.extraction.multi.CheckException;
import org.jdownloader.logging.LogController;

public class HachaSplit extends IExtraction {

    public static class HachaHeader {

        protected final String fileName;

        public String getFileName() {
            return fileName;
        }

        public long getFileSize() {
            return fileSize;
        }

        public int getNumberOfParts() {
            return numberOfParts;
        }

        protected final long fileSize;
        protected final int  headerSize;

        public int getHeaderSize() {
            return headerSize;
        }

        protected final int numberOfParts;

        protected HachaHeader(final String fileName, final int headerSize, final long fileSize, final int numberOfParts) {
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.headerSize = headerSize;
            this.numberOfParts = numberOfParts;
        }
    }

    private final SplitType splitType = SplitType.HACHA_SPLIT;

    public Archive buildArchive(ArchiveFactory link, boolean allowDeepInspection) throws ArchiveException {
        return SplitType.createArchive(link, splitType, allowDeepInspection);
    }

    @Override
    public boolean findPassword(ExtractionController controller, String password, boolean optimized) {
        return true;
    }

    @Override
    public void extract(ExtractionController ctrl) {
        final Archive archive = getArchive();
        final String matches[] = splitType.getMatches(archive.getFirstArchiveFile().getFilePath());
        if (matches != null) {
            try {
                final HachaHeader hachaHeader = parseHachaHeader(archive.getFirstArchiveFile());
                if (SplitUtil.merge(controller, hachaHeader.getFileName(), hachaHeader.getHeaderSize(), getConfig())) {
                    archive.setExitCode(ExtractionControllerConstants.EXIT_CODE_SUCCESS);
                } else {
                    if (archive.getExitCode() == -1) {
                        archive.setExitCode(ExtractionControllerConstants.EXIT_CODE_FATAL_ERROR);
                    }
                }
                return;
            } catch (ExtractionControllerException e) {
                archive.setExitCode(e.getExitCode());
            }
        } else {
            archive.setExitCode(ExtractionControllerConstants.EXIT_CODE_FATAL_ERROR);
        }
    }

    public boolean isAvailable() {
        return true;
    }

    public int getCrackProgress() {
        return 100;
    }

    public boolean prepare() {
        return true;
    }

    public void close() {
    }

    public DummyArchive checkComplete(Archive archive) throws CheckException {
        try {
            final DummyArchive ret = new DummyArchive(archive, splitType.name());
            boolean hasMissingArchiveFiles = false;
            for (ArchiveFile archiveFile : archive.getArchiveFiles()) {
                if (archiveFile instanceof MissingArchiveFile) {
                    hasMissingArchiveFiles = true;
                }
                ret.add(new DummyArchiveFile(archiveFile));
            }
            if (hasMissingArchiveFiles == false) {
                final String firstArchiveFile = archive.getFirstArchiveFile().getFilePath();
                final String partNumberOfFirstArchiveFile = splitType.getPartNumberString(firstArchiveFile);
                if (splitType.getFirstPartIndex() != splitType.getPartNumber(partNumberOfFirstArchiveFile)) {
                    throw new CheckException("Wrong firstArchiveFile(" + firstArchiveFile + ") for Archive(" + archive.getName() + ")");
                }
                if (archive.getFirstArchiveFile().exists()) {
                    final HachaHeader hachaHeader = parseHachaHeader(archive.getFirstArchiveFile());
                    final List<ArchiveFile> missingArchiveFiles = SplitType.getMissingArchiveFiles(archive, splitType, hachaHeader.getNumberOfParts());
                    if (missingArchiveFiles != null) {
                        for (ArchiveFile missingArchiveFile : missingArchiveFiles) {
                            ret.add(new DummyArchiveFile(missingArchiveFile));
                        }
                    }
                    if (ret.getSize() < hachaHeader.getNumberOfParts()) {
                        throw new CheckException("Missing archiveParts(" + hachaHeader.getNumberOfParts() + "!=" + ret.getSize() + ") for Archive(" + archive.getName() + ")");
                    } else if (ret.getSize() > hachaHeader.getNumberOfParts()) {
                        throw new CheckException("Too many archiveParts(" + hachaHeader.getNumberOfParts() + "!=" + ret.getSize() + ") for Archive(" + archive.getName() + ")");
                    }
                }
            }
            return ret;
        } catch (CheckException e) {
            throw e;
        } catch (Throwable e) {
            throw new CheckException("Cannot check Archive(" + archive.getName() + ")", e);
        }
    }

    public static HachaHeader parseHachaHeader(ArchiveFile archiveFile) {
        try {
            if (archiveFile != null && archiveFile.exists()) {
                final byte[] hachaHeaderBytes = IO.readFile(new File(archiveFile.getFilePath()), 1024);
                if (hachaHeaderBytes.length > 9) {
                    final String hachaHeaderString = new String(hachaHeaderBytes, 9, hachaHeaderBytes.length - 9, "US-ASCII");
                    final String parsedHachaHeader[] = new Regex(hachaHeaderString, "\\?{5}(.*?)\\?{5}(\\d+)\\?{5}(\\d+)\\?{5}").getRow(0);
                    final long fileSize = Long.parseLong(parsedHachaHeader[1]);
                    final int headerSize = 4 + (5 * 5) + parsedHachaHeader[0].length() + parsedHachaHeader[1].length() + parsedHachaHeader[2].length();
                    final long completeSize = fileSize + headerSize;
                    final long segmentSize = Long.parseLong(parsedHachaHeader[2]);
                    final int numberOfParts = (int) (completeSize / segmentSize) + (completeSize % segmentSize == 0 ? 0 : 1);
                    return new HachaHeader(parsedHachaHeader[0], headerSize, fileSize, numberOfParts);
                }
            }
        } catch (final Throwable e) {
            LogController.CL().log(e);
        }
        return null;
    }

    @Override
    public Boolean isSupported(ArchiveFactory factory, boolean allowDeepInspection) {
        if (allowDeepInspection) {
            try {
                return SplitType.createArchive(factory, splitType, allowDeepInspection) != null;
            } catch (ArchiveException e) {
                getLogger().log(e);
                return false;
            }
        } else {
            return splitType.matches(factory.getFilePath());
        }
    }

}
