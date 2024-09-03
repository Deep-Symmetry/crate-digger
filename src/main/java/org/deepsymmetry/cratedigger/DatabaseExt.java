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
        final Map<Long, RekordboxPdb.TagRow> mutableTagIndex = new HashMap<>();
        final SortedMap<String, SortedSet<Long>> mutableTagNameIndex = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        final Map<Long, RekordboxPdb.TagRow> mutableCategoryIndex = new HashMap<>();
        final SortedMap<String, SortedSet<Long>> mutableCategoryNameIndex = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        databaseUtil.indexRows(RekordboxPdb.PageTypeExt.TAGS, row -> {
            // We found a tag or category; index it by its ID.
            RekordboxPdb.TagRow tagRow = (RekordboxPdb.TagRow)row;
            final long id = tagRow.id();
            if (tagRow.isCategory()) {
                mutableCategoryIndex.put(id, tagRow);
            } else {
                mutableTagIndex.put(id, tagRow);
            }
            // ALso index the tag and categories by name.
            final String title = Database.getText(tagRow.name());
            if (tagRow.isCategory()) {
                databaseUtil.addToSecondaryIndex(mutableCategoryNameIndex, title, tagRow.id());

            } else {
                databaseUtil.addToSecondaryIndex(mutableTagNameIndex, title, tagRow.id());
            }
        });
        tagIndex = Collections.unmodifiableMap(mutableTagIndex);
        tagCategoryIndex = Collections.unmodifiableMap(mutableCategoryIndex);
        logger.info("Indexed {} Tag names in {} categories.", tagIndex.size(), tagCategoryIndex.size());
        tagNameIndex = databaseUtil.freezeSecondaryIndex(mutableTagNameIndex);
        tagCategoryNameIndex = databaseUtil.freezeSecondaryIndex(mutableCategoryNameIndex);

        // Build the list of category names in the order in which they should be displayed.
        Long[] mutableTagCategoryOrder = new Long[tagCategoryIndex.size()];
        for (RekordboxPdb.TagRow row : tagCategoryIndex.values()) {
            mutableTagCategoryOrder[(int) row.categoryPos()] = row.id();
        }
        tagCategoryOrder = List.of(mutableTagCategoryOrder);

        // For each category build the list of tag names in that category, in the order they should be displayed.
        final Map<Long,ArrayList<RekordboxPdb.TagRow>> mutableCategoryContents = new HashMap<>();
        for (RekordboxPdb.TagRow row : tagIndex.values()) {
            mutableCategoryContents.computeIfAbsent(row.category(), k -> new ArrayList<>()).add(row);
        }

        final Map<Long,List<Long>> mutableTagCategoryTagOrder = new HashMap<>();
        for (Long categoryId : mutableCategoryContents.keySet()) {
            final List<RekordboxPdb.TagRow> category = mutableCategoryContents.get(categoryId);
            final Long[] mutableTagIds = new Long[category.size()];
            for (RekordboxPdb.TagRow row : category) {
                mutableTagIds[(int) row.categoryPos()] = row.id();
            }
            mutableTagCategoryTagOrder.put(categoryId, List.of(mutableTagIds));
        }
        tagCategoryTagOrder = Collections.unmodifiableMap(mutableTagCategoryTagOrder);

        // Gather and index the track tag and tag category information.
        final Map<Long,Set<Long>> mutableTagTrackIndex = new HashMap<>();
        final Map<Long, Set<Long>> mutableTrackTagIndex = new HashMap<>();
        databaseUtil.indexRows(RekordboxPdb.PageTypeExt.TAG_TRACKS, row -> {
            RekordboxPdb.TagTrackRow tagTrackRow = (RekordboxPdb.TagTrackRow)row;
            mutableTagTrackIndex.computeIfAbsent(tagTrackRow.tagId(), k -> new HashSet<>()).add(tagTrackRow.trackId());
            mutableTrackTagIndex.computeIfAbsent(tagTrackRow.trackId(), k -> new HashSet<>()).add(tagTrackRow.tagId());
        });

        mutableTagTrackIndex.replaceAll((k, v) -> Collections.unmodifiableSet(mutableTagTrackIndex.get(k)));
        tagTrackIndex = Collections.unmodifiableMap(mutableTagTrackIndex);

        mutableTrackTagIndex.replaceAll((k, v) -> Collections.unmodifiableSet(mutableTrackTagIndex.get(k)));
        trackTagIndex = Collections.unmodifiableMap(mutableTrackTagIndex);

        logger.info("Indexed {} tags on {} tagged tracks.", tagTrackIndex.size(), trackTagIndex.size());
    }

    /**
     * A map from tag ID to the actual tag object (does not include rows that are categories).
     */
    @API(status = API.Status.EXPERIMENTAL)
    public final Map<Long, RekordboxPdb.TagRow> tagIndex;

    /**
     * A map from tag ID to the actual category object (includes only rows that are categories).
     */
    @API(status = API.Status.EXPERIMENTAL)
    public final Map<Long, RekordboxPdb.TagRow> tagCategoryIndex;

    /**
     * A sorted map from tag names to the IDs of tags with that name (does not include categories).
     */
    @API(status = API.Status.EXPERIMENTAL)
    public final SortedMap<String, SortedSet<Long>> tagNameIndex;

    /**
     * A sorted map from category names to the IDs of tag categories with that name (only includes categories).
     */
    @API(status = API.Status.EXPERIMENTAL)
    public final SortedMap<String, SortedSet<Long>> tagCategoryNameIndex;

    /**
     * The list of category IDs in the order that they are supposed to be presented to the user.
     */
    @API(status = API.Status.EXPERIMENTAL)
    public final List<Long> tagCategoryOrder;

    /**
     * A map from category ID to the list of tag IDs that belong to that category,
     * in the order that they are supposed to be presented to the user.
     */
    @API(status = API.Status.EXPERIMENTAL)
    public final Map<Long,List<Long>> tagCategoryTagOrder;

    /**
     * A map from tag ID to the IDs of all tracks that have been assigned that tag.
     */
    @API(status = API.Status.EXPERIMENTAL)
    final Map<Long,Set<Long>> tagTrackIndex;

    /**
     * A map from track ID to the IDs of all tags that have been assigned to that track.
     */
    @API(status = API.Status.EXPERIMENTAL)
    final Map<Long, Set<Long>> trackTagIndex;


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
