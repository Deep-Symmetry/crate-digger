package org.deepsymmetry.cratedigger;

import io.kaitai.struct.KaitaiStruct;
import io.kaitai.struct.RandomAccessFileKaitaiStream;
import org.deepsymmetry.cratedigger.pdb.RekordboxPdb;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Provides helpful utility functions for working with a rekordbox database export. Used by
 * {@link Database} and {@link DatabaseExt}
 */
class DatabaseUtil implements Closeable {
    /**
     * Tracks whether we were configured to parse an {@code exportExt.pdb} file.
     */
    final boolean isExportExt;

    /**
     * Holds a reference to the parser for the file we were constructed with.
     */
    final RekordboxPdb pdb;

    /**
     * Holds a reference to the file this database was constructed from.
     */
    final File sourceFile;

    /**
     * <p>Construct a database access helper from the specified recordbox export file.
     * The file can obtained either from the SD or USB media, or directly from a player
     * using {@link FileFetcher#fetch(InetAddress, String, String, File)}.</p>
     *
     * <p>Be sure to call {@link #close()} when you are done using the parsed database
     * to close the underlying file or users will be unable to unmount the drive holding
     * it until they quit your program.</p>
     *
     * @param sourceFile an export.pdb file
     * @param isExportExt indicates whether this is an ordinary export.pdb file or a newer exportExt.pdb file.
     *
     * @throws IOException if there is a problem reading the file
     */
    DatabaseUtil(File sourceFile, boolean isExportExt) throws IOException {
        this.sourceFile = sourceFile;
        this.isExportExt = isExportExt;
        pdb = new RekordboxPdb(new RandomAccessFileKaitaiStream(sourceFile.getAbsolutePath()), isExportExt);
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
        pdb._io().close();
    }

    /**
     * An interface used to process each row found in a table when traversing them to build our indices.
     * This allows the common code for traversing a table to be reused, while specializing the handling
     * of each kind of table's rows.
     */
    interface RowHandler {
        /**
         * Each row found in a table being scanned will be passed to this function.
         *
         * @param row the row that has just been found
         */
        void rowFound(KaitaiStruct row);
    }

    /**
     * Parse and index all the rows found in a particular {@code export.pdb} table. This method performs a scan of the
     * specified table, passing all rows that are encountered to an interface that knows what to do with them.
     *
     * @param type the type of table present in export.pdb files to be scanned and parsed
     * @param handler the code that knows how to index that kind of row
     *
     * @throws IllegalStateException if there is more than (or less than) one table of that type in the file
     */
    void indexRows(RekordboxPdb.PageType type, DatabaseUtil.RowHandler handler) {
        if (isExportExt) {
            throw new IllegalStateException("Calling indexRows() with a non-ext page type can never succeed for an exportExt.pdb file");
        }
        boolean done = false;
        for (RekordboxPdb.Table table : pdb.tables()) {
            if (table.type() == type) {
                if (done) throw new IllegalStateException("More than one table found with type " + type);
                final long lastIndex = table.lastPage().index();  // This is how we know when to stop.
                RekordboxPdb.PageRef currentRef = table.firstPage();
                boolean moreLeft = true;
                do {
                    // logger.info("Indexing page " + currentRef.index());
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
     * Parse and index all the rows found in a particular {@code exportExt.pdb} table. This method performs a scan of the
     * specified table, passing all rows that are encountered to an interface that knows what to do with them.
     *
     * @param type the type of table present in exportExt.pdb files to be scanned and parsed
     * @param handler the code that knows how to index that kind of row
     *
     * @throws IllegalStateException if there is more than (or less than) one table of that type in the file
     */
    void indexRows(RekordboxPdb.PageTypeExt type, DatabaseUtil.RowHandler handler) {
        if (!isExportExt) {
            throw new IllegalStateException("Calling indexRows() with an ext page type can never succeed for an export.pdb file");
        }
        boolean done = false;
        for (RekordboxPdb.Table table : pdb.tables()) {
            if (table.typeExt() == type) {
                if (done) throw new IllegalStateException("More than one table found with type " + type);
                final long lastIndex = table.lastPage().index();  // This is how we know when to stop.
                RekordboxPdb.PageRef currentRef = table.firstPage();
                boolean moreLeft = true;
                do {
                    // logger.info("Indexing page " + currentRef.index());
                    final RekordboxPdb.Page page = currentRef.body();

                    // Process only ordinary data pages.
                    if (page.isDataPage()) {
                        for (RekordboxPdb.RowGroup rowGroup : page.rowGroups()) {
                            for (RekordboxPdb.RowRef rowRef : rowGroup.rows()) {
                                if (rowRef.present()) {
                                    // We found a row, pass it to the handler to be indexed appropriately.
                                    handler.rowFound(rowRef.bodyExt());
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
    <K> void addToSecondaryIndex(SortedMap<K, SortedSet<Long>> index, K key, Long id) {
        SortedSet<Long> existingIds = index.computeIfAbsent(key, k -> new TreeSet<>());
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
    <K> SortedMap<K, SortedSet<Long>> freezeSecondaryIndex(SortedMap<K, SortedSet<Long>> index) {
        index.replaceAll((k, v) -> Collections.unmodifiableSortedSet(index.get(k)));
        return Collections.unmodifiableSortedMap(index);
    }

}
