/*
 * JS-Collider framework tests.
 * Copyright (C) 2013 Sergey Zubarev
 * info@js-labs.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.jsl.tests.recv_throughput;

import org.jsl.tests.Util;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class Client
{
    private InetAddress m_addr;
    private int m_portNumber;
    private final int m_messages;
    private final int m_messageLength;
    private final byte [] m_messageBlock;
    private Thread [] m_threads;

    private class SessionThread extends Thread
    {
        public void run()
        {
            try
            {
                Socket socket = new Socket( "localhost", m_portNumber );
                socket.setTcpNoDelay( true );

                System.out.println( "Client socket connected " + socket.getRemoteSocketAddress() + "." );
                int messagesRest = m_messages;
                int blockMessages = (m_messageBlock.length / m_messageLength);
                long startTime = System.nanoTime();

                while (messagesRest > blockMessages)
                {
                    socket.getOutputStream().write( m_messageBlock );
                    messagesRest -= blockMessages;
                }
                socket.getOutputStream().write( m_messageBlock, 0, messagesRest*m_messageLength );
                long endTime = System.nanoTime();
                socket.close();

                double tm = ((endTime - startTime) / 1000);
                tm /= 1000000;
                tm = (m_messages / tm);
                System.out.println( "Sent " + m_messages + " messages (" +
                        m_messages*m_messageLength + " bytes) at " +
                        Util.formatDelay(startTime, endTime) + " sec (" +
                        (int)tm + " msgs/sec)." );
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }
    }

    public Client( int sessions, int messages, int messageLength )
    {
        if (messageLength < 12)
            messageLength = 12;

        m_messages = messages;
        m_messageLength = messageLength;

        int blockSize = (messages * messageLength);
        if (blockSize > 1024*1024)
            blockSize = 1024*1024;

        int blockMessages = (blockSize / messageLength);
        blockSize = (blockMessages * messageLength);

        m_messageBlock = new byte[blockSize];
        ByteBuffer byteBuffer = ByteBuffer.wrap( m_messageBlock );
        for (int idx=0; idx<blockMessages; idx++)
        {
            byteBuffer.putInt( messageLength );
            byteBuffer.putInt( messages );
            byteBuffer.putInt( sessions );
            int cc = (messageLength - 12);
            for (; cc>0; cc--)
                byteBuffer.put( (byte) cc );
        }

        m_threads = new Thread[sessions];
        for (int idx=0; idx<sessions; idx++)
            m_threads[idx] = new SessionThread();
    }

    public void start( InetAddress addr, int portNumber )
    {
        m_addr = addr;
        m_portNumber = portNumber;
        for (Thread thread : m_threads)
            thread.start();
    }

    public void stopAndWait()
    {
        for (Thread thread : m_threads)
        {
            try
            {
                thread.join();
            }
            catch (InterruptedException ex)
            {
                ex.printStackTrace();
            }
        }
    }

    public static void main( String [] args )
    {
        if (args.length != 5)
        {
            System.out.println( "Usage: <server_addr> <server_port> <sessions> <messages> <message_length>" );
            return;
        }

        try
        {
            InetAddress addr = InetAddress.getByName( args[0] );
            int portNumber = Integer.parseInt( args[1] );
            int sessions = Integer.parseInt( args[2] );
            int messages = Integer.parseInt( args[3] );
            int messageLength = Integer.parseInt( args[4] );

            new Client(sessions, messages, messageLength).start( addr, portNumber );
        }
        catch (UnknownHostException ex)
        {
            ex.printStackTrace();
        }
    }
}
