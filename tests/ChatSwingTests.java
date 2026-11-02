package tests;

import client.controller.ClientController;
import client.ui.*;
import common.model.User;
import common.protocol.*;
import server.network.GameServer;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

public final class ChatSwingTests {
    private static Object field(Object o,String name)throws Exception{return SwingSmoke.field(o,name);}
    private static ChatPanel panel(ClientController c)throws Exception{return (ChatPanel)field(field(c,"chat"),"panel");}
    private static JTextArea input(ClientController c)throws Exception{return (JTextArea)field(panel(c),"input");}
    private static void check(boolean value,String text){TestRunner.check(value,text);}
    private static int messageCount(ClientController c,int peer)throws Exception {
        Map<?,?> all=(Map<?,?>)field(field(c,"chat"),"conversations");Object conversation=all.get(peer);return conversation==null?0:((Map<?,?>)field(conversation,"messages")).size();
    }
    private static void type(ClientController c,String value)throws Exception {input(c).setText(value);input(c).getActionMap().get("send-message").actionPerformed(new ActionEvent(input(c),0,"enter"));}
    public static void run()throws Exception {
        Theme.install();Path dir=DomainTests.empty("battleship-chat-ui-");GameServer server=new GameServer(0,dir);Thread thread=PenaltyTests.start(server);
        ClientController a=new ClientController(),b=new ClientController();a.start();b.start();
        try {
            SwingSmoke.waitFor(()->field(a,"loginFrame")!=null&&field(b,"loginFrame")!=null,"chat login windows");SwingSmoke.login(a,server.getPort(),"hai_dang");SwingSmoke.login(b,server.getPort(),"minh_anh");
            int aid=server.getStorage().findByUsername("hai_dang").getUserId(),bid=server.getStorage().findByUsername("minh_anh").getUserId();
            SwingSmoke.waitFor(()->((JTable)field(field(a,"lobbyFrame"),"tblOnlineUsers")).getRowCount()==1,"chat recipient in lobby");
            try(Connection db=DriverManager.getConnection("jdbc:sqlite:"+dir.resolve("battleship.db"));Statement s=db.createStatement()){s.execute("CREATE TRIGGER chat_ui_fail BEFORE INSERT ON chat_messages BEGIN SELECT RAISE(ABORT,'test write failure'); END");}
            SwingSmoke.edt(()->{
                Object lobby=field(a,"lobbyFrame");JTable users=(JTable)field(lobby,"tblOnlineUsers");users.setRowSelectionInterval(0,0);SwingSmoke.clickField(lobby,"btnChat");
                check(panel(a).isExpanded(),"lobby message button opens chat");type(a,"Chào bạn! Mình làm một ván nhé? 🌊");
            });
            SwingSmoke.waitFor(()->((JLabel)field(panel(a),"status")).getText().contains("Chưa lưu"),"chat save error is visible");
            SwingSmoke.edt(()->{check(!input(a).getText().isEmpty()&&messageCount(a,bid)==0&&messageCount(b,aid)==0,"failed send keeps draft and is not displayed as delivered");check(((JButton)field(panel(a),"send")).isEnabled(),"failed send can be retried");});
            try(Connection db=DriverManager.getConnection("jdbc:sqlite:"+dir.resolve("battleship.db"));Statement s=db.createStatement()){s.execute("DROP TRIGGER chat_ui_fail");}
            SwingSmoke.edt(()->SwingSmoke.clickField(panel(a),"send"));
            SwingSmoke.waitFor(()->messageCount(a,bid)==1&&messageCount(b,aid)==1&&input(a).getText().isEmpty(),"enter sends and clears only after ack");
            SwingSmoke.edt(()->{
                check(!panel(b).isExpanded(),"incoming message does not open overlay or steal focus");check(((JButton)field(panel(b),"toggle")).getText().contains("● 1"),"collapsed chat shows unread count");
                SwingSmoke.snapshot((Component)field(b,"lobbyFrame"),dir.resolve("chat-unread.png"));panel(b).setExpanded(true);type(b,"Được chứ! Bạn tạo phòng đi, mình vào ngay.");
            });
            SwingSmoke.waitFor(()->messageCount(a,bid)==2&&messageCount(b,aid)==2,"two-way chat");
            SwingSmoke.edt(()->{input(a).setText("Tin nhắn đang soạn");panel(a).setExpanded(false);panel(a).setExpanded(true);check(input(a).getText().equals("Tin nhắn đang soạn"),"collapse preserves draft");input(a).setText("");SwingSmoke.snapshot((Component)field(a,"lobbyFrame"),dir.resolve("chat-lobby.png"));SwingSmoke.snapshotMinimum((JFrame)field(a,"lobbyFrame"),dir.resolve("chat-lobby-small.png"));a.joinQueue();b.joinQueue();});
            SwingSmoke.waitFor(()->field(a,"placementPanel")!=null&&field(b,"placementPanel")!=null,"chat follows paired players");
            SwingSmoke.edt(()->{check(panel(a).getParent()==((JFrame)field(a,"gameWindow")).getLayeredPane(),"chat reattaches to game window");panel(a).setExpanded(true);check(messageCount(a,bid)==2,"conversation remains across lobby/placement");});
            SwingSmoke.arrange(a);SwingSmoke.arrange(b);SwingSmoke.edt(()->{SwingSmoke.clickField(field(a,"placementPanel"),"btnReady");SwingSmoke.clickField(field(b,"placementPanel"),"btnReady");});
            SwingSmoke.waitFor(()->field(a,"battlePanel")!=null&&field(b,"battlePanel")!=null,"battle with chat");
            SwingSmoke.edt(()->{type(a,"Chúc bạn may mắn!\nHạm đội đã sẵn sàng ⚓");});
            SwingSmoke.waitFor(()->messageCount(b,aid)==3,"message received during running match");
            SwingSmoke.edt(()->{
                JFrame window=(JFrame)field(a,"gameWindow");JPanel board=(JPanel)field(field(a,"battlePanel"),"mainBoardHost");Dimension before=board.getSize();
                panel(a).setExpanded(false);window.validate();check(board.getSize().equals(before),"collapsed chat does not resize board");SwingSmoke.snapshot(window,dir.resolve("chat-battle-collapsed.png"));
                panel(a).setExpanded(true);window.validate();check(board.getSize().equals(before),"expanded chat preserves full map size");SwingSmoke.snapshot(window,dir.resolve("chat-battle.png"));
                Dimension original=window.getSize();window.setSize(window.getMinimumSize());window.validate();Rectangle bounds=panel(a).getBounds();check(bounds.x>=0&&bounds.x+bounds.width<=window.getLayeredPane().getWidth()&&bounds.y+bounds.height<=window.getLayeredPane().getHeight(),"chat remains inside resized window immediately");SwingSmoke.snapshot(window,dir.resolve("chat-battle-small.png"));window.setSize(original);window.validate();
                input(a).setText(String.join("",Collections.nCopies(520,"a")));check(input(a).getText().length()==500,"input enforces 500 character limit");input(a).setText("");
            });
            for(int i=0;i<23;i++)server.getStorage().saveChat(aid,bid,"Tin nhắn lịch sử "+i,"old-"+i);
            SwingSmoke.edt(a::logout);SwingSmoke.waitFor(()->server.getStorage().findByUsername("hai_dang").getStatus()==common.model.UserStatus.OFFLINE,"logout complete");SwingSmoke.login(a,server.getPort(),"hai_dang");
            SwingSmoke.edt(()->a.openChat(bid,"minh_anh"));SwingSmoke.waitFor(()->messageCount(a,bid)==20,"history loads after relogin");
            SwingSmoke.edt(()->{check(((JButton)field(panel(a),"older")).isVisible(),"older page available");SwingSmoke.clickField(panel(a),"older");});SwingSmoke.waitFor(()->messageCount(a,bid)==26,"older messages merge into history");
            SwingSmoke.edt(()->SwingSmoke.snapshot((Component)field(a,"lobbyFrame"),dir.resolve("chat-history.png")));
            SwingSmoke.edt(a::logout);SwingSmoke.waitFor(()->server.getStorage().findByUsername("hai_dang").getStatus()==common.model.UserStatus.OFFLINE,"second logout");SwingSmoke.login(a,server.getPort(),"new_account");
            SwingSmoke.edt(()->check(messageCount(a,bid)==0,"switching account clears private cache"));
        }finally{server.stop();thread.join(5000);SwingSmoke.edt(()->{for(Window w:Window.getWindows())w.dispose();});}
        System.out.println("PASS chat Swing: send/receive/Enter, unread/collapse/draft, lobby-to-game, map size, 500-char limit, SQL history/paging, account isolation");System.out.println("Chat screenshots: "+dir);
    }
}
