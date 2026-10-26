package client.ui;

import common.model.Coordinate;
import common.model.Ship;
import common.model.ShotResult;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.*;
import java.util.List;

/**
 * Combat screen. The whole phase is staged at sea: an animated swell behind translucent decks, a
 * sonar scope around the turn clock, a water-tube turn timer, glass tiles over living water on the
 * boards and shell impacts that throw foam and wreck bubbles.
 */
public class BattlePanel extends OceanPanel {
    public interface BattleActionListener {void onFire(int x,int y);void onSurrender();void onRematch();void onExitToLobby();}
    private final BattleActionListener actionListener;
    private final BoardGridPanel myBoardGrid,oppBoardGrid;
    private final JLabel lblTurnStatus=Theme.label("Chờ lệnh khai hỏa",Theme.FONT_HEADER,Theme.PRIMARY);
    private final JLabel lblTimer=Theme.label("15",Theme.FONT_GRID.deriveFont(Font.BOLD,44f),Theme.SEA_FOAM);
    private final JLabel lblMyFleetStatus=Theme.label("",Theme.FONT_BOLD,Theme.PRIMARY);
    private final JLabel lblOppFleetStatus=Theme.label("",Theme.FONT_BOLD,Theme.DANGER);
    private final JLabel lblTarget=Theme.label("— —",Theme.FONT_GRID.deriveFont(Font.BOLD,26f),Theme.SEA_FOAM);
    private final JLabel lblHint=Theme.label(" ",Theme.FONT_SMALL,Theme.TEXT_MUTED);
    private final JLabel lblAccuracy=Theme.muted("0 phát bắn  /  0 trúng đích");
    private final JButton btnFire=Theme.button("Khai hỏa  →",true);
    private final JButton btnSurrender=Theme.button("Rời trận",false);
    private final WaveProgress turnProgress=new WaveProgress(0,1000);
    private final DefaultListModel<String> logModel=new DefaultListModel<>();
    private final String myName,oppName;
    private JDialog resultDialog;
    private JButton rematchButton;
    private final JLabel lblConnection = Theme.muted("Đang đo kết nối...");
    private boolean resultStorageSaved = true;
    private boolean rematchAllowed = true;
    private Integer savedAward;
    private JLabel resultHint;
    private boolean pendingShot,finished,isMyTurn;
    private long localDeadlineNanos,turnDurationNanos;
    private int remainingTime=15,myRemainingShips=5,oppRemainingShips=5,shots,hits;
    private final Timer visualTimer;
    private final Timer effectTimer;
    private final JPanel mainBoardHost=Theme.transparent(new BorderLayout());
    private final JPanel miniBoardHost=Theme.transparent(new BorderLayout());
    private final SeaDecor miniSeascape=new SeaDecor(SeaDecor.Kind.HORIZON);
    private final JCheckBox miniToggle=new JCheckBox("Bản đồ thu nhỏ",true);
    private final JLabel focusTitle=Theme.label("HẢI PHẬN ĐỐI THỦ",Theme.FONT_HEADER,Theme.PRIMARY);
    private final Theme.SeaTitle titleLabel;
    private final java.util.prefs.Preferences preferences=java.util.prefs.Preferences.userNodeForPackage(BattlePanel.class);
    private boolean animating,impactPlayed,practice;
    private long effectStarted;
    private Coordinate effectCell;
    private ShotResult effectResult;
    private boolean effectShooter;
    private Point effectFrom,effectTo;
    private Runnable afterEffect,impactAction;
    /** Enemy fleet released by the server once the match is over, for the battle report. */
    private List<Ship> revealedFleet;
    /** Own fleet as deployed, kept for the after-action charts. */
    private final List<Ship> myFleet;


