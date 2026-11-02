package tests;

import client.controller.ClientController;
import client.ui.*;
import common.model.*;
import server.bot.BotBrain;
import server.network.GameServer;
import javax.swing.*;
import javax.imageio.*;
import javax.imageio.metadata.*;
import javax.imageio.stream.ImageOutputStream;
import javax.sound.sampled.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class VisualFeatureTests {
    private static void check(boolean value,String text){TestRunner.check(value,text);}
    private static Object field(Object o,String name)throws Exception{return SwingSmoke.field(o,name);}
    private static JPasswordField password(Container root){for(Component c:root.getComponents()){if(c instanceof JPasswordField)return (JPasswordField)c;if(c instanceof Container){JPasswordField f=password((Container)c);if(f!=null)return f;}}return null;}
    public static void run()throws Exception {
        Path dir=DomainTests.empty("battleship-visual-features-");GameServer server=new GameServer(0,dir);Thread thread=PenaltyTests.start(server);
        ClientController client=new ClientController();client.start();
        try{
            SwingSmoke.waitFor(()->field(client,"loginFrame")!=null,"login ready");SwingSmoke.login(client,server.getPort(),"visual");
            SwingSmoke.edt(()->SwingUtilities.invokeLater(()->{try{SwingSmoke.clickField(field(client,"lobbyFrame"),"btnBot");}catch(Exception e){throw new RuntimeException(e);}}));
            SwingSmoke.waitFor(()->{for(Window w:Window.getWindows())if(w instanceof JDialog&&w.isShowing()&&"Đấu máy".equals(((JDialog)w).getTitle())){SwingSmoke.snapshot(w,dir.resolve("bot-levels.png"));SwingSmoke.button((Container)w,"Khó").doClick(0);return true;}return false;},"difficulty choice");
            SwingSmoke.waitFor(()->field(client,"placementPanel")!=null,"bot starts placement");SwingSmoke.arrange(client);
            SwingSmoke.edt(()->SwingSmoke.clickField(field(client,"placementPanel"),"btnReady"));SwingSmoke.waitFor(()->field(client,"battlePanel")!=null,"bot battle starts");
            SwingSmoke.edt(()->{BattlePanel battle=(BattlePanel)field(client,"battlePanel");check((Boolean)field(battle,"practice"),"practice mode reaches battle confirmation");SwingSmoke.snapshot((Component)field(client,"gameWindow"),dir.resolve("bot-battle.png"));client.onSurrender();});
            SwingSmoke.waitFor(()->field(client,"gameWindow")==null,"practice exits");check(server.getStorage().findByUsername("visual").getMatchmakingBlockedUntil()==0,"practice exit no ban");
            SwingSmoke.edt(()->SwingUtilities.invokeLater(()->{try{SwingSmoke.clickField(field(client,"lobbyFrame"),"btnAccount");}catch(Exception e){throw new RuntimeException(e);}}));
            SwingSmoke.waitFor(()->{for(Window w:Window.getWindows())if(w instanceof JDialog&&w.isShowing()&&"Tài khoản".equals(((JDialog)w).getTitle())){SwingSmoke.button((Container)w,"Xóa tài khoản").doClick(0);return true;}return false;},"account menu");
            SwingSmoke.waitFor(()->{for(Window w:Window.getWindows())if(w instanceof JDialog&&w.isShowing()&&"Xóa tài khoản".equals(((JDialog)w).getTitle())){password((Container)w).setText("secret");SwingSmoke.snapshot(w,dir.resolve("delete-account.png"));JButton confirm=SwingSmoke.button((Container)w,"Xóa tài khoản");if(confirm==null)confirm=SwingSmoke.button((Container)w,"OK");check(confirm!=null,"delete confirmation button");confirm.doClick(0);return true;}return false;},"account confirmation");
            SwingSmoke.waitFor(()->field(client,"currentUser")==null&&((JFrame)field(client,"loginFrame")).isVisible(),"deleted user returns to login");check(server.getStorage().findByUsername("visual")==null,"UI deletion persisted");
        }finally{server.stop();thread.join(5000);SwingSmoke.edt(()->{for(Window w:Window.getWindows())w.dispose();});}
        animation(dir);audio(dir);System.out.println("PASS visual features: bot picker/play/exit, account deletion UI, map focus/minimap, animation and audio samples");System.out.println("Feature screenshots: "+dir);
    }
    private static void animation(Path dir)throws Exception {
        AtomicReference<JFrame> frame=new AtomicReference<>();AtomicReference<BattlePanel> panel=new AtomicReference<>();
        SwingSmoke.edt(()->{
            JFrame window=new JFrame("Hải chiến • Hiệu ứng");BattlePanel battle=new BattlePanel("Bạn","Đối thủ",BotBrain.fleet(new Random(12)),new BattlePanel.BattleActionListener(){public void onFire(int x,int y){}public void onSurrender(){}public void onRematch(){}public void onExitToLobby(){}});
            window.setContentPane(battle);window.setSize(1360,820);window.setLocationRelativeTo(null);window.setVisible(true);frame.set(window);panel.set(battle);battle.setTurn(true,15);
        });
        BattlePanel battle=panel.get();boolean original=((JCheckBox)field(battle,"miniToggle")).isSelected();
        try{
            SwingSmoke.edt(()->{
                check(((Component)field(battle,"oppBoardGrid")).getParent()==field(battle,"mainBoardHost"),"your turn expands enemy board");
                battle.setTurn(false,15);check(((Component)field(battle,"myBoardGrid")).getParent()==field(battle,"mainBoardHost"),"enemy turn expands own board");
                SwingSmoke.snapshot(frame.get(),dir.resolve("defense.png"));JCheckBox toggle=(JCheckBox)field(battle,"miniToggle");if(toggle.isSelected())toggle.doClick();
                check(!((Component)field(battle,"miniBoardHost")).isVisible(),"minimap hides");battle.setTurn(true,15);SwingSmoke.snapshot(frame.get(),dir.resolve("no-minimap.png"));toggle.doClick();
                battle.handleFireResult(true,new Coordinate(4,3),ShotResult.SUNK,Arrays.asList(new Coordinate(2,3),new Coordinate(3,3),new Coordinate(4,3)),4,true);battle.setTurn(true,15);
                check(!((JButton)field(battle,"btnFire")).isEnabled(),"effect does not enable duplicate shots");
            });
            java.util.List<BufferedImage> frames=new ArrayList<>();
            for(int i=0;i<22;i++) {
                AtomicReference<BufferedImage> image=new AtomicReference<>();
                SwingSmoke.edt(()->{JFrame window=frame.get();BufferedImage img=new BufferedImage(window.getWidth(),window.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=img.createGraphics();window.paintAll(g);g.dispose();image.set(img);});
                frames.add(image.get());Thread.sleep(50);
            }
            ImageIO.write(frames.get(3),"png",dir.resolve("bomb-flight.png").toFile());ImageIO.write(frames.get(10),"png",dir.resolve("ship-sunk.png").toFile());
            ImageWriter writer=ImageIO.getImageWritersByFormatName("gif").next();
            try(ImageOutputStream output=ImageIO.createImageOutputStream(dir.resolve("bomb-animation.gif").toFile())) {
                writer.setOutput(output);writer.prepareWriteSequence(null);int index=0;
                for(BufferedImage img:frames) {
                    IIOMetadata metadata=writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(img),writer.getDefaultWriteParam());String format=metadata.getNativeMetadataFormatName();IIOMetadataNode root=(IIOMetadataNode)metadata.getAsTree(format),control=(IIOMetadataNode)root.getElementsByTagName("GraphicControlExtension").item(0);control.setAttribute("delayTime","6");control.setAttribute("disposalMethod","none");
                    if(index++==0){IIOMetadataNode extensions=new IIOMetadataNode("ApplicationExtensions"),loop=new IIOMetadataNode("ApplicationExtension");loop.setAttribute("applicationID","NETSCAPE");loop.setAttribute("authenticationCode","2.0");loop.setUserObject(new byte[]{1,0,0});extensions.appendChild(loop);root.appendChild(extensions);}
                    metadata.setFromTree(format,root);writer.writeToSequence(new IIOImage(img,null,metadata),null);
                }writer.endWriteSequence();
            }finally{writer.dispose();}
            SwingSmoke.waitFor(()->!battle.isAnimating(),"animation terminates");
        }finally{SwingSmoke.edt(()->{JCheckBox toggle=(JCheckBox)field(battle,"miniToggle");if(toggle.isSelected()!=original)toggle.doClick();battle.disposePanel();frame.get().dispose();check(!((javax.swing.Timer)field(battle,"effectTimer")).isRunning(),"animation timer disposed");});}
    }
    private static void audio(Path dir)throws Exception {
        Path audio=Files.createDirectories(dir.resolve("audio"));Set<Integer> distinct=new HashSet<>();
        for(GameAudio.Sound sound:GameAudio.Sound.values()) {byte[] pcm=GameAudio.synthesize(sound);distinct.add(Arrays.hashCode(pcm));check(pcm.length>0,"audio contains PCM");try(AudioInputStream stream=new AudioInputStream(new ByteArrayInputStream(pcm),GameAudio.format(),pcm.length/2)){AudioSystem.write(stream,AudioFileFormat.Type.WAVE,audio.resolve(sound.name().toLowerCase(Locale.ROOT)+".wav").toFile());}}
        check(distinct.size()==GameAudio.Sound.values().length,"different game events produce distinct sounds");
    }
}
