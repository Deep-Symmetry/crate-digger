/**
 * XDR structure specifications and ONC/RPC call specifications for
 * NFS version 2, from IETF RFC 1094 of March 1989, Section 2.2,
 * Server Procedures.
 *
 * The protocol definition is given as a set of procedures with
 * arguments and results defined using the RPC language (XDR language
 * extended with program, version, and procedure declarations). A
 * brief description of the function of each procedure should provide
 * enough information to allow implementation. Section 2.3 describes
 * the basic data types in more detail.
 *
 * All of the procedures in the NFS protocol are assumed to be
 * synchronous. When a procedure returns to the client, the client
 * can assume that the operation has completed and any data
 * associated with the request is now on stable storage. For example,
 * a client WRITE request may cause the server to update data blocks,
 * filesystem information blocks (such as indirect blocks), and file
 * attribute information (size and modify times). When the WRITE
 * returns to the client, it can assume that the write is safe, even
 * in case of a server crash, and it can discard the data written.
 * This is a very important part of the statelessness of the server.
 * If the server waited to flush data from remote requests, the
 * client would have to save those requests so that it could resend
 * them in case of a server crash.
 **/

/*
 * The maximum number of bytes of data in a READ or WRITE
 * request.
 */
const MAXDATA = 8192;

/* The maximum number of bytes in a pathname argument. */
const MAXPATHLEN = 1024;

/* The maximum number of bytes in a file name argument. */
const MAXNAMLEN = 255;

/* The size in bytes of the opaque "cookie" passed by READDIR. */
const COOKIESIZE  = 4;

/* The size in bytes of the opaque file handle. */
const FHSIZE = 32;

/*
 * The "Stat" type is returned with every procedure's results. A value
 * of NFS_OK indicates that the call completed successfully and the
 * results are valid. The other values indicate some kind of error
 * occurred on the server side during the servicing of the procedure.
 * The error values are derived from UNIX error numbers.
 */
enum Stat {
  NFS_OK = 0,
  /*
   * Not owner. The caller does not have correct ownership to perform
   * the requested operation.
   */
  NFSERR_PERM=1,
  /*
   * No such file or directory. The file or directory specified does
   * not exist.
   */
  NFSERR_NOENT=2,
  /*
   * Some sort of hard error occurred when the operation was in
   * progress. This could be a disk error, for example.
   */
  NFSERR_IO=5,
  /*
   * No such device or address.
   */
  NFSERR_NXIO=6,
  /*
   * Permission denied. The caller does not have the correct
   * permission to perform the requested operation.
   */
  NFSERR_ACCES=13,
  /*
   * File exists.  The file specified already exists.
   */
  NFSERR_EXIST=17,
  /*
   * No such device.
   */
  NFSERR_NODEV=19,
  /*
   * Not a directory. The caller specified a non-directory in a
   * directory operation.
   */
  NFSERR_NOTDIR=20,
  /*
   * Is a directory. The caller specified a directory in a non-
   * directory operation.
   */
  NFSERR_ISDIR=21,
  /*
   * File too large. The operation caused a file to grow beyond the
   * server's limit.
   */
  NFSERR_FBIG=27,
  /*
   * No space left on device. The operation caused the server's
   * filesystem to reach its limit.
   */
  NFSERR_NOSPC=28,
  /*
   * Read-only filesystem. Write attempted on a read-only filesystem.
   */
  NFSERR_ROFS=30,
  /*
   * File name too long. The file name in an operation was too long.
   */
  NFSERR_NAMETOOLONG=63,
  /*
   * Directory not empty. Attempted to remove a directory that was not
   * empty.
   */
  NFSERR_NOTEMPTY=66,
  /*
   * Disk quota exceeded. The client's disk quota on the server has
   * been exceeded.
   */
  NFSERR_DQUOT=69,
  /*
   * The "FHandle" given in the arguments was invalid. That is, the
   * file referred to by that file handle no longer exists, or access
   * to it has been revoked.
   */
  NFSERR_STALE=70,
  /*
   * The server's write cache used in the "WRITECACHE" call got
   * flushed to disk.
   */
  NFSERR_WFLUSH=99
};

