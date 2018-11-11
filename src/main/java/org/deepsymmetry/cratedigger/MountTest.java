package org.deepsymmetry.cratedigger;

import org.acplt.oncrpc.*;
import org.deepsymmetry.cratedigger.rpc.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.Charset;

public class MountTest {
    /**
     * The character set with which paths are sent to the NFS servers running on players.
     */
    public final static Charset CHARSET = Charset.forName("UTF-16LE");

    /**
     * The number of bytes to read from the player in each request for file data. This is a tradeoff
     * between reducing the number of requests and reducing IP fragmentation and expensive retransmissions
     * of already-sent fragments whenever one is lost.
     */
    public final static int READ_SIZE = 2048;

    /**
     * After this many timeouts in a row while trying to read data from a player, we will give up.
     */
    public final static int MAX_CONSECUTIVE_TIMEOUTS = 4;

    public static FHStatus mount (InetAddress host, String path) throws IOException, OncRpcException {
        OncRpcClient client = OncRpcUdpClient.newOncRpcClient(host, mount.MOUNTPROG, mount.MOUNTVERS, OncRpcProtocols.ONCRPC_UDP);
        DirPath mountPath = new DirPath(path.getBytes(CHARSET));
        FHStatus result = new FHStatus();
        client.call(mount.MOUNTPROC_MNT_1, mountPath, result);
        return result;
    }

    public static DirOpResBody find (InetAddress host, String mountPath, String filePath) throws IOException, OncRpcException {
        FHStatus root = mount(host, mountPath);
        if (root.status != 0) {
            throw new IOException("Unable to mount \"" + mountPath + "\", mount returned status of " + root.status);
        }
        OncRpcClient client = OncRpcUdpClient.newOncRpcClient(host, nfs.NFS_PROGRAM, nfs.NFS_VERSION, OncRpcProtocols.ONCRPC_UDP);
        String[] elements = filePath.split("/");
        FHandle fileHandle = root.directory;
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
        return result.diropok;
    }

    public static void fetch(InetAddress host, String mountPath, String sourcePath, File destination) throws IOException {
        FileOutputStream outputStream = new FileOutputStream(destination);
        try {
            DirOpResBody found = find(host, mountPath, sourcePath);
            if (found.attributes.type != FType.NFREG) {
                throw new IOException("Path \"" + sourcePath + "\" is not a normal file.");
            }
            OncRpcUdpClient client = (OncRpcUdpClient) OncRpcClient.newOncRpcClient(host, nfs.NFS_PROGRAM, nfs.NFS_VERSION, OncRpcProtocols.ONCRPC_UDP);
            client.setRetransmissionTimeout(250);  // It only takes a few milliseconds to respond if it is ever going to.
            client.setRetransmissionMode(OncRpcUdpRetransmissionMode.EXPONENTIAL);
            ReadArgs args = new ReadArgs();
            args.file = found.file;
            args.offset = 0;
            ReadRes result = new ReadRes();
            int bytesLeft = found.attributes.size;
            while (bytesLeft > 0) {
                if (bytesLeft < READ_SIZE) {
                    args.count = bytesLeft;
                } else {
                    args.count = READ_SIZE;
                }
                int timeouts = 0;
                boolean succeeded = false;
                try {
                    while (!succeeded) {
                        client.call(nfs.NFSPROC_READ_2, args, result);
                        succeeded = true;
                    }
                }  catch (OncRpcTimeoutException e) {
                    ++timeouts;
                    if (timeouts > MAX_CONSECUTIVE_TIMEOUTS) {
                        throw e;
                    }
                }
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
            if (outputStream != null) {
                outputStream.close();
            }
        }

    }

}