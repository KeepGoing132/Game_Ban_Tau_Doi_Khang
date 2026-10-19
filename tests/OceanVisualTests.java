package tests;

import client.ui.*;
import common.model.*;
import server.bot.BotBrain;
import javax.imageio.*;
import javax.imageio.metadata.*;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Renders the sea-themed match screens and their entrance effect, and checks the animation
 * lifecycle: the effect must end by itself, never block combat, stop with the screen, and be
 * switchable from the UI.
 */
public final class OceanVisualTests {
    private static final List<BufferedImage> INTRO_FRAMES = new ArrayList<>();

    private static Object field(Object target,String name)throws Exception{return SwingSmoke.field(target,name);}
    private static void check(boolean value,String text){TestRunner.check(value,text);}
    private static JFrame window;
    private static BattlePanel battle;
    private static PlacementPanel placement;
    private static MatchHistoryDialog historyDialog;

    public static void run()throws Exception {
        Path dir=DomainTests.empty("battleship-ocean-");
        boolean animatedBefore=OceanScene.isAnimated();
        OceanScene.setAnimated(true);
        try {
            battleScreen(dir);
            placementScreen(dir);
            historyDialog(dir);
            animationLifecycle(dir);
        } finally {
            SwingSmoke.edt(()->{for(Window w:Window.getWindows())w.dispose();});
            OceanScene.setAnimated(animatedBefore);
        }
        check(OceanScene.isAnimated()==animatedBefore,"sea animation preference restored");
        System.out.println("PASS sea screens: entrance effect, glass boards, splash frames, animation toggle, lifecycle");
        System.out.println("Sea screenshots: "+dir);
    }