/*
 * The enumeration "FType" gives the type of a file. The type NFNON
 * indicates a non-file, NFREG is a regular file, NFDIR is a
 * directory, NFBLK is a block-special device, NFCHR is a character-
 * special device, and NFLNK is a symbolic link.
 */
enum FType {
   NFNON = 0,
   NFREG = 1,
   NFDIR = 2,
   NFBLK = 3,
   NFCHR = 4,
   NFLNK = 5
};

/*
 * The "FHandle" is the file handle passed between the server and the
 * client. All file operations are done using file handles to refer to
 * a file or directory. The file handle can contain whatever
 * information the server needs to distinguish an individual file.
 */
typedef opaque FHandle[FHSIZE];

/*
 * The "TimeVal" structure is the number of seconds and microseconds
 * since midnight January 1, 1970, Greenwich Mean Time. It is used to
 * pass time and date information.
 */
struct TimeVal {
  unsigned int seconds;
  unsigned int useconds;
};

/*
 * The "FAttr" structure contains the attributes of a file; "type" is
 * the type of the file; "nlink" is the number of hard links to the
 * file (the number of different names for the same file); "uid" is
 * the user identification number of the owner of the file; "gid" is
 * the group identification number of the group of the file; "size" is
 * the size in bytes of the file; "blocksize" is the size in bytes of
 * a block of the file; "rdev" is the device number of the file if it
 * is type NFCHR or NFBLK; "blocks" is the number of blocks the file
 * takes up on disk; "fsid" is the file system identifier for the
 * filesystem containing the file; "fileid" is a number that uniquely
 * identifies the file within its filesystem; "atime" is the time when
 * the file was last accessed for either read or write; "mtime" is the
 * time when the file data was last modified (written); and "ctime" is
 * the time when the status of the file was last changed. Writing to
 * the file also changes "ctime" if the size of the file changes.
 *
 * "Mode" is the access mode encoded as a set of bits. Notice that the
 * file type is specified both in the mode bits and in the file type.
 * This is really a bug in the protocol and will be fixed in future
 * versions. The descriptions given below specify the bit positions
 * using octal numbers.
 *
 *     0040000 This is a directory; "type" field should be NFDIR.
 *     0020000 This is a character special file; "type" field should
 *             be NFCHR.
 *     0060000 This is a block special file; "type" field should be
 *             NFBLK.
 *     0100000 This is a regular file; "type" field should be NFREG.
 *     0120000 This is a symbolic link file;  "type" field should be
 *             NFLNK.
 *     0140000 This is a named socket; "type" field should be NFNON.
 *     0004000 Set user id on execution.
 *     0002000 Set group id on execution.
 *     0001000 Save swapped text even after use.
 *     0000400 Read permission for owner.
 *     0000200 Write permission for owner.
 *     0000100 Execute and search permission for owner.
 *     0000040 Read permission for group.
 *     0000020 Write permission for group.
 *     0000010 Execute and search permission for group.
 *     0000004 Read permission for others.
 *     0000002 Write permission for others.
 *     0000001 Execute and search permission for others.
 *
 * Notes: The bits are the same as the mode bits returned by the
 * stat(2) system call in UNIX. The file type is specified both in the
 * mode bits and in the file type. This is fixed in future versions.
 *
 * The "rdev" field in the attributes structure is an operating system
 * specific device specifier. It will be removed and generalized in
 * the next revision of the protocol.
 */
struct FAttr {
  FType        type;
  unsigned int mode;
  unsigned int nlink;
  unsigned int uid;
  unsigned int gid;
  unsigned int size;
  unsigned int blocksize;
  unsigned int rdev;
  unsigned int blocks;
  unsigned int fsid;
  unsigned int fileid;
  TimeVal      atime;
  TimeVal      mtime;
  TimeVal      ctime;
};

/*
 * The "SAttr" structure contains the file attributes which can be set
 * from the client. The fields are the same as for "FAttr" above. A
 * "size" of zero means the file should be truncated. A value of -1
 * indicates a field that should be ignored.
 */
struct SAttr {
  unsigned int mode;
  unsigned int uid;
  unsigned int gid;
  unsigned int size;
  TimeVal      atime;
  TimeVal      mtime;
};

