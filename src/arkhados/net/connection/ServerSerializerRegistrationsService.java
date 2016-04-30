
import com.jme3.network.HostedConnection;
import com.jme3.network.Server;
import com.jme3.network.message.SerializerRegistrationsMessage;
import com.jme3.network.serializing.Serializer;
import com.jme3.network.service.AbstractHostedService;
import com.jme3.network.service.HostedServiceManager;

public class ServerSerializerRegistrationsService extends AbstractHostedService {

    @Override
    protected void onInitialize( HostedServiceManager serviceManager ) {
        // Make sure our message type is registered
        Serializer.registerClass(SerializerRegistrationsMessage.class);
        Serializer.registerClass(SerializerRegistrationsMessage.Registration.class);
    }
    
    @Override
    public void start() {
        // Compile the registrations into a message we will
        // send to all connecting clients
        SerializerRegistrationsMessage.compile();
    }
    
    @Override
    public void connectionAdded(Server server, HostedConnection hc) {
        // Just in case
        super.connectionAdded(server, hc);
        
        // Send the client the registration information
        hc.send(SerializerRegistrationsMessage.INSTANCE);
    }
}