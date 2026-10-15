package client.ui;

import common.model.Coordinate;
import common.model.Ship;
import common.model.ShotResult;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

public class BattlePanel extends JPanel {
    public interface BattleActionListener {void onFire(int x,int y);void onSurrender();void onRematch();void onExitToLobby();}
    private final BattleActionListener actionListener;
    private final BoardGridPanel myBoardGrid,oppBoardGrid;
    private final JLabel lblTurnStatus=Theme.label("Chờ lệnh khai hỏa",Theme.FONT_HEADER,Theme.PRIMARY);
    private final JLabel lblTimer=Theme.label("15",Theme.FONT_GRID.deriveFont(Font.BOLD,48f),Theme.TEXT_MAIN);
    private final JLabel lblMyFleetStatus=Theme.label("",Theme.FONT_BOLD,Theme.PRIMARY);
    private final JLabel lblOppFleetStatus=Theme.label("",Theme.FONT_BOLD,Theme.DANGER);
    private final JLabel lblTarget=Theme.label("— —",Theme.FONT_GRID.deriveFont(Font.BOLD,30f),Theme.TEXT_MAIN);
    private final JLabel lblHint=Theme.label(" ",Theme.FONT_SMALL,Theme.TEXT_MUTED);
    private final JLabel lblAccuracy=Theme.muted("0 phát bắn  /  0 trúng đích");
    private final JButton btnFire=Theme.button("Khai hỏa  →",true);
    private final JButton btnSurrender=Theme.button("Rời trận",false);
    private final JProgressBar turnProgress=new JProgressBar(0,1000);
    private final DefaultListModel<String> logModel=new DefaultListModel<>();
    private final String myName,oppName;
    private JDialog resultDialog;
    private JButton rematchButton;
    private JLabel resultHint;
    private boolean pendingShot,finished,isMyTurn;
    private long localDeadlineNanos,turnDurationNanos;
    private int remainingTime=15,myRemainingShips=5,oppRemainingShips=5,shots,hits;
    private final Timer visualTimer;
    private final Timer effectTimer;
    private final JPanel mainBoardHost=Theme.transparent(new BorderLayout());
    private final JPanel miniBoardHost=Theme.transparent(new BorderLayout());
    private final JCheckBox miniToggle=new JCheckBox("Bản đồ thu nhỏ",true);
    private final JLabel focusTitle=Theme.label("HẢI PHẬN ĐỐI THỦ",Theme.FONT_HEADER,Theme.PRIMARY);
    private final java.util.prefs.Preferences preferences=java.util.prefs.Preferences.userNodeForPackage(BattlePanel.class);
    private boolean animating,impactPlayed,practice;
    private long effectStarted;
    private Coordinate effectCell;
    private ShotResult effectResult;
    private boolean effectShooter;
    private Point effectFrom,effectTo;
    private Runnable afterEffect,impactAction;


