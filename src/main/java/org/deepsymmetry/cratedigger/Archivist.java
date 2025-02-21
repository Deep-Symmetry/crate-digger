package org.deepsymmetry.cratedigger;

import org.apiguardian.api.API;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Supports the creation of archives of all the metadata needed from rekordbox media exports to enable full Beat Link
 * features when working with the Opus Quad, which is unable to serve the metadata itself.
 */
@API(status = API.Status.EXPERIMENTAL)
public class Archivist {

    /**
     * Holds the singleton instance of this class.
     */
    private static final Archivist instance = new Archivist();

    /**
     * Look up the singleton instance of this class.
     *
     * @return the only instance that exists
     */
    @API(status = API.Status.EXPERIMENTAL)
    public static Archivist getInstance() {
        return instance;
    }

    /**
     * Make sure the only way to get an instance is to call {@link #getInstance()}.
     */
    private Archivist() {
        // Prevent instantiation
    }

    /**
     * An interface that can be used to display progress to the user as an archive is being created, and allow
     * them to cancel the process if desired.
     */
    @API(status = API.Status.EXPERIMENTAL)
    public interface ArchiveListener {

        /**
         * Called as each analysis or artwork file is archived, so that progress can be displayed;
         * the process can be canceled by returning {@code false}.
         *
         * @param bytesCopied how many bytes of analysis and artwork files have been added to the archive
         * @param bytesTotal how many bytes of analysis and artwork files are present in the media export being archived
         *
         * @return {@code true} to continue archiving tracks, or {@code false} to cancel the process and delete the archive.
         */
        boolean continueCreating(long bytesCopied, long bytesTotal);
    }

    /**
     * Allows our recursive file copy operations to exclude files that we do not want in the archive.
     */
    private interface PathFilter {
        /**
         * Check whether something belongs in the archive
         * @param path the file that will potentially be copied
         * @return {@code true} to actually copy the file.
         */
        boolean include(Path path);
    }

    /**
     * Creates an archive file containing all the metadata found in the rekordbox media export containing the
     * supplied database export that is needed to enable full Beat Link features when that media is being used in
     * an Opus Quad, which is unable to serve the metadata itself.
     *
     * @param database the parsed database found within the media export for which an archive is desired
     * @param file where to write the archive
     *
     * @throws IOException if there is a problem creating the archive
     */
    @API(status = API.Status.EXPERIMENTAL)
    public void createArchive(Database database, File file) throws IOException {
        createArchive(database, file, null);
    }

