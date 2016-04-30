/*
 * Copyright (c) 2009-2012 jMonkeyEngine
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'jMonkeyEngine' nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package arkhados.net.connection;

import com.jme3.network.*;
import com.jme3.network.base.KernelFactory;
import com.jme3.network.base.MessageListenerRegistry;
import com.jme3.network.base.MessageProtocol;
import com.jme3.network.kernel.Endpoint;
import com.jme3.network.kernel.Kernel;
import com.jme3.network.kernel.udp.UdpKernel;
import com.jme3.network.message.ChannelInfoMessage;
import com.jme3.network.message.ClientRegistrationMessage;
import com.jme3.network.message.DisconnectMessage;
import com.jme3.network.service.HostedServiceManager;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A default implementation of the Server interface that delegates its network
 * connectivity to kernel.Kernel.
 *
 * @version $Revision$
 * @author Paul Speed
 */
public class UdpServer implements Server {

    static final Logger log = Logger.getLogger(UdpServer.class.getName());
    private static final long MAX_DELTA_TIME = 2000;

    // The first channel is reserved for unreliable
    private static final int CH_UNRELIABLE = 0;

    private boolean isRunning = false;
    private final AtomicInteger nextId = new AtomicInteger(0);
    private String gameName;
    private int version;
    private final KernelFactory kernelFactory = KernelFactory.DEFAULT;
    private MyKernelAdapter fastAdapter;
    private final List<MyKernelAdapter> channels = new ArrayList<>();
    private final List<Integer> alternatePorts;
    private final Redispatch dispatcher = new Redispatch();
    private final Map<Integer, HostedConnection> connections;
    private final Map<Endpoint, Connection> endpointConnections
            = new ConcurrentHashMap<>();
    private final Map<Endpoint, Integer> endpointOrdernums
            = new ConcurrentHashMap<>();

    // Keeps track of clients for whom we've only received the UDP
    // registration message
    private final Map<Long, Connection> connecting = new ConcurrentHashMap<>();

    private final MessageListenerRegistry<HostedConnection> messageListeners
            = new MessageListenerRegistry<>();
    private final List<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();

    private final Map<HostedConnection, Integer> orderNums
            = new ConcurrentHashMap<>();
    private final Map<HostedConnection, Integer> orderNumCounters
            = new ConcurrentHashMap<>();
    private final Map<HostedConnection, Queue<Message>> forceQueues
            = new ConcurrentHashMap<>();
    private final Map<HostedConnection, Long> lastReceivals
            = new ConcurrentHashMap<>();

    private HostedServiceManager services;

