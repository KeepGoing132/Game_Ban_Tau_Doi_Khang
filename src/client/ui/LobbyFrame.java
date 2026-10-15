package client.ui;

import client.controller.ClientController;
import common.model.User;
import common.protocol.JsonUtil;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.Map;

public class LobbyFrame extends JFrame {
    private final ClientController controller;
    private final JLabel lblUserInfo=Theme.label("Chào mừng, chỉ huy",Theme.FONT_TITLE,Theme.TEXT_MAIN);
    private final JLabel lblPoints=Theme.label("0",Theme.FONT_TITLE,Theme.TEXT_MAIN);
    private final JLabel lblWins=Theme.label("0",Theme.FONT_TITLE,Theme.TEXT_MAIN);
    private final JLabel lblRate=Theme.label("0%",Theme.FONT_TITLE,Theme.TEXT_MAIN);
    private final JLabel lblOnlineCount=Theme.muted("Đang cập nhật hạm đội...");
    private final JLabel lblFeedback=Theme.muted("Chọn ghép trận nhanh hoặc mời một người bạn.");
    private final JLabel lblPenalty=Theme.badge(" ",Theme.WARNING);
    private final JLabel lblQuickMatchStatus=Theme.label("Sẵn sàng cho một cuộc đấu trí?",Theme.FONT_REGULAR,Theme.TEXT_MUTED);
    private final JLabel lblCreatedRoomCode=Theme.label("— — — — — —",Theme.FONT_GRID.deriveFont(Font.BOLD,29f),Theme.PRIMARY);
    private final JLabel lblRoomHint=Theme.muted("Tạo mã riêng và gửi cho bạn bè.");
    private final JTextField txtRoomCode=new JTextField();
    private final JButton btnBot=Theme.button("Đấu máy",false);
    private final JButton btnAccount=Theme.button("Tài khoản",false);
    private final JButton btnQuickMatch=Theme.button("Tìm đối thủ  →",true);
    private final JButton btnCreateRoom=Theme.button("Tạo phòng riêng",false);
    private final JButton btnJoin=Theme.button("Vào phòng",true);
    private final JButton btnInvite=Theme.button("Mời thách đấu",false);
    private final JButton btnChat=Theme.button("Nhắn tin",true);
    private final JButton btnLeaveRoom=Theme.button("Rời phòng",false);
    private final JButton btnCopy=Theme.button("Sao chép mã",false);
    private final JTable tblOnlineUsers;
    private final DefaultTableModel tableModel;
    private boolean inQueue,pending;
    private String phase="IDLE",roomCode;
    private int currentUserId=-1;
    private long queueStarted;
    private final Timer queueTimer;
    private final Timer penaltyTimer;
    private long penaltyDeadlineNanos;
    private boolean penaltyPending;

