package com.grun.calorietracker.service.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;

class LocalMediaPathGuardTest {
    @TempDir Path directory;

    @Test void rejectsOutsidePaths() {
        assertThatThrownBy(() -> LocalMediaPathGuard.requireInside(directory, directory.resolve("../outside.bin")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void readsOnlyUpToTheActualByteLimit() throws Exception {
        Path file = directory.resolve("file.bin");
        Files.write(file, new byte[]{1, 2, 3});
        assertThat(LocalMediaPathGuard.readBounded(directory, file, 3)).containsExactly(1, 2, 3);
        assertThatThrownBy(() -> LocalMediaPathGuard.readBounded(directory, file, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsFinalFileAndParentDirectorySymlinks() throws Exception {
        Path outside = Files.createDirectory(directory.resolve("outside"));
        Path root = Files.createDirectory(directory.resolve("root"));
        Path file = Files.write(outside.resolve("secret.bin"), new byte[]{9});
        Path link = root.resolve("link");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            Assumptions.abort("Host does not permit symlink creation: " + exception.getClass().getSimpleName());
        }
        assertThatThrownBy(() -> LocalMediaPathGuard.requireInside(root, link.resolve("secret.bin")))
                .isInstanceOf(IllegalArgumentException.class);
        Path finalLink = root.resolve("secret.bin");
        Files.createSymbolicLink(finalLink, file);
        assertThatThrownBy(() -> LocalMediaPathGuard.readBounded(root, finalLink, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readAllBytes(file)).containsExactly(9);
    }

    @Test void rejectsWindowsJunctionRedirectingTheStorageRoot() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        Path outside = Files.createDirectory(directory.resolve("outside-junction"));
        Path root = directory.resolve("storage-junction");
        Path file = Files.write(outside.resolve("secret.bin"), new byte[]{7});
        var process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", root.toString(), outside.toString())
                .redirectErrorStream(true).start();
        assertThat(process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThatThrownBy(() -> LocalMediaPathGuard.requireInside(root, root.resolve("secret.bin")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readAllBytes(file)).containsExactly(7);
    }
}
