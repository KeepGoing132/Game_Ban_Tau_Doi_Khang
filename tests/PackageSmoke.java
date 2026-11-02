package tests;

import common.protocol.*;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarFile;

/** Runs the shipped server JAR from another directory and the client from its isolated JAR. */
public final class PackageSmoke {
    public static void run(Path distribution) throws Exception {
        Path serverJar=distribution.resolve("battleship-server.jar").toAbsolutePath();
        Path clientJar=distribution.resolve("battleship-client.jar").toAbsolutePath();
        Path data=DomainTests.empty("battleship-package-");
        try (JarFile jar=new JarFile(clientJar.toFile())) {
            TestRunner.check(jar.getEntry("client/ui/PasswordDialog.class")!=null && jar.getEntry("client/model/FleetPresets.class")!=null,"new client classes packaged");
            TestRunner.check(!jar.stream().anyMatch(e->e.getName().startsWith("data/")),"client JAR contains no user data");
        }
        Process server=new ProcessBuilder(Paths.get(System.getProperty("java.home"),"bin","java").toString(),
                "-Dfile.encoding=UTF-8","-Dbattleship.data_dir="+data,"-jar",serverJar.toString(),"0")
                .directory(data.toFile()).redirectErrorStream(true).start();
        CountDownLatch ready=new CountDownLatch(1); AtomicInteger port=new AtomicInteger();
        Thread reader=new Thread(()->{
            try (BufferedReader input=new BufferedReader(new InputStreamReader(server.getInputStream()))) {
                String line; while ((line=input.readLine())!=null) if (line.startsWith("Battleship TCP listening on ")) {
                    port.set(Integer.parseInt(line.substring(line.lastIndexOf(' ')+1))); ready.countDown();
                }
            } catch (Exception ignored) { ready.countDown(); }
        },"package-server-output"); reader.setDaemon(true); reader.start();
        try {
            TestRunner.check(ready.await(10,TimeUnit.SECONDS) && port.get()>0,"server JAR starts from a different working directory with adjacent lib");
            try (SocketPeer a=new SocketPeer(port.get(),true); SocketPeer b=new SocketPeer(port.get(),true)) {
                a.login("package_a"); b.login("package_b");
                String id=IntegrationTests.queuePair(a,b); Message start=IntegrationTests.start(a,b,id);
                SocketPeer shooter=JsonUtil.requireInt(start.getData(),"current_turn_player_id")==a.userId?a:b;
                IntegrationTests.ok(shooter,IntegrationTests.match(MessageType.FIRE_REQ,id).put("turn_id",JsonUtil.requireInt(start.getData(),"turn_id")).put("x",0).put("y",0));
                IntegrationTests.ok(a,IntegrationTests.match(MessageType.SURRENDER_REQ,id));
                TestRunner.check(!JsonUtil.getList(IntegrationTests.ok(b,new Message(MessageType.GET_MATCH_HISTORY_REQ)).getData(),"matches").isEmpty(),"packaged server persists results");
            }
            try (URLClassLoader loader=new URLClassLoader(new URL[]{clientJar.toUri().toURL()},null)) {
                Class<?> main=loader.loadClass("client.ClientMain"); main.getMethod("main",String[].class).invoke(null,(Object)new String[0]);
                final JFrame[] lobby={null};
                SwingSmoke.waitFor(()->{
                    for (Window w:Window.getWindows()) if (w.isShowing() && w.getClass().getClassLoader()==loader && w.getClass().getName().equals("client.ui.LoginFrame")) {
                        ((JTextField)SwingSmoke.field(w,"txtHost")).setText("127.0.0.1");
                        ((JTextField)SwingSmoke.field(w,"txtPort")).setText(String.valueOf(port.get()));
                        ((JTextField)SwingSmoke.field(w,"txtUsername")).setText("package_ui");
                        ((JPasswordField)SwingSmoke.field(w,"txtPassword")).setText("secret");
                        SwingSmoke.clickField(w,"btnLogin"); return true;
                    }
                    return false;
                },"packaged client login window");
                SwingSmoke.waitFor(()->{
                    for (Window w:Window.getWindows()) if (w.isShowing() && w.getClass().getClassLoader()==loader && w.getClass().getName().equals("client.ui.LobbyFrame")) { lobby[0]=(JFrame)w;return true; }
                    return false;
                },"packaged client connects to packaged server");
                SwingSmoke.edt(()->{
                    SwingSmoke.snapshot(lobby[0],data.resolve("packaged-lobby.png"));
                    Object controller=SwingSmoke.field(lobby[0],"controller"); controller.getClass().getMethod("logout").invoke(controller);
                    for (Window w:Window.getWindows()) if (w.getClass().getClassLoader()==loader) w.dispose();
                });
            }
            System.out.println("PASS packaged JARs: external working directory, SQLite library, two-player match/history, isolated client JAR login");
            System.out.println("Package test data: "+data);
        } finally {
            server.destroy(); if (!server.waitFor(5,TimeUnit.SECONDS)) server.destroyForcibly(); reader.join(1000);
            SwingSmoke.edt(()->{for(Window w:Window.getWindows())w.dispose();});
        }
    }
}
