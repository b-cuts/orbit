/*
 Copyright (C) 2015 Electronic Arts Inc.  All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 1.  Redistributions of source code must retain the above copyright
     notice, this list of conditions and the following disclaimer.
 2.  Redistributions in binary form must reproduce the above copyright
     notice, this list of conditions and the following disclaimer in the
     documentation and/or other materials provided with the distribution.
 3.  Neither the name of Electronic Arts, Inc. ("EA") nor the names of
     its contributors may be used to endorse or promote products derived
     from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY ELECTRONIC ARTS AND ITS CONTRIBUTORS "AS IS" AND ANY
 EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL ELECTRONIC ARTS OR ITS CONTRIBUTORS BE LIABLE FOR ANY
 DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.ea.orbit.actors.server;

import com.ea.orbit.actors.ActorObserver;
import com.ea.orbit.actors.Stage;
import com.ea.orbit.actors.cluster.NodeAddress;
import com.ea.orbit.actors.net.HandlerAdapter;
import com.ea.orbit.actors.net.HandlerContext;
import com.ea.orbit.actors.net.Pipeline;
import com.ea.orbit.actors.peer.Peer;
import com.ea.orbit.actors.runtime.BasicRuntime;
import com.ea.orbit.actors.runtime.DefaultDescriptorFactory;
import com.ea.orbit.actors.runtime.DefaultHandlers;
import com.ea.orbit.actors.runtime.Messaging;
import com.ea.orbit.actors.runtime.SerializationHandler;
import com.ea.orbit.actors.server.streams.ServerSideStreamProxyImpl;
import com.ea.orbit.concurrent.Task;
import com.ea.orbit.container.Startable;

public class ServerPeer extends Peer implements Startable, BasicRuntime
{
    private Stage stage;

    public ServerPeer()
    {

    }

    public Stage getStage()
    {
        return stage;
    }

    public void setStage(final Stage stage)
    {
        this.stage = stage;
    }

    public Task<?> start()
    {
        final Pipeline pipeline = getPipeline();

        final ServerSideStreamProxyImpl streamProxy = new ServerSideStreamProxyImpl();
        streamProxy.setStage(stage);
        streamProxy.setPeer(this);
        streamProxy.start();

        ServerExtension serverExtension = (ServerExtension) stage.getExtensions().stream()
                .filter(e -> e instanceof ServerExtension).findFirst().orElse(null);
        ServerPeerExecutor executor = new ServerPeerExecutor(stage);
        executor.setObjects(objects);
        pipeline.addLast(DefaultHandlers.EXECUTION, executor);
        pipeline.addLast(DefaultHandlers.MESSAGING, new Messaging());
        pipeline.addLast(DefaultHandlers.SERIALIZATION, new SerializationHandler(this, getMessageSerializer()));
        pipeline.addLast("serverNotification", new HandlerAdapter()
        {
            @Override
            public void onInactive(final HandlerContext ctx) throws Exception
            {
                if (serverExtension != null)
                {
                    serverExtension.connectionClosed(ServerPeer.this);
                }
                super.onInactive(ctx);
            }

            @Override
            public void onActive(final HandlerContext ctx) throws Exception
            {
                if (serverExtension != null)
                {
                    serverExtension.connectionOpened(ServerPeer.this);
                }
                super.onActive(ctx);
            }
        });
        pipeline.addLast(DefaultHandlers.NETWORK, getNetwork());
        installPipelineExtensions();
        return Task.done();
    }


    @Override
    public <T extends ActorObserver> T getRemoteObserverReference(final NodeAddress address, final Class<T> iClass, final Object id)
    {
        return getReference(address, iClass, id);
    }

    @Override
    public <T> T getReference(final BasicRuntime runtime, final NodeAddress address, final Class<T> iClass, final Object id)
    {
        if (address != null)
        {
            return stage.getReference(address, iClass, id);
        }
        else
        {
            return DefaultDescriptorFactory.get().getReference(this, address, iClass, id);
        }
    }

    @Override
    public Task<?> stop()
    {
        // todo implement this.
        return super.stop();
    }

    @Override
    public String toString()
    {
        return "ServerPeer{localIdentity=" + localIdentity + ", stage=" + stage + "}";
    }
}
