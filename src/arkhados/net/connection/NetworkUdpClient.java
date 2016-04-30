/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package arkhados.net.connection;

import com.jme3.network.NetworkClient;
import com.jme3.network.kernel.Connector;
import com.jme3.network.kernel.udp.UdpConnector;
import java.io.IOException;
import java.net.InetAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NetworkUdpClient extends UdpClient implements NetworkClient {

    public NetworkUdpClient(String gameName, int version) {
        super(gameName, version);
    }

    public NetworkUdpClient(String gameName, int version, Connector fast) {
        super(gameName, version, fast);
    }

    @Override
    public void connectToServer(String host, int port, int remoteUdpPort) throws IOException {
        connectToServer(InetAddress.getByName(host), port, remoteUdpPort);
    }

    @Override
    public void connectToServer(InetAddress address, int port, int remoteUdpPort) throws IOException {
        connectToServer(address, port);
    }

    private void connectToServer(InetAddress address, int port) {
        UdpConnector fast;
        try {
            fast = new UdpConnector(address, port);
        } catch (IOException ex) {
            Logger.getLogger(NetworkUdpClient.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }

        setPrimaryConnectors(fast);
    }

}