/*
 * The type "Filename" is used for passing file names or pathname
 * components.
 *
 * In the standard protocol, this is an ASCII string. For Pioneer
 * players, it is an UTF-16LE encoded string; to ensure that encoding,
 * we define it as an opaque byte stream and do the encoding and
 * decoding in our NFS helper classes.
 */
typedef opaque Filename<MAXNAMLEN>;

/*
 * The type "Path" is a pathname. The server considers it as a string
 * with no internal structure, but to the client it is the name of a
 * node in a filesystem tree.
 *
 * In the standard protocol, this is an ASCII string. For Pioneer
 * players, it is an UTF-16LE encoded string; to ensure that encoding,
 * we define it as an opaque byte stream and do the encoding and
 * decoding in our NFS helper classes.
 */
typedef opaque Path<MAXPATHLEN>;

typedef opaque NFSData<MAXDATA>;

typedef opaque NFSCookie[COOKIESIZE];

/*
 * The "AttrStat" structure is a common procedure result. It contains
 * a "status" and, if the call succeeded, it also contains the
 * attributes of the file on which the operation was done.
 */
union AttrStat switch (Stat status) {
  case NFS_OK:
    FAttr attributes;
  default:
    void;
};

/*
 * The "DirOpArgs" structure is used in directory operations. The
 * "FHandle" "dir" is the directory in which to find the file "name".
 * A directory operation is one in which the directory is affected.
 */
struct DirOpArgs {
  FHandle  dir;
  Filename name;
};

/*
 * This structure is returned in a "diropres" structure when the call
 * succeeded and "status" has the value NFS_OK.
 */
struct DirOpResBody {
  FHandle file;
  FAttr   attributes;
};

/*
 * The results of a directory operation are returned in a "DirOpRes"
 * structure. If the call succeeded, a new file handle "file" and the
 * "attributes" associated with that file are returned along with the
 * "status".
 */
union DirOpRes switch (Stat status) {
  case NFS_OK:
    DirOpResBody diropok;
  default:
    void;
};

struct SAttrArgs {
  FHandle file;
  SAttr attributes;
};

union ReadLinkRes switch (Stat status) {
  case NFS_OK:
    Path data;
  default:
    void;
};

struct ReadArgs {
  FHandle file;
  unsigned offset;
  unsigned count;
  unsigned totalcount;
};

struct ReadResBody {
   FAttr attributes;
   NFSData data;
};

union ReadRes switch (Stat status) {
 case NFS_OK:
   ReadResBody readResOk;
 default:
   void;
 };

struct WriteArgs {
  FHandle file;
  unsigned beginOffset;
  unsigned offset;
  unsigned totalCount;
  NFSData data;
};

struct CreateArgs {
  DirOpArgs where;
  SAttr attributes;
};

struct RenameArgs {
  DirOpArgs from;
  DirOpArgs to;
};

struct LinkArgs {
  FHandle from;
  DirOpArgs to;
};

struct SymLinkArgs {
  DirOpArgs from;
  Path to;
  SAttr attributes;
};

struct ReadDirArgs {
  FHandle dir;
  NFSCookie cookie;
  unsigned count;
};

struct Entry {
  unsigned fileId;
  Filename name;
  NFSCookie cookie;
  Entry *next;
};

struct ReadDirResBody {
  Entry *entries;
  bool eof;
};

union ReadDirRes switch (Stat status) {
  case NFS_OK:
    ReadDirResBody readdirok;
  default:
   void;
};

struct StatFSResBody {
  unsigned tsize;
  unsigned bsize;
  unsigned blocks;
  unsigned bfree;
  unsigned bavail;
};

union StatFSRes switch (Stat status) {
  case NFS_OK:
    StatFSResBody info;
  default:
    void;
};

/*
 * Remote file service routines
 */
