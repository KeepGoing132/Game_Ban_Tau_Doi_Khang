package tests;

import client.controller.ClientController;
import client.ui.*;
import common.model.User;
import server.network.GameServer;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.lang.reflect.Field;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Uses real Swing components and real TCP; all component actions run on the EDT. */
public class SwingSmoke {
    interface Checked { void run()throws Exception; }
    interface Condition { boolean ready()throws Exception; }
    static Object field(Object obj,String name)throws Exception {Field f=obj.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(obj);}
    static void edt(Checked task)throws Exception{AtomicReference<Throwable> error=new AtomicReference<>();SwingUtilities.invokeAndWait(()->{try{task.run();}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new AssertionError(error.get());}
    static void waitFor(Condition condition,String description)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(System.nanoTime()<end){AtomicReference<Boolean> result=new AtomicReference<>(false);edt(()->result.set(condition.ready()));if(result.get())return;Thread.sleep(10);}throw new AssertionError("GUI timeout: "+description);}
    static boolean present(ClientController c,String name)throws Exception{return field(c,name)!=null;}
    static void login(ClientController c,int port,String name)throws Exception{
        edt(()->{Object frame=field(c,"loginFrame");((JTextField)field(frame,"txtHost")).setText("127.0.0.1");((JTextField)field(frame,"txtPort")).setText(String.valueOf(port));((JTextField)field(frame,"txtUsername")).setText(name);((JPasswordField)field(frame,"txtPassword")).setText("secret");((JButton)field(frame,"btnLogin")).doClick(0);});
        waitFor(()->present(c,"currentUser") && present(c,"lobbyFrame"),"login "+name);
    }
    static void clickField(Object o,String name)throws Exception{((JButton)field(o,name)).doClick(0);}
    static void snapshot(Component frame,Path file)throws Exception {BufferedImage image=new BufferedImage(frame.getWidth(),frame.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D graphics=image.createGraphics();frame.paintAll(graphics);graphics.dispose();ImageIO.write(image,"png",file.toFile());}
    static void snapshotMinimum(JFrame frame,Path file)throws Exception {Dimension original=frame.getSize();frame.setSize(frame.getMinimumSize());frame.validate();snapshot(frame,file);frame.setSize(original);frame.validate();}
    static JButton button(Container parent,String label){for(Component c:parent.getComponents()){if(c instanceof JButton && ((JButton)c).getText().contains(label))return (JButton)c;if(c instanceof Container){JButton found=button((Container)c,label);if(found!=null)return found;}}return null;}
    static void chooseConfirm()throws Exception{waitFor(()->{for(Window w:Window.getWindows())if(w instanceof JDialog && w.isShowing()){JButton yes=button((Container)w,"Đồng ý");if(yes!=null){yes.doClick(0);return true;}}return false;},"confirm dialog");}
    static void closePlacement(ClientController c)throws Exception{edt(()->{JFrame frame=(JFrame)field(c,"gameWindow");frame.dispatchEvent(new WindowEvent(frame,WindowEvent.WINDOW_CLOSING));});waitFor(()->!present(c,"gameWindow"),"window leave acknowledged");}
    static void arrange(ClientController c)throws Exception{edt(()->{Object p=field(c,"placementPanel");Object grid=field(p,"gridPanel");JButton[][] cells=(JButton[][])field(grid,"cellButtons");JComboBox<?> combo=(JComboBox<?>)field(p,"cbShips");for(int y=0;y<5;y++){combo.setSelectedIndex(y);cells[0][y].doClick(0);}TestRunner.check(((JButton)field(p,"btnReady")).isEnabled(),"fleet enables Ready");});}
    public static void run()throws Exception{
        Theme.install();
        TestRunner.check(!GraphicsEnvironment.isHeadless(),"Swing desktop available");Path dir=DomainTests.empty("battleship-swing-");java.util.concurrent.atomic.AtomicLong clockOffset=new java.util.concurrent.atomic.AtomicLong();GameServer server=new GameServer(0,new server.manager.StorageManager(dir,()->System.currentTimeMillis()+clockOffset.get()));Thread thread=new Thread(server::start);thread.start();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(server.getPort()<=0 && System.nanoTime()<end)Thread.sleep(5);
        ClientController a=new ClientController(),b=new ClientController();a.start();b.start();
        try {
            waitFor(()->present(a,"loginFrame") && present(b,"loginFrame"),"login windows");
            edt(()->{
                Object frame=field(a,"loginFrame");snapshot((Component)frame,dir.resolve("login.png"));
                snapshotMinimum((JFrame)frame,dir.resolve("login-small.png"));
                ((JTextField)field(frame,"txtPort")).setText("abc");clickField(frame,"btnLogin");
                TestRunner.check(((JLabel)field(frame,"lblStatus")).getText().contains("65535"),"invalid port has inline feedback");
                TestRunner.check(((JButton)field(frame,"btnLogin")).isEnabled(),"validation keeps form usable");
            });
            login(a,server.getPort(),"gui_a");login(b,server.getPort(),"gui_b");
            waitFor(()->((JTable)field(field(a,"lobbyFrame"),"tblOnlineUsers")).getRowCount()==1,"opponent visible in lobby");
            edt(()->{snapshot((Component)field(a,"lobbyFrame"),dir.resolve("lobby.png"));snapshotMinimum((JFrame)field(a,"lobbyFrame"),dir.resolve("lobby-small.png"));});
            // Room creation/join, then actual WINDOW_CLOSING in PLACING.
            edt(()->clickField(field(a,"lobbyFrame"),"btnCreateRoom"));waitFor(()->present(a,"roomId"),"create room ack");
            edt(()->snapshot((Component)field(a,"lobbyFrame"),dir.resolve("room.png")));
            edt(()->{Object lobby=field(b,"lobbyFrame");((JTextField)field(lobby,"txtRoomCode")).setText(((String)field(a,"roomId")).toLowerCase(java.util.Locale.ROOT));clickField(lobby,"btnJoin");});waitFor(()->present(a,"placementPanel") && present(b,"placementPanel"),"join room placement");
            edt(()->{
                Object p=field(a,"placementPanel");BoardGridPanel grid=(BoardGridPanel)field(p,"gridPanel");JButton[][] cells=(JButton[][])field(grid,"cellButtons");
                cells[2][2].dispatchEvent(new java.awt.event.MouseEvent(cells[2][2],java.awt.event.MouseEvent.MOUSE_ENTERED,0,0,2,2,0,false));
                TestRunner.check(grid.getCellState(6,2)==BoardGridPanel.CellState.PREVIEW_VALID,"hover previews full carrier");
                cells[2][2].dispatchEvent(new java.awt.event.MouseEvent(cells[2][2],java.awt.event.MouseEvent.MOUSE_EXITED,0,0,2,2,0,false));
                TestRunner.check(grid.getCellState(6,2)==BoardGridPanel.CellState.WATER,"leaving clears preview");
                cells[9][9].doClick(0);TestRunner.check(((java.util.List<?>)field(p,"placedShips")).isEmpty(),"out-of-bounds placement preserves fleet");
                clickField(p,"btnRotate");cells[0][0].doClick(0);TestRunner.check(grid.getCellState(0,4)==BoardGridPanel.CellState.SHIP,"rotation places vertical carrier");
                clickField(p,"btnRandom");TestRunner.check(((JButton)field(p,"btnReady")).isEnabled(),"random fleet is valid");
                clickField(p,"btnReset");TestRunner.check(!((JButton)field(p,"btnReady")).isEnabled(),"reset disables Ready");
            });
            closePlacement(a);waitFor(()->!present(b,"gameWindow"),"opponent returns lobby");
            // Invite dialog acceptance through the real Swing button.
            edt(()->a.sendInvite(((User)field(b,"currentUser")).getUserId()));chooseConfirm();waitFor(()->present(a,"placementPanel") && present(b,"placementPanel"),"invite placement");closePlacement(b);waitFor(()->!present(a,"gameWindow"),"invite cancellation cleanup");
            // Queue cancellation and pairing through visible buttons.
            edt(()->clickField(field(a,"lobbyFrame"),"btnQuickMatch"));waitFor(()->(Boolean)field(field(a,"lobbyFrame"),"inQueue"),"queued");edt(()->clickField(field(a,"lobbyFrame"),"btnQuickMatch"));waitFor(()->!(Boolean)field(field(a,"lobbyFrame"),"inQueue"),"queue canceled");
            edt(()->{clickField(field(a,"lobbyFrame"),"btnQuickMatch");clickField(field(b,"lobbyFrame"),"btnQuickMatch");});waitFor(()->present(a,"placementPanel") && present(b,"placementPanel"),"queue placement");arrange(a);arrange(b);
            edt(()->{snapshot((Component)field(a,"gameWindow"),dir.resolve("placement.png"));snapshotMinimum((JFrame)field(a,"gameWindow"),dir.resolve("placement-small.png"));});
            edt(()->clickField(field(a,"placementPanel"),"btnReady"));waitFor(()->(Boolean)field(field(a,"placementPanel"),"ready"),"ready ack");
            edt(()->{Object p=field(a,"placementPanel");Object grid=field(p,"gridPanel");((JButton[][])field(grid,"cellButtons"))[8][8].doClick(0);TestRunner.check(((java.util.List<?>)field(p,"placedShips")).size()==5,"Ready locks board clicks");clickField(p,"btnReady");});waitFor(()->!(Boolean)field(field(a,"placementPanel"),"ready"),"cancel Ready ack");
            edt(()->{clickField(field(a,"placementPanel"),"btnReady");clickField(field(b,"placementPanel"),"btnReady");});waitFor(()->present(a,"battlePanel") && present(b,"battlePanel"),"battle start");edt(()->{snapshot((Component)field(a,"gameWindow"),dir.resolve("battle.png"));snapshotMinimum((JFrame)field(a,"gameWindow"),dir.resolve("battle-small.png"));});
            AtomicReference<ClientController> first=new AtomicReference<>();edt(()->first.set((Boolean)field(field(a,"battlePanel"),"isMyTurn")?a:b));
            edt(()->{Object battle=field(first.get(),"battlePanel");Object grid=field(battle,"oppBoardGrid");((JButton[][])field(grid,"cellButtons"))[9][9].doClick(0);TestRunner.check(((JLabel)field(battle,"lblTarget")).getText().equals("J10"),"selected target displayed");clickField(battle,"btnFire");});
            waitFor(()->!(Boolean)field(field(first.get(),"battlePanel"),"isMyTurn")&&(Boolean)field(field(first.get()==a?b:a,"battlePanel"),"isMyTurn"),"miss changes turn in both clients");
            waitFor(()->!((BattlePanel)field(a,"battlePanel")).isAnimating()&&!((BattlePanel)field(b,"battlePanel")).isAnimating(),"miss animation complete");
            edt(()->{BoardGridPanel grid=(BoardGridPanel)field(field(first.get(),"battlePanel"),"oppBoardGrid");TestRunner.check(grid.getCellState(9,9)==BoardGridPanel.CellState.MISS,"miss remains visible");});
            ClientController shooter=first.get()==a?b:a;int[] sizes={5,4,3,3,2};
            for(int y=0;y<5;y++)for(int x=0;x<sizes[y];x++){final int cx=x,cy=y;edt(()->{Object battle=field(shooter,"battlePanel");Object grid=field(battle,"oppBoardGrid");((JButton[][])field(grid,"cellButtons"))[cx][cy].doClick(0);clickField(battle,"btnFire");clickField(battle,"btnFire");});waitFor(()->{Object battle=field(shooter,"battlePanel");return !(Boolean)field(battle,"pendingShot")&&!((BattlePanel)battle).isAnimating();},"shot ack");if(y==1&&x==3)edt(()->snapshot((Component)field(shooter,"gameWindow"),dir.resolve("battle-active.png")));}
            waitFor(()->(Boolean)field(field(a,"battlePanel"),"finished") && (Boolean)field(field(b,"battlePanel"),"finished"),"game over dialogs");waitFor(()->field(field(a,"battlePanel"),"resultDialog")!=null&&field(field(b,"battlePanel"),"resultDialog")!=null,"result after final explosion");TestRunner.check(server.getStorage().getMatchRecords().size()==1,"GUI fast clicks score once");
            // Both sides receive the enemy fleet with the end message, so the report can show it.
            edt(()->{
                @SuppressWarnings("unchecked") java.util.List<common.model.Ship> revealed=(java.util.List<common.model.Ship>)field(field(shooter,"battlePanel"),"revealedFleet");
                TestRunner.check(revealed!=null && revealed.size()==5,"end of match releases the enemy fleet");
                int cells=0;for(common.model.Ship ship:revealed)cells+=ship.getCoordinates().size();
                TestRunner.check(cells==17,"released fleet carries all 17 ship cells");
                int[] fleetSizes=new int[revealed.size()];for(int i=0;i<fleetSizes.length;i++)fleetSizes[i]=revealed.get(i).getSize();java.util.Arrays.sort(fleetSizes);
                TestRunner.check(java.util.Arrays.equals(fleetSizes,new int[]{2,3,3,4,5}),"released fleet has the standard ship sizes");
            });
            edt(()->snapshot((Component)field(field(shooter,"battlePanel"),"resultDialog"),dir.resolve("result.png")));
            String old=(String)field(a,"matchId");
            edt(()->{button((Container)field(field(a,"battlePanel"),"resultDialog"),"Rematch").doClick(0);button((Container)field(field(b,"battlePanel"),"resultDialog"),"Rematch").doClick(0);});waitFor(()->present(a,"placementPanel") && present(b,"placementPanel") && !old.equals(field(a,"matchId")),"GUI rematch new session");
            arrange(a);arrange(b);edt(()->{clickField(field(a,"placementPanel"),"btnReady");clickField(field(b,"placementPanel"),"btnReady");});waitFor(()->present(a,"battlePanel") && present(b,"battlePanel"),"second battle timer alive");
            edt(()->{JFrame frame=(JFrame)field(a,"gameWindow");SwingUtilities.invokeLater(()->frame.dispatchEvent(new WindowEvent(frame,WindowEvent.WINDOW_CLOSING)));});chooseConfirm();waitFor(()->!present(a,"gameWindow") && !present(b,"gameWindow"),"running window exit cleanup");
            edt(()->{
                Object lobby=field(a,"lobbyFrame");
                for(String name:new String[]{"btnQuickMatch","btnCreateRoom","btnJoin","btnInvite"})TestRunner.check(!((JButton)field(lobby,name)).isEnabled(),"penalty disables "+name);
                TestRunner.check(((JLabel)field(lobby,"lblPenalty")).isVisible(),"cooldown banner is visible");
                TestRunner.check(((User)field(a,"currentUser")).getTotalPoints()==0,"surrender score is reflected in lobby");
                snapshot((Component)lobby,dir.resolve("penalty.png"));
            });
            // Match history: asked for from the lobby, after the rematch round is over.
            // a closed the window, so a's newest row is a surrender while the first battle was
            // decided by sinking the fleet, whichever side happened to shoot first.
            edt(()->a.requestMatchHistory(0));
            waitFor(()->{for(Window w:Window.getWindows())if(w instanceof MatchHistoryDialog && w.isShowing())return true;return false;},"match history dialog");
            edt(()->{
                String opponentName=((User)field(b,"currentUser")).getUsername();
                for(Window w:Window.getWindows())if(w instanceof MatchHistoryDialog && w.isShowing()){
                    JTable history=((MatchHistoryDialog)w).rows();
                    TestRunner.check(history.getRowCount()==2,"history lists both finished matches");
                    TestRunner.check("THUA".equals(history.getValueAt(0,2)),"newest row marks the surrender as a loss");
                    TestRunner.check(opponentName.equals(history.getValueAt(0,1)),"history names the opponent");
                    TestRunner.check("Một bên rời trận".equals(history.getValueAt(0,6)),"newest row explains the surrender");
                    TestRunner.check("Đánh chìm toàn bộ hạm đội".equals(history.getValueAt(1,6)),"older row explains the finished battle");
                    snapshot(w,dir.resolve("match-history.png"));
                    w.dispose();return;
                }
            });
            edt(b::requestLeaderboard);waitFor(()->{for(Window w:Window.getWindows())if(w instanceof LeaderboardDialog && w.isShowing()){snapshot(w,dir.resolve("leaderboard.png"));w.dispose();return true;}return false;},"leaderboard dialog");
            server.stop();waitFor(()->!present(a,"currentUser") && !present(b,"currentUser"),"server death responsive UI");
            edt(()->{Object frame=field(a,"loginFrame");((JButton)field(frame,"btnLogin")).doClick(0);});
            waitFor(()->((JLabel)field(field(a,"loginFrame"),"lblStatus")).getText().contains("Không thể kết nối"),"failed connect returns without EDT blocking");
            GameServer recovered=new GameServer(0,new server.manager.StorageManager(dir,()->System.currentTimeMillis()+clockOffset.get()));Thread recoveredThread=new Thread(recovered::start);recoveredThread.start();long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(recovered.getPort()<=0 && System.nanoTime()<limit)Thread.sleep(5);
            try{
                login(a,recovered.getPort(),"gui_a");login(b,recovered.getPort(),"gui_b");
                edt(()->TestRunner.check(!((JButton)field(field(a,"lobbyFrame"),"btnQuickMatch")).isEnabled(),"restarting/relogin preserves UI cooldown"));
                edt(a::logout);waitFor(()->recovered.getConnectionCount()==1,"logout cleanup");login(a,recovered.getPort(),"gui_a");
                edt(()->TestRunner.check(!((JButton)field(field(a,"lobbyFrame"),"btnQuickMatch")).isEnabled(),"logout does not bypass cooldown"));
                clockOffset.addAndGet(common.config.GameConfig.MATCHMAKING_PENALTY_MS);
                waitFor(()->((JButton)field(field(a,"lobbyFrame"),"btnQuickMatch")).isEnabled()&&!((JLabel)field(field(a,"lobbyFrame"),"lblPenalty")).isVisible(),"expiry automatically unlocks lobby");
                edt(()->clickField(field(a,"lobbyFrame"),"btnQuickMatch"));waitFor(()->(Boolean)field(field(a,"lobbyFrame"),"inQueue"),"queue accepted after penalty expires");
                edt(()->clickField(field(a,"lobbyFrame"),"btnQuickMatch"));waitFor(()->!(Boolean)field(field(a,"lobbyFrame"),"inQueue"),"queue can be canceled after penalty");
                TestRunner.check(present(a,"currentUser"),"reconnect ignores old connection callbacks");
            }finally{recovered.stop();recoveredThread.join(5000);}
            System.out.println("PASS Swing real components/TCP: login/room/join/invite/queue/cancel Ready/combat/fast click/rematch/window close/leaderboard/server death/failed connect/reconnect");System.out.println("Swing screenshots: "+dir);
        }finally{server.stop();thread.join(5000);edt(()->{for(Window w:Window.getWindows())w.dispose();});}
    }
}
