package server.network;

import common.config.GameConfig;
import server.manager.*;
import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class GameServer {
    private final int port;
    private volatile ServerSocket listener;
    private volatile boolean running;
    private final AtomicBoolean stopped=new AtomicBoolean();
    private final ExecutorService workers=Executors.newFixedThreadPool(GameConfig.MAX_CONNECTIONS);
    private final Set<ClientHandler> connections=ConcurrentHashMap.newKeySet();
    private final StorageManager storage;
    private final UserManager users;
    private final MatchManager matches;
    private final LobbyManager lobby;
    public GameServer(int port){this(port,new StorageManager());}
    public GameServer(int port,Path directory){this(port,new StorageManager(directory));}
    public GameServer(int port,StorageManager storage) {
        this.port=port;this.storage=storage;users=new UserManager(storage);matches=new MatchManager(users,storage);lobby=new LobbyManager(users,matches);
    }
    public void start() {
        try {
            listener=new ServerSocket(port);running=true;
            System.out.println("Battleship TCP listening on " + listener.getLocalPort());
            while(running && !stopped.get()) {
                Socket socket=listener.accept();
                if(connections.size()>=GameConfig.MAX_CONNECTIONS){socket.close();continue;}
                final ClientHandler[] holder=new ClientHandler[1];
                ClientHandler handler=new ClientHandler(socket,users,lobby,matches,()->connections.remove(holder[0]));holder[0]=handler;
                connections.add(handler);
                try {workers.execute(handler);}catch(RejectedExecutionException e){handler.close();}
            }
        } catch(IOException e) {if(!stopped.get())throw new IllegalStateException("Cannot listen on port " + port,e);}
        finally{stop();}
    }
    public int getPort(){ServerSocket s=listener;return s==null?-1:s.getLocalPort();}
    public int getConnectionCount(){return connections.size();}
    public StorageManager getStorage(){return storage;}
    public void stop() {
        if(!stopped.compareAndSet(false,true))return;
        running=false;
        try{if(listener!=null)listener.close();}catch(IOException ignored){}
        // Server shutdown cancels sessions before socket cleanup to avoid awarding shutdown wins.
        matches.shutdown();lobby.shutdown();
        storage.retryPending();
        if (storage.getPendingCount() > 0)
            System.err.println("[Shutdown] Some results still await storage. Preserve data/pending-results with the database backup.");
        for(ClientHandler handler:connections)handler.close();
        workers.shutdownNow();
        try{workers.awaitTermination(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
}
