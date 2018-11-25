package org.deepsymmetry.cratedigger;

import io.kaitai.struct.KaitaiStruct;
import org.deepsymmetry.cratedigger.pdb.RekordboxPdb;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

/**
 * <p>Parses rekordbox database export files, providing access to the information they contain.</p>
 */
@SuppressWarnings("WeakerAccess")
public class Database {

    private static final Logger logger = LoggerFactory.getLogger(Database.class);

    /**
     * Holds a reference to the parser for the file we were constructed with.
     */
    private final RekordboxPdb pdb;

    /**
     * Holds a reference to the file this database was constructed from.
     */
    public final File sourceFile;

    /**
     * Construct a database access instance from the specified recordbox export file.
     * The file can obtained either from the SD or USB media, or directly from a player
     * using {@link FileFetcher#fetch(InetAddress, String, String, File)}
     *
     * @param sourceFile an export.pdb file
     *
     * @throws IOException if there is a problem reading the file
     */
    @SuppressWarnings("WeakerAccess")
    public Database(File sourceFile) throws IOException {
        this.sourceFile = sourceFile;
        pdb = RekordboxPdb.fromFile(sourceFile.getAbsolutePath());

        final SortedMap<String, SortedSet<Long>> mutableTrackTitleIndex = new TreeMap<String, SortedSet<Long>>(String.CASE_INSENSITIVE_ORDER);
        final SortedMap<Long, SortedSet<Long>> mutableTrackArtistIndex = new TreeMap<Long, SortedSet<Long>>();
        final SortedMap<Long, SortedSet<Long>> mutableTrackAlbumIndex = new TreeMap<Long, SortedSet<Long>>();
        final SortedMap<Long, SortedSet<Long>> mutableTrackGenreIndex = new TreeMap<Long, SortedSet<Long>>();
        trackIndex = indexTracks(mutableTrackTitleIndex, mutableTrackArtistIndex, mutableTrackAlbumIndex, mutableTrackGenreIndex);
        trackTitleIndex = freezeSecondaryIndex(mutableTrackTitleIndex);
        trackAlbumIndex = freezeSecondaryIndex(mutableTrackAlbumIndex);
        trackArtistIndex = freezeSecondaryIndex(mutableTrackArtistIndex);
        trackGenreIndex = freezeSecondaryIndex(mutableTrackGenreIndex);

        final SortedMap<String, SortedSet<Long>> mutableArtistNameIndex = new TreeMap<String, SortedSet<Long>>(String.CASE_INSENSITIVE_ORDER);
        artistIndex = indexArtists(mutableArtistNameIndex);
        artistNameIndex = freezeSecondaryIndex(mutableArtistNameIndex);

        final SortedMap<String, SortedSet<Long>> mutableColorNameIndex = new TreeMap<String, SortedSet<Long>>(String.CASE_INSENSITIVE_ORDER);
        colorIndex = indexColors(mutableColorNameIndex);
        colorNameIndex = freezeSecondaryIndex(mutableColorNameIndex);

        final SortedMap<String, SortedSet<Long>> mutableAlbumNameIndex = new TreeMap<String, SortedSet<Long>>(String.CASE_INSENSITIVE_ORDER);
        final SortedMap<Long, SortedSet<Long>> mutableAlbumArtistIndex = new TreeMap<Long, SortedSet<Long>>();
        albumIndex = indexAlbums(mutableAlbumNameIndex, mutableAlbumArtistIndex);
        albumNameIndex = freezeSecondaryIndex(mutableAlbumNameIndex);
        albumArtistIndex = freezeSecondaryIndex(mutableAlbumArtistIndex);

        final SortedMap<String, SortedSet<Long>> mutableLabelNameIndex = new TreeMap<String, SortedSet<Long>>(String.CASE_INSENSITIVE_ORDER);
        labelIndex = indexLabels(mutableLabelNameIndex);
        labelNameIndex = freezeSecondaryIndex(mutableLabelNameIndex);

        final SortedMap<String, SortedSet<Long>> mutableMusicalKeyNameIndex = new TreeMap<String, SortedSet<Long>>(String.CASE_INSENSITIVE_ORDER);
        musicalKeyIndex = indexKeys(mutableMusicalKeyNameIndex);
        musicalKeyNameIndex = freezeSecondaryIndex(mutableMusicalKeyNameIndex);

        final SortedMap<String, SortedSet<Long>> mutableGenreNameIndex = new TreeMap<String, SortedSet<Long>>(String.CASE_INSENSITIVE_ORDER);
        genreIndex = indexGenres(mutableGenreNameIndex);
        genreNameIndex = freezeSecondaryIndex(mutableGenreNameIndex);

        artworkIndex = indexArtwork();
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
     * @param type the type of table to be scanned and parsed
     * @param handler the code that knows how to index that kind of row
     *
     * @throws IllegalStateException if there is more than (or less than) one table of that type in the file
     */
    private void indexRows(RekordboxPdb.PageType type, RowHandler handler) {
        boolean done = false;
        for (RekordboxPdb.Table table : pdb.tables()) {
            if (table.type() == type) {
                if (done) throw new IllegalStateException("More than one table found with type " + type);
                final long lastIndex = table.lastPage().index();  // This is how we know when to stop.
                RekordboxPdb.PageRef currentRef = table.firstPage();
                boolean moreLeft = true;
                do {
                    final RekordboxPdb.Page page = currentRef.body();

                    // Process only ordinary data pages.
                    if (page.isDataPage()) {
                        for (RekordboxPdb.RowGroup rowGroup : page.rowGroups()) {
                            for (RekordboxPdb.RowRef rowRef : rowGroup.rows()) {
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
                done = true;
            }
        }

        if (!done) throw new IllegalStateException("No table found of type " + type);
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
     *
     * @return an unmodifiable top-level view of the unmodifiable children
     */
    private <K> SortedMap<K, SortedSet<Long>> freezeSecondaryIndex(SortedMap<K, SortedSet<Long>> index) {
        for (K key : index.keySet()) {
            index.put(key, Collections.unmodifiableSortedSet(index.get(key)));
        }
        return Collections.unmodifiableSortedMap(index);
    }

    /**
     * A map from track ID to the actual track object. If this ends up taking too much space, it would be
     * possible to reorganize the Kaitai Struct mapping specification so that rows are parse instances of
     * the file itself, with parameters for the page and row numbers as well as the page type, allowing
     * them to be loaded directly, and this index would only need to store their addresses. Or we could
     * figure out how to find and use the index tables that must exist in the file somewhere, and avoid
     * building this at all.
     */
    @SuppressWarnings("WeakerAccess")
    public final Map<Long, RekordboxPdb.TrackRow> trackIndex;

    /**
     * A sorted map from track title to the set of track IDs with that title.
     */
    @SuppressWarnings("WeakerAccess")
    public final SortedMap<String, SortedSet<Long>> trackTitleIndex;

    /**
     * A sorted map from artist ID to the set of track IDs associated with that artist.
     */
    @SuppressWarnings("WeakerAccess")
    public final SortedMap<Long, SortedSet<Long>> trackArtistIndex;

    /**
     * A sorted map from album ID to the set of track IDs associated with that album.
     */
    @SuppressWarnings("WeakerAccess")
    public final SortedMap<Long, SortedSet<Long>> trackAlbumIndex;

    /**
     * A sorted map from genre ID to the set of track IDs associated with that genre.
     */
    @SuppressWarnings("WeakerAccess")
    public final SortedMap<Long, SortedSet<Long>> trackGenreIndex;

    /**
     * Parse and index all the tracks found in the database export.
     *
     * @param titleIndex the sorted map in which the secondary track title index should be built
     * @param artistIndex the map in which the secondary track artist index should be built
     * @param albumIndex the map in which the secondary track album index should be built
     * @param genreIndex the map in which the secondary track genre index should be built
     *
     * @return the populated and unmodifiable primary track index
     */
    private Map<Long, RekordboxPdb.TrackRow> indexTracks(final SortedMap<String, SortedSet<Long>> titleIndex,
                                                    final SortedMap<Long, SortedSet<Long>> artistIndex,
                                                    final SortedMap<Long, SortedSet<Long>> albumIndex,
                                                    final SortedMap<Long, SortedSet<Long>> genreIndex) {
        final Map<Long, RekordboxPdb.TrackRow> index = new HashMap<Long, RekordboxPdb.TrackRow>();

        indexRows(RekordboxPdb.PageType.TRACKS, new RowHandler() {
            @Override
            public void rowFound(KaitaiStruct row) {
                // We found a track; index it by its ID.
                RekordboxPdb.TrackRow trackRow = (RekordboxPdb.TrackRow)row;
                final long id = trackRow.id();
                index.put(id, trackRow);

                // Index the track ID by title, artist (in all roles), album, and genre as well.
                final String title = getText(trackRow.title());
                if (title.length() > 0) {
                    addToSecondaryIndex(titleIndex, title, id);
                }
                if (trackRow.artistId() > 0) {
                    addToSecondaryIndex(artistIndex, trackRow.artistId(), id);
                }
                if (trackRow.composerId() > 0) {
                    addToSecondaryIndex(artistIndex, trackRow.composerId(), id);
                }
                if (trackRow.originalArtistId() > 0) {
                    addToSecondaryIndex(artistIndex, trackRow.originalArtistId(), id);
                }
                if (trackRow.remixerId() > 0) {
                    addToSecondaryIndex(artistIndex, trackRow.remixerId(), id);
                }
                if (trackRow.albumId() > 0) {
                    addToSecondaryIndex(albumIndex, trackRow.albumId(), id);
                }
                if (trackRow.genreId() > 0) {
                    addToSecondaryIndex(genreIndex, trackRow.genreId(), id);
                }
            }
        });

        logger.info("Indexed " + index.size() + " Tracks.");
        return Collections.unmodifiableMap(index);
    }


    /**
     * A map from artist ID to the actual artist object.
     */
    @SuppressWarnings("WeakerAccess")
    public final Map<Long, RekordboxPdb.ArtistRow> artistIndex;

    /**
     * A sorted map from artist name to the set of artist IDs with that name.
     */
    @SuppressWarnings("WeakerAccess")
    public final SortedMap<String, SortedSet<Long>> artistNameIndex;

    /**
     * Parse and index all the artists found in the database export.
     *
     * @param nameIndex the sorted map in which the secondary artist name index should be built
     *
     * @return the populated and unmodifiable primary artist index
     */
    private Map<Long, RekordboxPdb.ArtistRow> indexArtists(final SortedMap<String, SortedSet<Long>> nameIndex) {
        final Map<Long, RekordboxPdb.ArtistRow> index = new HashMap<Long, RekordboxPdb.ArtistRow>();

        indexRows(RekordboxPdb.PageType.ARTISTS, new RowHandler() {
            @Override
            public void rowFound(KaitaiStruct row) {
                RekordboxPdb.ArtistRow artistRow = (RekordboxPdb.ArtistRow)row;
                final long id = artistRow.id();
                index.put(id, artistRow);

                // Index the artist ID by name as well.
                final String name = getText(artistRow.name());
                if (name.length() > 0) {
                    addToSecondaryIndex(nameIndex, name, id);
                }
            }
        });

        logger.info("Indexed " + index.size() + " Artists.");
        return Collections.unmodifiableMap(index);
    }


    /**
     * A map from color ID to the actual color object.
     */
    @SuppressWarnings("WeakerAccess")
    public final Map<Long,RekordboxPdb.ColorRow> colorIndex;

    /**
     * A sorted map from color name to the set of color IDs with that name.
     */
    @SuppressWarnings("WeakerAccess")
    public final SortedMap<String, SortedSet<Long>> colorNameIndex;

    /**
     * Parse and index all the colors found in the database export.
     *
     * @param nameIndex the sorted map in which the secondary color name index should be built
     *
     * @return the populated and unmodifiable primary color index
     */
    private Map<Long, RekordboxPdb.ColorRow> indexColors(final SortedMap<String, SortedSet<Long>> nameIndex) {
        final Map<Long, RekordboxPdb.ColorRow> index = new HashMap<Long, RekordboxPdb.ColorRow>();

        indexRows(RekordboxPdb.PageType.COLORS, new RowHandler() {
            @Override
            public void rowFound(KaitaiStruct row) {
                RekordboxPdb.ColorRow colorRow = (RekordboxPdb.ColorRow)row;
                final long id = colorRow.id();
                index.put(id, colorRow);

                // Index the color by name as well.
                final String name = Database.getText(colorRow.name());
                if (name.length() > 0) {
                    addToSecondaryIndex(nameIndex, name, id);
                }
            }
        });

        logger.info("Indexed " + index.size() + " Colors.");
        return Collections.unmodifiableMap(index);
    }

    /**
     * A map from album ID to the actual album object.
     */
    @SuppressWarnings("WeakerAccess")
    public final Map<Long, RekordboxPdb.AlbumRow> albumIndex;

    /**
     * A sorted map from album name to the set of album IDs with that name.
     */
    @SuppressWarnings("WeakerAccess")
    public final SortedMap<String, SortedSet<Long>> albumNameIndex;

    /**
     * A sorted map from artist ID to the set of album IDs associated with that artist.
     */
    @SuppressWarnings("WeakerAccess")
    public final SortedMap<Long, SortedSet<Long>> albumArtistIndex;

    /**
     * Parse and index all the albums found in the database export.
     *
     * @param nameIndex the sorted map in which the secondary album name index should be built
     * @param artistIndex the map in which the secondary track artist index should be built
     *
     * @return the populated and unmodifiable primary album index
     */
    private Map<Long, RekordboxPdb.AlbumRow> indexAlbums(final SortedMap<String, SortedSet<Long>> nameIndex,
                                                    final SortedMap<Long, SortedSet<Long>> artistIndex) {
        final Map<Long, RekordboxPdb.AlbumRow> index = new HashMap<Long, RekordboxPdb.AlbumRow>();

        indexRows(RekordboxPdb.PageType.ALBUMS, new RowHandler() {
            @Override
            public void rowFound(KaitaiStruct row) {
                RekordboxPdb.AlbumRow albumRow = (RekordboxPdb.AlbumRow) row;
                final long id = albumRow.id();
                index.put(id, albumRow);

                // Index the album ID by name and artist as well.
                final String name = getText(albumRow.name());
                if (name.length() > 0) {
                    addToSecondaryIndex(nameIndex, name, id);
                }
                if (albumRow.artistId() > 0) {
                    addToSecondaryIndex(artistIndex, albumRow.artistId(), id);
                }
            }
        });

        logger.info("Indexed " + index.size() + " Albums.");
        return Collections.unmodifiableMap(index);
    }


    /**
     * A map from label ID to the actual label object.
     */
    @SuppressWarnings("WeakerAccess")
    public final Map<Long, RekordboxPdb.LabelRow> labelIndex;

    /**
     * A sorted map from label name to the set of label IDs with that name.
     */
    @SuppressWarnings("WeakerAccess")
    public final SortedMap<String, SortedSet<Long>> labelNameIndex;

    /**
     * Parse and index all the labels found in the database export.
     *
     * @param nameIndex the sorted map in which the secondary label name index should be built
     *
     * @return the populated and unmodifiable primary label index
     */
    private Map<Long, RekordboxPdb.LabelRow> indexLabels(final SortedMap<String, SortedSet<Long>> nameIndex) {
        final Map<Long, RekordboxPdb.LabelRow> index = new HashMap<Long, RekordboxPdb.LabelRow>();

        indexRows(RekordboxPdb.PageType.LABELS, new RowHandler() {
            @Override
            public void rowFound(KaitaiStruct row) {
                RekordboxPdb.LabelRow labelRow = (RekordboxPdb.LabelRow) row;
                final long id = labelRow.id();
                index.put(id, labelRow);

                // Index the label ID by name as well.
                final String name = getText(labelRow.name());
                if (name.length() > 0) {
                    addToSecondaryIndex(nameIndex, name, id);
                }
            }
        });

        logger.info("Indexed " + index.size() + " Labels.");
        return Collections.unmodifiableMap(index);
    }


    /**
     * A map from (musical) key ID to the actual key object.
     */
    @SuppressWarnings("WeakerAccess")
    public final Map<Long, RekordboxPdb.KeyRow> musicalKeyIndex;

    /**
     * A sorted map from musical key name to the set of musical key IDs with that name.
     */
    @SuppressWarnings("WeakerAccess")
    public final SortedMap<String, SortedSet<Long>> musicalKeyNameIndex;

    /**
     * Parse and index all the musical keys found in the database export.
     *
     * @param nameIndex the sorted map in which the secondary musical key name index should be built
     *
     * @return the populated and unmodifiable primary musical key index
     */
    private Map<Long, RekordboxPdb.KeyRow> indexKeys(final SortedMap<String, SortedSet<Long>> nameIndex) {
        final Map<Long, RekordboxPdb.KeyRow> index = new HashMap<Long, RekordboxPdb.KeyRow>();

        indexRows(RekordboxPdb.PageType.KEYS, new RowHandler() {
            @Override
            public void rowFound(KaitaiStruct row) {
                RekordboxPdb.KeyRow keyRow = (RekordboxPdb.KeyRow) row;
                final long id = keyRow.id();
                index.put(id, keyRow);

                // Index the musical key ID by name as well.
                final String name = getText(keyRow.name());
                if (name.length() > 0) {
                    addToSecondaryIndex(nameIndex, name,  id);
                }
            }
        });

        logger.info("Indexed " + index.size() + " Musical Keys.");
        return Collections.unmodifiableMap(index);
    }


    /**
     * A map from genre ID to the actual genre object.
     */
    @SuppressWarnings("WeakerAccess")
    public final Map<Long, RekordboxPdb.GenreRow> genreIndex;

    /**
     * A sorted map from genre name to the set of genre IDs with that name.
     */
    @SuppressWarnings("WeakerAccess")
    public final SortedMap<String, SortedSet<Long>> genreNameIndex;

    /**
     * Parse and index all the genres found in the database export.
     *
     * @param nameIndex the sorted map in which the secondary genre name index should be built
     *
     * @return the populated and unmodifiable primary genre index
     */
    private Map<Long, RekordboxPdb.GenreRow> indexGenres(final SortedMap<String, SortedSet<Long>> nameIndex) {
        final Map<Long, RekordboxPdb.GenreRow> index = new HashMap<Long, RekordboxPdb.GenreRow>();

        indexRows(RekordboxPdb.PageType.GENRES, new RowHandler() {
            @Override
            public void rowFound(KaitaiStruct row) {
                RekordboxPdb.GenreRow genreRow = (RekordboxPdb.GenreRow) row;
                final long id = genreRow.id();
                index.put(id, genreRow);

                // Index the genre by name as well.
                final String name = getText(genreRow.name());
                if (name.length() > 0) {
                    addToSecondaryIndex(nameIndex, name, id);
                }
            }
        });

        logger.info("Indexed " + index.size() + " Genres.");
        return Collections.unmodifiableMap(index);
    }

    /**
     * A map from artwork ID to the artwork row containing its file path.
     */
    @SuppressWarnings("WeakerAccess")
    public final Map<Long, RekordboxPdb.ArtworkRow> artworkIndex;

    /**
     * Parse and index all the artwork paths found in the database export.
     *
     * @return the populated and unmodifiable artwork path index
     */
    private Map<Long, RekordboxPdb.ArtworkRow> indexArtwork() {
        final Map<Long, RekordboxPdb.ArtworkRow> index = new HashMap<Long, RekordboxPdb.ArtworkRow>();

        indexRows(RekordboxPdb.PageType.ARTWORK, new RowHandler() {
            @Override
            public void rowFound(KaitaiStruct row) {
                RekordboxPdb.ArtworkRow artworkRow = (RekordboxPdb.ArtworkRow) row;
                index.put(artworkRow.id(), artworkRow);
            }
        });

        logger.info(("Indexed " + index.size() + " Artwork Paths."));
        return Collections.unmodifiableMap(index);
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
    public static String getText(RekordboxPdb.DeviceSqlString string) {
        if (string.body() instanceof RekordboxPdb.DeviceSqlShortAscii) {
            return ((RekordboxPdb.DeviceSqlShortAscii) string.body()).text();
        } else if (string.body() instanceof  RekordboxPdb.DeviceSqlLongAscii) {
            return ((RekordboxPdb.DeviceSqlLongAscii) string.body()).text();
        } else if (string.body() instanceof RekordboxPdb.DeviceSqlLongUtf16be) {
            return ((RekordboxPdb.DeviceSqlLongUtf16be) string.body()).text();
        }
        throw new IllegalArgumentException("Unrecognized DeviceSqlString subtype:" + string);
    }
}