    // ------------------------------------------------------------------ combat screen
    private static void battleScreen(Path dir)throws Exception {
        List<Ship> myFleet=BotBrain.fleet(new Random(7));
        SwingSmoke.edt(()->{
            window=new JFrame("Battleship • gui_a vs gui_b");
            battle=new BattlePanel("gui_a","gui_b",myFleet,new BattlePanel.BattleActionListener(){
                public void onFire(int x,int y){}public void onSurrender(){}public void onRematch(){}public void onExitToLobby(){}});
            Theme.configureWindow(window,1440,940,1120,800);
            window.setContentPane(battle);window.setLocationRelativeTo(null);window.setVisible(true);
        });
        SwingSmoke.waitFor(battle::isIntroPlaying,"entrance effect starts on the combat screen");
        SwingSmoke.edt(()->{battle.setTurn(true,15);check(!battle.isAnimating(),"entrance effect never blocks firing");});
        for(int i=0;i<30;i++) { INTRO_FRAMES.add(grab(window)); Thread.sleep(100); }
        writeGif(INTRO_FRAMES,dir.resolve("sea-intro.gif"),10);
        for(int index:new int[]{1,4,8,12,16,22}) ImageIO.write(INTRO_FRAMES.get(index),"png",dir.resolve("sea-intro-"+(index+1)+".png").toFile());
        SwingSmoke.waitFor(()->!battle.isIntroPlaying(),"entrance effect ends by itself");
        Thread.sleep(400);
        SwingSmoke.edt(()->SwingSmoke.snapshot(window,dir.resolve("battle.png")));
        SwingSmoke.edt(()->SwingSmoke.snapshotMinimum(window,dir.resolve("battle-small.png")));
        SwingSmoke.edt(()->{
            BoardGridPanel mine=(BoardGridPanel)field(battle,"myBoardGrid"),theirs=(BoardGridPanel)field(battle,"oppBoardGrid");
            // a plausible half-played match on both boards
            List<Ship> fleet=BotBrain.fleet(new Random(7));
            for(Ship ship:fleet)for(Coordinate c:ship.getCoordinates())mine.setCellState(c.getX(),c.getY(),BoardGridPanel.CellState.SHIP);
            List<Coordinate> carrier=fleet.get(0).getCoordinates();
            mine.setCellState(carrier.get(0).getX(),carrier.get(0).getY(),BoardGridPanel.CellState.HIT);
            mine.setCellState(carrier.get(1).getX(),carrier.get(1).getY(),BoardGridPanel.CellState.HIT);
            mine.setCellState(9,9,BoardGridPanel.CellState.MISS);mine.setCellState(8,4,BoardGridPanel.CellState.MISS);
            theirs.setCellState(0,0,BoardGridPanel.CellState.MISS);theirs.setCellState(3,5,BoardGridPanel.CellState.MISS);theirs.setCellState(7,2,BoardGridPanel.CellState.MISS);
            theirs.setCellState(4,4,BoardGridPanel.CellState.HIT);theirs.setCellState(5,4,BoardGridPanel.CellState.HIT);
            theirs.setCellState(2,7,BoardGridPanel.CellState.SUNK);theirs.setCellState(3,7,BoardGridPanel.CellState.SUNK);
            theirs.setSelectedCoordinate(new Coordinate(6,6));
        });
        log(battle,
                "Đối thủ  •  C8  •  Đánh chìm",
                "Bạn  •  E5  •  Trúng tàu",
                "Bạn  •  D4  •  Trúng tàu",
                "Đối thủ  •  J10  •  Trượt",
                "Bạn  •  A1  •  Trượt");
        Thread.sleep(400);
        SwingSmoke.edt(()->SwingSmoke.snapshot(window,dir.resolve("battle-active.png")));
        SwingSmoke.edt(()->battle.setTurn(false,15));
        Thread.sleep(300);
        SwingSmoke.edt(()->SwingSmoke.snapshot(window,dir.resolve("defense.png")));
        SwingSmoke.edt(()->{JCheckBox toggle=(JCheckBox)field(battle,"miniToggle");if(toggle.isSelected())toggle.doClick();});
        Thread.sleep(200);
        SwingSmoke.edt(()->SwingSmoke.snapshot(window,dir.resolve("no-minimap.png")));
        SwingSmoke.edt(()->{JCheckBox toggle=(JCheckBox)field(battle,"miniToggle");toggle.doClick();battle.setTurn(true,15);});
        // shell flight and impact, sampled at exact effect times so the frames are reproducible
        SwingSmoke.edt(()->{battle.setTurn(true,15);battle.handleFireResult(true,new Coordinate(6,6),ShotResult.MISS,null,5,true);});
        List<BufferedImage> splash=new ArrayList<>();
        for(double moment:new double[]{0.06,0.16,0.28,0.38,0.46,0.56,0.64,0.72,0.80}) {
            SwingSmoke.edt(()->{rewindEffect(battle,moment);splash.add(grabNow(window));});
        }
        ImageIO.write(splash.get(2),"png",dir.resolve("bomb-flight.png").toFile());
        ImageIO.write(splash.get(6),"png",dir.resolve("battle-splash.png").toFile());
        writeGif(splash,dir.resolve("bomb-animation.gif"),9);
        SwingSmoke.waitFor(()->!battle.isAnimating(),"miss effect ends");
        SwingSmoke.edt(()->{battle.setTurn(true,15);battle.handleFireResult(true,new Coordinate(4,4),ShotResult.SUNK,
                Arrays.asList(new Coordinate(4,4),new Coordinate(5,4)),3,true);rewindEffect(battle,0.60);});
        Thread.sleep(120);
        SwingSmoke.edt(()->{rewindEffect(battle,0.74);ImageIO.write(grabNow(window),"png",dir.resolve("ship-sunk.png").toFile());});
        SwingSmoke.waitFor(()->!battle.isAnimating(),"sunk effect ends");
        // The server releases the enemy fleet with the end message: the report must show it.
        List<Ship> enemy=BotBrain.fleet(new Random(11));
        SwingSmoke.edt(()->battle.showGameOver(true,3,"ALL_SHIPS_DESTROYED",enemy));
        SwingSmoke.waitFor(()->field(battle,"resultDialog")!=null,"result dialog opens");
        Thread.sleep(500);
        SwingSmoke.edt(()->{
            @SuppressWarnings("unchecked") List<Ship> revealed=(List<Ship>)field(battle,"revealedFleet");
            check(revealed!=null && revealed.size()==5,"battle report keeps the released fleet");
            BoardGridPanel shown=(BoardGridPanel)field(battle,"oppBoardGrid");
            int shipCells=0;
            for(Ship ship:revealed)for(Coordinate c:ship.getCoordinates()){
                shipCells++;
                check(shown.getCellState(c.getX(),c.getY())!=BoardGridPanel.CellState.WATER,"released fleet is painted on the enemy board");
            }
            check(shipCells==17,"released fleet covers all 17 ship cells");
        });
        SwingSmoke.edt(()->{try{SwingSmoke.snapshot((Component)field(battle,"resultDialog"),dir.resolve("result-report.png"));}catch(Exception e){throw new RuntimeException(e);}});
        SwingSmoke.edt(()->{try{SwingSmoke.snapshot((Component)field(battle,"resultDialog"),dir.resolve("result.png"));}catch(Exception e){throw new RuntimeException(e);}});
        SwingSmoke.edt(()->battle.disposePanel());
        SwingSmoke.edt(()->{window.dispose();window=null;battle=null;});
    }

