package org.deepsymmetry.cratedigger;

import org.apiguardian.api.API;
import org.deepsymmetry.cratedigger.pdb.RekordboxPdb;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

/**
 * <p>Parses rekordbox database {@code export.pdb} files, providing access to the information they contain.</p>
 */
@API(status = API.Status.STABLE)
public class Database implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(Database.class);

    /**
     * Helper class to parse and interact with the database file conveniently.
     */
    private final DatabaseUtil databaseUtil;

    /**
     * Holds a reference to the file this database was constructed from.
     */
    public final File sourceFile;

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
    @API(status = API.Status.STABLE)
    public Database(File sourceFile) throws IOException {
        this.sourceFile = sourceFile;
        databaseUtil = new DatabaseUtil(sourceFile, false);

        final SortedMap<String, SortedSet<Long>> mutableTrackTitleIndex = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        final SortedMap<Long, SortedSet<Long>> mutableTrackArtistIndex = new TreeMap<>();
        final SortedMap<Long, SortedSet<Long>> mutableTrackAlbumIndex = new TreeMap<>();
        final SortedMap<Long, SortedSet<Long>> mutableTrackGenreIndex = new TreeMap<>();
        trackIndex = indexTracks(mutableTrackTitleIndex, mutableTrackArtistIndex, mutableTrackAlbumIndex, mutableTrackGenreIndex);
        trackTitleIndex = databaseUtil.freezeSecondaryIndex(mutableTrackTitleIndex);
        trackAlbumIndex = databaseUtil.freezeSecondaryIndex(mutableTrackAlbumIndex);
        trackArtistIndex = databaseUtil.freezeSecondaryIndex(mutableTrackArtistIndex);
        trackGenreIndex = databaseUtil.freezeSecondaryIndex(mutableTrackGenreIndex);

        final SortedMap<String, SortedSet<Long>> mutableArtistNameIndex = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        artistIndex = indexArtists(mutableArtistNameIndex);
        artistNameIndex = databaseUtil.freezeSecondaryIndex(mutableArtistNameIndex);

        final SortedMap<String, SortedSet<Long>> mutableColorNameIndex = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        colorIndex = indexColors(mutableColorNameIndex);
        colorNameIndex = databaseUtil.freezeSecondaryIndex(mutableColorNameIndex);

        final SortedMap<String, SortedSet<Long>> mutableAlbumNameIndex = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        final SortedMap<Long, SortedSet<Long>> mutableAlbumArtistIndex = new TreeMap<>();
        albumIndex = indexAlbums(mutableAlbumNameIndex, mutableAlbumArtistIndex);
        albumNameIndex = databaseUtil.freezeSecondaryIndex(mutableAlbumNameIndex);
        albumArtistIndex = databaseUtil.freezeSecondaryIndex(mutableAlbumArtistIndex);

        final SortedMap<String, SortedSet<Long>> mutableLabelNameIndex = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        labelIndex = indexLabels(mutableLabelNameIndex);
        labelNameIndex = databaseUtil.freezeSecondaryIndex(mutableLabelNameIndex);

        final SortedMap<String, SortedSet<Long>> mutableMusicalKeyNameIndex = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        musicalKeyIndex = indexKeys(mutableMusicalKeyNameIndex);
        musicalKeyNameIndex = databaseUtil.freezeSecondaryIndex(mutableMusicalKeyNameIndex);

        final SortedMap<String, SortedSet<Long>> mutableGenreNameIndex = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        genreIndex = indexGenres(mutableGenreNameIndex);
        genreNameIndex = databaseUtil.freezeSecondaryIndex(mutableGenreNameIndex);

        artworkIndex = indexArtwork();

        playlistIndex = indexPlaylists();
        playlistFolderIndex = indexPlaylistFolders();

        historyPlaylistIndex = indexHistoryPlaylists();
        historyPlaylistNameIndex = indexHistoryPlaylistNames();
    }

    /**
     * A map from track ID to the actual track object. If this ends up taking too much space, it would be
     * possible to reorganize the Kaitai Struct mapping specification so that rows are parse instances of
     * the file itself, with parameters for the page and row numbers as well as the page type, allowing
     * them to be loaded directly, and this index would only need to store their addresses. Or we could
     * figure out how to find and use the index tables that must exist in the file somewhere, and avoid
     * building this at all.
     */
    @API(status = API.Status.STABLE)
    public final Map<Long, RekordboxPdb.TrackRow> trackIndex;

    /**
     * A sorted map from track title to the set of track IDs with that title.
     */
    @API(status = API.Status.STABLE)
    public final SortedMap<String, SortedSet<Long>> trackTitleIndex;

    /**
     * A sorted map from artist ID to the set of track IDs associated with that artist.
     */
    @API(status = API.Status.STABLE)
    public final SortedMap<Long, SortedSet<Long>> trackArtistIndex;

    /**
     * A sorted map from album ID to the set of track IDs associated with that album.
     */
    @API(status = API.Status.STABLE)
    public final SortedMap<Long, SortedSet<Long>> trackAlbumIndex;

    /**
     * A sorted map from genre ID to the set of track IDs associated with that genre.
     */
    @API(status = API.Status.STABLE)
    public final SortedMap<Long, SortedSet<Long>> trackGenreIndex;

    /**
     * A sorted map from history playlist name to the ID by which its entries can be found.
     */
    @API(status = API.Status.STABLE)
    public final SortedMap<String, Long> historyPlaylistNameIndex;

    /**
     * A map from playlist ID to the list of tracks IDs making up a history playlist.
     */
    @API(status = API.Status.STABLE)
    public final Map<Long, List<Long>> historyPlaylistIndex;

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
        final Map<Long, RekordboxPdb.TrackRow> index = new HashMap<>();

        databaseUtil.indexRows(RekordboxPdb.PageType.TRACKS, row -> {
            // We found a track; index it by its ID.
            RekordboxPdb.TrackRow trackRow = (RekordboxPdb.TrackRow)row;
            final long id = trackRow.id();
            index.put(id, trackRow);

            // Index the track ID by title, artist (in all roles), album, and genre as well.
            final String title = getText(trackRow.title());
            if (!title.isEmpty()) {
                databaseUtil.addToSecondaryIndex(titleIndex, title, id);
            }
            if (trackRow.artistId() > 0) {
                databaseUtil.addToSecondaryIndex(artistIndex, trackRow.artistId(), id);
            }
            if (trackRow.composerId() > 0) {
                databaseUtil.addToSecondaryIndex(artistIndex, trackRow.composerId(), id);
            }
            if (trackRow.originalArtistId() > 0) {
                databaseUtil.addToSecondaryIndex(artistIndex, trackRow.originalArtistId(), id);
            }
            if (trackRow.remixerId() > 0) {
                databaseUtil.addToSecondaryIndex(artistIndex, trackRow.remixerId(), id);
            }
            if (trackRow.albumId() > 0) {
                databaseUtil.addToSecondaryIndex(albumIndex, trackRow.albumId(), id);
            }
            if (trackRow.genreId() > 0) {
                databaseUtil.addToSecondaryIndex(genreIndex, trackRow.genreId(), id);
            }
        });

        logger.info("Indexed {} Tracks.", index.size());
        return Collections.unmodifiableMap(index);
    }


    /**
     * A map from artist ID to the actual artist object.
     */
    @API(status = API.Status.STABLE)
    public final Map<Long, RekordboxPdb.ArtistRow> artistIndex;

    /**
     * A sorted map from artist name to the set of artist IDs with that name.
     */
    @API(status = API.Status.STABLE)
    public final SortedMap<String, SortedSet<Long>> artistNameIndex;

    /**
     * Parse and index all the artists found in the database export.
     *
     * @param nameIndex the sorted map in which the secondary artist name index should be built
     *
     * @return the populated and unmodifiable primary artist index
     */
    private Map<Long, RekordboxPdb.ArtistRow> indexArtists(final SortedMap<String, SortedSet<Long>> nameIndex) {
        final Map<Long, RekordboxPdb.ArtistRow> index = new HashMap<>();

        databaseUtil.indexRows(RekordboxPdb.PageType.ARTISTS, row -> {
            RekordboxPdb.ArtistRow artistRow = (RekordboxPdb.ArtistRow)row;
            final long id = artistRow.id();
            index.put(id, artistRow);

            // Index the artist ID by name as well.
            final String  name = getText(artistRow.name());
            if (!name.isEmpty()) {
                databaseUtil.addToSecondaryIndex(nameIndex, name, id);
            }
        });

        logger.info("Indexed {} Artists.", index.size());
        return Collections.unmodifiableMap(index);
    }


    /**
     * A map from color ID to the actual color object.
     */
    @API(status = API.Status.STABLE)
    public final Map<Long,RekordboxPdb.ColorRow> colorIndex;

    /**
     * A sorted map from color name to the set of color IDs with that name.
     */
    @API(status = API.Status.STABLE)
    public final SortedMap<String, SortedSet<Long>> colorNameIndex;

    /**
     * Parse and index all the colors found in the database export.
     *
     * @param nameIndex the sorted map in which the secondary color name index should be built
     *
     * @return the populated and unmodifiable primary color index
     */
    private Map<Long, RekordboxPdb.ColorRow> indexColors(final SortedMap<String, SortedSet<Long>> nameIndex) {
        final Map<Long, RekordboxPdb.ColorRow> index = new HashMap<>();

        databaseUtil.indexRows(RekordboxPdb.PageType.COLORS, row -> {
            RekordboxPdb.ColorRow colorRow = (RekordboxPdb.ColorRow)row;
            final long id = colorRow.id();
            index.put(id, colorRow);

            // Index the color by name as well.
            final String name = Database.getText(colorRow.name());
            if (!name.isEmpty()) {
                databaseUtil.addToSecondaryIndex(nameIndex, name, id);
            }
        });

        logger.info("Indexed {} Colors.", index.size());
        return Collections.unmodifiableMap(index);
    }

    /**
     * A map from album ID to the actual album object.
     */
    @API(status = API.Status.STABLE)
    public final Map<Long, RekordboxPdb.AlbumRow> albumIndex;

    /**
     * A sorted map from album name to the set of album IDs with that name.
     */
    @API(status = API.Status.STABLE)
    public final SortedMap<String, SortedSet<Long>> albumNameIndex;

    /**
     * A sorted map from artist ID to the set of album IDs associated with that artist.
     */
    @API(status = API.Status.STABLE)
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
        final Map<Long, RekordboxPdb.AlbumRow> index = new HashMap<>();

        databaseUtil.indexRows(RekordboxPdb.PageType.ALBUMS, row -> {
            RekordboxPdb.AlbumRow albumRow = (RekordboxPdb.AlbumRow) row;
            final long id = albumRow.id();
            index.put(id, albumRow);

            // Index the album ID by name and artist as well.
            final String name = getText(albumRow.name());
            if (!name.isEmpty()) {
                databaseUtil.addToSecondaryIndex(nameIndex, name, id);
            }
            if (albumRow.artistId() > 0) {
                databaseUtil.addToSecondaryIndex(artistIndex, albumRow.artistId(), id);
            }
        });

        logger.info("Indexed {} Albums.", index.size());
        return Collections.unmodifiableMap(index);
    }


    /**
     * A map from label ID to the actual label object.
     */
    @API(status = API.Status.STABLE)
    public final Map<Long, RekordboxPdb.LabelRow> labelIndex;

    /**
     * A sorted map from label name to the set of label IDs with that name.
     */
    @API(status = API.Status.STABLE)
    public final SortedMap<String, SortedSet<Long>> labelNameIndex;

    /**
     * Parse and index all the labels found in the database export.
     *
     * @param nameIndex the sorted map in which the secondary label name index should be built
     *
     * @return the populated and unmodifiable primary label index
     */
    private Map<Long, RekordboxPdb.LabelRow> indexLabels(final SortedMap<String, SortedSet<Long>> nameIndex) {
        final Map<Long, RekordboxPdb.LabelRow> index = new HashMap<>();

        databaseUtil.indexRows(RekordboxPdb.PageType.LABELS, row -> {
            RekordboxPdb.LabelRow labelRow = (RekordboxPdb.LabelRow) row;
            final long id = labelRow.id();
            index.put(id, labelRow);

            // Index the label ID by name as well.
            final String name = getText(labelRow.name());
            if (!name.isEmpty()) {
                databaseUtil.addToSecondaryIndex(nameIndex, name, id);
            }
        });

        logger.info("Indexed {} Labels.", index.size());
        return Collections.unmodifiableMap(index);
    }


    /**
     * A map from (musical) key ID to the actual key object.
     */
    @API(status = API.Status.STABLE)
    public final Map<Long, RekordboxPdb.KeyRow> musicalKeyIndex;

    /**
     * A sorted map from musical key name to the set of musical key IDs with that name.
     */
    @API(status = API.Status.STABLE)
    public final SortedMap<String, SortedSet<Long>> musicalKeyNameIndex;

    /**
     * Parse and index all the musical keys found in the database export.
     *
     * @param nameIndex the sorted map in which the secondary musical key name index should be built
     *
     * @return the populated and unmodifiable primary musical key index
     */
    private Map<Long, RekordboxPdb.KeyRow> indexKeys(final SortedMap<String, SortedSet<Long>> nameIndex) {
        final Map<Long, RekordboxPdb.KeyRow> index = new HashMap<>();

        databaseUtil.indexRows(RekordboxPdb.PageType.KEYS, row -> {
            RekordboxPdb.KeyRow keyRow = (RekordboxPdb.KeyRow) row;
            final long id = keyRow.id();
            index.put(id, keyRow);

            // Index the musical key ID by name as well.
            final String name = getText(keyRow.name());
            if (!name.isEmpty()) {
                databaseUtil.addToSecondaryIndex(nameIndex, name,  id);
            }
        });

        logger.info("Indexed {} Musical Keys.", index.size());
        return Collections.unmodifiableMap(index);
    }


    /**
     * A map from genre ID to the actual genre object.
     */
    @API(status = API.Status.STABLE)
    public final Map<Long, RekordboxPdb.GenreRow> genreIndex;

    /**
     * A sorted map from genre name to the set of genre IDs with that name.
     */
    @API(status = API.Status.STABLE)
    public final SortedMap<String, SortedSet<Long>> genreNameIndex;

    /**
     * Parse and index all the genres found in the database export.
     *
     * @param nameIndex the sorted map in which the secondary genre name index should be built
     *
     * @return the populated and unmodifiable primary genre index
     */
    private Map<Long, RekordboxPdb.GenreRow> indexGenres(final SortedMap<String, SortedSet<Long>> nameIndex) {
        final Map<Long, RekordboxPdb.GenreRow> index = new HashMap<>();

        databaseUtil.indexRows(RekordboxPdb.PageType.GENRES, row -> {
            RekordboxPdb.GenreRow genreRow = (RekordboxPdb.GenreRow) row;
            final long id = genreRow.id();
            index.put(id, genreRow);

            // Index the genre by name as well.
            final String name = getText(genreRow.name());
            if (!name.isEmpty()) {
                databaseUtil.addToSecondaryIndex(nameIndex, name, id);
            }
        });

        logger.info("Indexed {} Genres.", index.size());
        return Collections.unmodifiableMap(index);
    }

    /**
     * A map from artwork ID to the artwork row containing its file path.
     */
    @API(status = API.Status.STABLE)
    public final Map<Long, RekordboxPdb.ArtworkRow> artworkIndex;

    /**
     * Parse and index all the artwork paths found in the database export.
     *
     * @return the populated and unmodifiable artwork path index
     */
    private Map<Long, RekordboxPdb.ArtworkRow> indexArtwork() {
        final Map<Long, RekordboxPdb.ArtworkRow> index = new HashMap<>();

        databaseUtil.indexRows(RekordboxPdb.PageType.ARTWORK, row -> {
            RekordboxPdb.ArtworkRow artworkRow = (RekordboxPdb.ArtworkRow) row;
            index.put(artworkRow.id(), artworkRow);
        });

        logger.info("Indexed {} Artwork Paths.", index.size());
        return Collections.unmodifiableMap(index);
    }

    /**
     * A map from playlist ID to the list of tracks IDs making up a playlist.
     */
    @API(status = API.Status.STABLE)
    public final Map<Long, List<Long>> playlistIndex;

    /**
     * Playlist folders can either contain playlists or other folders. Each
     * entry has a flag explaining how the ID should be interpreted.
     */
    @API(status = API.Status.STABLE)
    public static class PlaylistFolderEntry {
        /**
         * The name by which this playlist or folder is known.
         */
        public final String name;

        /**
         * Indicates whether this entry links to another folder or a playlist.
         */
        public final boolean isFolder;

        /**
         * The ID of the folder or playlist linked to by this entry.
         */
        public final long id;

        /**
         * Constructor simply sets the immutable fields.
         *
         * @param name the name by which this folder is known.
         * @param isFolder indicates whether this entry links to another folder or a playlist
         * @param id the id of the folder or playlist linked to by this entry
         */
        PlaylistFolderEntry(String name, boolean isFolder, long id) {
            this.name = name;
            this.isFolder = isFolder;
            this.id = id;
        }

        @Override
        public String toString() {
            return "PlaylistFolderEntry[name:" + name + ", id:" + id + ", isFolder? " + isFolder + "]";
        }
    }

    /**
     * A map from folder ID to the list of playlists or folders in a playlist folder.
     */
    @API(status = API.Status.STABLE)
    public final Map<Long, List<PlaylistFolderEntry>> playlistFolderIndex;

    /**
     * Parse and index all the playlists found in the database export.
     *
     * @return the populated and unmodifiable playlist index
     */
    private Map<Long, List<Long>> indexPlaylists() {
        final Map<Long, List<Long>> result = new HashMap<>();
        databaseUtil.indexRows(RekordboxPdb.PageType.PLAYLIST_ENTRIES, row -> {
            RekordboxPdb.PlaylistEntryRow entryRow = (RekordboxPdb.PlaylistEntryRow) row;
            ArrayList<Long> playlist = (ArrayList<Long>) result.get(entryRow.playlistId());
            if (playlist == null) {
                playlist = new ArrayList<>();
                result.put(entryRow.playlistId(), playlist);
            }
            while (playlist.size() <= entryRow.entryIndex()) {  // Grow to hold the new entry we are going to set.
                playlist.add(0L);
            }
            playlist.set((int) entryRow.entryIndex(), entryRow.trackId());
        });
        // Freeze the finished lists and overall map
        result.replaceAll((k, v) -> Collections.unmodifiableList(v));
        logger.info("Indexed {} playlists.", result.size());
        return Collections.unmodifiableMap(result);
    }

    /**
     * Parse and index the tree that organizes playlists into folders found in the database export.
     *
     * @return the populated and unmodifiable playlist folder index
     */
    private Map<Long, List<PlaylistFolderEntry>> indexPlaylistFolders() {
        final Map<Long, List<PlaylistFolderEntry>> result = new HashMap<>();
        databaseUtil.indexRows(RekordboxPdb.PageType.PLAYLIST_TREE, row -> {
            RekordboxPdb.PlaylistTreeRow treeRow = (RekordboxPdb.PlaylistTreeRow) row;
            ArrayList<PlaylistFolderEntry> parent = (ArrayList<PlaylistFolderEntry>) result.get(treeRow.parentId());
            if (parent == null) {
                parent = new ArrayList<>();
                result.put(treeRow.parentId(), parent);
            }
            while (parent.size() <= treeRow.sortOrder()) {  // Grow to hold the new entry we are going to set.
                parent.add(null);
            }
            parent.set((int) treeRow.sortOrder(), new PlaylistFolderEntry(Database.getText(treeRow.name()),
                    treeRow.isFolder(), treeRow.id()));
        });
        // Freeze the finished lists and overall map
        result.replaceAll((k, v) -> Collections.unmodifiableList(v));
        logger.info("Indexed {} playlist folders.", result.size());
        return Collections.unmodifiableMap(result);
    }

    /**
     * Parse and index the names of all available history playlists.
     *
     * @return a map sorted by the history playlist names identifying the IDs by which their entries can be found.
     */
    private SortedMap<String, Long> indexHistoryPlaylistNames() {
        final SortedMap<String, Long> result = new TreeMap<>();
        databaseUtil.indexRows(RekordboxPdb.PageType.HISTORY_PLAYLISTS, row -> {
            RekordboxPdb.HistoryPlaylistRow historyRow = (RekordboxPdb.HistoryPlaylistRow) row;
            result.put(getText(historyRow.name()), historyRow.id());
        });
        logger.info("Indexed {} history playlist names.", result.size());
        return Collections.unmodifiableSortedMap(result);
    }

    /**
     * Parse and index all the history playlists found in the database export.
     *
     * @return the populated and unmodifiable history playlist index.
     */
    private Map<Long, List<Long>> indexHistoryPlaylists() {
        final Map<Long, List<Long>> result = new HashMap<>();
        databaseUtil.indexRows(RekordboxPdb.PageType.HISTORY_ENTRIES, row -> {
            RekordboxPdb.HistoryEntryRow entryRow = (RekordboxPdb.HistoryEntryRow) row;
            ArrayList<Long> playList = (ArrayList<Long>) result.get(entryRow.playlistId());
            if (playList == null) {
                playList = new ArrayList<>();
                result.put(entryRow.playlistId(), playList);
            }
            while (playList.size() <= entryRow.entryIndex()) {  // Grow to hold the new entry we are going to set.
                playList.add(0L);
            }
            playList.set((int) entryRow.entryIndex(), entryRow.trackId());
        });
        // Freeze the finished lists and overall map.
        result.replaceAll((k, v) -> Collections.unmodifiableList(v));
        logger.info("Indexed {} history playlists.", result.size());
        return Collections.unmodifiableMap(result);
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

    /**
     * Helper function to extract the text value from one of the strings found in the database, which
     * have a variety of obscure representations.
     *
     * @param string the string-encoding structure
     *
     * @return the text it contains, which may have zero length, but will never be {@code null}
     */
    @API(status = API.Status.STABLE)
    public static String getText(RekordboxPdb.DeviceSqlString string) {
        String text = null;
        if (string.body() instanceof RekordboxPdb.DeviceSqlShortAscii) {
            text = ((RekordboxPdb.DeviceSqlShortAscii) string.body()).text();
        } else if (string.body() instanceof  RekordboxPdb.DeviceSqlLongAscii) {
            text = ((RekordboxPdb.DeviceSqlLongAscii) string.body()).text();
        } else if (string.body() instanceof RekordboxPdb.DeviceSqlLongUtf16le) {
            text = ((RekordboxPdb.DeviceSqlLongUtf16le) string.body()).text();
        }
        if (text != null) {
            return text;
        }
        logger.warn("Received unusable DeviceSqlString, returning empty string; lengthAndKind: {}", string.lengthAndKind());
        return "";
    }
}
