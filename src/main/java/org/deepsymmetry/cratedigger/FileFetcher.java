package org.deepsymmetry.cratedigger;

import org.acplt.oncrpc.*;
import org.deepsymmetry.cratedigger.rpc.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>Retrieves data files from a Pioneer player over NFS to enable use of track metadata even when four players
 * are using the same media.</p>
 *
 * <p>The primary purpose of this class is provided through the {@link #fetch(InetAddress, String, String, File)}
 * method.</p>
 *
 * <p>This is a singleton, so the single instance is obtained through the {@link #getInstance()} method.</p>
 */
public class FileFetcher {

    /**
     * The character set with which paths are sent to the NFS servers running on players.
     */
    @SuppressWarnings("WeakerAccess")
    public final static Charset CHARSET = Charset.forName("UTF-16LE");

    /**
     * The default number of bytes to read from the player in each request for file data. This is a trade-off
     * between reducing the number of requests and reducing IP fragmentation and expensive retransmissions
     * of already-sent fragments whenever one is lost.
     */
    @SuppressWarnings("WeakerAccess")
    public final static int DEFAULT_READ_SIZE = 2048;

    /**
     * How long to wait for a response to our UDP RPC calls before retransmitting. The players respond within a
     * few milliseconds if they are going to at all.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_RPC_RETRANSMIT_TIMEOUT = 250;

    /**
     * Holds the singleton instance of this class.
     */
    private static final FileFetcher instance = new FileFetcher();

    /**
     * Look up the singleton instance of this class.
     *
     * @return the only instance that exists
     */
    public static FileFetcher getInstance() {
        return instance;
    }

    /**
     * Make sure the only way to get an instance is to call {@link #getInstance()}.
     */
    private FileFetcher() {
        // Prevent instantiation.
    }

    /**
     * Check the number of bytes to read from the player in each request for file data. This is a trade-off
     * between reducing the number of requests and reducing IP fragmentation and expensive retransmissions
     * of already-sent fragments whenever one is lost.
     *
     * @return the current read size
     */
    public int getReadSize() {
        return readSize;
    }

    /**
     * Set the number of bytes to read from the player in each request for file data. This is a trade-off
     * between reducing the number of requests and reducing IP fragmentation and expensive retransmissions
     * of already-sent fragments whenever one is lost. Changes do not affect operations already in progress.
     *
     * @param readSize the new read size, must be between 1024 and the largest value supported by NFS, inclusive
     * @throws IllegalArgumentException if {@code readSize} is less than 1024 or greater than {@link nfs#MAXDATA}
     */
    public void setReadSize(int readSize) {
        if (readSize < 1024 || readSize > nfs.MAXDATA) {
            throw new IllegalArgumentException("readSize must be between 1024 and " + nfs.MAXDATA + ", inclusive.");
        }
        this.readSize = readSize;
    }

    /**
     * The number of bytes to read from the player in each request for file data. This is a trade-off
     * between reducing the number of requests and reducing IP fragmentation and expensive retransmissions
     * of already-sent fragments whenever one is lost.
     */
    private int readSize = DEFAULT_READ_SIZE;

    /**
     * Check how long to wait for a response to our UDP RPC calls before retransmitting. The players respond within a
     * few milliseconds if they are going to at all.
     *
     * @return the current retransmit timeout
     */
    public int getRetransmitTimeout() {
        return retransmitTimeout;
    }

    /**
     * Set how long to wait for a response to our UDP RPC calls before retransmitting. The players respond within a
     * few milliseconds if they are going to at all. Changes do not affect operations already in progress.
     *
     * @param retransmitTimeout the new retransmit timeout, must be between 1 an 30000, inclusive
     */
    public void setRetransmitTimeout(int retransmitTimeout) {
        if (retransmitTimeout < 1 || retransmitTimeout > 30000) {
            throw new IllegalArgumentException("retransmitTimeout must be between 1 and 30000, inclusive.");
        }
        this.retransmitTimeout = retransmitTimeout;
    }

    /**
     * How long to wait for a response to our UDP RPC calls before retransmitting. The players respond within a
     * few milliseconds if they are going to at all.
     */
    private int retransmitTimeout = DEFAULT_RPC_RETRANSMIT_TIMEOUT;

    /**
     * Keeps track of the root filesystems of the known players so we don't have to mount them every time we want a
     * file. Keys are the address of the player, values are a map from mount paths to the corresponding file handles.
     */
    private final Map<InetAddress, Map<String, FHandle>> mounts = new ConcurrentHashMap<InetAddress,  Map<String, FHandle>>();

    /**
     * Mount a filesystem in preparation to retrieving files from it. Since NFS is a stateless protocol, and the
     * players don't even maintain a mount list, there is no need to unmount it later.
     *
     * @param player the address of the player from which we are going to retrieve a file
     * @param path the mount path of the filesystem from which the file is to be retrieved
     *
     * @return an indication of whether the mount was successful, and, if so, the root file handle of that filesystem
     *
     * @throws IOException if there is a problem talking to the player
     * @throws OncRpcException if there is a problem in the remote procedure call layer
     */
    private FHStatus mount (InetAddress player, String path) throws IOException, OncRpcException {
        OncRpcUdpClient client = (OncRpcUdpClient) OncRpcUdpClient.newOncRpcClient(player, mount.MOUNTPROG, mount.MOUNTVERS, OncRpcProtocols.ONCRPC_UDP);
        client.setRetransmissionTimeout(retransmitTimeout);
        client.setRetransmissionMode(OncRpcUdpRetransmissionMode.EXPONENTIAL);
        DirPath mountPath = new DirPath(path.getBytes(CHARSET));
        FHStatus result = new FHStatus();
        client.call(mount.MOUNTPROC_MNT_1, mountPath, result);
        return result;
    }

    /**
     * Looks up the root file handle for the filesystem exported by the specified player on the specified path. If we
     * have already mounted the filesystem, return the cached file handle. Otherwise, try to mount it, cache it, and
     * return it.
     *
     * @param player the address of the player from which we are going to retrieve a file
     * @param path the mount path of the filesystem from which the file is to be retrieved
     *
     * @return the file handle for the root of the filesystem exported at the specified path
     *
     * @throws IOException if there is a problem talking to the player
     * @throws OncRpcException if there is a problem in the remote procedure call layer
     */
    private FHandle findRoot(InetAddress player, String path) throws IOException, OncRpcException {
        Map<String, FHandle> playerMap = mounts.get(player);
        if (playerMap != null) {
            FHandle cached = playerMap.get(path);
            if (cached != null) {
                return cached;  // Found it, no need to mount again.
            }
        }

        // Not found in our cache, mount the filesystem from the player.
        FHStatus mountResult = mount(player, path);
        if (mountResult.status != 0) {
            throw new IOException("Unable to mount path \"" + path + "\" on player " + player.getHostAddress() +
                    ", mount command returned " + mountResult.status);
        }

        // Create a cache for the player if one does not yet exist.
        if (playerMap == null) {
            playerMap = new ConcurrentHashMap<String, FHandle>();
            mounts.put(player, playerMap);
        }

        // Put the new file handle in the player cache.
        playerMap.put(path, mountResult.directory);
        return mountResult.directory;
    }

    /**
     * Clear any cached mount points for the player with the specified address. This should be called when the
     * player drops off the network, in case a different player later appears on the same address, or if one of the
     * media slots in the player unmounts, because that file handle will no longer be valid. Also clears our
     * cached NFS client for the player.
     *
     * @param player the player that has disappeared or unmounted a filesystem
     */
    public void removePlayer(InetAddress player) {
        mounts.remove(player);
        nfsClients.remove(player);
    }

    /**
     * Holds an NFS client for talking to the player at the specified address, so we don't need to create a new one
     * each time we retrieve another file from the player. The client will be created the first time we need a file
     * from a player, and removed when {@link #removePlayer(InetAddress)} is called.
     */
    private final Map<InetAddress, OncRpcUdpClient> nfsClients = new ConcurrentHashMap<InetAddress, OncRpcUdpClient>();

    /**
     * Find or create the NFS client that can talk to a particular player.
     *
     * @param player the address of the player from which we are going to retrieve files
     *
     * @return the RPC client that can perform NFS calls on the specified player
     *
     * @throws IOException if there is a problem talking to the player
     * @throws OncRpcException if there is a problem in the remote procedure call layer
     */
    private OncRpcUdpClient getNfsClient(InetAddress player) throws OncRpcException, IOException {
        // Find or create the NFS client we will use.
        OncRpcUdpClient client = nfsClients.get(player);
        if (client == null) {
            client = (OncRpcUdpClient) OncRpcClient.newOncRpcClient(player, nfs.NFS_PROGRAM, nfs.NFS_VERSION, OncRpcProtocols.ONCRPC_UDP);
            nfsClients.put(player, client);
            client.setRetransmissionMode(OncRpcUdpRetransmissionMode.EXPONENTIAL);
        }
        client.setRetransmissionTimeout(retransmitTimeout);  // In case the value has changed since the last invocation.
        return client;
    }

    /**
     * Try to find a file on a player, using our cached mount point if possible, or a new mount.
     *
     * @param player the address of the player from which we are going to retrieve a file
     * @param mountPath the mount path of the filesystem from which the file is to be retrieved
     * @param filePath the path to the file itself within the exported filesystem
     *
     * @return the result of looking up the last element in the path to the file
     *
     * @throws IOException if there is a problem talking to the player
     * @throws OncRpcException if there is a problem in the remote procedure call layer
     */
    private DirOpResBody find (InetAddress player, String mountPath, String filePath) throws IOException, OncRpcException {
        FHandle root = findRoot(player, mountPath);
        OncRpcUdpClient client = getNfsClient(player);

        // Iterate over the elements of the path to the file we want to find (the players can't handle multi-part
        // pathnames themselves).
        String[] elements = filePath.split("/");
        FHandle fileHandle = root;
        DirOpRes result = null;
        for (String element : elements) {
            if (element.length() > 0) {
                DirOpArgs args = new DirOpArgs();
                args.dir = fileHandle;
                args.name = new Filename(element.getBytes(CHARSET));
                result = new DirOpRes();
                client.call(nfs.NFSPROC_LOOKUP_2, args, result);
                if (result.status != Stat.NFS_OK) {
                    String message = "Unable to find file \"" + filePath + "\", lookup of element \"" + element +
                            "\" returned status of " + result.status;
                    if (result.status == Stat.NFSERR_NOENT) {
                        throw new FileNotFoundException(message);
                    }
                    throw new IOException(message);
                }
                fileHandle = result.diropok.file;
            }
        }
        if (result == null) {
            throw new IllegalArgumentException("Must supply at least one non-empty mountPath element to look up.");
        }
        return result.diropok;
    }

    /**
     * Downnload a file from a player, storing it locally.
     *
     * @param player the address of the player from which we are to retrieve a file
     * @param mountPath the mount path of the filesystem from which the file is to be retrieved
     * @param sourcePath the path to the file itself within the exported filesystem
     * @param destination the local file to which the remote contents should be saved
     *
     * @throws IOException if there is a problem retrieving the file
     */
    public synchronized void fetch(InetAddress player, String mountPath, String sourcePath, File destination) throws IOException {
        FileOutputStream outputStream = new FileOutputStream(destination);
        try {
            // Make sure the file exists on the player, and find its file handle.
            DirOpResBody found = find(player, mountPath, sourcePath);
            if (found.attributes.type != FType.NFREG) {
                throw new IOException("Path \"" + sourcePath + "\" is not a normal file.");
            }

            // Read the contents of the remote file to the local file.
            OncRpcUdpClient client = getNfsClient(player);
            ReadArgs args = new ReadArgs();
            args.file = found.file;
            args.offset = 0;
            ReadRes result = new ReadRes();
            int bytesLeft = found.attributes.size;
            while (bytesLeft > 0) {
                if (bytesLeft < DEFAULT_READ_SIZE) {
                    args.count = bytesLeft;
                } else {
                    args.count = DEFAULT_READ_SIZE;
                }
                client.call(nfs.NFSPROC_READ_2, args, result);
                if (result.status != Stat.NFS_OK) {
                    throw new IOException("Problem reading \"" + sourcePath + "\": NFS read call returned status: " + result.status);
                }
                byte[] data = result.readResOk.data.value;
                if (data.length != args.count) {
                    throw new IOException("Problem reading \"" + sourcePath + "\": tried to read " + args.count +
                            " bytes but only received " + data.length);
                }
                outputStream.write(data);
                args.offset += data.length;
                bytesLeft -= data.length;
            }

        } catch (OncRpcException e) {
            throw new IOException("Unable to download file \"" + sourcePath + "\", caught ONC RPC exception.", e);
        } finally {
            outputStream.close();
        }
    }
}