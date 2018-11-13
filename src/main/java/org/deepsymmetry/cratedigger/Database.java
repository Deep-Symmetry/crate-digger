package org.deepsymmetry.cratedigger;

import io.kaitai.struct.KaitaiStruct;
import org.deepsymmetry.cratedigger.pdb.PdbFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

/**
 * <p>Parses rekordbox database export and track analysis files, providing access to the information they contain.</p>
 */
public class Database {

    private static final Logger logger = LoggerFactory.getLogger(FileFetcher.class);

    /**
     * Holds a reference to the parser for the file we were constructed with.
     */
    private final PdbFile pdbFile;

    /**
     * Construct a database access instance from the specified recordbox export file.
     * The file can obtained either from the SD or USB media, or directly from a player
     * using {@link FileFetcher#fetch(InetAddress, String, String, File)}
     *
     * @param pdbFile an export.pdb file
     *
     * @throws IOException if there is a problem reading the file
     */
    public Database(File pdbFile) throws IOException {
        this.pdbFile = PdbFile.fromFile(pdbFile.getAbsolutePath());
        indexTables();
    }

    /**
     * Scan the tables present in the database export, building our indices.
     */
    private void indexTables() {
        for (PdbFile.Table table : pdbFile.tables()) {
            switch (table.type()) {
                case TRACKS:
                    indexTracks(table);
                    break;
            }
        }
    }

    /**
     * A map from track ID to the actual track object. If this ends up taking too much space, it would be
     * possible to reorganize the Kaitai Struct mapping specification so that rows are parse instances of
     * the file itself, with parameters for the page and row numbers as well as the page type, allowing
     * them to be loaded directly, and this index would only need to store their addresses. Or we could
     * figure out how to find and use the index tables that must exist in the file somewhere, and avoid
     * building this at all.
     */
    private final SortedMap<Long,PdbFile.TrackRow> trackIndex = new TreeMap<Long, PdbFile.TrackRow>();

    /**
     * A map from track title to the set of track IDs with that title.
     */
    private final SortedMap<String, SortedSet<Long>> trackTitleIndex = new TreeMap<String, SortedSet<Long>>();

    /**
     * Parse and index all the tracks found in the database export.
     *
     * @param table the track table that has been found in the database.
     */
    private void indexTracks(PdbFile.Table table) {
        if (!trackIndex.isEmpty()) {
            throw new IllegalStateException("PDB file contains more than one Tracks table");
        }

        final long lastIndex = table.lastPage().index();  // This is how we know when to stop.
        PdbFile.PageRef currentRef = table.firstPage();
        boolean moreLeft = true;
        do {
            final PdbFile.Page page = currentRef.body();

            // Process only ordinary data pages.
            if (page.isDataPage()) {
                for (PdbFile.RowGroup rowGroup : page.rowGroups()) {
                    for (PdbFile.RowRef rowRef : rowGroup.rows()) {
                        if (rowRef.present()) {
                            // We found a track; index it by its ID.
                            PdbFile.TrackRow trackRow = (PdbFile.TrackRow)rowRef.body();
                            final long id = trackRow.id();
                            trackIndex.put(id, trackRow);

                            // Index the track ID by title as well.
                            final String title = getText(trackRow.title());
                            SortedSet<Long> titleIds = trackTitleIndex.get(title);
                            if (titleIds == null) {
                                titleIds = new TreeSet<Long>();
                                trackTitleIndex.put(title, titleIds);
                            }
                            titleIds.add(id);
                        }
                    }
                }
            }

            // Was this the final page in the table? If so, stop, otherwise, move on to the next page.
            if (currentRef.index() == lastIndex) {
                moreLeft = false;
            } else {
                currentRef = page.nextPage();
            }
        } while (moreLeft);

        // Freeze the contents of the track ID indices.
        for (String title : trackTitleIndex.keySet()) {
            trackTitleIndex.put(title, Collections.unmodifiableSortedSet(trackTitleIndex.get(title)));
        }

        logger.info("Indexed " + trackIndex.size() + " Tracks.");
    }

    /**
     * Look up the track with the specified ID.
     *
     * @param id the rekordbox id of the track desired
     *
     * @return the corresponding track row, if found, or {@code null}
     */
    public PdbFile.TrackRow findTrack(long id) {
        return trackIndex.get(id);
    }

    /**
     * Get the set of all known track IDs, in ascending order.
     *
     * @return a sorted set containing the rekordbox id values for all tracks found in the database export
     */
    public Set<Long> getTrackIds() {
        return Collections.unmodifiableSet(trackIndex.keySet());
    }

    /**
     * Get the index of track IDs by title.
     *
     * @return a sorted map whose keys are track titles and whose values are sorted sets of the IDs of tracks with
     *         that title
     */
    public SortedMap<String, SortedSet<Long>> getTrackTitleIndex() {
        return Collections.unmodifiableSortedMap(trackTitleIndex);
    }

    /**
     * Helper function to extract the text value from one of the strings found in the database, which
     * have a variety of obscure representations.
     *
     * @param string the string-encoding structure
     *
     * @return the text it contains
     */
    public String getText(PdbFile.DeviceSqlString string) {
        if (string.body() instanceof PdbFile.DeviceSqlShortAscii) {
            return ((PdbFile.DeviceSqlShortAscii) string.body()).text();
        } else if (string.body() instanceof  PdbFile.DeviceSqlLongAscii) {
            return ((PdbFile.DeviceSqlLongAscii) string.body()).text();
        } else if (string.body() instanceof PdbFile.DeviceSqlLongUtf16be) {
            return ((PdbFile.DeviceSqlLongUtf16be) string.body()).text();
        }
        throw new IllegalArgumentException("Unrecognized DeviceSqlString subtype:" + string);
    }
}