    // ------------------------------------------------------------------ deployment screen
    private static void placementScreen(Path dir)throws Exception {
        SwingSmoke.edt(()->{
            window=new JFrame("Battleship • Triển khai hạm đội");
            placement=new PlacementPanel(ships->{});
            placement.setMatchInfo("gui_b",()->{});
            Theme.configureWindow(window,1440,940,1120,800);
            window.setContentPane(placement);window.setLocationRelativeTo(null);window.setVisible(true);
        });
        SwingSmoke.waitFor(placement::isIntroPlaying,"entrance effect starts on the deployment screen");
        List<BufferedImage> frames=new ArrayList<>();
        for(int i=0;i<26;i++){frames.add(grab(window));Thread.sleep(100);}
        writeGif(frames,dir.resolve("sea-intro-placement.gif"),10);
        SwingSmoke.waitFor(()->!placement.isIntroPlaying(),"deployment entrance ends");
        SwingSmoke.edt(()->{placement.randomizePlacement();placement.showReadyState(true);});
        Thread.sleep(400);
        SwingSmoke.edt(()->SwingSmoke.snapshot(window,dir.resolve("placement.png")));
        SwingSmoke.edt(()->SwingSmoke.snapshotMinimum(window,dir.resolve("placement-small.png")));
        SwingSmoke.edt(()->{BoardGridPanel grid=(BoardGridPanel)field(placement,"gridPanel");
            grid.setCellState(2,4,BoardGridPanel.CellState.PREVIEW_VALID);grid.setCellState(3,4,BoardGridPanel.CellState.PREVIEW_VALID);});
        Thread.sleep(200);
        SwingSmoke.edt(()->SwingSmoke.snapshot(window,dir.resolve("placement-preview.png")));
        SwingSmoke.edt(()->{placement=null;window.dispose();window=null;});
    }

