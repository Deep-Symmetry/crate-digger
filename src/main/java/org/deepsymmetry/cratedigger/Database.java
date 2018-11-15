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
                case COLORS:
                    indexColors(table);
                    break;
                case ARTISTS:
                    indexArtists(table);
            }
        }
    }

    /**
     * An interface used to process each row found in a table when traversing them to build our indices.
     * This allows the common code for traversing a table to be reused, while specializing the handling
     * of each kind of table's rows.
     */
    private interface RowHandler {
        /**
         * Each row found in a table being scanned will be passed to this function.
         *
         * @param row the row that has just been found
         */
        void rowFound(KaitaiStruct row);
    }

    /**
     * Parse and index all the rows found in a particular table. This method performs a scan of the
     * specified table, passing all rows that are encountered to an interface that knows what to do
     * with them.
     *
     * @param table the table being scanned and parsed
     * @param handler the code that knows how to index that kind of row
     */
    private void indexRows(PdbFile.Table table, RowHandler handler) {
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
                            // We found a row, pass it to the handler to be indexed appropriately.
                            handler.rowFound(rowRef.body());
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

    }

    /**
     * Adds a row ID to a secondary index which is sorted by some other attribute of the record (for example,
     * add a track id to the title index, so the track can be found by title).
     *
     * @param index the secondary index, which holds all the row IDs that have the specified key
     * @param key the secondary index value by which this row can be looked up
     * @param id the ID of the row to index under the specified key
     * @param <K> the type of the key (often String, but may be Long, e.g. to index tracks by artist ID)
     */
    private <K> void addToSecondaryIndex(SortedMap<K, SortedSet<Long>> index, K key, Long id) {
        SortedSet<Long> existingIds = index.get(key);
        if (existingIds ==  null) {
            existingIds = new TreeSet<Long>();
            index.put(key, existingIds);
        }
        existingIds.add(id);
    }

    /**
     * Protects a secondary index against further changes once we have finished indexing all the rows that
     * are going in to it.
     *
     * @param index the index that should no longer be modified.
     * @param <K> the type of the key (often String, but may be Long, e.g. to index tracks by artist ID)
     */
    private <K> void freezeSecondaryIndex(SortedMap<K, SortedSet<Long>> index) {
        for (K key : index.keySet()) {
            index.put(key, Collections.unmodifiableSortedSet(index.get(key)));
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
     * @param table the track table that has been found in the database
     */
    private void indexTracks(PdbFile.Table table) {
        if (!trackIndex.isEmpty()) {
            throw new IllegalStateException("PDB file contains more than one Tracks table.");
        }

        indexRows(table, new RowHandler() {
            @Override
            public void rowFound(KaitaiStruct row) {
                // We found a track; index it by its ID.
                PdbFile.TrackRow trackRow = (PdbFile.TrackRow)row;
                final long id = trackRow.id();
                trackIndex.put(id, trackRow);

                // Index the track ID by title as well.
                final String title = getText(trackRow.title());
                if (title.length() > 0) {
                    addToSecondaryIndex(trackTitleIndex, title, id);
                }
            }
        });
        freezeSecondaryIndex(trackTitleIndex);

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
     * A map from artist ID to the actual artist object.
     */
    private final SortedMap<Long,PdbFile.ArtistRow> artistIndex = new TreeMap<Long, PdbFile.ArtistRow>();

    /**
     * A map from artist name to the set of artist IDs with that name.
     */
    private final SortedMap<String, SortedSet<Long>> artistNameIndex = new TreeMap<String, SortedSet<Long>>();

    private void indexArtists(PdbFile.Table table) {
        if (!artistIndex.isEmpty()) {
            throw new IllegalStateException("PDB file contains more than one Artists table.");
        }

        indexRows(table, new RowHandler() {
            @Override
            public void rowFound(KaitaiStruct row) {
                PdbFile.ArtistRow artistRow = (PdbFile.ArtistRow)row;
                final long id = artistRow.id();
                artistIndex.put(id, artistRow);

                // Index the artist ID by name as well.
                final String name = getText(artistRow.name());
                if (name.length() > 0) {
                    addToSecondaryIndex(artistNameIndex, name, id);
                }
            }
        });
        freezeSecondaryIndex(artistNameIndex);

        logger.info("Indexed " + artistIndex.size() + " Artists.");
    }

    /**
     * Look up the artist with the specified ID.
     *
     * @param id the rekordbox id of the artist desired
     *
     * @return the corresponding artist row, if found, or {@code null}
     */
    public PdbFile.ArtistRow findArtist(long id) {
        return artistIndex.get(id);
    }

    /**
     * Get the index of artist IDs by name.
     *
     * @return a sorted map whose keys are artist names and whose values are sorted sets of the IDs of artists with
     *         that name
     */
    public SortedMap<String, SortedSet<Long>> getArtistNameIndex() {
        return Collections.unmodifiableSortedMap(artistNameIndex);
    }

    /**
     * A map from color ID to the actual color object.
     */
    private final SortedMap<Long,PdbFile.ColorRow> colorIndex = new TreeMap<Long, PdbFile.ColorRow>();

    /**
     * A map from color name to the set of color IDs with that name.
     */
    private final SortedMap<String, SortedSet<Long>> colorNameIndex = new TreeMap<String, SortedSet<Long>>();

    /**
     * Parse and index all the colors found in the database export.
     *
     * @param table the color table that has been found in the database
     */
    private void indexColors(PdbFile.Table table) {
        if (!colorIndex.isEmpty()) {
            throw new IllegalStateException("PDB file contains more than one Colors table.");
        }

        indexRows(table, new RowHandler() {
            @Override
            public void rowFound(KaitaiStruct row) {
                PdbFile.ColorRow colorRow = (PdbFile.ColorRow)row;
                final long id = colorRow.id();
                colorIndex.put(id, colorRow);

                // Index the color by name as well.
                final String name = Database.getText(colorRow.name());
                if (name.length() > 0) {
                    addToSecondaryIndex(colorNameIndex, name, id);
                }
            }
        });
        freezeSecondaryIndex(colorNameIndex);

        logger.info("Indexed " + colorIndex.size() + " Colors.");
    }

    /**
     * Look up the color with the specified ID.
     *
     * @param id the database id of the color desired
     *
     * @return the corresponding color row, if found, or {@code null}
     */
    public PdbFile.ColorRow findColor(long id) {
        return colorIndex.get(id);
    }

    /**
     * Get the index of colors by name.
     *
     * @return a sorted map whose keys are color names and whose values are sorted sets of the IDs of colors with
     *         that name
     */
    public SortedMap<String, SortedSet<Long>> getColorNameIndex() {
        return Collections.unmodifiableSortedMap(colorNameIndex);
    }

    /**
     * Helper function to extract the text value from one of the strings found in the database, which
     * have a variety of obscure representations.
     *
     * @param string the string-encoding structure
     *
     * @return the text it contains, which may have zero length, but will never be {@code null}
     */
    @SuppressWarnings("WeakerAccess")
    public static String getText(PdbFile.DeviceSqlString string) {
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
