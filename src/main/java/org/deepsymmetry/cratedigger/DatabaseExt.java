package org.deepsymmetry.cratedigger;

import org.apiguardian.api.API;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

/**
 * <p>Parses rekordbox database {@code exportExt.pdb} files, providing access to the information they contain.</p>
 */
@API(status = API.Status.EXPERIMENTAL)
public class DatabaseExt implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseExt.class);

    /**
     * Helper class to parse and interact with the database file conveniently.
     */
    private final DatabaseUtil databaseUtil;

    /**
     * <p>Construct a database access instance from the specified recordbox export file.
     * The file can obtained either from the SD or USB media, or directly from a player
     * using {@link FileFetcher#fetch(InetAddress, String, String, File)}.</p>
     *
     * <p>Be sure to call {@link #close()} when you are done using the parsed database
     * to close the underlying file or users will be unable to unmount the drive holding
     * it until they quit your program.</p>
     *
     * @param sourceFile an export.pdb file
     *
     * @throws IOException if there is a problem reading the file
     */
    @API(status = API.Status.EXPERIMENTAL)
    public DatabaseExt(File sourceFile) throws IOException {
        databaseUtil = new DatabaseUtil(sourceFile, true);
        // TODO: Gather and index the tag information.
    }

    /**
     * Close the file underlying the parsed database. This needs to be called if you want to be able
     * to unmount the media on which that file resides, but once it is done, you can no longer access
     * lazy elements within the database which have not already been parsed.
     *
     * @throws IOException if there is a problem closing the file
     */
    @Override
    public void close() throws IOException {
        databaseUtil.close();
    }

}
