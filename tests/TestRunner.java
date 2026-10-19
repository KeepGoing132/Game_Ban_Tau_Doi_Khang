package tests;

import common.protocol.*;

public class TestRunner {
    public static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void rejects(String json) {
        try { Message.fromJson(json); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Accepted malformed JSON: " + json);
    }
    public static void main(String[] args) throws Exception {
        if(args.length>0 && "fail".equals(args[0]))throw new AssertionError("Intentional runner failure probe");
        if(args.length>0 && "chat".equals(args[0])){ChatTests.run();return;}
        if(args.length>0 && "chatgui".equals(args[0])){try{ChatSwingTests.run();System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
        if(args.length>0 && "visual".equals(args[0])) {try{client.ui.Theme.install();VisualFeatureTests.run();System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
        if(args.length>0 && "ocean".equals(args[0])) {try{client.ui.Theme.install();OceanVisualTests.run();System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
        if(args.length>0 && "gui".equals(args[0])) {
            try { SwingSmoke.run(); VisualFeatureTests.run(); ChatSwingTests.run(); OceanVisualTests.run(); System.exit(0); }
            catch(Throwable e) {e.printStackTrace();System.exit(1);}
        }
        Message m = new Message(MessageType.HEARTBEAT_REQ).requestId("test").put("text", "Tiếng Việt\n\"escaped\"");
        check(Message.fromJson(m.toJson()).getData().equals(m.getData()), "Unicode roundtrip");
        try(java.net.ServerSocket listener=new java.net.ServerSocket(0);
            java.net.Socket sender=new java.net.Socket("127.0.0.1",listener.getLocalPort());
            java.net.Socket receiver=listener.accept();
            LineConnection transport=new LineConnection(receiver)) {
            byte[] frame=(m.toJson()+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            for(byte value:frame)sender.getOutputStream().write(value);
            sender.getOutputStream().flush();
            check(Message.fromJson(transport.readLine()).getData().equals(m.getData()),"Split UTF-8 exact payload over TCP");
        }
        rejects("{\"type\":\"LOGIN_REQ\"} {}");
        rejects("{\"type\":\"LOGIN_REQ\",\"data\":[]}");
        rejects("{\"type\":\"LOGIN_REQ\",\"a\":\"\\q\"}");
        rejects("{\"type\":\"LOGIN_REQ\",\"a\":01}");
        System.out.println("PASS protocol strict JSON + UTF-8");
        IntegrationTests.run();
        DomainTests.run();
        PenaltyTests.run();
        RaceTests.run();
        FeatureTests.run();
        ChatTests.run();
        long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
        while(hasServerThreads() && System.nanoTime()<end)Thread.sleep(10);
        check(!hasServerThreads(),"server-owned executors terminated");
        System.out.println("ALL TESTS PASSED");
    }
    private static boolean hasServerThreads() {
        for(Thread t:Thread.getAllStackTraces().keySet())if(t.isAlive() && !t.isDaemon() && t.getName().startsWith("pool-"))return true;
        return false;
    }
}