    // ------------------------------------------------------------------ match history
    private static void historyDialog(Path dir)throws Exception {
        final java.util.Map<String,Object> data=sampleHistory();
        // a modal dialog blocks its dispatch thread, so it is opened from invokeLater
        SwingUtilities.invokeLater(()->{
            MatchHistoryDialog dialog=new MatchHistoryDialog(null,data);
            historyDialog=dialog;
            dialog.setVisible(true);
        });
        SwingSmoke.waitFor(()->historyDialog!=null&&historyDialog.isShowing(),"history dialog shown");
        SwingSmoke.edt(()->{
            check(historyDialog.rows().getRowCount()==8,"history lists every finished match");
            check("THẮNG".equals(historyDialog.rows().getValueAt(0,2))||"THUA".equals(historyDialog.rows().getValueAt(0,2)),"history marks the result");
            SwingSmoke.snapshot(historyDialog,dir.resolve("match-history.png"));
        });
        SwingSmoke.edt(()->{historyDialog.dispose();historyDialog=null;});
    }
    private static java.util.Map<String,Object> sampleHistory() {
        java.util.Map<String,Object> data=new java.util.LinkedHashMap<>();
        data.put("user_id",7);data.put("username","ngoc_bao");
        data.put("total_games",23);data.put("total_wins",15);data.put("total_points",45);
        List<Object> rows=new ArrayList<>();
        String[] opponents={"thanh_hai","dang_khoa","tuan_duong","player1","gui_b","thanh_hai","dang_khoa","tuan_duong"};
        String[] reasons={"ALL_SHIPS_DESTROYED","ALL_SHIPS_DESTROYED","SURRENDER","ALL_SHIPS_DESTROYED","DISCONNECTED","ALL_SHIPS_DESTROYED","SURRENDER","ALL_SHIPS_DESTROYED"};
        long base=System.currentTimeMillis();
        for(int i=0;i<opponents.length;i++){
            boolean won=i%3!=2;
            java.util.Map<String,Object> row=new java.util.LinkedHashMap<>();
            row.put("opponent_name",opponents[i]);row.put("won",won);row.put("points",won?3:(i==4?-3:0));
            row.put("shots",18+i*3);row.put("duration_seconds",120+i*37);row.put("reason",reasons[i]);
            row.put("ended_at_ms",base-(long)i*5_400_000L);
            rows.add(row);
        }
        data.put("matches",rows);
        return data;
    }

    // ------------------------------------------------------------------ lifecycle checks
    private static void animationLifecycle(Path dir)throws Exception {
        SwingSmoke.edt(()->{
            window=new JFrame("Battleship • Kiểm thử hiệu ứng");
            battle=new BattlePanel("gui_a","gui_b",BotBrain.fleet(new Random(3)),new BattlePanel.BattleActionListener(){
                public void onFire(int x,int y){}public void onSurrender(){}public void onRematch(){}public void onExitToLobby(){}});
            Theme.configureWindow(window,1280,860,1120,800);
            window.setContentPane(battle);window.setLocationRelativeTo(null);window.setVisible(true);
        });
        SwingSmoke.waitFor(battle::isIntroPlaying,"entrance effect starts");
        SwingSmoke.waitFor(()->!battle.isIntroPlaying(),"entrance effect completes");
        javax.swing.Timer clock=seaTimer(battle);
        SwingSmoke.edt(()->{
            check(clock.isRunning(),"sea clock runs while the screen is shown");
            battle.setSeaAnimated(false);
            check(!clock.isRunning(),"sea clock stops when waves are switched off");
            check(!OceanScene.isAnimated(),"wave preference is stored");
        });
        Thread.sleep(200);
        BufferedImage frozen=grab(window);
        Thread.sleep(400);
        BufferedImage later=grab(window);
        check(similar(frozen,later),"frozen sea keeps the frame nearly identical");
        check(distinctColors(frozen)>12,"frozen sea still paints the full scene");
        SwingSmoke.edt(()->{
            battle.setSeaAnimated(true);
            check(clock.isRunning(),"sea clock restarts with waves on");
        });
        Thread.sleep(300);
        BufferedImage moving=grab(window);
        Thread.sleep(400);
        check(!similar(moving,grab(window)),"animated sea keeps moving the frame");
        SwingSmoke.edt(()->battle.disposePanel());
        SwingSmoke.edt(()->{window.dispose();window=null;battle=null;});
        Thread.sleep(150);
        check(!clock.isRunning(),"sea clock stops when the screen is disposed");
    }
    private static javax.swing.Timer seaTimer(Object panel)throws Exception {
        Class<?> type=panel.getClass();while(type!=null&&!type.equals(OceanPanel.class))type=type.getSuperclass();
        Field timer=type.getDeclaredField("seaTimer");timer.setAccessible(true);return (javax.swing.Timer)timer.get(panel);
    }