    final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);

    public UdpServer(String gameName, int version, Kernel fast) {
        this.connections = new ConcurrentHashMap<>();
        this.alternatePorts = new ArrayList<>();
        if (fast == null) {
            throw new IllegalArgumentException("Default server requires a fast kernel instance.");
        }

        this.gameName = gameName;
        this.version = version;
        this.services = new HostedServiceManager(this);
        addStandardServices();

        fastAdapter = new MyKernelAdapter(this, fast, dispatcher);
        channels.add(fastAdapter);

    }

    protected final void addStandardServices() {
        log.fine("Adding standard services...");
        // TODO: Enable this later
//        services.addService(new ServerSerializerRegistrationsService());
    }

    @Override
    public String getGameName() {
        return gameName;
    }

    @Override
    public int getVersion() {
        return version;
    }

    @Override
    public HostedServiceManager getServices() {
        return services;
    }

    @Override
    public int addChannel(int port) {
        throw new UnsupportedOperationException("Channels are not supported in the UdpServer");
//        if (isRunning) {
//            throw new IllegalStateException("Channels cannot be added once server is started.");
//        }

        // Note: it does bug me that channels aren't 100% universal and
        // setup externally but it requires a more invasive set of changes
        // for "connection types" and some kind of registry of kernel and
        // connector factories.  This really would be the best approach and
        // would allow all kinds of channel customization maybe... but for
        // now, we hard-code the standard connections and treat the +2 extras
        // differently.
        // Check for consistency with the channels list
//        if (channels.size() - CH_FIRST != alternatePorts.size()) {
//            throw new IllegalStateException("Channel and port lists do not match.");
//        }
//        try {
//            int result = alternatePorts.size();
//            alternatePorts.add(port);
//
//            Kernel kernel = kernelFactory.createKernel(result, port);
//            channels.add(new MyKernelAdapter(this, kernel, dispatcher, true));
//
//            return result;
//        } catch (IOException e) {
//            throw new RuntimeException("Error adding channel for port:" + port, e);
//        }
    }

    protected void checkChannel(int channel) {
        if (channel < MessageConnection.CHANNEL_DEFAULT_RELIABLE
                || channel >= alternatePorts.size()) {
            throw new IllegalArgumentException("Channel is undefined:" + channel);
        }
    }

    @Override
    public void start() {
        if (isRunning) {
            throw new IllegalStateException("Server is already started.");
        }

        // Initialize the kernels
        for (MyKernelAdapter ka : channels) {
            ka.initialize();
        }

        // Start em up
        for (MyKernelAdapter ka : channels) {
            ka.start();
        }

        isRunning = true;

        // Start the services
        services.start();

        executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                for (Map.Entry<HostedConnection, Queue<Message>> entry
                        : forceQueues.entrySet()) {
                    Message m = entry.getValue().peek();
                    if (m == null) {
                        continue;
                    }

                    Integer orderNum = orderNumCounters.get(entry.getKey());

                    ConnectionMessageContainer container
                            = new ConnectionMessageContainer(orderNum, m, true);

                    entry.getKey().send(container);
                }

                for (Iterator<Map.Entry<HostedConnection, Long>> it 
                        = lastReceivals.entrySet().iterator(); it.hasNext();) {
                    Map.Entry<HostedConnection, Long> entry = it.next();
                    long delta = System.currentTimeMillis() - entry.getValue();
                    if (delta > MAX_DELTA_TIME) {
                        ((Connection) entry.getKey()).closeConnection();
                        it.remove();
                    }
                }
            }
        }, 0, 200, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public void close() {
        if (!isRunning) {
            throw new IllegalStateException("Server is not started.");
        }

        // First stop the services since we are about to
        // kill the connections they are using
        services.stop();
        executor.shutdown();

        try {
            // Kill the adpaters, they will kill the kernels
            for (MyKernelAdapter ka : channels) {
                ka.close();
            }

            isRunning = false;

            // Now terminate all of the services
            services.terminate();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while closing", e);
        }
    }

    @Override
    public void broadcast(Message message) {
        broadcast(null, message);
    }

    @Override
    public void broadcast(Filter<? super HostedConnection> filter, Message message) {
        if (log.isLoggable(Level.FINER)) {
            log.log(Level.FINER, "broadcast({0}, {1})", new Object[]{filter, message});
        }

        if (connections.isEmpty()) {
            return;
        }

        ByteBuffer buffer = MessageProtocol.messageToBuffer(message, null);

        FilterAdapter adapter = filter == null ? null : new FilterAdapter(filter);

        fastAdapter.broadcast(adapter, buffer, false);
    }

    @Override
    public void broadcast(int channel, Filter<? super HostedConnection> filter, Message message) {
        throw new UnsupportedOperationException("UdpServer does not support alternate channels");
//        if (log.isLoggable(Level.FINER)) {
//            log.log(Level.FINER, "broadcast({0}, {1}. {2})", new Object[]{channel, filter, message});
//        }
//
//        if (connections.isEmpty()) {
//            return;
//        }
//
//        checkChannel(channel);
//
//        ByteBuffer buffer = MessageProtocol.messageToBuffer(message, null);
//
//        FilterAdapter adapter = filter == null ? null : new FilterAdapter(filter);
//
//        channels.get(channel + CH_FIRST).broadcast(adapter, buffer, true, false);
    }

    @Override
    public HostedConnection getConnection(int id) {
        return connections.get(id);
    }

    @Override
    public boolean hasConnections() {
        return !connections.isEmpty();
    }

    @Override
    public Collection<HostedConnection> getConnections() {
        return Collections.unmodifiableCollection((Collection<HostedConnection>) connections.values());
    }

    @Override
    public void addConnectionListener(ConnectionListener listener) {
        connectionListeners.add(listener);
    }

    @Override
    public void removeConnectionListener(ConnectionListener listener) {
        connectionListeners.remove(listener);
    }

    @Override
    public void addMessageListener(MessageListener<? super HostedConnection> listener) {
        messageListeners.addMessageListener(listener);
    }

    @Override
    public void addMessageListener(MessageListener<? super HostedConnection> listener, Class... classes) {
        messageListeners.addMessageListener(listener, classes);
    }

    @Override
    public void removeMessageListener(MessageListener<? super HostedConnection> listener) {
        messageListeners.removeMessageListener(listener);
    }

    @Override
    public void removeMessageListener(MessageListener<? super HostedConnection> listener, Class... classes) {
        messageListeners.removeMessageListener(listener, classes);
    }

    protected void dispatch(HostedConnection source, Message m) {
        if (log.isLoggable(Level.FINER)) {
            log.log(Level.FINER, "{0} received:{1}", new Object[]{source, m});
        }

        if (source == null) {
            messageListeners.messageReceived(source, m);
        } else {

            // A semi-heavy handed way to make sure the listener
            // doesn't get called at the same time from two different
            // threads for the same hosted connection.
            synchronized (source) {
                messageListeners.messageReceived(source, m);
            }
        }
    }

    protected void fireConnectionAdded(HostedConnection conn) {
        for (ConnectionListener l : connectionListeners) {
            l.connectionAdded(this, conn);
        }
    }

    protected void fireConnectionRemoved(HostedConnection conn) {
        for (ConnectionListener l : connectionListeners) {
            l.connectionRemoved(this, conn);
        }
    }

    protected int getChannel(MyKernelAdapter ka) {
        return channels.indexOf(ka);
    }

    protected void registerClient(MyKernelAdapter ka, Endpoint p,
            ClientRegistrationMessage m, int orderNum) {
        Connection addedConnection = null;

        // TODO: Check ordernum, register it somehow and block old orderNums
        // generally this will only be called by one thread but it's        
        // important enough I won't take chances
        synchronized (this) {

            Integer epOrdernum = endpointOrdernums.get(p);
            if (epOrdernum != null && orderNum <= epOrdernum) {
                return;
            }

            endpointOrdernums.put(p, orderNum);

            // Grab the random ID that the client created when creating
            // its two registration messages
            long tempId = m.getId();

            // See if we already have one
            Connection c = connecting.remove(tempId);

            if (c == null) {
                c = new Connection(channels.size());
                log.log(Level.FINE, "Registering client for endpoint, pass 1:{0}.", p);
            } else {
                log.log(Level.FINE, "Refining client registration for endpoint:{0}.", p);
            }

            // Fill in what we now know
            int channel = getChannel(ka);
            c.setChannel(channel, p);
            log.log(Level.FINE, "Setting up channel:{0}", channel);

            // This is the initial connection
            // and we will send the connection information
            // Validate the name and version which is only sent
            // over the reliable connection at this point.
            if (!getGameName().equals(m.getGameName())
                    || getVersion() != m.getVersion()) {

                log.log(Level.FINE, "Kicking client due to name/version mismatch:{0}.", c);

                // Need to kick them off... I may regret doing this from within
                // the sync block but the alternative is more code
                c.close("Server client mismatch, server:" + getGameName() + " v" + getVersion()
                        + "  client:" + m.getGameName() + " v" + m.getVersion());
                return;
            }

            // Else send the extra channel information to the client
            if (!alternatePorts.isEmpty()) { // TODO: MAYBE handle this
                ChannelInfoMessage cim = new ChannelInfoMessage(m.getId(), alternatePorts);
                c.send(cim);
            }

            if (c.isComplete()) {
                // Then we are fully connected
                if (connections.put(c.getId(), c) == null) {

                    for (Endpoint cp : c.channels) {
                        if (cp == null) {
                            continue;
                        }
                        endpointConnections.put(cp, c);
                    }

                    addedConnection = c;
                    forceQueues.put(addedConnection, new ArrayDeque<Message>());
                    orderNums.put(addedConnection, -1);
                    orderNumCounters.put(addedConnection, 0);
                }
            } else {
                // Need to keep getting channels so we'll keep it in
                // the map
                connecting.put(tempId, c);
            }
        }

        // Best to do this outside of the synch block to avoid
        // over synchronizing which is the path to deadlocks
        if (addedConnection != null) {
            log.log(Level.FINE, "Client registered:{0}.", addedConnection);

            // Send the ID back to the client letting it know it's
            // fully connected.
            m = new ClientRegistrationMessage();
            m.setId(addedConnection.getId());
            m.setReliable(false);

            forceQueues.get(addedConnection).add(m);
            // Now we can notify the listeners about the
            // new connection.
            fireConnectionAdded(addedConnection);

            // Send a second registration message with an invalid ID
            // to let the connection know that it can start its services
            m = new ClientRegistrationMessage();
            m.setId(-1);
            m.setReliable(false);
            forceQueues.get(addedConnection).add(m);
        }
    }

    protected HostedConnection getConnection(Endpoint endpoint) {
        return endpointConnections.get(endpoint);
    }

    protected void removeConnecting(Endpoint p) {
        // No easy lookup for connecting Connections
        // from endpoint.
        for (Map.Entry<Long, Connection> e : connecting.entrySet()) {
            if (e.getValue().hasEndpoint(p)) {
                connecting.remove(e.getKey());
                return;
            }
        }
    }

    protected void connectionClosed(Endpoint p) {
        if (p.isConnected()) {
            log.log(Level.FINE, "Connection closed:{0}.", p);
        } else {
            log.log(Level.FINE, "Connection closed:{0}.", p);
        }

        // Try to find the endpoint in all ways that it might
        // exist.  Note: by this point the raw network channel is 
        // closed already.
        // Also note: this method will be called multiple times per
        // HostedConnection if it has multiple endpoints.
        Connection removed;
        synchronized (this) {
            // Just in case the endpoint was still connecting
            removeConnecting(p);

            // And the regular management
            removed = (Connection) endpointConnections.remove(p);
            if (removed != null) {
                connections.remove(removed.getId());
            }

            log.log(Level.FINE, "Connections size:{0}", connections.size());
            log.log(Level.FINE, "Endpoint mappings size:{0}", endpointConnections.size());
        }

        // Better not to fire events while we hold a lock
        // so always do this outside the synch block.
        // Note: checking removed.closed just to avoid spurious log messages
        //       since in general we are called back for every endpoint closing.
        if (removed != null && !removed.closed) {

            log.log(Level.FINE, "Client closed:{0}.", removed);

            removed.closeConnection();
        }        
    }

    protected class Connection implements HostedConnection {

        private final int id;
        private boolean closed;
        private Endpoint[] channels;
        private int setChannelCount = 0;

        private final Map<String, Object> sessionData = new ConcurrentHashMap<>();

        public Connection(int channelCount) {
            id = nextId.getAndIncrement();
            channels = new Endpoint[channelCount];
        }

        boolean hasEndpoint(Endpoint p) {
            for (Endpoint e : channels) {
                if (p == e) {
                    return true;
                }
            }
            return false;
        }

        void setChannel(int channel, Endpoint p) {
            if (channels[channel] != null && channels[channel] != p) {
                throw new RuntimeException("Channel has already been set:" + channel
                        + " = " + channels[channel] + ", cannot be set to:" + p);
            }
            channels[channel] = p;
            if (p != null) {
                setChannelCount++;
            }
        }

        boolean isComplete() {
            return setChannelCount == channels.length;
        }

        @Override
        public Server getServer() {
            return UdpServer.this;
        }

        @Override
        public int getId() {
            return id;
        }

        @Override
        public String getAddress() {
            return channels[CH_UNRELIABLE] == null ? null : channels[CH_UNRELIABLE].getAddress();
        }

        @Override
        public void send(Message message) {
            if (log.isLoggable(Level.FINER)) {
                log.log(Level.FINER, "send({0})", message);
            }

            ByteBuffer buffer = MessageProtocol.messageToBuffer(message, null);
            channels[CH_UNRELIABLE].send(buffer);
        }

        @Override
        public void send(int channel, Message message) {
            throw new UnsupportedOperationException("Alternate channels are not supported");
//            if (log.isLoggable(Level.FINER)) {
//                log.log(Level.FINER, "send({0}, {1})", new Object[]{channel, message});
//            }
//            checkChannel(channel);
//            ByteBuffer buffer = MessageProtocol.messageToBuffer(message, null);
//            channels[channel + CH_FIRST].send(buffer);
        }

        protected void closeConnection() {
            if (closed) {
                return;
            }
            closed = true;

            // Make sure all endpoints are closed. 
            for (Endpoint p : channels) {
                if (p == null || !p.isConnected()) {
                    continue;
                }
                p.close();
            }

            fireConnectionRemoved(this);
        }

        @Override
        public void close(String reason) {
            // Send a reason
            DisconnectMessage m = new DisconnectMessage();
            m.setType(DisconnectMessage.KICK);
            m.setReason(reason);
            m.setReliable(true);

            forceQueues.get(this).clear(); // TODO: Not sure if this is good
            forceQueues.get(this).add(m);

            // fast will be cleaned up as a side-effect
            // when closeConnection() is called by the
            // connectionClosed() endpoint callback.
        }

        @Override
        public Object setAttribute(String name, Object value) {
            if (value == null) {
                return sessionData.remove(name);
            }
            return sessionData.put(name, value);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getAttribute(String name) {
            return (T) sessionData.get(name);
        }

        @Override
        public Set<String> attributeNames() {
            return Collections.unmodifiableSet(sessionData.keySet());
        }

        @Override
        public String toString() {
            return "Connection[ id=" + id
                    + ", fast=" + channels[CH_UNRELIABLE] + " ]";
        }
    }

    protected class Redispatch implements MessageListener<HostedConnection> {

        @Override
        public void messageReceived(HostedConnection source, Message m) {
            lastReceivals.put(source, System.currentTimeMillis());

            if (m instanceof ConnectionMessageContainer) {
                System.out.println("Received message");
                ConnectionMessageContainer c = (ConnectionMessageContainer) m;
                Integer orderNumber = orderNums.get(source);

                if (orderNumber == null) {
                    // TODO: Throw error or handle null
                    return;
                }
                if (c.getOrderNum() <= orderNumber) {
                    return;
                }

                if (c.confirms()) {
                    forceQueues.get(source).remove();
                    int orderNumCounter = orderNumCounters.get(source);
                    orderNumCounters.put(source, orderNumCounter + 1);
                }

                orderNums.put(source, c.getOrderNum());

                dispatch(source, c.getMessage());
            } else {
                dispatch(source, m);
            }
        }
    }

    protected class FilterAdapter implements Filter<Endpoint> {

        private final Filter<? super HostedConnection> delegate;

        public FilterAdapter(Filter<? super HostedConnection> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean apply(Endpoint input) {
            HostedConnection conn = getConnection(input);
            if (conn == null) {
                return false;
            }
            return delegate.apply(conn);
        }
    }
}
