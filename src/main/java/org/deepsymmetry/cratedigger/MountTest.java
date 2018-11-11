package org.deepsymmetry.cratedigger;

import org.acplt.oncrpc.OncRpcClient;
import org.acplt.oncrpc.OncRpcException;
import org.acplt.oncrpc.OncRpcProtocols;
import org.acplt.oncrpc.OncRpcUdpClient;
import org.deepsymmetry.cratedigger.rpc.*;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.Charset;

public class MountTest {
    public final static Charset CHARSET = Charset.forName("UTF-16LE");

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
                    throw new IOException("Unable to find file \"" + filePath + "\", lookup of element \"" + element +
                            "\" returned status of " + result.status);
                }
                fileHandle = result.diropok.file;
            }
        }
        return result.diropok;
    }

}