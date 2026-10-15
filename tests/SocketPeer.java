package tests;

import common.config.GameConfig;
import common.protocol.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;

public class SocketPeer implements AutoCloseable {
    public final Socket socket;
    private final BufferedReader reader;
    private final List<Message> inbox=new ArrayList<>();
    private final ScheduledExecutorService heartbeat=Executors.newSingleThreadScheduledExecutor();
    public int userId;
    public SocketPeer(int port,boolean alive)throws Exception{
        socket=new Socket("127.0.0.1",port);socket.setSoTimeout(30000);reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
        Thread t=new Thread(()->{try{String line;while((line=reader.readLine())!=null){Message m=Message.fromJson(line);synchronized(inbox){inbox.add(m);inbox.notifyAll();}}}catch(Exception ignored){}},"test-reader");t.setDaemon(true);t.start();
        if(alive)heartbeat.scheduleAtFixedRate(()->{try{send(new Message(MessageType.HEARTBEAT_REQ).requestId(UUID.randomUUID().toString()));}catch(Exception ignored){}},200,200,TimeUnit.MILLISECONDS);
    }
    public synchronized void raw(String data)throws Exception{socket.getOutputStream().write(data.getBytes(StandardCharsets.UTF_8));socket.getOutputStream().flush();}
    public void send(Message m)throws Exception{raw(m.toJson()+"\n");}
    public Message request(Message m)throws Exception{
        if(m.getRequestId()==null)m.requestId(UUID.randomUUID().toString());send(m);return waitFor(x->m.getRequestId().equals(x.getRequestId()),5000);
    }
    public Message waitType(MessageType type)throws Exception{return waitFor(x->x.getType()==type && x.getRequestId()==null,5000);}
    public Message waitFor(Predicate<Message> predicate,long timeout)throws Exception{
        long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeout);
        synchronized(inbox){while(true){for(Iterator<Message> it=inbox.iterator();it.hasNext();){Message m=it.next();if(predicate.test(m)){it.remove();return m;}}
            long remaining=end-System.nanoTime();if(remaining<=0)throw new AssertionError("Timed out awaiting message; inbox="+inbox);TimeUnit.NANOSECONDS.timedWait(inbox,remaining);}}
    }
    public void login(String name)throws Exception{Message m=request(new Message(MessageType.LOGIN_REQ).put("username",name).put("password","secret"));TestRunner.check("SUCCESS".equals(m.getStatus()),"login "+name+": "+m);userId=JsonUtil.requireInt(m.getData(),"user_id");}
    public void clear(){synchronized(inbox){inbox.clear();}}
    public void close()throws Exception{heartbeat.shutdownNow();socket.close();}
}
