/*
 * Copyright (C) 2015 XiNGRZ <chenxingyu92@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package me.xingrz.prox.udp;

import android.util.Log;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;

import me.xingrz.prox.AbstractTransportProxy;

public class UdpProxySession extends AbstractTransportProxy.Session implements Runnable {

    private static final String TAG = "UdpProxySession";

    private final UdpProxy udpProxy;

    private final Selector selector;
    private final DatagramChannel serverChannel;

    private final ByteBuffer buffer = ByteBuffer.allocate(0xFFFF);

    private final Thread thread;

    public UdpProxySession(UdpProxy udpProxy, int sourcePort,
                           InetAddress remoteAddress, int remotePort) throws IOException {
        super(sourcePort, remoteAddress, remotePort);

        this.udpProxy = udpProxy;

        selector = Selector.open();

        serverChannel = DatagramChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.socket().bind(new InetSocketAddress(0));
        serverChannel.register(selector, SelectionKey.OP_READ);

        thread = new Thread(this, "UDPSession/" + sourcePort);
        thread.start();
    }

    public DatagramSocket socket() {
        return serverChannel.socket();
    }

    public void send(ByteBuffer buffer) throws IOException {
        serverChannel.send(buffer, new InetSocketAddress(getRemoteAddress(), getRemotePort()));
    }

    @Override
    public void close() {
        thread.interrupt();
        IOUtils.closeQuietly(selector);
        IOUtils.closeQuietly(serverChannel);
    }

    @Override
    public synchronized void run() {
        try {
            while (true) {
                selector.select();
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    if (key.isReadable()) {
                        onReadable((DatagramChannel) key.channel());
                    }
                }
            }
        } catch (ClosedSelectorException e) {
            Log.v(TAG, "UDP session local:" + getSourcePort() + " closed");
        } catch (IOException e) {
            Log.w(TAG, "UDP session local:" + getSourcePort() + " error", e);
        }
    }

    private void onReadable(DatagramChannel channel) throws IOException {
        buffer.clear();
        channel.receive(buffer);

        finish();

        buffer.flip();
        udpProxy.feedback(buffer, this);
    }

}
