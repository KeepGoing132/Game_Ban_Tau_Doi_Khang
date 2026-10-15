package client.network;

import common.config.GameConfig;
import common.protocol.*;
import java.io.*;
import java.net.*;
import java.util.UUID;
import java.util.concurrent.*;
import javax.swing.SwingUtilities;

public class SocketClient {
    private final ServerListener listener;
    private volatile Session current;
    private long generation;
    private static final class Session {
        final long generation;final LineConnection wire;final ScheduledExecutorService heartbeat;
        Session(long generation,LineConnection wire){this.generation=generation;this.wire=wire;heartbeat=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"client-heartbeat");t.setDaemon(true);return t;});}
        void close(){heartbeat.shutdownNow();wire.close();}
    }
    public SocketClient(ServerListener listener){this.listener=listener;}
    public boolean connect(String host,int port){
        final long token;final Session previous;
        synchronized(this){token=++generation;previous=current;current=null;}
        if(previous!=null)previous.close();
        Socket socket=new Socket();
        try {
            socket.connect(new InetSocketAddress(host,port),5000);
            Session session=new Session(token,new LineConnection(socket));
            synchronized(this){if(generation!=token){session.close();return false;}current=session;}
            dispatch(session,()->listener.onConnected());
            session.heartbeat.scheduleAtFixedRate(()->session.wire.send(new Message(MessageType.HEARTBEAT_REQ).requestId(UUID.randomUUID().toString())),GameConfig.HEARTBEAT_INTERVAL_MS,GameConfig.HEARTBEAT_INTERVAL_MS,TimeUnit.MILLISECONDS);
            Thread reader=new Thread(()->listen(session),"client-reader-"+token);reader.setDaemon(true);reader.start();return true;
        }catch(IOException e){try{socket.close();}catch(IOException ignored){}return false;}
    }
    private void dispatch(Session s,Runnable action){SwingUtilities.invokeLater(()->{if(current==s && generation==s.generation)action.run();});}
    private void listen(Session session){
        try {String line;while((line=session.wire.readLine())!=null){Message m=Message.fromJson(line);if(m==null)continue;session.wire.touch();if(m.getType()!=MessageType.HEARTBEAT_RESP)dispatch(session,()->listener.onMessageReceived(m));}}
        catch(IOException | IllegalArgumentException e){}finally{lost(session,"Mất kết nối với máy chủ");}
    }
    private void lost(Session session,String reason){
        session.close();final long token;
        synchronized(this){if(current!=session)return;current=null;token=generation;}
        SwingUtilities.invokeLater(()->{if(generation==token && current==null)listener.onDisconnected(reason);});
    }
    public void send(Message message){Session s=current;if(s==null || message==null)return;if(message.getRequestId()==null)message.requestId(UUID.randomUUID().toString());s.wire.send(message);}
    public void disconnect(String reason){
        final Session session; synchronized(this){++generation;session=current;current=null;}
        if(session!=null){Thread close=new Thread(session::close,"client-close");close.setDaemon(true);close.start();}
    }
    public boolean isConnected(){Session s=current;return s!=null && s.wire.isOpen();}
}