    public BattlePanel(String myName,String oppName,List<Ship> myShips,BattleActionListener actionListener) {
        this.actionListener=actionListener;this.myName=myName;this.oppName=oppName;
        setLayout(new BorderLayout(20,14));setBackground(Theme.BG_DARK);setBorder(new EmptyBorder(18,24,18,24));
        JPanel header=Theme.transparent(new BorderLayout());JPanel title=Theme.stack();Theme.addStack(title,Theme.label(myName+"  vs  "+oppName,Theme.FONT_TITLE,Theme.TEXT_MAIN),5);Theme.addStack(title,focusTitle,0);header.add(title,BorderLayout.CENTER);
        JPanel options=Theme.transparent(new FlowLayout(FlowLayout.RIGHT,12,4));
        miniToggle.setSelected(preferences.getBoolean("minimap",true));miniToggle.setOpaque(false);miniToggle.setForeground(Theme.TEXT_MAIN);miniToggle.setFont(Theme.FONT_REGULAR);miniToggle.addActionListener(e->{preferences.putBoolean("minimap",miniToggle.isSelected());miniBoardHost.setVisible(miniToggle.isSelected());revalidate();GameAudio.play(GameAudio.Sound.SELECT);});options.add(miniToggle);
        JButton sound=Theme.button(GameAudio.isEnabled()?"Âm thanh: Bật":"Âm thanh: Tắt",false);sound.addActionListener(e->{GameAudio.setEnabled(!GameAudio.isEnabled());sound.setText(GameAudio.isEnabled()?"Âm thanh: Bật":"Âm thanh: Tắt");});options.add(sound);options.add(btnSurrender);header.add(options,BorderLayout.EAST);add(header,BorderLayout.NORTH);
        myBoardGrid=new BoardGridPanel("Hạm đội của bạn",false);oppBoardGrid=new BoardGridPanel("Hạm đội đối thủ",true);
        myBoardGrid.setFleet(myShips);
        add(mainBoardHost,BorderLayout.CENTER);
        JPanel right=Theme.transparent(new BorderLayout(0,14));right.setPreferredSize(new Dimension(290,0));add(right,BorderLayout.EAST);
        JPanel command=Theme.card(Theme.PANEL_BG,16);command.setLayout(new BoxLayout(command,BoxLayout.Y_AXIS));
        Theme.addStack(command,lblTurnStatus,8);
        JPanel clock=Theme.transparent(new BorderLayout());clock.add(lblTimer,BorderLayout.WEST);lblTarget.setForeground(Theme.WARNING);clock.add(lblTarget,BorderLayout.EAST);Theme.addStack(command,clock,8);
        turnProgress.setBorderPainted(false);turnProgress.setBackground(Theme.PANEL_LIGHT);turnProgress.setForeground(Theme.PRIMARY);turnProgress.setPreferredSize(new Dimension(180,5));turnProgress.setMaximumSize(new Dimension(Integer.MAX_VALUE,5));Theme.addStack(command,turnProgress,16);
        btnFire.setMaximumSize(new Dimension(Integer.MAX_VALUE,48));btnFire.setEnabled(false);btnFire.addActionListener(e->fire());Theme.addStack(command,btnFire,10);
        lblHint.setMaximumSize(new Dimension(Integer.MAX_VALUE,44));lblHint.setPreferredSize(new Dimension(245,44));Theme.addStack(command,lblHint,12);
        Theme.addStack(command,lblMyFleetStatus,6);Theme.addStack(command,lblOppFleetStatus,0);right.add(command,BorderLayout.NORTH);
        JPanel lower=Theme.transparent(new BorderLayout(0,12));right.add(lower,BorderLayout.CENTER);
        miniBoardHost.setPreferredSize(new Dimension(290,244));miniBoardHost.setVisible(miniToggle.isSelected());lower.add(miniBoardHost,BorderLayout.NORTH);
        JPanel journal=Theme.createCardPanel();journal.setLayout(new BorderLayout(0,6));
        JList<String> log=new JList<>(logModel);log.setBackground(Theme.PANEL_BG);log.setForeground(Theme.TEXT_MAIN);log.setFont(Theme.FONT_REGULAR);log.setFixedCellHeight(28);log.setFocusable(false);journal.add(Theme.scroll(log),BorderLayout.CENTER);lblAccuracy.setFont(Theme.FONT_REGULAR);journal.add(lblAccuracy,BorderLayout.SOUTH);lower.add(journal,BorderLayout.CENTER);
        updateFleet();focusBoard(true);
        oppBoardGrid.setCellClickListener((x,y)->{
            if(pendingShot||finished||animating||remainingTime<=0)return;
            if(!isMyTurn){hint("Đối thủ đang ra lệnh. Hãy chờ lượt của bạn.",Theme.TEXT_MUTED);return;}
            if(oppBoardGrid.getCellState(x,y)!=BoardGridPanel.CellState.WATER){hint("Tọa độ này đã được bắn. Chọn một ô khác.",Theme.WARNING);return;}
            GameAudio.play(GameAudio.Sound.SELECT);oppBoardGrid.setSelectedCoordinate(new Coordinate(x,y));lblTarget.setText(BoardGridPanel.coordinate(x,y));btnFire.setText("Khai hỏa  "+BoardGridPanel.coordinate(x,y)+"  →");btnFire.setEnabled(true);hint("Mục tiêu đã khóa. Nhấn Khai hỏa để bắn.",Theme.PRIMARY);
        });
        btnSurrender.addActionListener(e->confirmLeave());
        visualTimer=new Timer(100,e->updateClock());
        effectTimer=new Timer(16,e->tickEffect());GameAudio.battle(true);
    }
    public void setPractice(boolean value){practice=value;}
    public boolean isAnimating(){return animating;}
    private void focusBoard(boolean attack){
        mainBoardHost.removeAll();miniBoardHost.removeAll();
        BoardGridPanel large=attack?oppBoardGrid:myBoardGrid,small=attack?myBoardGrid:oppBoardGrid;
        large.setCompact(false);small.setCompact(true);mainBoardHost.add(large,BorderLayout.CENTER);miniBoardHost.add(small,BorderLayout.CENTER);
        focusTitle.setText(attack?"TẤN CÔNG  /  HẢI PHẬN ĐỐI THỦ":"PHÒNG THỦ  /  HẠM ĐỘI CỦA BẠN");focusTitle.setForeground(attack?Theme.PRIMARY:Theme.VIOLET);
        revalidate();repaint();
    }
    private void startEffect(boolean shooter,Coordinate cell,ShotResult result){
        if(impactAction!=null){impactAction.run();impactAction=null;}
        effectShooter=shooter;effectCell=cell;effectResult=result;focusBoard(shooter);validate();
        BoardGridPanel target=shooter?oppBoardGrid:myBoardGrid,source=shooter?myBoardGrid:oppBoardGrid;
        effectTo=target.cellCenter(cell.getX(),cell.getY(),this);
        effectFrom=miniToggle.isSelected()?source.cellCenter(5,5,this):new Point(getWidth()-100,90);
        effectStarted=System.nanoTime();impactPlayed=false;animating=true;oppBoardGrid.setInteractionEnabled(false);btnFire.setEnabled(false);GameAudio.play(GameAudio.Sound.LAUNCH);effectTimer.start();
    }
    private void tickEffect(){
        double t=(System.nanoTime()-effectStarted)/1e9;
        if(t>=.52&&!impactPlayed){impactPlayed=true;if(impactAction!=null){impactAction.run();impactAction=null;}GameAudio.play(effectResult==ShotResult.SUNK?GameAudio.Sound.SUNK:effectResult==ShotResult.HIT?GameAudio.Sound.HIT:GameAudio.Sound.MISS);}
        double duration=effectResult==ShotResult.SUNK?1.18:.82;
        if(t>=duration){effectTimer.stop();animating=false;focusBoard(isMyTurn);oppBoardGrid.setInteractionEnabled(isMyTurn&&!finished);Runnable next=afterEffect;afterEffect=null;if(next!=null)next.run();}repaint();
    }
    @Override protected void paintChildren(Graphics graphics){
        super.paintChildren(graphics);if(!animating||effectFrom==null||effectTo==null)return;
        Graphics2D g=Theme.graphics(graphics);double seconds=(System.nanoTime()-effectStarted)/1e9;
        if(seconds<.52){
            double p=Math.min(1,seconds/.52);double x=effectFrom.x+(effectTo.x-effectFrom.x)*p,y=effectFrom.y+(effectTo.y-effectFrom.y)*p-150*Math.sin(Math.PI*p);
            for(int i=12;i>0;i--){double q=Math.max(0,p-i*.018);int tx=(int)(effectFrom.x+(effectTo.x-effectFrom.x)*q),ty=(int)(effectFrom.y+(effectTo.y-effectFrom.y)*q-150*Math.sin(Math.PI*q));g.setColor(new Color(255,190,85,Math.max(10,160-i*12)));int r=3+i/2;g.fillOval(tx-r,ty-r,r*2,r*2);}
            g.translate(x,y);double angle=Math.atan2(effectTo.y-effectFrom.y-150*Math.PI*Math.cos(Math.PI*p),effectTo.x-effectFrom.x);g.rotate(angle);
            g.setColor(new Color(255,136,72));g.fillPolygon(new int[]{-21,-35,-21},new int[]{-7,0,7},3);g.setColor(new Color(228,237,255));g.fillRoundRect(-21,-8,32,16,8,8);g.setColor(Theme.DANGER);g.fillPolygon(new int[]{8,20,8},new int[]{-8,0,8},3);g.setColor(Theme.VIOLET);g.fillRect(-16,-11,7,22);
        }else{
            double p=Math.min(1,(seconds-.52)/(effectResult==ShotResult.SUNK?.66:.30));int x=effectTo.x,y=effectTo.y;
            Color color=effectResult==ShotResult.MISS?Theme.BLUE:Theme.WARNING;
            for(int i=0;i<3;i++){int r=(int)(12+p*(70+i*25));g.setColor(new Color(color.getRed(),color.getGreen(),color.getBlue(),(int)(120*(1-p))));g.setStroke(new BasicStroke((float)Math.max(1,6*(1-p))));g.drawOval(x-r,y-r,r*2,r*2);}
            if(effectResult!=ShotResult.MISS){for(int i=0;i<24;i++){double a=i*2*Math.PI/24;int distance=(int)(p*(i%3==0?125:80));int px=x+(int)(Math.cos(a)*distance),py=y+(int)(Math.sin(a)*distance+p*p*35);int size=(int)(11*(1-p))+2;g.setColor(new Color(255,i%2==0?132:213,86,(int)(255*(1-p))));g.fillRect(px,py,size,size);}g.setColor(new Color(255,236,167,(int)(190*(1-p))));int r=(int)(38*(1-p));g.fillOval(x-r,y-r,r*2,r*2);}
            if(effectResult==ShotResult.SUNK){g.setFont(Theme.FONT_TITLE);String text="ĐÁNH CHÌM!";int width=g.getFontMetrics().stringWidth(text);int tx=Math.max(15,Math.min(getWidth()-width-20,x-width/2)),ty=Math.max(80,y-55-(int)(p*25));g.setColor(Theme.BG_DARK);g.fillRoundRect(tx-14,ty-34,width+28,48,12,12);g.setColor(Theme.WARNING);g.drawString(text,tx,ty);}
        }g.dispose();
    }
    private JPanel boardColumn(BoardGridPanel board,JLabel fleet){JPanel p=Theme.transparent(new BorderLayout(0,12));p.add(fleet,BorderLayout.NORTH);p.add(board,BorderLayout.CENTER);return p;}
    private void updateFleet(){lblMyFleetStatus.setText(myName+"  •  "+myRemainingShips+" / 5 tàu");lblOppFleetStatus.setText(oppName+"  •  "+oppRemainingShips+" / 5 tàu");}
    private void hint(String text,Color color){lblHint.setText("<html>"+Theme.html(text)+"</html>");lblHint.setForeground(color);}
    private void log(String text){logModel.add(0,text);while(logModel.size()>30)logModel.remove(logModel.size()-1);}
    public void showNotice(String text){hint(text,Theme.WARNING);log(text);}
    private void fire(){Coordinate c=oppBoardGrid.getSelectedCoordinate();if(c==null||!isMyTurn||pendingShot||finished||animating||remainingTime<=0)return;pendingShot=true;btnFire.setEnabled(false);btnFire.setText("Đang khai hỏa...");hint("Đang chờ kết quả từ máy chủ.",Theme.TEXT_MUTED);actionListener.onFire(c.getX(),c.getY());}
    public void confirmLeave(){if(finished){actionListener.onExitToLobby();return;}int choice=JOptionPane.showConfirmDialog(this,practice?"Kết thúc luyện tập và về sảnh?":"Rời trận bị tính thua, trừ 3 điểm (không dưới 0) và cấm ghép trận 1 phút.\nBạn có muốn rời trận và trở về sảnh?","Rời trận đấu",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE);if(choice==JOptionPane.YES_OPTION){btnSurrender.setEnabled(false);actionListener.onSurrender();}}
    public void leaveRejected(){btnSurrender.setEnabled(!finished);}
    private void updateClock(){long left=Math.max(0,localDeadlineNanos-System.nanoTime());remainingTime=(int)((left+999999999L)/1000000000L);lblTimer.setText(String.format("%02d",remainingTime));Color color=remainingTime<=5?Theme.DANGER:Theme.PRIMARY;lblTimer.setForeground(color);turnProgress.setForeground(color);turnProgress.setValue((int)(1000.0*left/Math.max(1,turnDurationNanos)));if(remainingTime==0){btnFire.setEnabled(false);hint("Hết thời gian. Đang chờ chuyển lượt...",Theme.WARNING);}}
    public void setTurn(boolean myTurn,int timeLimitSec){applyTurn(myTurn,Math.max(0,timeLimitSec)*1000L);}
    public void setServerTurn(boolean myTurn,long deadline,long serverTime){applyTurn(myTurn,Math.max(0,deadline-serverTime));}
    private void applyTurn(boolean myTurn,long remainingMillis){
        if(finished)return;isMyTurn=myTurn;pendingShot=false;turnDurationNanos=java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(remainingMillis);localDeadlineNanos=System.nanoTime()+turnDurationNanos;
        oppBoardGrid.setSelectedCoordinate(null);lblTarget.setText("— —");btnFire.setText("Khai hỏa  →");btnFire.setEnabled(false);oppBoardGrid.setInteractionEnabled(myTurn&&!animating);if(!animating)focusBoard(myTurn);if(myTurn)GameAudio.play(GameAudio.Sound.TURN);
        lblTurnStatus.setText(myTurn?"Đến lượt của bạn":"Đối thủ đang bắn");lblTurnStatus.setForeground(myTurn?Theme.PRIMARY:Theme.TEXT_MUTED);
        hint(myTurn?"Chọn ô để khai hỏa.":"Đối thủ đang ngắm bắn…",Theme.TEXT_MUTED);updateClock();visualTimer.restart();
    }
    public void shotRejected(){pendingShot=false;Coordinate c=oppBoardGrid.getSelectedCoordinate();btnFire.setEnabled(!finished&&isMyTurn&&remainingTime>0&&c!=null);btnFire.setText("Thử khai hỏa lại");}
    public void handleFireResult(boolean iAmShooter,Coordinate c,ShotResult result,List<Coordinate> sunkCoords,int shipsLeft,boolean keepTurn){
        startEffect(iAmShooter,c,result);
        BoardGridPanel grid=iAmShooter?oppBoardGrid:myBoardGrid;
        BoardGridPanel.CellState state=result==ShotResult.MISS?BoardGridPanel.CellState.MISS:result==ShotResult.HIT?BoardGridPanel.CellState.HIT:BoardGridPanel.CellState.SUNK;
        impactAction=()->{grid.setCellState(c.getX(),c.getY(),state);if(result==ShotResult.SUNK&&sunkCoords!=null)for(Coordinate cell:sunkCoords)grid.setCellState(cell.getX(),cell.getY(),state);};
        if(iAmShooter){pendingShot=false;shots++;if(result!=ShotResult.MISS)hits++;oppRemainingShips=shipsLeft;oppBoardGrid.setSelectedCoordinate(null);btnFire.setEnabled(false);btnFire.setText("Khai hỏa  →");lblTarget.setText("— —");if(!keepTurn)isMyTurn=false;lblAccuracy.setText(shots+" phát bắn  /  "+hits+" trúng đích");}else myRemainingShips=shipsLeft;
        String outcome=result==ShotResult.MISS?"Trượt":result==ShotResult.HIT?"Trúng tàu":"Đánh chìm";
        log((iAmShooter?"Bạn":"Đối thủ")+"  •  "+BoardGridPanel.coordinate(c.getX(),c.getY())+"  •  "+outcome);updateFleet();
    }
    public void showGameOver(boolean iWon,int awardedPoints,String reason){
        if(animating){finished=true;visualTimer.stop();btnFire.setEnabled(false);afterEffect=()->showGameOver(iWon,awardedPoints,reason);return;}
        GameAudio.battle(false);GameAudio.play(iWon?GameAudio.Sound.WIN:GameAudio.Sound.LOSE);
        finished=true;isMyTurn=false;pendingShot=false;visualTimer.stop();btnFire.setEnabled(false);btnSurrender.setEnabled(false);oppBoardGrid.setInteractionEnabled(false);lblTurnStatus.setText("Trận đấu kết thúc");lblTimer.setText("—");turnProgress.setValue(0);
        resultDialog=new JDialog(SwingUtilities.getWindowAncestor(this),"Battleship • Kết quả trận đấu",Dialog.ModalityType.MODELESS);resultDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        JPanel result=Theme.transparent(new BorderLayout(0,24));result.setOpaque(true);result.setBackground(Theme.BG_DARK);result.setBorder(new EmptyBorder(30,32,28,32));
        JPanel summary=Theme.stack();Color accent=iWon?Theme.PRIMARY:Theme.WARNING;Theme.addStack(summary,Theme.badge(iWon?"NHIỆM VỤ HOÀN THÀNH":"NHIỆM VỤ KẾT THÚC",accent),18);
        Theme.addStack(summary,Theme.label(iWon?"Chiến thắng, chỉ huy!":"Hẹn lần ra khơi tiếp theo.",Theme.FONT_TITLE,Theme.TEXT_MAIN),10);
        String reasonText="ALL_SHIPS_DESTROYED".equals(reason)?(iWon?"Bạn đã đánh chìm toàn bộ hạm đội đối thủ.":"Hạm đội của bạn đã bị đánh chìm."):"DISCONNECTED".equals(reason)?"Trận đấu kết thúc do mất kết nối.":"Trận đấu kết thúc do một người chơi rời trận.";
        Theme.addStack(summary,Theme.label("<html>"+Theme.html(reasonText)+"</html>",Theme.FONT_REGULAR,Theme.TEXT_MUTED),22);
        JPanel stats=Theme.card(Theme.PANEL_BG,18);stats.setLayout(new GridLayout(1,3,18,0));stats.add(resultStat("THAY ĐỔI ĐIỂM",String.format("%+d",awardedPoints)));stats.add(resultStat("PHÁT BẮN",String.valueOf(shots)));stats.add(resultStat("CHÍNH XÁC",(shots==0?0:Math.round(100.0*hits/shots))+"%"));stats.setMaximumSize(new Dimension(Integer.MAX_VALUE,100));Theme.addStack(summary,stats,20);
        resultHint=Theme.label("Chơi thêm một ván, hay trở về phòng chỉ huy?",Theme.FONT_SMALL,Theme.TEXT_MUTED);Theme.addStack(summary,resultHint,0);result.add(summary,BorderLayout.CENTER);
        JPanel buttons=Theme.transparent(new GridLayout(1,2,12,0));rematchButton=Theme.button("Chơi lại (Rematch)",true);JButton exit=Theme.button("Về phòng chỉ huy",false);
        rematchButton.addActionListener(e->{rematchButton.setEnabled(false);rematchButton.setText("Đã gửi đề nghị");resultHint.setText("Đang chờ đối thủ đồng ý chơi lại...");actionListener.onRematch();});exit.addActionListener(e->{resultDialog.dispose();actionListener.onExitToLobby();});buttons.add(rematchButton);buttons.add(exit);result.add(buttons,BorderLayout.SOUTH);
        resultDialog.addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosing(java.awt.event.WindowEvent e){resultDialog.dispose();actionListener.onExitToLobby();}});
        resultDialog.setContentPane(result);resultDialog.setSize(560,480);resultDialog.setResizable(false);resultDialog.setLocationRelativeTo(this);resultDialog.setVisible(true);
    }
    private JPanel resultStat(String title,String value){JPanel p=Theme.stack();Theme.addStack(p,Theme.muted(title),10);Theme.addStack(p,Theme.label(value,Theme.FONT_TITLE,Theme.PRIMARY),0);return p;}
    public void rematchRejected(){if(rematchButton!=null){rematchButton.setEnabled(true);rematchButton.setText("Chơi lại (Rematch)");resultHint.setText("Chưa thể chơi lại. Bạn có thể thử lại hoặc về sảnh.");}}
    public void disposePanel(){effectTimer.stop();animating=false;afterEffect=null;impactAction=null;GameAudio.battle(false);if(resultDialog!=null)resultDialog.dispose();visualTimer.stop();finished=true;pendingShot=false;oppBoardGrid.setCellClickListener(null);}
    @Override public void removeNotify(){if(visualTimer!=null)visualTimer.stop();if(effectTimer!=null)effectTimer.stop();GameAudio.battle(false);super.removeNotify();}
}