    // ------------------------------------------------------------------ helpers
    private static void log(BattlePanel panel,String... lines)throws Exception {
        java.lang.reflect.Method method=BattlePanel.class.getDeclaredMethod("log",String.class);
        method.setAccessible(true);
        for(String line:lines)method.invoke(panel,line);
    }
    /** Rewinds the shell clock so a frame can be sampled at an exact moment of the effect. */
    private static void rewindEffect(BattlePanel panel,double seconds)throws Exception {
        Field started=BattlePanel.class.getDeclaredField("effectStarted");
        started.setAccessible(true);
        started.setLong(panel,System.nanoTime()-(long)(seconds*1e9));
    }
    private static BufferedImage grabNow(JFrame frame) {
        BufferedImage image=new BufferedImage(Math.max(1,frame.getWidth()),Math.max(1,frame.getHeight()),BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics=image.createGraphics();frame.paintAll(graphics);graphics.dispose();return image;
    }
    private static BufferedImage grab(JFrame frame)throws Exception {
        AtomicReference<BufferedImage> image=new AtomicReference<>();
        SwingSmoke.edt(()->image.set(grabNow(frame)));
        return image.get();
    }
    /** Compares a coarse grid of pixels: enough to tell "frozen" from "moving" without noise. */
    private static boolean similar(BufferedImage a,BufferedImage b) {
        long difference=0;int samples=0;
        for(int y=0;y<a.getHeight();y+=13)for(int x=0;x<a.getWidth();x+=13) {
            if(x>=b.getWidth()||y>=b.getHeight())continue;
            int first=a.getRGB(x,y),second=b.getRGB(x,y);
            difference+=Math.abs(((first>>16)&0xff)-((second>>16)&0xff))+Math.abs(((first>>8)&0xff)-((second>>8)&0xff))+Math.abs((first&0xff)-(second&0xff));
            samples++;
        }
        return difference/(double)Math.max(1,samples)<1.5;
    }
    private static int distinctColors(BufferedImage image) {
        Set<Integer> colors=new HashSet<>();
        for(int y=0;y<image.getHeight();y+=17)for(int x=0;x<image.getWidth();x+=17)colors.add(image.getRGB(x,y)&0xffffff);
        return colors.size();
    }
    private static void writeGif(List<BufferedImage> frames,Path file,int delayCentiseconds)throws Exception {
        if(frames.isEmpty())return;
        ImageWriter writer=ImageIO.getImageWritersByFormatName("gif").next();
        try(ImageOutputStream output=ImageIO.createImageOutputStream(file.toFile())) {
            writer.setOutput(output);writer.prepareWriteSequence(null);int index=0;
            for(BufferedImage frame:frames) {
                BufferedImage scaled=new BufferedImage(frame.getWidth()/2,frame.getHeight()/2,BufferedImage.TYPE_INT_RGB);
                Graphics2D g=scaled.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(frame,0,0,scaled.getWidth(),scaled.getHeight(),null);g.dispose();
                IIOMetadata metadata=writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(scaled),writer.getDefaultWriteParam());
                String format=metadata.getNativeMetadataFormatName();
                IIOMetadataNode root=(IIOMetadataNode)metadata.getAsTree(format),control=(IIOMetadataNode)root.getElementsByTagName("GraphicControlExtension").item(0);
                control.setAttribute("delayTime",String.valueOf(delayCentiseconds));control.setAttribute("disposalMethod","none");
                if(index++==0){IIOMetadataNode extensions=new IIOMetadataNode("ApplicationExtensions"),loop=new IIOMetadataNode("ApplicationExtension");
                    loop.setAttribute("applicationID","NETSCAPE");loop.setAttribute("authenticationCode","2.0");loop.setUserObject(new byte[]{1,0,0});
                    extensions.appendChild(loop);root.appendChild(extensions);}
                metadata.setFromTree(format,root);writer.writeToSequence(new IIOImage(scaled,null,metadata),null);
            }
            writer.endWriteSequence();
        } finally { writer.dispose(); }
    }
}