    public BattlePanel(String myName,String oppName,List<Ship> myShips,BattleActionListener actionListener) {
        super(new BorderLayout(20,14),"VÀO TRẬN","HẢI CHIẾN BẮT ĐẦU  •  "+myName+"  vs  "+oppName);
        this.actionListener=actionListener;this.myName=myName;this.oppName=oppName;this.myFleet=myShips;
        setBorder(new EmptyBorder(18,24,18,24));
        JPanel header=Theme.transparent(new BorderLayout(18,0));
        SeaDecor compass=new SeaDecor(SeaDecor.Kind.COMPASS);compass.setPreferredSize(new Dimension(56,56));header.add(compass,BorderLayout.WEST);
        JPanel title=Theme.stack();
        titleLabel=new Theme.SeaTitle(myName+"  vs  "+oppName,Theme.FONT_TITLE,Theme.GOLD,Theme.AZURE);titleLabel.setShimmer(true);
        Theme.addStack(title,titleLabel,4);Theme.addStack(title,focusTitle,2);
        SeaDecor rule=new SeaDecor(SeaDecor.Kind.WAVES);rule.setPreferredSize(new Dimension(0,8));rule.setMaximumSize(new Dimension(Integer.MAX_VALUE,8));Theme.addStack(title,rule,0);
        header.add(title,BorderLayout.CENTER);
        JPanel options=Theme.stack();
        JPanel controls=Theme.transparent(new FlowLayout(FlowLayout.RIGHT,12,4));
        miniToggle.setSelected(preferences.getBoolean("minimap",true));miniToggle.setOpaque(false);miniToggle.setForeground(Theme.TEXT_MAIN);miniToggle.setFont(Theme.FONT_REGULAR);miniToggle.addActionListener(e->{preferences.putBoolean("minimap",miniToggle.isSelected());miniBoardHost.setVisible(miniToggle.isSelected());revalidate();GameAudio.play(GameAudio.Sound.SELECT);});controls.add(miniToggle);
        controls.add(animationToggle());
        JButton settings=Theme.button("Cài đặt",false);settings.addActionListener(e->SettingsDialog.open(this));controls.add(settings);
        controls.add(btnSurrender);
        Theme.addStack(options,controls,0);Theme.addStack(options,lblConnection,3);Theme.addStack(options,seaState(),0);header.add(options,BorderLayout.EAST);add(header,BorderLayout.NORTH);
        myBoardGrid=new BoardGridPanel("Hạm đội của bạn",false);oppBoardGrid=new BoardGridPanel("Hạm đội đối thủ",true);
        myBoardGrid.setFleet(myShips);
        add(mainBoardHost,BorderLayout.CENTER);
        // combat log runs as a wide bar under the boards, so the entries stay readable
        JPanel journal=Theme.seaCard(152,14);journal.setLayout(new BorderLayout(12,4));journal.setPreferredSize(new Dimension(0,104));
        SeaDecor depth=new SeaDecor(SeaDecor.Kind.DEPTH);depth.setPreferredSize(new Dimension(46,0));journal.add(depth,BorderLayout.WEST);
        JList<String> log=new JList<>(logModel);log.setOpaque(false);log.setBackground(new Color(0,0,0,0));
        log.setForeground(Theme.TEXT_MAIN);log.setFont(Theme.FONT_REGULAR);log.setFixedCellHeight(24);log.setFocusable(false);
        log.setSelectionBackground(new Color(44,120,140,150));log.setSelectionForeground(Theme.SEA_FOAM);log.setCellRenderer(Theme.logRenderer());
        JScrollPane logScroll=Theme.scroll(log);Theme.transparentScroll(logScroll);
        journal.add(logScroll,BorderLayout.CENTER);
        JPanel journalFoot=Theme.transparent(new BorderLayout(10,0));journalFoot.add(Theme.label("NHẬT KÝ HẢI CHIẾN",Theme.FONT_SMALL,Theme.SEA_HORIZON),BorderLayout.WEST);
        journalFoot.add(lblAccuracy,BorderLayout.EAST);journal.add(journalFoot,BorderLayout.SOUTH);
        JPanel centre=Theme.transparent(new BorderLayout(0,12));centre.add(mainBoardHost,BorderLayout.CENTER);centre.add(journal,BorderLayout.SOUTH);
        add(centre,BorderLayout.CENTER);
        JPanel right=Theme.transparent(new BorderLayout(0,14));right.setPreferredSize(new Dimension(290,0));add(right,BorderLayout.EAST);
        JPanel command=Theme.seaCard(152,16);command.setLayout(new BoxLayout(command,BoxLayout.Y_AXIS));
        Theme.addStack(command,lblTurnStatus,8);
        SeaDecor scope=new SeaDecor(SeaDecor.Kind.SONAR,new BorderLayout(10,0));scope.add(lblTimer,BorderLayout.WEST);lblTarget.setForeground(Theme.WARNING);scope.add(lblTarget,BorderLayout.EAST);
        scope.setPreferredSize(new Dimension(250,84));scope.setMaximumSize(new Dimension(Integer.MAX_VALUE,84));Theme.addStack(command,scope,6);
        turnProgress.setPreferredSize(new Dimension(180,10));turnProgress.setMaximumSize(new Dimension(Integer.MAX_VALUE,10));turnProgress.setForeground(Theme.PRIMARY);Theme.addStack(command,turnProgress,14);
        btnFire.setMaximumSize(new Dimension(Integer.MAX_VALUE,46));btnFire.setEnabled(false);btnFire.addActionListener(e->fire());Theme.addStack(command,btnFire,8);
        lblHint.setMaximumSize(new Dimension(Integer.MAX_VALUE,42));lblHint.setPreferredSize(new Dimension(245,42));Theme.addStack(command,lblHint,10);
        Theme.addStack(command,lblMyFleetStatus,6);Theme.addStack(command,lblOppFleetStatus,0);right.add(command,BorderLayout.NORTH);
        miniBoardHost.setVisible(miniToggle.isSelected());
        miniSeascape.setPreferredSize(new Dimension(290,30));miniBoardHost.add(miniSeascape,BorderLayout.NORTH);right.add(miniBoardHost,BorderLayout.CENTER);
        updateFleet();focusBoard(true);
        oppBoardGrid.setCellClickListener((x,y)->{
            if(pendingShot||finished||animating||remainingTime<=0)return;
            if(!isMyTurn){hint("Đối thủ đang ra lệnh. Hãy chờ lượt của bạn.",Theme.TEXT_MUTED);return;}
            if(oppBoardGrid.getCellState(x,y)!=BoardGridPanel.CellState.WATER){hint("Tọa độ này đã được bắn. Chọn một ô khác.",Theme.WARNING);return;}
            GameAudio.play(GameAudio.Sound.SELECT);oppBoardGrid.setSelectedCoordinate(new Coordinate(x,y));lblTarget.setText(BoardGridPanel.coordinate(x,y));btnFire.setText("Khai hỏa  "+BoardGridPanel.coordinate(x,y)+"  →");btnFire.setEnabled(true);hint("Mục tiêu đã khóa. Nhấn Khai hỏa để bắn.",Theme.PRIMARY);
        });
        btnSurrender.addActionListener(e->confirmLeave());
        visualTimer=new Timer(100,e->updateClock());
        effectTimer=new Timer(16,e->tickEffect());GameAudio.battle(true);GameAudio.play(GameAudio.Sound.PING);GameAudio.play(GameAudio.Sound.WAVE);
    }
    public void setPractice(boolean value){practice=value;}
    public boolean isAnimating(){return animating;}
    private void focusBoard(boolean attack){
        mainBoardHost.removeAll();miniBoardHost.removeAll();
        BoardGridPanel large=attack?oppBoardGrid:myBoardGrid,small=attack?myBoardGrid:oppBoardGrid;
        large.setCompact(false);small.setCompact(true);mainBoardHost.add(large,BorderLayout.CENTER);
        miniBoardHost.add(miniSeascape,BorderLayout.NORTH);miniBoardHost.add(small,BorderLayout.CENTER);
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
        if(t>=.52&&!impactPlayed){impactPlayed=true;if(impactAction!=null){impactAction.run();impactAction=null;}GameAudio.play(effectResult==ShotResult.SUNK?GameAudio.Sound.SUNK:effectResult==ShotResult.HIT?GameAudio.Sound.HIT:GameAudio.Sound.SPLASH);}
        double duration=effectResult==ShotResult.SUNK?1.18:.82;
        if(t>=duration){effectTimer.stop();animating=false;focusBoard(isMyTurn);oppBoardGrid.setInteractionEnabled(isMyTurn&&!finished);Runnable next=afterEffect;afterEffect=null;if(next!=null)next.run();}repaint();
    }
    @Override protected void paintChildren(Graphics graphics){
        super.paintChildren(graphics);if(!animating||effectFrom==null||effectTo==null)return;
        Graphics2D g=Theme.graphics(graphics);double seconds=(System.nanoTime()-effectStarted)/1e9;
        if(seconds<.52){
            double p=Math.min(1,seconds/.52);double x=effectFrom.x+(effectTo.x-effectFrom.x)*p,y=effectFrom.y+(effectTo.y-effectFrom.y)*p-150*Math.sin(Math.PI*p);
            double angle=Math.atan2(effectTo.y-effectFrom.y-150*Math.PI*Math.cos(Math.PI*p),effectTo.x-effectFrom.x);
            for(int i=18;i>0;i--){
                double q=Math.max(0,p-i*.016);int tx=(int)(effectFrom.x+(effectTo.x-effectFrom.x)*q),ty=(int)(effectFrom.y+(effectTo.y-effectFrom.y)*q-150*Math.sin(Math.PI*q));
                int size=3+i/3;g.setColor(new Color(196,222,238,(int)(64*(1-i/18.0)*p)));g.fillOval(tx-size,ty-size,size*2,size*2);
                if(i<=12){double e=Math.max(0,p-i*.018);int ex=(int)(effectFrom.x+(effectTo.x-effectFrom.x)*e),ey=(int)(effectFrom.y+(effectTo.y-effectFrom.y)*e-150*Math.sin(Math.PI*e));int r=3+i/2;
                    g.setColor(new Color(255,190,85,Math.max(10,160-i*12)));g.fillOval(ex-r,ey-r,r*2,r*2);}
            }
            if(seconds<.26){double launch=1-seconds/.26;g.setColor(new Color(236,252,255,(int)(190*launch)));int lr=(int)(10+(1-launch)*26);g.setStroke(new BasicStroke((float)Math.max(1,4*launch)));g.drawOval(effectFrom.x-lr,effectFrom.y-lr,lr*2,lr*2);}
            g.translate(x,y);g.rotate(angle);
            g.setColor(new Color(255,136,72));g.fillPolygon(new int[]{-21,-35,-21},new int[]{-7,0,7},3);g.setColor(new Color(228,237,255));g.fillRoundRect(-21,-8,32,16,8,8);g.setColor(Theme.DANGER);g.fillPolygon(new int[]{8,20,8},new int[]{-8,0,8},3);g.setColor(Theme.VIOLET);g.fillRect(-16,-11,7,22);
        }else{
            double p=Math.min(1,(seconds-.52)/(effectResult==ShotResult.SUNK?.66:.30));int x=effectTo.x,y=effectTo.y;
            Color color=effectResult==ShotResult.MISS?Theme.BLUE:Theme.WARNING;
            for(int i=0;i<3;i++){int r=(int)(12+p*(70+i*25));g.setColor(new Color(color.getRed(),color.getGreen(),color.getBlue(),(int)(120*(1-p))));g.setStroke(new BasicStroke((float)Math.max(1,6*(1-p))));g.drawOval(x-r,y-r,r*2,r*2);}
            // foam ring rolling outward on the surface
            int foam=(int)(p*(96+40*Math.min(1,p)));g.setColor(new Color(236,252,255,(int)(190*(1-p))));g.setStroke(new BasicStroke((float)Math.max(1,3.4*(1-p))));g.drawOval(x-foam,(int)(y-foam*.55),foam*2,(int)(foam*1.1));
            if(effectResult==ShotResult.MISS){
                double rise=Math.min(1,p*1.8),fall=Math.max(0,(p-.42)/.58),column=118*rise*(1-fall*.9);
                if(column>2){
                    Path2D jet=new Path2D.Double();jet.moveTo(x-10,y+4);jet.lineTo(x-4.5,y-column);jet.lineTo(x+4.5,y-column);jet.lineTo(x+10,y+4);jet.closePath();
                    g.setColor(new Color(238,252,255,(int)(205*(1-p*.7))));g.fill(jet);
                    g.setColor(new Color(255,255,255,(int)(170*(1-p))));g.fill(new Ellipse2D.Double(x-13,y-column-14,26,21));
                }
                for(int i=0;i<26;i++){double a=i*2*Math.PI/26,distance=p*(96+i%3*22);int px=x+(int)(Math.cos(a)*distance),py=y+(int)(Math.sin(a)*distance*.6-Math.sin(p*Math.PI)*46);int size=(int)(9*(1-p))+2;g.setColor(new Color(236,252,255,(int)(230*(1-p))));g.fillOval(px,py,size,size);}
            }else{
                for(int i=0;i<24;i++){double a=i*2*Math.PI/24;int distance=(int)(p*(i%3==0?125:80));int px=x+(int)(Math.cos(a)*distance),py=y+(int)(Math.sin(a)*distance+p*p*35);int size=(int)(11*(1-p))+2;g.setColor(new Color(255,i%2==0?132:213,86,(int)(255*(1-p))));g.fillRect(px,py,size,size);}
                g.setColor(new Color(255,236,167,(int)(190*(1-p))));int r=(int)(38*(1-p));g.fillOval(x-r,y-r,r*2,r*2);
                for(int i=0;i<18;i++){double a=i*2*Math.PI/18;int distance=(int)(p*(i%2==0?104:76));int size=(int)(7*(1-p))+2;
                    g.setColor(new Color(240,253,255,(int)(215*(1-p))));g.fillOval(x+(int)(Math.cos(a)*distance),y+(int)(Math.sin(a)*distance*.55-p*22),size,size);}
                for(int i=0;i<7;i++){double a=i*2*Math.PI/7+.4,distance=p*(26+i*7),size=10+p*30;
                    g.setColor(new Color(38,50,68,(int)(95*(1-p))));g.fillOval(x+(int)(Math.cos(a)*distance)-(int)size/2,(int)(y+Math.sin(a)*distance*.5-p*p*84)-(int)size/2,(int)size,(int)size);}
            }
            if(effectResult==ShotResult.SUNK){
                g.setFont(Theme.FONT_TITLE);String text="ĐÁNH CHÌM!";int width=g.getFontMetrics().stringWidth(text);
                int tx=Math.max(15,Math.min(getWidth()-width-20,x-width/2)),ty=Math.max(80,y-55-(int)(p*25));
                g.setColor(new Color(6,22,44,220));g.fillRoundRect(tx-14,ty-34,width+28,52,12,12);
                g.setColor(Theme.WARNING);g.drawString(text,tx,ty);
                OceanScene.paintWaveRule(g,tx-8,ty+8,width+16,seconds,Theme.SEA_FOAM,0.55);
            }
        }g.dispose();
    }
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
        lblTurnStatus.setText(myTurn?"Đến lượt của bạn":"Đối thủ đang bắn");lblTurnStatus.setForeground(myTurn?Theme.MINT:Theme.CORAL);
        hint(myTurn?"Chọn ô để khai hỏa.":"Đối thủ đang ngắm bắn…",Theme.TEXT_MUTED);updateClock();visualTimer.restart();
    }
    public void shotRejected(){pendingShot=false;Coordinate c=oppBoardGrid.getSelectedCoordinate();btnFire.setEnabled(!finished&&isMyTurn&&remainingTime>0&&c!=null);btnFire.setText("Thử khai hỏa lại");GameAudio.play(GameAudio.Sound.MISS);}
    public void handleFireResult(boolean iAmShooter,Coordinate c,ShotResult result,List<Coordinate> sunkCoords,int shipsLeft,boolean keepTurn){
        startEffect(iAmShooter,c,result);
        BoardGridPanel grid=iAmShooter?oppBoardGrid:myBoardGrid;
        BoardGridPanel.CellState state=result==ShotResult.MISS?BoardGridPanel.CellState.MISS:result==ShotResult.HIT?BoardGridPanel.CellState.HIT:BoardGridPanel.CellState.SUNK;
        impactAction=()->{grid.setCellState(c.getX(),c.getY(),state);if(result==ShotResult.SUNK&&sunkCoords!=null)for(Coordinate cell:sunkCoords)grid.setCellState(cell.getX(),cell.getY(),state);};
        if(iAmShooter){pendingShot=false;shots++;if(result!=ShotResult.MISS)hits++;oppRemainingShips=shipsLeft;oppBoardGrid.setSelectedCoordinate(null);btnFire.setEnabled(false);btnFire.setText("Khai hỏa  →");lblTarget.setText("— —");if(!keepTurn)isMyTurn=false;lblAccuracy.setText(shots+" phát bắn  /  "+hits+" trúng đích");}else myRemainingShips=shipsLeft;
        String outcome=result==ShotResult.MISS?"Trượt":result==ShotResult.HIT?"Trúng tàu":"Đánh chìm";
        log((iAmShooter?"Bạn":"Đối thủ")+"  •  "+BoardGridPanel.coordinate(c.getX(),c.getY())+"  •  "+outcome);updateFleet();
    }
    public void showGameOver(boolean iWon,int awardedPoints,String reason){showGameOver(iWon,awardedPoints,reason,null,0);}
    public void showGameOver(boolean iWon,int awardedPoints,String reason,List<Ship> opponentFleet){showGameOver(iWon,awardedPoints,reason,opponentFleet,0);}
    /**
     * Ends the match with the full-screen result: the animated WIN / LOSE headline first, then both
     * charts are thrown open with the released enemy fleet next to your own. The result stays
     * modeless so the rematch offer, the exit button and the chat keep working while it plays.
     */
    public void showGameOver(boolean iWon,int awardedPoints,String reason,List<Ship> opponentFleet,int opponentShots){
        showGameOver(iWon,awardedPoints,reason,opponentFleet,opponentShots,0);
    }
    public void showGameOver(boolean iWon,int awardedPoints,String reason,List<Ship> opponentFleet,int opponentShots,long durationSeconds){
        rematchAllowed = !"SURRENDER".equals(reason) && !"DISCONNECTED".equals(reason);
        if(animating){finished=true;visualTimer.stop();btnFire.setEnabled(false);afterEffect=()->showGameOver(iWon,awardedPoints,reason,opponentFleet,opponentShots,durationSeconds);return;}
        GameAudio.battle(false);GameAudio.play(iWon?GameAudio.Sound.WIN:GameAudio.Sound.LOSE);
        finished=true;isMyTurn=false;pendingShot=false;visualTimer.stop();btnFire.setEnabled(false);btnSurrender.setEnabled(false);oppBoardGrid.setInteractionEnabled(false);lblTurnStatus.setText("Trận đấu kết thúc");lblTimer.setText("—");turnProgress.setValue(0);
        if(opponentFleet!=null)oppBoardGrid.revealFleet(opponentFleet);
        revealedFleet=opponentFleet;
        // the result screen covers the battle, so the combat screen's sea clock can rest
        disposeOcean();
        Window owner=SwingUtilities.getWindowAncestor(this);
        resultDialog=new JDialog(owner,"Battleship • Kết quả trận đấu",Dialog.ModalityType.MODELESS);
        resultDialog.setUndecorated(true);
        resultDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        MatchResultView result=new MatchResultView(iWon,myName,oppName,reason,awardedPoints,shots,hits,opponentShots,
                durationSeconds,myFleet,myBoardGrid,revealedFleet,oppBoardGrid);
        rematchButton=result.rematchButton();resultHint=result.hintLabel();
        if (savedAward!=null) result.setAwardedPoints(savedAward);
        applyResultStorage();
        rematchButton.addActionListener(e->{rematchButton.setEnabled(false);rematchButton.setText("Đã gửi đề nghị");resultHint.setText("Đang chờ đối thủ đồng ý chơi lại...");actionListener.onRematch();});
        result.exitButton().addActionListener(e->{resultDialog.dispose();actionListener.onExitToLobby();});
        resultDialog.addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosing(java.awt.event.WindowEvent e){resultDialog.dispose();actionListener.onExitToLobby();}});
        resultDialog.setContentPane(result);
        if(owner!=null && owner.getWidth()>420 && owner.getHeight()>320)resultDialog.setBounds(owner.getBounds());
        else{resultDialog.setSize(1180,800);resultDialog.setLocationRelativeTo(this);}
        resultDialog.setVisible(true);
        resultDialog.toFront();
    }
    public void setConnectionStatus(String value) { lblConnection.setText(value); }
    public void setResultStorage(boolean saved) { resultStorageSaved = saved; applyResultStorage(); }
    public void setSavedAward(int points) {
        savedAward=points;
        if (resultDialog!=null && resultDialog.getContentPane() instanceof MatchResultView)
            ((MatchResultView)resultDialog.getContentPane()).setAwardedPoints(points);
    }
    private void applyResultStorage() {
        if (rematchButton == null) return;
        rematchButton.setEnabled(resultStorageSaved && rematchAllowed);
        if (!resultStorageSaved) resultHint.setText("Kết quả đang chờ lưu; điểm chưa được áp dụng. Bạn có thể về sảnh.");
        else if (!rematchAllowed) resultHint.setText("Trận đã kết thúc do rời trận hoặc mất kết nối. Hãy về sảnh.");
        else resultHint.setText("Kết quả đã được ghi nhận. Có thể đề nghị chơi lại hoặc về sảnh.");
    }
    public void rematchRejected(){if(rematchButton!=null){applyResultStorage();rematchButton.setText("Chơi lại (Rematch)");}}
    /** Minimap on/off from the settings dialog; also keeps the header toggle in sync. */
    public void setMinimapVisible(boolean value){
        preferences.putBoolean("minimap",value);
        if(miniToggle.isSelected()!=value)miniToggle.setSelected(value);
        miniBoardHost.setVisible(value);
        revalidate();repaint();
    }
    public void disposePanel(){effectTimer.stop();animating=false;afterEffect=null;impactAction=null;GameAudio.battle(false);if(resultDialog!=null)resultDialog.dispose();visualTimer.stop();disposeOcean();finished=true;pendingShot=false;oppBoardGrid.setCellClickListener(null);}
    @Override public void removeNotify(){if(visualTimer!=null)visualTimer.stop();if(effectTimer!=null)effectTimer.stop();GameAudio.battle(false);super.removeNotify();}
}
