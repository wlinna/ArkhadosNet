package arkhados.net.connection;

import com.jme3.network.kernel.udp.UdpKernel;
import java.io.IOException;

public class MyUdpKernel extends UdpKernel{

    public MyUdpKernel(int port) throws IOException {
        super(port);
    }

    @Override
    public void terminate() throws InterruptedException {
        super.terminate();
        addEnvelope(EVENTS_PENDING);
    }    
}
