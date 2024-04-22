package org.deepsymmetry.cratedigger;

import org.deepsymmetry.cratedigger.pdb.RekordboxPdb;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;

/**
 * Supports the creation of archives of all the metadata needed from rekordbox media exports to enable full Beat Link
 * features when working with the Opus Quad, which is unable to serve the metadata itself.
 */
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
    public interface ArchiveListener {

        /**
         * Called once we determine how many tracks need to be archived, and as each one is completed, so that
         * progress can be displayed; the process can be canceled by returning {@code false}.
         *
         * @param tracksCompleted how many tracks have been added to the archive
         * @param tracksTotal how many tracks are present in the media export being archived
         *
         * @return {@code true} to continue archiving tracks, or {@code false} to cancel the process and delete the archive.
         */
        boolean continueCreating(int tracksCompleted, int tracksTotal);
    }

    /**
     * Creates an archive file containing all the metadata found in the rekordbox media export containing the
     * supplied database export that needed to enable full Beat Link features when that media is being used in
     * an Opus Quad, which is unable to serve the metadata itself.
     *
     * @param database the parsed database found within the media export for which an archive is desired
     * @param file where to write the archive
     *
     * @throws IOException if there is a problem creating the archive
     */
    public void createArchive(Database database, File file) throws IOException {
        createArchive(database, file, null);
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
    public void createArchive(Database database, File archiveFile, ArchiveListener listener) throws IOException {
        final Path archivePath = archiveFile.toPath();
        final Path mediaPath = database.sourceFile.getParentFile().getParentFile().getParentFile().toPath();
        Files.deleteIfExists(archivePath);
        final URI fileUri = archivePath.toUri();
        final int totalTracks = database.trackIndex.size();
        try (FileSystem fileSystem = FileSystems.newFileSystem(new URI("jar:" + fileUri.getScheme(), fileUri.getPath(), null),
                Map.of("create", "true"))) {

            // Copy the database export itself.
            Files.copy(database.sourceFile.toPath(), fileSystem.getPath("/export.pdb"));

            // Copy each track's analysis and artwork files.
            final Iterator<Map.Entry<Long, RekordboxPdb.TrackRow>> iterator = database.trackIndex.entrySet().iterator();
            int completed = 0;
            while ((listener == null || listener.continueCreating(completed, totalTracks)) && iterator.hasNext()) {
                final Map.Entry<Long, RekordboxPdb.TrackRow> entry = iterator.next();
                final RekordboxPdb.TrackRow track = entry.getValue();

                // First the original analysis file.
                final String anlzPathString = Database.getText(track.analyzePath());
                final Path anlzPath = mediaPath.resolve(anlzPathString.substring(1));
                Path destPath = fileSystem.getPath(anlzPathString);
                Files.createDirectories(destPath.getParent());
                Files.copy(anlzPath, destPath);

                // Then the extended analysis file, if it exists.
                final String extPathString = anlzPathString.substring(0, anlzPathString.length() - 3) + "EXT";
                final Path extPath = mediaPath.resolve(extPathString.substring(1));
                if (extPath.toFile().canRead()) {
                    destPath = fileSystem.getPath(extPathString);
                    Files.copy(extPath, destPath);
                }

                // Finally, the album art.
                final RekordboxPdb.ArtworkRow artwork = database.artworkIndex.get(track.artworkId());
                if (artwork != null) {
                    final String artPathString = Database.getText(artwork.path());
                    final Path artPath = mediaPath.resolve(artPathString.substring(1));
                    if (artPath.toFile().canRead()) {
                        destPath = fileSystem.getPath(artPathString);
                        Files.createDirectories(destPath.getParent());
                        Files.copy(artPath, destPath);
                    }
                }

                ++completed;  // For use in providing progress feedback if there is a listener.
            }
        } catch (URISyntaxException e) {
            Files.deleteIfExists(archivePath);
            throw new IOException("Unable to create jar filesystem at file location", e);
        } catch (IOException e) {
            Files.deleteIfExists(archivePath);
            throw e;
        }
    }
}
