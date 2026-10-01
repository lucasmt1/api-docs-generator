package dev.apidocs.core.testsupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;

/** NTFS directory junctions (Windows only; they need no special privilege, unlike symbolic links). */
public final class Junctions {

    private Junctions() {
    }

    /** Creates {@code link} as a junction to the directory {@code target}; skips the test elsewhere. */
    public static void create(Path link, Path target) throws IOException, InterruptedException {
        Assumptions.assumeTrue(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows"),
                "NTFS junctions exist only on Windows");
        Files.createDirectories(link.getParent());
        Process mklink = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString())
                .redirectErrorStream(true)
                .start();
        mklink.getInputStream().readAllBytes();
        boolean created = mklink.waitFor(30, TimeUnit.SECONDS) && mklink.exitValue() == 0;
        Assumptions.assumeTrue(created, "mklink /J is not available here");
        BasicFileAttributes attributes = Files.readAttributes(link, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        Assumptions.assumeTrue(attributes.isOther(), "the junction is not reported as a reparse point");
    }
}
