package org.deepsymmetry.cratedigger;

import org.apiguardian.api.API;
import org.deepsymmetry.cratedigger.pdb.RekordboxPdb;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.Iterator;
import java.util.Map;

/**
 * Supports the creation of archives of all the metadata needed from rekordbox media exports to enable full Beat Link
 * features when working with the Opus Quad, which is unable to serve the metadata itself.
 */
@API(status = API.Status.EXPERIMENTAL)
public class Archivist {

    private static final Logger logger = LoggerFactory.getLogger(Archivist.class);
    
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
    @API(status = API.Status.EXPERIMENTAL)
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
    @API(status = API.Status.EXPERIMENTAL)
    public void createArchive(Database database, File archiveFile, ArchiveListener listener) throws IOException {
        final Path archivePath = archiveFile.toPath();
        final Path mediaPath = database.sourceFile.getParentFile().getParentFile().getParentFile().toPath();
        Files.deleteIfExists(archivePath);
        final URI fileUri = archivePath.toUri();
        final int totalTracks = database.trackIndex.size();
        boolean failed = false;

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
                archiveMediaItem(mediaPath, anlzPathString, fileSystem, "analysis file");

                // Then the extended analysis file, if it exists.
                final String extPathString = anlzPathString.substring(0, anlzPathString.length() - 3) + "EXT";
                archiveMediaItem(mediaPath, extPathString, fileSystem, "extended analysis file");

                // Finally, the album art.
                final RekordboxPdb.ArtworkRow artwork = database.artworkIndex.get(track.artworkId());
                if (artwork != null) {
                    final String artPathString = Database.getText(artwork.path());
                    archiveMediaItem(mediaPath, artPathString, fileSystem, "artwork file");

                    // Then, copy the high resolution album art, if it exists
                    final String highResArtPathString = artPathString.replaceFirst("(\\.\\w+$)", "_m$1");
                    archiveMediaItem(mediaPath, highResArtPathString, fileSystem, "high-resolution artwork file");
                }

                ++completed;  // For use in providing progress feedback if there is a listener.
            }

            if (iterator.hasNext()) {
                failed = true;  // We were canceled.
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

    /**
     * Helper method to archive a single media export file when creating a metadata archive.
     *
     * @param mediaPath the path to the file to be archived
     * @param pathString the string which holds the absolute path to the media item
     * @param archive the ZIP filesystem in which the metadata archive is being created
     * @param description the text identifying the type of file being archived, in case we need to log a warning
     *
     * @throws IOException if there is an unexpected problem adding the media item to the archive
     */
    private static void archiveMediaItem(Path mediaPath, String pathString, FileSystem archive, String description) throws IOException {
        final Path sourcePath = mediaPath.resolve(pathString.substring(1));
        final Path destinationPath = archive.getPath(pathString);
        Files.createDirectories(destinationPath.getParent());
        try {
            Files.copy(sourcePath, destinationPath);
        } catch (FileAlreadyExistsException e) {
            logger.warn("Skipping copy of {} {} because it has already been archived." , description, destinationPath);
        }
    }
}
