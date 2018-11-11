package org.deepsymmetry.cratedigger;

import org.acplt.oncrpc.OncRpcClient;
import org.acplt.oncrpc.OncRpcException;
import org.acplt.oncrpc.OncRpcProtocols;
import org.acplt.oncrpc.OncRpcUdpClient;
import org.deepsymmetry.cratedigger.rpc.DirPath;
import org.deepsymmetry.cratedigger.rpc.mount;
import org.deepsymmetry.cratedigger.rpc.FHStatus;

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

}