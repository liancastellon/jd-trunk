package jd.unrar.hjsplitt;

import java.io.File;
import java.io.FileFilter;

import jd.controlling.ProgressController;
import jd.parser.Regex;
import jd.unrar.FileSignatures;

public class Merge {

    public static File mergeIt(File file, String[] following, boolean autoDelete, File Target) {
        try {

            String name = file.getName();
            boolean unix = false;
            if (name.matches(".*\\.\\a.$")) {
                unix = true;
            }
            if (!unix && (!name.matches(".*\\.[\\d]{3,20}($|\\..*)") || name.matches("(?is).*\\.7z\\.[\\d]+$"))) { return null; }
            File mergeFile = null;
            if (unix) {
                mergeFile = new File(file.getParentFile(), name.replaceFirst("\\.a.$", ".aa"));
            } else {
                mergeFile = new File(file.getParentFile(), name.replaceFirst("\\.[\\d]+($|\\.)", ".001$1"));
            }
            if (!mergeFile.exists() || !mergeFile.isFile()) { return null; }
            final String matcher = unix ? name.replaceFirst("\\.a.$", "") + "\\.a.$" : name.replaceFirst("\\.[\\d]+($|\\..*)", "") + "\\.[\\d]+($|\\..*)";
            if (following != null) {
                for (String element : following) {
                    if (element.matches(matcher)) { return null; }
                }
            }
            File[] files = file.getParentFile().listFiles(new FileFilter() {

                public boolean accept(File pathname) {
                    if (pathname.isFile() && pathname.getName().matches(matcher)) { return true; }
                    return false;
                }
            });
            if (unix) {
                char c = 'a';
                for (File element : files) {
                    try {
                        String n = element.getName().toLowerCase();
                        if (n.charAt(n.length() - 1) != c) { return null; }
                        ++c;
                    } catch (Exception e) {
                        // TODO: handle exception
                    }

                }
            } else {
                for (int i = 0; i < files.length; i++) {
                    try {
                        if (Integer.parseInt(new Regex(files[i].getName(), "\\.([\\d]+)($|\\.)").getMatch(0)) != i + 1) { return null; }
                    } catch (Exception e) {
                        // TODO: handle exception
                    }

                }
            }
            if (FileSignatures.fileSignatureEquals(new Integer[][] { FileSignatures.SIGNATURE_RAR, FileSignatures.SIGNATURE_7Z }, FileSignatures.getFileSignature(mergeFile))) { return null; }
            final ProgressController progress = new ProgressController("Default HJMerge", 100);
            try {

                JAxeJoiner join = JoinerFactory.getJoiner(mergeFile, Target);
                join.setProgressEventListener(new ProgressEventListener() {

                    long last = System.currentTimeMillis() + 1000;

                    public void handleEvent(ProgressEvent pe) {
                        try {
                            if (System.currentTimeMillis() - last > 100) {
                                progress.setStatus((int) (pe.lCurrent * 100 / pe.lMax));
                                last = System.currentTimeMillis();
                                progress.setStatusText(pe.lCurrent / 1048576 + " MB merged");
                            }
                        } catch (Exception e) {
                            // TODO: handle exception
                        }

                    }
                });
                join.run();
                if (autoDelete) {
                    for (File element : files) {
                        element.delete();
                    }
                }
                try {
                    progress.finalize();
                } catch (Exception e) {
                    // TODO: handle exception
                }

                return new File(join.sJoinedFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
            progress.finalize();
        } catch (Exception e) {
            // TODO: handle exception
        }
        return null;

    }
}
