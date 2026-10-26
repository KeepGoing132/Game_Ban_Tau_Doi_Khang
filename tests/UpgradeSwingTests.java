package tests;

import client.controller.ClientController;
import client.model.FleetPresets;
import client.ui.*;
import common.model.User;
import common.protocol.*;
import server.manager.StorageManager;
import server.network.GameServer;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.prefs.Preferences;

/** Real Swing/TCP checks for password, history filters/pages, presets and the placement clock. */
public final class UpgradeSwingTests {
    private static Object field(Object value,String name)throws Exception { return SwingSmoke.field(value,name); }
    private static void check(boolean value,String text) { TestRunner.check(value,text); }
    public static void run() throws Exception {
        Theme.install(); Path directory=DomainTests.empty("battleship-completion-gui-");
        StorageManager storage=new StorageManager(directory);
        storage.registerUser("upgrade_a","secret"); storage.registerUser("upgrade_b","secret");
        for (int i=0;i<65;i++) storage.commitResult(UpgradeTests.record(storage,"gui-page-"+i,System.currentTimeMillis()+i,i%2==0));
        GameServer server=new GameServer(0,storage); Thread thread=PenaltyTests.start(server);
        Preferences root=Preferences.userRoot().node("battleship-tests/"+UUID.randomUUID());
        ClientController client=new ClientController(); client.start();
        try (SocketPeer opponent=new SocketPeer(server.getPort(),true)) {
            opponent.login("upgrade_b");
            SwingSmoke.waitFor(()->field(client,"loginFrame")!=null,"upgrade login frame");
            SwingSmoke.login(client,server.getPort(),"upgrade_a");
            SwingSmoke.edt(client::openPasswordDialog);
            SwingSmoke.edt(()->{
                PasswordDialog dialog=(PasswordDialog)field(client,"passwordDialog");
                ((JPasswordField)field(dialog,"oldPassword")).setText("secret");
                ((JPasswordField)field(dialog,"newPassword")).setText("new-secret");
                ((JPasswordField)field(dialog,"confirmation")).setText("mismatch");
                SwingSmoke.clickField(dialog,"submit");
                check(((JLabel)field(dialog,"status")).getText().contains("khớp"),"password mismatch stays in form");
                ((JPasswordField)field(dialog,"confirmation")).setText("new-secret"); SwingSmoke.clickField(dialog,"submit");
            });
            SwingSmoke.waitFor(()->((JLabel)field(field(client,"passwordDialog"),"status")).getText().contains("Đã đổi"),"password ack updates dialog");
            SwingSmoke.edt(()->{JDialog dialog=(JDialog)field(client,"passwordDialog");SwingSmoke.snapshot(dialog,directory.resolve("change-password.png"));dialog.dispose();});
            SwingSmoke.edt(()->client.requestMatchHistory(0));
            SwingSmoke.waitFor(()->((MatchHistoryDialog)field(client,"historyDialog")).rows().getRowCount()==20,"history first page");
            SwingSmoke.edt(()->SwingSmoke.clickField(field(client,"historyDialog"),"moreButton"));
            SwingSmoke.waitFor(()->((MatchHistoryDialog)field(client,"historyDialog")).rows().getRowCount()==40,"history next page");
            SwingSmoke.edt(()->{
                Object dialog=field(client,"historyDialog"); ((JComboBox<?>)field(dialog,"resultFilter")).setSelectedIndex(1);
                SwingSmoke.button((Container)dialog,"Lọc").doClick(0);
            });
            SwingSmoke.waitFor(()->{Object dialog=field(client,"historyDialog");return !(Boolean)field(dialog,"loading") && ((MatchHistoryDialog)dialog).rows().getRowCount()==20;},"filtered history response");
            SwingSmoke.edt(()->{
                MatchHistoryDialog dialog=(MatchHistoryDialog)field(client,"historyDialog");
                for (int i=0;i<dialog.rows().getRowCount();i++) check("THẮNG".equals(dialog.rows().getValueAt(i,2)),"filter renders only wins");
                SwingSmoke.snapshot(dialog,directory.resolve("history-filtered.png"));
                Dimension old=dialog.getSize();dialog.setSize(dialog.getMinimumSize());dialog.validate();
                JLabel title=(JLabel)field(dialog,"profileTitle");check(title.getWidth()>=title.getFontMetrics(title.getFont()).stringWidth(title.getText()),"history profile name fits at minimum size");
                SwingSmoke.snapshot(dialog,directory.resolve("history-small.png"));dialog.setSize(old);
                ((JTextField)field(dialog,"opponentFilter")).setText("nobody");SwingSmoke.button(dialog,"Lọc").doClick(0);
            });
            SwingSmoke.waitFor(()->{Object dialog=field(client,"historyDialog");return !(Boolean)field(dialog,"loading") && ((MatchHistoryDialog)dialog).rows().getRowCount()==0;},"empty filtered history");
            SwingSmoke.edt(()->{
                Object dialog=field(client,"historyDialog");((JTextField)field(dialog,"fromDate")).setText("31/02/2026");
                SwingSmoke.button((Container)dialog,"Lọc").doClick(0);
                check(((JLabel)field(dialog,"lblStatus")).getText().contains("dd/MM"),"bad date explained in form");
                ((JDialog)dialog).dispose();
                client.joinQueue();
            });
            IntegrationTests.ok(opponent,new Message(MessageType.QUEUE_JOIN_REQ));
            Message found=opponent.waitType(MessageType.MATCH_FOUND);
            SwingSmoke.waitFor(()->field(client,"placementPanel")!=null,"placement screen");
            final PlacementPanel[] savedPanel=new PlacementPanel[1];
            SwingSmoke.edt(()->{
                PlacementPanel panel=(PlacementPanel)field(client,"placementPanel");savedPanel[0]=panel;
                FleetPresets presets=new FleetPresets(root,"test-endpoint",1);presets.save(0,"Demo",DomainTests.ships());panel.setPresets(presets);
                check(((JLabel)field(panel,"lblDeadline")).getText().contains("giây"),"server placement deadline shown");
                SwingUtilities.invokeLater(()->{try{SwingSmoke.clickField(panel,"btnPresets");}catch(Exception e){throw new RuntimeException(e);}});
            });
            SwingSmoke.waitFor(()->{for(Window window:Window.getWindows())if(window instanceof JDialog&&window.isShowing()&&"Mẫu bố trí trên máy này".equals(((JDialog)window).getTitle())){SwingSmoke.button((Container)window,"Dùng mẫu").doClick(0);return true;}return false;},"preset picker");
            SwingSmoke.waitFor(()->((JButton)field(field(client,"placementPanel"),"btnReady")).isEnabled(),"saved fleet enables Ready");
            SwingSmoke.waitFor(()->((JLabel)field(field(client,"placementPanel"),"lblConnection")).getText().contains("ms"),"latency displayed in placement");
            SwingSmoke.edt(()->{
                JFrame game=(JFrame)field(client,"gameWindow");SwingSmoke.snapshot(game,directory.resolve("placement-deadline.png"));SwingSmoke.snapshotMinimum(game,directory.resolve("placement-small.png"));
                SwingSmoke.clickField(field(client,"placementPanel"),"btnReady");
            });
            SwingSmoke.waitFor(()->(Boolean)field(field(client,"placementPanel"),"ready"),"preset Ready acknowledged");
            SwingSmoke.edt(()->check(!((JButton)field(field(client,"placementPanel"),"btnPresets")).isEnabled(),"Ready locks preset button"));
            SwingSmoke.closePlacement(client); opponent.waitType(MessageType.ROOM_USER_LEFT);
            SwingSmoke.edt(()->check(!((javax.swing.Timer)field(savedPanel[0],"placementClock")).isRunning(),"placement timer disposed on leave"));
            SwingSmoke.edt(client::logout);
            SwingSmoke.edt(()->{
                Object login=field(client,"loginFrame");((JPasswordField)field(login,"txtPassword")).setText("new-secret");SwingSmoke.clickField(login,"btnLogin");
            });
            SwingSmoke.waitFor(()->field(client,"currentUser")!=null,"relogin with new password");
            check(((User)field(client,"currentUser")).getTotalGames()==65,"account stats preserved through password change");
            System.out.println("PASS completion Swing/TCP: password confirmation, history pages/filters/empty/invalid date, saved fleet picker/Ready lock, placement clock cleanup and relogin");
            System.out.println("Completion screenshots: "+directory);
        } finally {
            server.stop();thread.join(5000);root.removeNode();root.flush();
            SwingSmoke.edt(()->{for(Window window:Window.getWindows())window.dispose();});
        }
    }
}
