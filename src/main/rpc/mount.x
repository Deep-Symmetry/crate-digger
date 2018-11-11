/**
 * XDR structure specifications and ONC/RPC call specifications for
 * Mount version 1, used by NFS version 2, from IETF RFC 1094 of March
 * 1989, Appendix A.
 *
 * The mount protocol is separate from, but related to, the NFS
 * protocol. It provides operating system specific services to get the
 * NFS off the ground -- looking up server path names, validating user
 * identity, and checking access permissions. Clients use the mount
 * protocol to get the first file handle, which allows them entry into
 * a remote filesystem.
 *
 * The mount protocol is kept separate from the NFS protocol to make
 * it easy to plug in new access checking and validation methods
 * without changing the NFS server protocol.
 *
 * Notice that the protocol definition implies stateful servers
 * because the server maintains a list of client's mount requests. The
 * mount list information is not critical for the correct functioning
 * of either the client or the server. It is intended for advisory use
 * only, for example, to warn possible clients when a server is going
 * down.
 *
 * Version one of the mount protocol is used with version two of the
 * NFS protocol. The only information communicated between these two
 * protocols is the "FHandle" structure.
 */

/*
 * The maximum number of bytes in a pathname argument.
 */
const MNTPATHLEN = 1024;

/*
 * The maximum number of bytes in a name argument.
 */
const MNTNAMLEN = 255;

/*
 * The size in bytes of the opaque file handle.
 */
const FHSIZE = 32;

/*
 * The type "FHandle" is the file handle that the server passes to the
 * client. All file operations are done using file handles to refer to
 * a file or directory. The file handle can contain whatever
 * information the server needs to distinguish an individual file.
 *
 * This is the same as the "FHandle" XDR definition in version 2 of
 * the NFS protocol; see section "2.3.3. FHandle" under "Basic Data
 * Types".
 */
typedef opaque FHandle[FHSIZE];

/*
 * The type "FHStatus" is a union.  If a "status" of zero is returned,
 * the call completed successfully, and a file handle for the
 * "directory" follows.  A non-zero status indicates some sort of error.
 * In this case, the status is a UNIX error number.
 */
union FHStatus switch (unsigned status) {
  case 0:
    FHandle directory;
  default:
    void;
};

/*
 * The type "DirPath" is a server pathname of a directory. In the
 * standard protocol, this is an ASCII string. For Pioneer players, it
 * is an UTF-16LE encoded string; to ensure that encoding, we define
 * it as an opaque byte stream and do the encoding in our mount helper
 * class.
 */
typedef opaque DirPath<MNTPATHLEN>;

/*
 * The type "Name" is an arbitrary string used for various names.
 */
typedef string Name<MNTNAMLEN>;

/*
 * Enumerates the currently mounted directories on the server.
 */
struct MountList {
  Name      hostName;
  DirPath   directory;
  MountList *next;
};

/*
 * The response to the mount list call, points at a list of zero or
 * more mount list entries.
 */
struct MountListRes {
  MountList *next;
};

/*
 * Enumerates the names of the groups that are allowed to mount a
 * filesystem in the export list.
 */
struct Groups {
  Name name;
  Groups *next;
};

/*
 * Enumerates the filesystems available for mounting from the server,
 * along with the groups allowed to mount them.
 */
struct ExportList {
  DirPath fileSystem;
  Groups *groups;
  ExportList *next;
};

/*
 * The response to the export list call, points at a list of zero or
 * more export list entries.
 */
struct ExportListRes {
  ExportList *next;
};

/*
 * Protocol description for the mount program
 */
program MOUNTPROG {
    /*
     * Version 1 of the mount protocol used with
     * version 2 of the NFS protocol.
     */
    version MOUNTVERS {
        /*
         * Do Nothing.
         *
         * This procedure does no work. It is made available in all RPC
         * services to allow server response testing and timing.
         */
        void
        MOUNTPROC_NULL(void) = 0;

        /*
         * Add Mount Entry.
         *
         * If the reply "status" is 0, then the reply "directory"
         * contains the file handle for the directory "dirname". This
         * file handle may be used in the NFS protocol. This procedure
         * also adds a new entry to the mount list for this client
         * mounting "dirname".
         */
        FHStatus
        MOUNTPROC_MNT(DirPath) = 1;

        /*
         * Return Mount Entries.
         *
         * Returns the list of remote mounted filesystems. The
         * "MountList" contains one entry for each "hostName" and
         * "directory" pair.
         */
        MountListRes
        MOUNTPROC_DUMP(void) = 2;

        /*
         * Remove Mount Entry.
         *
         * Removes the mount list entry for the input "DirPath".
         */
        void
        MOUNTPROC_UMNT(DirPath) = 3;

        /*
         * Remove All Mount Entries.
         *
         * Removes all of the mount list entries for this client.
         */
        void
        MOUNTPROC_UMNTALL(void) = 4;

        /*
         * Return Export List.
         *
         * Returns a variable number of export list entries. Each
         * entry contains a filesystem name and a list of groups that
         * are allowed to import it. The filesystem name is in
         * "filesys", and the group name is in the list "groups".
         */
        ExportListRes
        MOUNTPROC_EXPORT(void)  = 5;
    } = 1;
} = 100005;