    /**
     * Helper method to recursively count the number of file bytes that will be copied if we copy a folder.
     *
     * @param source the folder to be copied
     * @param filter if present, allows files to be selectively excluded from being counted
     *
     * @return the new total number of bytes that need to be copied.
     *
     * @throws IOException if there is a problem scanning the folder
     */
    private long sizeFolder(Path source, PathFilter filter)
            throws IOException {

        final AtomicLong totalBytes = new AtomicLong(0);

        Files.walkFileTree(source, new SimpleFileVisitor<>() {

            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                if (filter == null || filter.include(file)) {
                    totalBytes.addAndGet(Files.size(file));
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return totalBytes.get();
    }

    /**
     * Helper method to recursively copy a folder.
     *
     * @param source the folder to be copied
     * @param target where the folder should be copied
     * @param filter if present, allows files to be selectively excluded from being counted
     * @param listener if not {@code null} will be called after copying each file to support progress reports and
     *                 allow cancellation
     * @param bytesCopied the number of bytes that have already been copied, for use in updating the listener
     * @param totalBytes the total number of bytes that are going to be copied, for use in updating the listener
     * @param options the copy options (see {@link Files#copy(Path, Path, CopyOption...)})
     *
     * @return the new total number of bytes copied, or -1 if the listener requested that the copy be canceled.
     *
     * @throws IOException if there is a problem copying the folder
     */
    private long copyFolder(Path source, Path target, PathFilter filter, ArchiveListener listener, long bytesCopied, long totalBytes, CopyOption... options)
            throws IOException {

        final AtomicLong nowCopied = new AtomicLong(bytesCopied);
        final AtomicBoolean canceled = new AtomicBoolean(false);

        Files.walkFileTree(source, new SimpleFileVisitor<>() {

            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                if (filter == null || filter.include(file)) {
                    Files.copy(file, target.resolve(source.relativize(file).toString()), options);
                    nowCopied.addAndGet(Files.size(file));
                    if (listener == null || listener.continueCreating(nowCopied.get(), totalBytes)) {
                        return FileVisitResult.CONTINUE;
                    } else {
                        canceled.set(true);
                        return FileVisitResult.TERMINATE;
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });

        if (canceled.get()) {
            return -1;
        }
        return nowCopied.get();
    }

    /**
     * Creates an archive file containing all the metadata found in the rekordbox media export containing the
     * supplied database export that needed to enable full Beat Link features when that media is being used in
     * an Opus Quad, which is unable to serve the metadata itself.
     *
     * @param database the parsed database found within the media export for which an archive is desired
     * @param archiveFile where to write the archive, will be replaced if it already exists
     * @param listener if not {@code null}, will be called throughout the archive process to support progress
     *                 reports and allow cancellation
     *
     * @throws IOException if there is a problem creating the archive
     */
    @API(status = API.Status.EXPERIMENTAL)
    public void createArchive(Database database, File archiveFile, ArchiveListener listener) throws IOException {
        final Path archivePath = archiveFile.toPath();
        Files.deleteIfExists(archivePath);
        final URI fileUri = archivePath.toUri();
        // We want to exclude .2EX files since we can’t use them, and they bloat the archive.
        final PathFilter analysisFilter = path -> !path.toString().endsWith(".2EX");
        boolean failed = false;

        try (FileSystem fileSystem = FileSystems.newFileSystem(new URI("jar:" + fileUri.getScheme(), fileUri.getPath(), null),
                Map.of("create", "true"))) {

            // Copy the database export itself.
            Files.copy(database.sourceFile.toPath(), fileSystem.getPath("/export.pdb"));

            // If there is a Device Library Plus export, copy it as well.
            final File plusFile = new File(database.sourceFile.getParentFile(), "exportLibrary.db");
            if (plusFile.exists() && plusFile.canRead()) {
                Files.copy(plusFile.toPath(), fileSystem.getPath("/exportLibrary.db"));
            }

            // Copy the track analysis and artwork files.
            final Path pioneerFolder = plusFile.toPath().getParent().getParent();
            final String pioneerFolderName = pioneerFolder.getFileName().toString();
            final Path artFolder = pioneerFolder.resolve("Artwork");
            //noinspection SpellCheckingInspection
            final Path analysisFolder = pioneerFolder.resolve("USBANLZ");
            final long totalBytes = sizeFolder(artFolder, null) + sizeFolder(analysisFolder, analysisFilter);
            long bytesCopied = copyFolder(artFolder, fileSystem.getPath(pioneerFolderName, "Artwork"), null, listener,
                    0, totalBytes, StandardCopyOption.REPLACE_EXISTING);
            if (bytesCopied < 0) {
                // Listener asked us to cancel.
                failed = true;
            } else {
                //noinspection SpellCheckingInspection
                bytesCopied = copyFolder(analysisFolder, fileSystem.getPath(pioneerFolderName, "USBANLZ"), analysisFilter, listener,
                        bytesCopied, totalBytes, StandardCopyOption.REPLACE_EXISTING);
                if (bytesCopied < 0) {
                    failed = true;
                }
            }
        } catch (URISyntaxException e) {
            failed = true;
            throw new IOException("Unable to create jar filesystem at file location", e);
        } catch (IOException e) {
            failed = true;
            throw e;
        }
        finally {
            if (failed) {
                Files.deleteIfExists(archivePath);
            }
        }
    }

}
