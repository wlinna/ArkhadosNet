/*
 * Copyright (c) 2009-2011 William Linna
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
 * * Neither the name of 'Arkhados' nor the names of its contributors
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
package arkhados.net;

import com.jme3.network.Client;
import com.jme3.network.HostedConnection;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author william
 */
public class ClientSender extends Sender {

    private List<OtmIdCommandListPair> unconfirmedGuaranteed = new ArrayList<>();
    private List<Command> enqueuedGuaranteed = new ArrayList<>();
    private List<Command> enqueuedUnreliables = new ArrayList<>();
    private Client client;

    @Override
    public void addCommand(Command command) {
        if (client == null || !client.isConnected()) {
            return;
        }

        if (command.isGuaranteed()) {
            enqueuedGuaranteed.add(command);
        } else {
            enqueuedUnreliables.add(command);
        }

        setShouldSend(true);
    }

    @Override
    public void sendMessage() {
        if (!client.isConnected()) {
            return;
        }

        OneTrueMessage otm = createOneTrueMessage(null);
        client.send(otm);
    }

    @Override
    public boolean isClient() {
        return true;
    }

    @Override
    public boolean isServer() {
        return false;
    }

    @Override
    protected List<OtmIdCommandListPair> getGuaranteedForSource(Object source) {
        return unconfirmedGuaranteed;
    }

    @Override
    protected List<Command> getEnqueuedGuaranteedForSource(HostedConnection connection) {
        return enqueuedGuaranteed;
    }

    @Override
    protected List<Command> getEnqueuedUnreliablesForSource(HostedConnection connection) {
        return enqueuedUnreliables;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    @Override
    public void reset() {
        unconfirmedGuaranteed.clear();
        enqueuedGuaranteed.clear();
        enqueuedUnreliables.clear();
    }
}