program NFS_PROGRAM {
    version NFS_VERSION {
        /*
         * Do Nothing.
         *
         * This procedure does no work. It is made available in all RPC
         * services to allow server response testing and timing.
         */
        void
        NFSPROC_NULL(void)              = 0;

        /*
         * Get File Attributes.
         *
         * If the reply status is NFS_OK, then the reply attributes contains
         * the attributes for the file given by the input FHandle
         */
        AttrStat
        NFSPROC_GETATTR(FHandle)        = 1;

        /*
         * Set File Attributes.
         *
         * The "attributes" argument contains fields which are either -1 or
         * are the new value for the attributes of "file". If the reply status
         * is NFS_OK, then the reply attributes have the attributes of the
         * file after the "SETATTR" operation has completed.
         *
         * Notes: The use of -1 to indicate an unused field in "attributes" is
         * changed in the next version of the protocol.
         */
        AttrStat
        NFSPROC_SETATTR(SAttrArgs)      = 2;

        /*
         * Get Filesystem Root.
         *
         * Obsolete. This procedure is no longer used because finding the root
         * file handle of a filesystem requires moving pathnames between
         * client and server. To do this right, we would have to define a
         * network standard representation of pathnames. Instead, the function
         * of looking up the root file handle is done by the MNTPROC_MNT
         * procedure. (See Appendix A, "Mount Protocol Definition", for
         * details).
         */
        void
        NFSPROC_ROOT(void)              = 3;

        /*
         * Look Up Filename.
         *
         * If the reply "status" is NFS_OK, then the reply "file" and reply
         * "attributes" are the file handle and attributes for the file "name"
         * in the directory given by "dir" in the argument.
         */
        DirOpRes
        NFSPROC_LOOKUP(DirOpArgs)       = 4;

        /*
         * Read from Symbolic Link.
         *
         * If "status" has the value NFS_OK, then the reply "data" is the data
         * in the symbolic link given by the file referred to by the FHandle
         * argument.
         *
         * Notes:  Since NFS always parses pathnames on the client, the pathname
         * in a symbolic link may mean something different (or be meaningless)
         * on a different client or on the server if a different pathname syntax
         * is used.
         */
        ReadLinkRes
        NFSPROC_READLINK(FHandle)       = 5;

        /*
         * Read from File.
         *
         * Returns up to "count" bytes of "data" from the file given by
         * "file", starting at "offset" bytes from the beginning of the file.
         * The first byte of the file is at offset zero. The file attributes
         * after the read takes place are returned in "attributes".
         *
         * Notes:  The argument "totalcount" is unused, and is removed in the
         * next protocol revision.
         */
        ReadRes
        NFSPROC_READ(ReadArgs)          = 6;

        /*
         * Write to Cache.
         *
         * To be used in the next protocol revision.
         */
        void
        NFSPROC_WRITECACHE(void)        = 7;

        /*
         * Write to File.
         *
         * Writes "data" beginning "offset" bytes from the beginning
         * of "file". The first byte of the file is at offset zero. If
         * the reply "status" is NFS_OK, then the reply "attributes"
         * contains the attributes of the file after the write has
         * completed. The write operation is atomic. Data from this
         * "WRITE" will not be mixed with data from another client's
         * "WRITE".
         *
         * Notes: The arguments "beginoffset" and "totalcount" are
         * ignored and are removed in the next protocol revision.
         */
        AttrStat
        NFSPROC_WRITE(WriteArgs)        = 8;

        /*
         * Create File.
         *
         * The file "name" is created in the directory given by "dir".
         * The initial attributes of the new file are given by
         * "attributes". A reply "status" of NFS_OK indicates that the
         * file was created, and reply "file" and reply "attributes"
         * are its file handle and attributes. Any other reply
         * "status" means that the operation failed and no file was
         * created.
         *
         * Notes: This routine should pass an exclusive create flag,
         * meaning "create the file only if it is not already there".
         */
        DirOpRes
        NFSPROC_CREATE(CreateArgs)      = 9;

        /*
         * Remove File.
         *
         * The file "name" is removed from the directory given by "dir".  A
         * reply of NFS_OK means the directory entry was removed.
         *
         * Notes: Possibly non-idempotent operation.
         */
        Stat
        NFSPROC_REMOVE(DirOpArgs)       = 10;

        /*
         * Rename File.
         *
         * The existing file "from.name" in the directory given by
         * "from.dir" is renamed to "to.name" in the directory given
         * by "to.dir". If the reply is NFS_OK, the file was renamed.
         * The RENAME operation is atomic on the server; it cannot be
         * interrupted in the middle.
         *
         * Notes: Possibly non-idempotent operation.
         */
        Stat
        NFSPROC_RENAME(RenameArgs)      = 11;

        /*
         * Create Link to File.
         *
         * Creates the file "to.name" in the directory given by
         * "to.dir", which is a hard link to the existing file given
         * by "from". If the return value is NFS_OK, a link was
         * created. Any other return value indicates an error, and the
         * link was not created.
         *
         * A hard link should have the property that changes to either
         * of the linked files are reflected in both files. When a
         * hard link is made to a file, the attributes for the file
         * should have a value for "nlink" that is one greater than
         * the value before the link.
         *
         * Notes: Possibly non-idempotent operation.
         */
        Stat
        NFSPROC_LINK(LinkArgs)          = 12;

        /*
         * Crete Symbolic Link.
         *
         * Creates the file "from.name" with FType NFLNK in the
         * directory given by "from.dir". The new file contains the
         * pathname "to" and has initial attributes given by
         * "attributes". If the return value is NFS_OK, a link was
         * created. Any other return value indicates an error, and the
         * link was not created.
         *
         * A symbolic link is a pointer to another file. The name
         * given in "to" is not interpreted by the server, only stored
         * in the newly created file. When the client references a
         * file that is a symbolic link, the contents of the symbolic
         * link are normally transparently reinterpreted as a pathxname
         * to substitute. A READLINK operation returns the data to the
         * client for interpretation.
         *
         * Notes: On UNIX servers the attributes are never used, since
         * symbolic links always have mode 0777.
         */
        Stat
        NFSPROC_SYMLINK(SymLinkArgs)    = 13;

        /*
         * Create Directory.
         *
         * The new directory "where.name" is created in the directory
         * given by "where.dir". The initial attributes of the new
         * directory are given by "attributes". A reply "status" of
         * NFS_OK indicates that the new directory was created, and
         * reply "file" and reply "attributes" are its file handle and
         * attributes. Any other reply "status" means that the
         * operation failed and no directory was created.
         *
         * Notes: Possibly non-idempotent operation.
         */
        DirOpRes
        NFSPROC_MKDIR(CreateArgs)       = 14;

        /*
         * Remove Directory.
         *
         * The existing empty directory "name" in the directory given
         * by "dir" is removed. If the reply is NFS_OK, the directory
         * was removed.
         *
         * Notes: Possibly non-idempotent operation.
         */
        Stat
        NFSPROC_RMDIR(DirOpArgs)        = 15;

        /*
         * Read from Directory.
         *
         * Returns a variable number of directory entries, with a
         * total size of up to "count" bytes, from the directory given
         * by "dir". If the returned value of "status" is NFS_OK, then
         * it is followed by a variable number of "Entry"s. Each
         * "Entry" contains a "fileid" which consists of a unique
         * number to identify the file within a filesystem, the "name"
         * of the file, and a "cookie" which is an opaque pointer to
         * the next entry in the directory. The cookie is used in the
         * next READDIR call to get more entries starting at a given
         * point in the directory. The special cookie zero (all bits
         * zero) can be used to get the entries starting at the
         * beginning of the directory. The "fileid" field should be
         * the same number as the "fileid" in the the attributes of
         * the file. (See section "2.3.5. FAttr" under "Basic Data
         * Types".) The "eof" flag has a value of TRUE if there are no
         * more entries in the directory.
         */
        ReadDirRes
        NFSPROC_READDIR(ReadDirArgs)    = 16;

        /*
         * Get Filesystem Attributes.
         *
         * If the reply "status" is NFS_OK, then the reply "info"
         * gives the attributes for the filesystem that contains file
         * referred to by the input FHandle. The attribute fields
         * contain the following values:
         *
         * tsize   The optimum transfer size of the server in bytes.  This is
         *         the number of bytes the server would like to have in the
         *         data part of READ and WRITE requests.
         *
         * bsize   The block size in bytes of the filesystem.
         *
         * blocks  The total number of "bsize" blocks on the filesystem.
         *
         * bfree   The number of free "bsize" blocks on the filesystem.
         *
         * bavail  The number of "bsize" blocks available to non-privileged
         *         users.
         *
         * Notes: This call does not work well if a filesystem has
         *        variable size blocks.
         */
        StatFSRes
        NFSPROC_STATFS(FHandle)         = 17;
    } = 2;
} = 100003;