    public LobbyFrame(ClientController controller) {
        super("Battleship • Phòng chỉ huy");this.controller=controller;
        Theme.configureWindow(this,1320,900,1120,800);setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel root=Theme.transparent(new BorderLayout());root.setOpaque(true);root.setBackground(Theme.BG_DARK);setContentPane(root);
        JPanel sidebar=Theme.stack();sidebar.setOpaque(true);sidebar.setBackground(Theme.PANEL_BG);sidebar.setPreferredSize(new Dimension(185,0));sidebar.setBorder(new EmptyBorder(30,20,24,20));
        Theme.addStack(sidebar,Theme.label("BATTLESHIP",Theme.FONT_HEADER,Theme.PRIMARY),4);
        Theme.addStack(sidebar,Theme.badge("HẢI CHIẾN",Theme.VIOLET),32);
        JButton home=Theme.button("01   Phòng chỉ huy",false);home.setForeground(Theme.PRIMARY);home.setMargin(new Insets(0,0,0,0));home.setFont(Theme.FONT_SMALL);home.setMaximumSize(new Dimension(Integer.MAX_VALUE,42));
        home.addActionListener(e->showStatus("Bạn đang ở phòng chỉ huy. Chọn một chế độ để bắt đầu.",false));Theme.addStack(sidebar,home,8);
        JButton ranking=Theme.button("02   Bảng xếp hạng",false);ranking.setFont(Theme.FONT_SMALL);ranking.setMaximumSize(new Dimension(Integer.MAX_VALUE,42));ranking.addActionListener(e->controller.requestLeaderboard());Theme.addStack(sidebar,ranking,8);
        JButton guide=Theme.button("03   Cách chơi",false);guide.setFont(Theme.FONT_SMALL);guide.setMaximumSize(new Dimension(Integer.MAX_VALUE,42));guide.addActionListener(e->showGuide());Theme.addStack(sidebar,guide,0);
        sidebar.add(Box.createVerticalGlue());
        JButton sound=Theme.button(GameAudio.isEnabled()?"Âm thanh: Bật":"Âm thanh: Tắt",false);sound.setMaximumSize(new Dimension(Integer.MAX_VALUE,42));sound.addActionListener(e->{GameAudio.setEnabled(!GameAudio.isEnabled());sound.setText(GameAudio.isEnabled()?"Âm thanh: Bật":"Âm thanh: Tắt");});Theme.addStack(sidebar,sound,8);
        btnAccount.setMaximumSize(new Dimension(Integer.MAX_VALUE,42));btnAccount.addActionListener(e->accountDialog());Theme.addStack(sidebar,btnAccount,8);
        Theme.addStack(sidebar,Theme.badge("●  Đã kết nối",Theme.PRIMARY),16);
        JButton logout=Theme.button("Đăng xuất",false);logout.setMaximumSize(new Dimension(Integer.MAX_VALUE,42));logout.addActionListener(e->controller.logout());Theme.addStack(sidebar,logout,0);root.add(sidebar,BorderLayout.WEST);

        JPanel main=Theme.transparent(new BorderLayout(0,16));main.setBorder(new EmptyBorder(24,28,22,28));root.add(main,BorderLayout.CENTER);
        JPanel top=Theme.stack();Theme.addStack(top,lblUserInfo,5);
        top.add(Box.createVerticalStrut(12));
        JPanel stats=Theme.transparent(new GridLayout(1,3,14,0));stats.add(stat("TỔNG ĐIỂM",lblPoints,"+3 điểm cho mỗi chiến thắng"));stats.add(stat("TRẬN THẮNG",lblWins,"Số trận thắng / đã chơi"));stats.add(stat("TỶ LỆ THẮNG",lblRate,""));stats.setMaximumSize(new Dimension(Integer.MAX_VALUE,94));Theme.addStack(top,stats,0);main.add(top,BorderLayout.NORTH);

        JPanel body=Theme.transparent(new BorderLayout(18,0));main.add(Theme.scrollPage(body,466,Theme.BG_DARK),BorderLayout.CENTER);
        JPanel left=Theme.transparent(new BorderLayout(0,18));body.add(left,BorderLayout.CENTER);
        JPanel queue=Theme.card(new Color(31,63,92),22);queue.setLayout(new BorderLayout(12,12));
        JPanel queueText=Theme.stack();Theme.addStack(queueText,Theme.badge("CHỌN ĐỐI THỦ",Theme.PRIMARY),12);
        Theme.addStack(queueText,Theme.label("Sẵn sàng khai hỏa?",Theme.FONT_TITLE.deriveFont(25f),Theme.TEXT_MAIN),7);
        Theme.addStack(queueText,lblQuickMatchStatus,16);btnQuickMatch.addActionListener(e->toggleMatchmakingQueue());JPanel modes=Theme.transparent(new GridLayout(1,2,12,0));modes.add(btnQuickMatch);Theme.styleButton(btnBot,Theme.VIOLET,Theme.BG_DARK);modes.add(btnBot);btnBot.addActionListener(e->botDialog());modes.setMaximumSize(new Dimension(Integer.MAX_VALUE,46));Theme.addStack(queueText,modes,0);queue.add(queueText,BorderLayout.CENTER);left.add(queue,BorderLayout.NORTH);

        JPanel online=Theme.createCardPanel();online.setLayout(new BorderLayout(0,10));
        JPanel onlineHeader=Theme.transparent(new BorderLayout());onlineHeader.add(Theme.label("Chỉ huy trực tuyến",Theme.FONT_HEADER,Theme.TEXT_MAIN),BorderLayout.WEST);
        JButton refresh=Theme.button("Làm mới",false);refresh.setFont(Theme.FONT_SMALL);refresh.setBorder(new EmptyBorder(6,10,6,10));refresh.addActionListener(e->controller.refreshOnlineUsers());onlineHeader.add(refresh,BorderLayout.EAST);
        JPanel titles=Theme.stack();Theme.addStack(titles,onlineHeader,4);Theme.addStack(titles,lblOnlineCount,0);online.add(titles,BorderLayout.NORTH);
        tableModel=new DefaultTableModel(new String[]{"ID","Chỉ huy","Điểm","Trạng thái"},0){public boolean isCellEditable(int r,int c){return false;}};
        tblOnlineUsers=new JTable(tableModel);Theme.styleTable(tblOnlineUsers);
        tblOnlineUsers.removeColumn(tblOnlineUsers.getColumnModel().getColumn(0));
        tblOnlineUsers.getColumnModel().getColumn(0).setPreferredWidth(160);tblOnlineUsers.getColumnModel().getColumn(1).setPreferredWidth(60);tblOnlineUsers.getColumnModel().getColumn(2).setPreferredWidth(100);
        tblOnlineUsers.getSelectionModel().addListSelectionListener(e->updateActions());online.add(Theme.scroll(tblOnlineUsers),BorderLayout.CENTER);
        btnInvite.addActionListener(e->inviteSelectedUser());
        JPanel playerActions=Theme.transparent(new GridLayout(1,2,10,0));playerActions.add(btnInvite);playerActions.add(btnChat);
        btnChat.addActionListener(e->{int row=tblOnlineUsers.getSelectedRow();if(row>=0){int model=tblOnlineUsers.convertRowIndexToModel(row);controller.openChat((Integer)tableModel.getValueAt(model,0),(String)tableModel.getValueAt(model,1));}});
        online.add(playerActions,BorderLayout.SOUTH);left.add(online,BorderLayout.CENTER);

        JPanel right=Theme.stack();right.setPreferredSize(new Dimension(286,0));body.add(right,BorderLayout.EAST);
        JPanel room=Theme.createCardPanel();room.setLayout(new BoxLayout(room,BoxLayout.Y_AXIS));
        Theme.addStack(room,Theme.label("Chơi cùng bạn bè",Theme.FONT_HEADER,Theme.TEXT_MAIN),8);Theme.addStack(room,lblRoomHint,20);
        Theme.addStack(room,lblCreatedRoomCode,18);btnCreateRoom.setMaximumSize(new Dimension(Integer.MAX_VALUE,42));btnCreateRoom.addActionListener(e->{beginAction();controller.createRoom();});Theme.addStack(room,btnCreateRoom,8);
        JPanel roomActions=Theme.transparent(new GridLayout(1,2,8,0));btnCopy.setFont(Theme.FONT_SMALL);btnLeaveRoom.setFont(Theme.FONT_SMALL);btnCopy.setBorder(new EmptyBorder(9,8,9,8));btnLeaveRoom.setBorder(new EmptyBorder(9,8,9,8));
        btnCopy.addActionListener(e->{try{Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(roomCode),null);showStatus("Đã sao chép mã phòng "+roomCode+".",false);}catch(IllegalStateException ex){showStatus("Chưa thể sao chép. Mã phòng: "+roomCode,true);}});
        btnLeaveRoom.addActionListener(e->{beginAction();controller.leaveRoom();});roomActions.add(btnCopy);roomActions.add(btnLeaveRoom);roomActions.setMaximumSize(new Dimension(Integer.MAX_VALUE,36));Theme.addStack(room,roomActions,0);
        room.setMaximumSize(new Dimension(Integer.MAX_VALUE,244));Theme.addStack(right,room,16);
        JPanel join=Theme.createCardPanel();join.setLayout(new BoxLayout(join,BoxLayout.Y_AXIS));Theme.addStack(join,Theme.label("Đã có mã phòng?",Theme.FONT_HEADER,Theme.TEXT_MAIN),8);
        Theme.addStack(join,Theme.muted("Nhập 6 ký tự từ người tạo phòng."),14);Theme.styleField(txtRoomCode);txtRoomCode.setFont(Theme.FONT_GRID.deriveFont(Font.BOLD,21f));txtRoomCode.setHorizontalAlignment(SwingConstants.CENTER);txtRoomCode.setMaximumSize(new Dimension(Integer.MAX_VALUE,46));txtRoomCode.getAccessibleContext().setAccessibleName("Mã phòng gồm sáu ký tự");
        ((AbstractDocument)txtRoomCode.getDocument()).setDocumentFilter(new DocumentFilter(){
            public void insertString(FilterBypass fb,int offset,String value,AttributeSet a)throws BadLocationException{replace(fb,offset,0,value,a);}
            public void replace(FilterBypass fb,int offset,int length,String value,AttributeSet a)throws BadLocationException{
                String clean=value==null?"":value.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]","");
                int room=6-fb.getDocument().getLength()+length;if(clean.length()>room)clean=clean.substring(0,Math.max(0,room));super.replace(fb,offset,length,clean,a);
            }
        });
        Theme.addStack(join,txtRoomCode,10);btnJoin.setMaximumSize(new Dimension(Integer.MAX_VALUE,42));btnJoin.addActionListener(e->joinRoom());txtRoomCode.addActionListener(e->{if(btnJoin.isEnabled())joinRoom();});Theme.addStack(join,btnJoin,0);join.setMaximumSize(new Dimension(Integer.MAX_VALUE,206));Theme.addStack(right,join,0);
        JPanel notices=Theme.stack();lblPenalty.setVisible(false);Theme.addStack(notices,lblPenalty,0);
        lblFeedback.setBorder(new EmptyBorder(6,0,0,0));Theme.addStack(notices,lblFeedback,0);main.add(notices,BorderLayout.SOUTH);
        queueTimer=new Timer(1000,e->updateQueueText());penaltyTimer=new Timer(250,e->updatePenalty());updateActions();
    }
    private JPanel stat(String title,JLabel value,String detail){JPanel p=Theme.card(Theme.PANEL_BG,15);p.setLayout(new BorderLayout(0,4));p.add(Theme.muted(title),BorderLayout.NORTH);p.add(value,BorderLayout.CENTER);p.add(Theme.muted(detail),BorderLayout.SOUTH);return p;}
    public void updateUserInfo(User u){if(u==null)return;currentUserId=u.getUserId();lblUserInfo.setText("Chào mừng, "+u.getUsername());lblPoints.setText(String.valueOf(u.getTotalPoints()));lblWins.setText(u.getTotalWins()+" / "+u.getTotalGames());lblRate.setText(String.format(java.util.Locale.ROOT,"%.0f%%",u.getWinRate()));updateActions();}
    public void updateOnlineUsers(List<Object> users) {
        Integer selected=selectedUserId();tableModel.setRowCount(0);int others=0;
        if(users!=null)for(Object item:users)if(item instanceof Map){@SuppressWarnings("unchecked") Map<String,Object> m=(Map<String,Object>)item;int id=JsonUtil.getInt(m,"user_id",0);if(id==currentUserId)continue;
            String status=JsonUtil.getString(m,"status","IDLE");boolean blocked=JsonUtil.getLong(m,"matchmaking_wait_ms",0)>0 || JsonUtil.getBoolean(m,"penalty_pending",false);tableModel.addRow(new Object[]{id,JsonUtil.getString(m,"username",""),JsonUtil.getInt(m,"total_points",0),blocked?"Đang bị phạt":"IDLE".equals(status)?"● Sẵn sàng":"Đang bận"});others++;
            if(selected!=null&&selected==id)tblOnlineUsers.setRowSelectionInterval(tableModel.getRowCount()-1,tableModel.getRowCount()-1);
        }
        lblOnlineCount.setText(others==0?"Chưa có đối thủ. Mời bạn bè cùng kết nối.":others+" người chơi • Chọn một chỉ huy để thách đấu");updateActions();
    }
    private Integer selectedUserId(){int r=tblOnlineUsers.getSelectedRow();return r<0?null:(Integer)tableModel.getValueAt(tblOnlineUsers.convertRowIndexToModel(r),0);}
    private void updateActions(){
        boolean idle="IDLE".equals(phase)&&!pending&&!isPenalized();
        btnBot.setEnabled(idle);btnAccount.setEnabled(!pending && ("IDLE".equals(phase)||"COOLDOWN".equals(phase)));btnCreateRoom.setEnabled(idle);btnJoin.setEnabled(idle);txtRoomCode.setEnabled(idle);
        int row=tblOnlineUsers==null?-1:tblOnlineUsers.getSelectedRow();boolean available=row>=0&&"● Sẵn sàng".equals(tableModel.getValueAt(tblOnlineUsers.convertRowIndexToModel(row),3));
        btnChat.setEnabled(row>=0);btnInvite.setEnabled(idle&&available);btnQuickMatch.setEnabled(!pending&&!isPenalized()&&(idle||inQueue));btnCopy.setEnabled(roomCode!=null);btnLeaveRoom.setEnabled(!pending&&roomCode!=null);
    }
    private boolean isPenalized(){return penaltyPending || (penaltyDeadlineNanos!=0 && penaltyDeadlineNanos-System.nanoTime()>0);}
    public void setPenalty(long remainingMs,boolean awaitingSave) {
        penaltyDeadlineNanos=System.nanoTime()+java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(Math.max(0,remainingMs));
        penaltyPending=awaitingSave;updatePenalty();if(isPenalized())penaltyTimer.start();
    }
    private void updatePenalty() {
        boolean wasVisible=lblPenalty.isVisible(),blocked=isPenalized();lblPenalty.setVisible(blocked);
        if(blocked) {
            long seconds=Math.max(0,(penaltyDeadlineNanos-System.nanoTime()+999999999L)/1000000000L);
            String time=String.format("%02d:%02d",seconds/60,seconds%60);
            lblPenalty.setText(seconds>0?"Phạt rời trận: tạm khóa ghép trận • Còn "+time:"Đang lưu kết quả rời trận. Chưa thể ghép trận mới.");
            lblQuickMatchStatus.setForeground(Theme.WARNING);
            lblQuickMatchStatus.setText("Rời trận: trừ tối đa 3 điểm và chờ 1 phút.");
            btnQuickMatch.setText(seconds>0?"Ghép trận sau "+time:"Đang lưu hình phạt...");
        } else {
            penaltyTimer.stop();if("COOLDOWN".equals(phase))phase="IDLE";
            lblQuickMatchStatus.setForeground(Theme.TEXT_MUTED);updateQueueText();
            btnQuickMatch.setText(inQueue?"Hủy tìm trận":"Tìm đối thủ  →");
            if(wasVisible)showStatus("Đã hết thời gian phạt. Bạn có thể ghép trận trở lại.",false);
        }
        updateActions();if(wasVisible!=blocked){revalidate();repaint();}
    }
    public void setLifecycle(String value){phase=value;updateActions();}
    private void beginAction(){pending=true;updateActions();showStatus("Đang chờ máy chủ xác nhận...",false);}
    public void completeAction(){pending=false;updateActions();}
    public void showStatus(String text,boolean error){lblFeedback.setText(text);lblFeedback.setForeground(error?Theme.DANGER:Theme.TEXT_MUTED);lblFeedback.setToolTipText(text);}
    public void clearRoom(){roomCode=null;lblCreatedRoomCode.setText("— — — — — —");lblRoomHint.setText("Tạo mã riêng và gửi cho bạn bè.");phase="IDLE";pending=false;updateActions();}
    public void setCreatedRoomCode(String code){roomCode=code;lblCreatedRoomCode.setText(code);lblRoomHint.setText("Đang chờ người bạn cùng ra khơi...");phase="IN_ROOM";pending=false;showStatus("Phòng đã sẵn sàng. Chia sẻ mã "+code+" để mời bạn bè.",false);updateActions();}
    public void setQueueState(boolean queued){inQueue=queued;pending=false;phase=queued?"QUEUED":"IDLE";btnQuickMatch.setText(queued?"Hủy tìm trận":"Tìm đối thủ  →");if(queued){queueStarted=System.nanoTime();queueTimer.start();}else queueTimer.stop();updateQueueText();showStatus(queued?"Đang ghép trận. Bạn có thể hủy bất cứ lúc nào.":"Chọn chế độ chơi để bắt đầu một trận mới.",false);updateActions();}
    private void updateQueueText(){if(isPenalized())return;long seconds=(System.nanoTime()-queueStarted)/1000000000L;lblQuickMatchStatus.setText(inQueue?String.format("Đang tìm đối thủ  •  %02d:%02d",seconds/60,seconds%60):"Sẵn sàng cho một cuộc đấu trí?");}
    private void toggleMatchmakingQueue(){beginAction();if(inQueue)controller.leaveQueue();else controller.joinQueue();}
    public void resetQueueButton(){setQueueState(false);}
    private void joinRoom(){String code=txtRoomCode.getText().trim();if(code.length()!=6){showStatus("Mã phòng cần đủ 6 ký tự.",true);txtRoomCode.requestFocusInWindow();return;}beginAction();controller.joinRoom(code);}
    private void inviteSelectedUser(){Integer id=selectedUserId();if(id==null||!btnInvite.isEnabled())return;controller.sendInvite(id);showStatus("Đã gửi lời mời. Đang chờ đối thủ trả lời...",false);}
    private void botDialog(){
        JDialog dialog=new JDialog(this,"Đấu máy",true);JPanel body=Theme.stack();body.setOpaque(true);body.setBackground(Theme.BG_DARK);body.setBorder(new EmptyBorder(24,24,24,24));
        Theme.addStack(body,Theme.label("Chọn độ khó",Theme.FONT_TITLE,Theme.TEXT_MAIN),12);Theme.addStack(body,Theme.label("Luyện tập • Không tính điểm hoặc phạt rời trận",Theme.FONT_REGULAR,Theme.TEXT_MUTED),22);
        String[] levels={"EASY","MEDIUM","HARD"},names={"Dễ — Làm quen","Vừa — Thử sức","Khó — Thách thức"},details={"Bắn ngẫu nhiên.","Săn ô xen kẽ, truy tìm quanh điểm trúng.","Tính xác suất, ưu tiên hướng tàu còn lại."};Color[] colors={Theme.PRIMARY,Theme.BLUE,Theme.VIOLET};
        for(int i=0;i<3;i++){final String level=levels[i];JButton b=Theme.button(names[i],true);Theme.styleButton(b,colors[i],Theme.BG_DARK);b.setMaximumSize(new Dimension(Integer.MAX_VALUE,48));b.addActionListener(e->{dialog.dispose();beginAction();controller.startBot(level);});Theme.addStack(body,b,6);Theme.addStack(body,Theme.label(details[i],Theme.FONT_REGULAR,Theme.TEXT_MUTED),18);}
        dialog.setContentPane(body);dialog.setSize(490,445);dialog.setResizable(false);dialog.setLocationRelativeTo(this);dialog.setVisible(true);
    }
    private void accountDialog(){
        JPanel body=Theme.stack();Theme.addStack(body,Theme.label("Xóa tài khoản của bạn?",Theme.FONT_HEADER,Theme.DANGER),12);
        Theme.addStack(body,Theme.label("<html>Tên đăng nhập và mật khẩu sẽ bị xóa khỏi database.<br>Điểm không thể khôi phục. Lịch sử trận được ẩn danh.</html>",Theme.FONT_REGULAR,Theme.TEXT_MAIN),16);
        JPasswordField password=new JPasswordField(22);Theme.styleField(password);password.getAccessibleContext().setAccessibleName("Mật khẩu xác nhận xóa tài khoản");Theme.addStack(body,Theme.label("Nhập mật khẩu để xác nhận",Theme.FONT_REGULAR,Theme.TEXT_MUTED),6);Theme.addStack(body,password,0);
        int choice=JOptionPane.showOptionDialog(this,body,"Xóa tài khoản",JOptionPane.DEFAULT_OPTION,JOptionPane.WARNING_MESSAGE,null,new Object[]{"Xóa tài khoản","Hủy"},"Hủy");
        if(choice==JOptionPane.OK_OPTION){char[] chars=password.getPassword();if(chars.length==0){showStatus("Cần nhập mật khẩu để xóa tài khoản.",true);return;}beginAction();controller.deleteAccount(new String(chars));java.util.Arrays.fill(chars,'\0');}
    }
    private void showGuide(){JOptionPane.showMessageDialog(this,"1. Tìm đối thủ hoặc tạo phòng để chơi cùng bạn bè.\n2. Đặt 5 tàu (5, 4, 3, 3, 2 ô), rồi nhấn Sẵn sàng.\n3. Chọn tọa độ trên bản đồ đối thủ, rồi nhấn Khai hỏa.\n\nBắn trúng: giữ lượt. Bắn trượt: đổi lượt.\nHết 15 giây: mất lượt. Đánh chìm cả 5 tàu để thắng.\nThắng +3 điểm. Sau trận, có thể đề nghị chơi lại.","Hướng dẫn chỉ huy",JOptionPane.PLAIN_MESSAGE);}
    @Override public void dispose(){if(queueTimer!=null)queueTimer.stop();if(penaltyTimer!=null)penaltyTimer.stop();super.dispose();}
}
