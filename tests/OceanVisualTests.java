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
    private static MatchReplayDialog replayDialog;

    public static void run()throws Exception {
        Path dir=DomainTests.empty("battleship-ocean-");
        boolean animatedBefore=OceanScene.isAnimated();
        OceanScene.setAnimated(true);
        try {
            battleScreen(dir);
            placementScreen(dir);
            historyDialog(dir);
            replayScreen(dir);
            settingsScreen(dir);
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
        // The server releases the enemy fleet with the end message: the result screen must open its charts.
        List<Ship> enemy=BotBrain.fleet(new Random(11));
        SwingSmoke.edt(()->battle.showGameOver(true,3,"ALL_SHIPS_DESTROYED",enemy,14,142));
        SwingSmoke.waitFor(()->field(battle,"resultDialog")!=null,"result dialog opens");
        JDialog resultWindow=(JDialog)field(battle,"resultDialog");
        MatchResultView resultView=(MatchResultView)resultWindow.getContentPane();
        SwingSmoke.edt(()->check(resultView.isUnfolding(),"charts start folded while the headline lands"));
        List<BufferedImage> resultFrames=new ArrayList<>();
        for(int i=0;i<34;i++){resultFrames.add(grabWindow(resultWindow));Thread.sleep(100);}
        writeGif(resultFrames,dir.resolve("result-win-animation.gif"),10);
        for(int index:new int[]{1,6,13,19,26,33})ImageIO.write(resultFrames.get(index),"png",dir.resolve("result-win-"+(index+1)+".png").toFile());
        SwingSmoke.waitFor(()->!resultView.isUnfolding(),"charts unfold after the headline");
        SwingSmoke.edt(()->{
            @SuppressWarnings("unchecked") List<Ship> revealed=(List<Ship>)field(battle,"revealedFleet");
            check(revealed!=null && revealed.size()==5,"battle report keeps the released fleet");
            BoardGridPanel shown=(BoardGridPanel)field(battle,"oppBoardGrid");
            int shipCells=0;
            for(Ship ship:revealed)for(Coordinate c:ship.getCoordinates()){
                shipCells++;
                check(shown.getCellState(c.getX(),c.getY())!=BoardGridPanel.CellState.WATER,"released fleet is painted on the enemy board");
                check(resultView.enemyChart().getCellState(c.getX(),c.getY())!=BoardGridPanel.CellState.WATER,"unfolded enemy chart shows the released fleet");
            }
            check(shipCells==17,"released fleet covers all 17 ship cells");
            for(Ship ship:myFleet)for(Coordinate c:ship.getCoordinates())
                check(resultView.myChart().getCellState(c.getX(),c.getY())!=BoardGridPanel.CellState.WATER,"unfolded own chart shows your own fleet");
            check(resultView.rematchButton().isEnabled()&&resultView.exitButton().isEnabled(),"result stays usable while it animates");
        });
        SwingSmoke.edt(()->{try{SwingSmoke.snapshot((Component)field(battle,"resultDialog"),dir.resolve("result-report.png"));}catch(Exception e){throw new RuntimeException(e);}});
        SwingSmoke.edt(()->{try{SwingSmoke.snapshot((Component)field(battle,"resultDialog"),dir.resolve("result.png"));}catch(Exception e){throw new RuntimeException(e);}});
        SwingSmoke.edt(()->battle.disposePanel());
        SwingSmoke.edt(()->{window.dispose();window=null;battle=null;});
        // A defeat must read as clearly as a victory.
        SwingSmoke.edt(()->{
            window=new JFrame("Battleship • Thất bại");
            battle=new BattlePanel("gui_a","gui_b",myFleet,new BattlePanel.BattleActionListener(){
                public void onFire(int x,int y){}public void onSurrender(){}public void onRematch(){}public void onExitToLobby(){}});
            Theme.configureWindow(window,1280,860,1120,800);
            window.setContentPane(battle);window.setLocationRelativeTo(null);window.setVisible(true);
            battle.showGameOver(false,-3,"SURRENDER",enemy,13,88);
        });
        SwingSmoke.waitFor(()->field(battle,"resultDialog")!=null,"defeat result opens");
        SwingSmoke.waitFor(()->!((MatchResultView)((JDialog)field(battle,"resultDialog")).getContentPane()).isUnfolding(),"defeat charts unfold");
        SwingSmoke.edt(()->SwingSmoke.snapshot((Component)field(battle,"resultDialog"),dir.resolve("result-lose.png")));
        SwingSmoke.edt(()->{battle.disposePanel();window.dispose();window=null;battle=null;});
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
        final List<String> requested=new ArrayList<>();
        // a modal dialog blocks its dispatch thread, so it is opened from invokeLater
        SwingUtilities.invokeLater(()->{
            MatchHistoryDialog dialog=new MatchHistoryDialog(null,data,requested::add);
            historyDialog=dialog;
            dialog.setVisible(true);
        });
        SwingSmoke.waitFor(()->historyDialog!=null&&historyDialog.isShowing(),"history dialog shown");
        SwingSmoke.edt(()->{
            check(historyDialog.rows().getRowCount()==8,"history lists every finished match");
            check("THẮNG".equals(historyDialog.rows().getValueAt(0,2))||"THUA".equals(historyDialog.rows().getValueAt(0,2)),"history marks the result");
            check("Xem lại".equals(historyDialog.rows().getValueAt(0,7)),"replayable rows offer the replay");
            check("—".equals(historyDialog.rows().getValueAt(7,7)),"rows without stored fleets offer nothing to replay");
            SwingSmoke.snapshot(historyDialog,dir.resolve("match-history.png"));
        });
        // one click on a row asks the server for that very match
        SwingSmoke.edt(()->{clickRow(historyDialog,0);});
        check(requested.size()==1&&"M_sample_0".equals(requested.get(0)),"a click opens the replay of that match");
        // a match recorded before the feature explains itself instead of asking the server
        SwingSmoke.edt(()->clickRow(historyDialog,7));
        check(requested.size()==1,"a row without stored fleets never asks the server");
        SwingSmoke.edt(()->{historyDialog.dispose();historyDialog=null;});
    }
    private static void clickRow(MatchHistoryDialog dialog,int row){
        JTable table=dialog.rows();
        table.setRowSelectionInterval(row,row);
        table.dispatchEvent(new java.awt.event.MouseEvent(table,java.awt.event.MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(),0,12,12,1,false,java.awt.event.MouseEvent.BUTTON1));
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
            row.put("match_id","M_sample_"+i);
            // the two oldest rows predate the replay feature, like real stored data
            row.put("replay",i<6);
            rows.add(row);
        }
        data.put("matches",rows);
        return data;
    }

    // ------------------------------------------------------------------ replay screen
    /** The replay of one match: both sealed fleets plus the shot log, driven shot by shot. */
    private static void replayScreen(Path dir)throws Exception {
        List<Ship> mine=BotBrain.fleet(new Random(21)),theirs=BotBrain.fleet(new Random(22));
        List<Object> log=new ArrayList<>();
        Set<String> fired=new HashSet<>();
        // my shots: the first enemy ship goes down, then two shots into empty water
        List<Coordinate> victim=theirs.get(0).getCoordinates();
        for(int i=0;i<victim.size();i++){
            boolean last=i==victim.size()-1;
            log.add(shot(i+1,7,victim.get(i),last?"SUNK":"HIT",last?victim:null));fired.add(key(victim.get(i)));
        }
        int misses=0;
        for(int x=0;x<10&&misses<2;x++)for(int y=0;y<10&&misses<2;y++){
            if(occupied(theirs,x,y)||!fired.add(x+":"+y))continue;
            log.add(shot(20+misses,7,new Coordinate(x,y),"MISS",null));misses++;
        }
        // the enemy answers by hitting two cells of my second ship
        List<Coordinate> myHits=mine.get(1).getCoordinates();
        for(int i=0;i<2;i++)log.add(shot(30+i,9,myHits.get(i),"HIT",null));
        Map<String,Object> data=sampleReplay(mine,theirs,log);
        SwingUtilities.invokeLater(()->{replayDialog=new MatchReplayDialog(null,7,data);replayDialog.setVisible(true);});
        SwingSmoke.waitFor(()->replayDialog!=null&&replayDialog.isShowing(),"replay screen shown");
        OceanPanel replayOcean=(OceanPanel)replayDialog.getContentPane();
        SwingSmoke.waitFor(()->!replayOcean.isIntroPlaying(),"replay entrance effect ends");
        SwingSmoke.edt(()->{
            check(replayDialog.shots().getModel().getSize()==log.size(),"shot log lists every recorded shot");
            for(Coordinate c:victim)check(replayDialog.enemyBoard().getCellState(c.getX(),c.getY())==BoardGridPanel.CellState.SUNK,"replayed enemy chart shows the sunk ship");
            for(int i=0;i<2;i++)check(replayDialog.myBoard().getCellState(myHits.get(i).getX(),myHits.get(i).getY())==BoardGridPanel.CellState.HIT,"replayed own chart shows the enemy hits");
            for(Ship ship:mine)for(Coordinate c:ship.getCoordinates())check(replayDialog.myBoard().getCellState(c.getX(),c.getY())!=BoardGridPanel.CellState.WATER,"own fleet is drawn on the replayed chart");
            // an untouched ship stays a hull, proving the marks are layered over the fleets
            for(Coordinate c:theirs.get(4).getCoordinates())check(replayDialog.enemyBoard().getCellState(c.getX(),c.getY())==BoardGridPanel.CellState.SHIP,"untouched ships stay visible on the replayed chart");
            SwingSmoke.snapshot(replayDialog,dir.resolve("match-replay.png"));
            // stepping back through the log rebuilds both charts up to that shot
            replayDialog.shots().setSelectedIndex(2);
        });
        SwingSmoke.edt(()->{
            check(replayDialog.shots().getModel().getSize()==3,"picking a shot rewinds the replay to that point");
            check(replayDialog.enemyBoard().getCellState(victim.get(3).getX(),victim.get(3).getY())==BoardGridPanel.CellState.SHIP,"later shots are removed when the replay is rewound");
            SwingSmoke.snapshot(replayDialog,dir.resolve("match-replay-early.png"));
            replayDialog.dispose();replayDialog=null;
        });
    }
    private static String key(Coordinate c){return c.getX()+":"+c.getY();}
    private static boolean occupied(List<Ship> fleet,int x,int y){
        for(Ship ship:fleet)if(ship.contains(new Coordinate(x,y)))return true;
        return false;
    }
    private static Map<String,Object> shot(int turn,int shooter,Coordinate c,String result,List<Coordinate> sunk){
        Map<String,Object> entry=new java.util.LinkedHashMap<>();
        entry.put("turn_id",turn);entry.put("shooter_id",shooter);entry.put("x",c.getX());entry.put("y",c.getY());entry.put("result",result);
        if(sunk!=null){List<Object> cells=new ArrayList<>();for(Coordinate cell:sunk){Map<String,Object> point=new java.util.LinkedHashMap<>();point.put("x",cell.getX());point.put("y",cell.getY());cells.add(point);}entry.put("sunk_ship_coords",cells);entry.put("sunk_ship_name","CARRIER");}
        return entry;
    }
    private static Map<String,Object> sampleReplay(List<Ship> mine,List<Ship> theirs,List<Object> log){
        Map<String,Object> data=new java.util.LinkedHashMap<>();
        data.put("match_id","M_sample");data.put("opponent_id",9);data.put("opponent_name","thanh_hai");
        data.put("won",true);data.put("points",3);data.put("reason","ALL_SHIPS_DESTROYED");
        data.put("duration_seconds",231);data.put("ended_at_ms",System.currentTimeMillis()-3600_000L);
        data.put("my_shots",countShots(log,7));data.put("opponent_shots",countShots(log,9));
        data.put("my_ships",fleetOf(mine));data.put("opponent_ships",fleetOf(theirs));data.put("shot_log",log);data.put("replay_available",true);
        return data;
    }
    private static int countShots(List<Object> log,int shooter){
        int total=0;
        for(Object item:log){@SuppressWarnings("unchecked") Map<String,Object> shot=(Map<String,Object>)item;if(common.protocol.JsonUtil.getInt(shot,"shooter_id",0)==shooter)total++;}
        return total;
    }
    private static List<Object> fleetOf(List<Ship> fleet){
        List<Object> list=new ArrayList<>();
        for(Ship ship:fleet){
            Map<String,Object> entry=new java.util.LinkedHashMap<>();
            entry.put("id",ship.getId());entry.put("size",ship.getSize());
            List<Object> cells=new ArrayList<>();
            for(Coordinate c:ship.getCoordinates()){Map<String,Object> point=new java.util.LinkedHashMap<>();point.put("x",c.getX());point.put("y",c.getY());cells.add(point);}
            entry.put("coordinates",cells);list.add(entry);
        }
        return list;
    }

    // ------------------------------------------------------------------ settings screen
    /** Volume sliders must reach the audio engine, and the dialog must stay reusable. */
    private static void settingsScreen(Path dir)throws Exception {
        float master=GameAudio.getMasterVolume();
        SwingSmoke.edt(()->SettingsDialog.open(null));
        SwingSmoke.waitFor(()->{for(Window w:Window.getWindows())if(w instanceof SettingsDialog && w.isShowing())return true;return false;},"settings dialog");
        SwingSmoke.edt(()->{
            SettingsDialog dialog=null;
            for(Window w:Window.getWindows())if(w instanceof SettingsDialog)dialog=(SettingsDialog)w;
            List<JSlider> sliders=new ArrayList<>();
            collect(dialog,sliders);
            check(sliders.size()==3,"settings offers master, effects and sea levels");
            check(sliders.get(0).getValue()==Math.round(GameAudio.getMasterVolume()*100),"master slider starts at the stored level");
            sliders.get(0).setValue(35);
            check(Math.abs(GameAudio.getMasterVolume()-.35f)<.01f,"master volume reaches the audio engine");
            List<JCheckBox> boxes=new ArrayList<>();
            collectBoxes(dialog,boxes);
            check(boxes.size()==3,"settings toggles sound, waves and the minimap");
            SwingSmoke.snapshot(dialog,dir.resolve("settings.png"));
            dialog.dispose();
        });
        // a disposed window stays in Window.getWindows() until it is collected, so only visibility is checked
        SwingSmoke.waitFor(()->{for(Window w:Window.getWindows())if(w instanceof SettingsDialog && w.isShowing())return false;return true;},"settings closed");
        SwingSmoke.edt(()->{GameAudio.setMasterVolume(master);check(Math.abs(GameAudio.getMasterVolume()-master)<.01f,"master volume restored after the test");});
    }
    private static void collect(Container root,List<JSlider> found){
        for(Component c:root.getComponents()){
            if(c instanceof JSlider)found.add((JSlider)c);
            if(c instanceof Container)collect((Container)c,found);
        }
    }
    private static void collectBoxes(Container root,List<JCheckBox> found){
        for(Component c:root.getComponents()){
            if(c instanceof JCheckBox)found.add((JCheckBox)c);
            if(c instanceof Container)collectBoxes((Container)c,found);
        }
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
    private static BufferedImage grabNow(Window frame) {
        BufferedImage image=new BufferedImage(Math.max(1,frame.getWidth()),Math.max(1,frame.getHeight()),BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics=image.createGraphics();frame.paintAll(graphics);graphics.dispose();return image;
    }
    private static BufferedImage grab(JFrame frame)throws Exception {
        AtomicReference<BufferedImage> image=new AtomicReference<>();
        SwingSmoke.edt(()->image.set(grabNow(frame)));
        return image.get();
    }
    /** Same as {@link #grab}, for dialogs: a heavyweight window must be captured on the EDT. */
    private static BufferedImage grabWindow(Window window)throws Exception {
        AtomicReference<BufferedImage> image=new AtomicReference<>();
        SwingSmoke.edt(()->image.set(grabNow(window)));
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
