package client.ui;

import common.config.GameConfig;
import common.model.Board;
import common.model.Coordinate;
import common.model.Ship;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;

public class PlacementPanel extends JPanel {
    public interface ReadyListener { void onReady(List<Ship> ships); }
    private static final String[] SHIP_IDS={"CARRIER","BATTLESHIP","CRUISER_1","CRUISER_2","DESTROYER"};
    private static final String[] SHIP_NAMES={"Tàu sân bay","Thiết giáp hạm","Tuần dương I","Tuần dương II","Khu trục hạm"};
    private static final int[] SHIP_SIZES=GameConfig.SHIP_SIZES;
    private final BoardGridPanel gridPanel;
    private final ReadyListener readyListener;
    private final List<Ship> placedShips=new ArrayList<>();
    private final JComboBox<String> cbShips=new JComboBox<>(SHIP_NAMES);
    private final JButton[] fleetButtons=new JButton[5];
    private final JButton btnRotate=Theme.button("Hướng ngang  ↔   [R]",false);
    private final JButton btnRandom=Theme.button("Xếp ngẫu nhiên",false);
    private final JButton btnReset=Theme.button("Đặt lại",false);
    private final JButton btnReady=Theme.button("Sẵn sàng chiến đấu  →",true);
    private final JButton btnLeave=Theme.button("←  Về sảnh",false);
    private final JLabel lblInstruction=Theme.label(" ",Theme.FONT_SMALL,Theme.TEXT_MUTED);
    private final JLabel lblProgress=Theme.label("0 / 5 tàu đã bố trí",Theme.FONT_BOLD,Theme.PRIMARY);
    private final JLabel lblOpponent=Theme.badge("●  Đối thủ đang bố trí",Theme.TEXT_MUTED);
    private final JLabel lblMatch=Theme.muted("Bố trí bí mật. Đối thủ không nhìn thấy tàu của bạn.");
    private final JProgressBar progress=new JProgressBar(0,5);
    private boolean locked,ready,opponentReady,isHorizontal=true;
    private int currentShipIndex,hoverX=-1,hoverY=-1;
    private Runnable cancelReady,leaveListener;

    public PlacementPanel(ReadyListener readyListener) {
        this.readyListener=readyListener;setLayout(new BorderLayout(22,18));setBackground(Theme.BG_DARK);setBorder(new EmptyBorder(24,28,24,28));
        JPanel header=Theme.transparent(new BorderLayout());JPanel heading=Theme.stack();
        Theme.addStack(heading,Theme.label("Triển khai hạm đội",Theme.FONT_TITLE,Theme.TEXT_MAIN),5);Theme.addStack(heading,lblMatch,0);header.add(heading,BorderLayout.CENTER);
        JPanel headerActions=Theme.stack();Theme.addStack(headerActions,btnLeave,12);Theme.addStack(headerActions,lblOpponent,0);btnLeave.addActionListener(e->{if(leaveListener!=null){btnLeave.setEnabled(false);leaveListener.run();}});header.add(headerActions,BorderLayout.EAST);add(header,BorderLayout.NORTH);
        gridPanel=new BoardGridPanel("HẢI PHẬN CỦA BẠN",true);gridPanel.setCaption("Rê chuột để xem trước  •  Nhấn R để xoay tàu");add(gridPanel,BorderLayout.CENTER);
        JPanel side=Theme.createCardPanel();side.setLayout(new BorderLayout(0,14));side.setPreferredSize(new Dimension(328,0));add(side,BorderLayout.EAST);
        JPanel fleet=Theme.stack();Theme.addStack(fleet,Theme.label("Biên đội của bạn",Theme.FONT_HEADER,Theme.TEXT_MAIN),6);
        Theme.addStack(fleet,Theme.muted("Chọn tàu, sau đó chọn vị trí trên bản đồ."),14);
        cbShips.addActionListener(e->{currentShipIndex=cbShips.getSelectedIndex();refreshFleet();preview(hoverX,hoverY);});
        for(int i=0;i<5;i++){
            final int index=i;JButton b=new JButton(){
                @Override protected void paintComponent(Graphics graphics){
                    super.paintComponent(graphics);Graphics2D g=Theme.graphics(graphics);g.setColor(locked?Theme.TEXT_MUTED:Theme.PRIMARY);
                    int start=getWidth()-94;for(int n=0;n<SHIP_SIZES[index];n++)g.fillRoundRect(start+n*13,getHeight()/2-4,10,8,3,3);
                    if(isPlaced(index)){g.setStroke(new BasicStroke(1.6f));g.drawLine(12,getHeight()/2,15,getHeight()/2+3);g.drawLine(15,getHeight()/2+3,21,getHeight()/2-4);}g.dispose();
                }
            };
            Theme.styleButton(b,Theme.PANEL_LIGHT,Theme.TEXT_MAIN);b.setFont(Theme.FONT_SMALL);b.setHorizontalAlignment(SwingConstants.LEFT);b.setBorder(new EmptyBorder(13,28,13,105));
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE,44));b.addActionListener(e->cbShips.setSelectedIndex(index));fleetButtons[i]=b;Theme.addStack(fleet,b,7);
        }
        btnRotate.setMaximumSize(new Dimension(Integer.MAX_VALUE,40));btnRotate.addActionListener(e->rotate());Theme.addStack(fleet,btnRotate,12);
        JPanel tools=Theme.transparent(new GridLayout(1,2,8,0));btnRandom.setFont(Theme.FONT_SMALL);btnReset.setFont(Theme.FONT_SMALL);btnRandom.setBorder(new EmptyBorder(10,8,10,8));btnReset.setBorder(new EmptyBorder(10,8,10,8));btnRandom.addActionListener(e->randomizePlacement());btnReset.addActionListener(e->resetPlacement());tools.add(btnRandom);tools.add(btnReset);tools.setMaximumSize(new Dimension(Integer.MAX_VALUE,40));Theme.addStack(fleet,tools,0);side.add(Theme.scrollPage(fleet,396,Theme.PANEL_BG),BorderLayout.CENTER);
        JPanel readyArea=Theme.stack();Theme.addStack(readyArea,lblProgress,8);progress.setBorderPainted(false);progress.setForeground(Theme.PRIMARY);progress.setBackground(Theme.PANEL_LIGHT);progress.setMaximumSize(new Dimension(Integer.MAX_VALUE,5));Theme.addStack(readyArea,progress,10);
        lblInstruction.setPreferredSize(new Dimension(270,44));lblInstruction.setMaximumSize(new Dimension(Integer.MAX_VALUE,44));Theme.addStack(readyArea,lblInstruction,10);
        btnReady.setMaximumSize(new Dimension(Integer.MAX_VALUE,46));btnReady.addActionListener(e->{if(ready){setPending();if(cancelReady!=null)cancelReady.run();}else{setPending();if(readyListener!=null)readyListener.onReady(new ArrayList<>(placedShips));}});Theme.addStack(readyArea,btnReady,0);side.add(readyArea,BorderLayout.SOUTH);
        JPanel footer=Theme.transparent(new BorderLayout());footer.add(Theme.muted("05 TÀU  /  17 Ô  /  Không được chồng lấn"),BorderLayout.WEST);add(footer,BorderLayout.SOUTH);
        gridPanel.setCellClickListener(this::placeCurrentShipAt);gridPanel.setCellHoverListener(this::preview);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('r'),"rotate");getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('R'),"rotate");getActionMap().put("rotate",new AbstractAction(){public void actionPerformed(ActionEvent e){rotate();}});
        checkReadyCondition();refreshFleet();
    }
    public void setMatchInfo(String opponent,Runnable leave){lblMatch.setText("Đối thủ: "+opponent);leaveListener=leave;}
    public void setCancelReadyListener(Runnable listener){cancelReady=listener;}
    private void rotate(){if(locked)return;isHorizontal=!isHorizontal;btnRotate.setText(isHorizontal?"Hướng ngang  ↔   [R]":"Hướng dọc  ↕   [R]");preview(hoverX,hoverY);}
    private boolean canPlace(int x,int y){
        int size=SHIP_SIZES[currentShipIndex];if(x<0||y<0||x+(isHorizontal?size:1)>10||y+(isHorizontal?1:size)>10)return false;
        for(int i=0;i<size;i++){Coordinate c=new Coordinate(x+(isHorizontal?i:0),y+(isHorizontal?0:i));for(Ship s:placedShips)if(!s.getId().equals(SHIP_IDS[currentShipIndex])&&s.contains(c))return false;}return true;
    }
    private void preview(int x,int y){
        hoverX=x;hoverY=y;refreshGridVisuals();if(locked||x<0||y<0)return;boolean valid=canPlace(x,y);
        for(int i=0;i<SHIP_SIZES[currentShipIndex];i++)gridPanel.setCellState(x+(isHorizontal?i:0),y+(isHorizontal?0:i),valid?BoardGridPanel.CellState.PREVIEW_VALID:BoardGridPanel.CellState.PREVIEW_INVALID);
    }
    private void placeCurrentShipAt(int x,int y){
        if(locked)return;if(!canPlace(x,y)){instruction("Vị trí chưa hợp lệ. Tàu cần nằm trong bản đồ và không chồng lấn.",Theme.DANGER);return;}
        String id=SHIP_IDS[currentShipIndex];List<Coordinate> coords=new ArrayList<>();for(int i=0;i<SHIP_SIZES[currentShipIndex];i++)coords.add(new Coordinate(x+(isHorizontal?i:0),y+(isHorizontal?0:i)));
        placedShips.removeIf(s->s.getId().equals(id));placedShips.add(new Ship(id,SHIP_SIZES[currentShipIndex],coords));hoverX=hoverY=-1;
        for(int i=1;i<=5;i++){int next=(currentShipIndex+i)%5;if(!isPlaced(next)){cbShips.setSelectedIndex(next);break;}}
        refreshGridVisuals();checkReadyCondition();refreshFleet();
    }
    private boolean isPlaced(int index){for(Ship s:placedShips)if(s.getId().equals(SHIP_IDS[index]))return true;return false;}
    private void refreshFleet(){for(int i=0;i<5;i++)if(fleetButtons[i]!=null){JButton b=fleetButtons[i];b.setText(SHIP_NAMES[i]);b.setBackground(i==currentShipIndex?new Color(34,71,76):Theme.PANEL_LIGHT);b.setForeground(i==currentShipIndex?Theme.PRIMARY:Theme.TEXT_MAIN);b.setEnabled(!locked);b.repaint();}}
    public void randomizePlacement(){
        if(locked)return;placedShips.clear();Random random=new Random();Set<Coordinate> occupied=new HashSet<>();
        for(int i=0;i<5;i++)for(int attempt=0;attempt<1000;attempt++){
            boolean horizontal=random.nextBoolean();int size=SHIP_SIZES[i],x=random.nextInt(horizontal?11-size:10),y=random.nextInt(horizontal?10:11-size);List<Coordinate> coords=new ArrayList<>();
            for(int n=0;n<size;n++)coords.add(new Coordinate(x+(horizontal?n:0),y+(horizontal?0:n)));if(!Collections.disjoint(occupied,coords))continue;
            placedShips.add(new Ship(SHIP_IDS[i],size,coords));occupied.addAll(coords);break;
        }
        hoverX=hoverY=-1;refreshGridVisuals();checkReadyCondition();refreshFleet();
    }
    public void resetPlacement(){if(locked)return;placedShips.clear();hoverX=hoverY=-1;cbShips.setSelectedIndex(0);refreshGridVisuals();checkReadyCondition();refreshFleet();}
    private void refreshGridVisuals(){gridPanel.resetAllCells();gridPanel.setFleet(placedShips);}
    private void instruction(String text,Color color){lblInstruction.setText("<html>"+Theme.html(text)+"</html>");lblInstruction.setForeground(color);}
    private boolean validFleet(){Board board=new Board();board.setShips(placedShips);return board.validateAllShips();}
    private void checkReadyCondition(){boolean valid=validFleet();btnReady.setEnabled(!locked&&valid);progress.setValue(placedShips.size());lblProgress.setText(placedShips.size()+" / 5 tàu đã bố trí");instruction(valid?"Hạm đội đã hoàn tất. Sẵn sàng khi bạn muốn.":"Còn "+(5-placedShips.size())+" tàu cần được bố trí.",valid?Theme.PRIMARY:Theme.TEXT_MUTED);}
    private void lockControls(){btnRotate.setEnabled(!locked);btnRandom.setEnabled(!locked);btnReset.setEnabled(!locked);cbShips.setEnabled(!locked);gridPanel.setInteractionEnabled(!locked);refreshFleet();refreshGridVisuals();}
    public void setServerReady(boolean value){ready=value;locked=value;lockControls();btnReady.setText(value?"Hủy sẵn sàng":"Sẵn sàng chiến đấu  →");btnReady.setEnabled(value||validFleet());if(value)instruction(opponentReady?"Cả hai đã sẵn sàng. Đang vào trận...":"Bạn đã sẵn sàng. Chờ đối thủ hoàn tất.",Theme.PRIMARY);else checkReadyCondition();}
    public void setPending(){locked=true;lockControls();btnReady.setEnabled(false);btnReady.setText("Đang xác nhận...");instruction("Đang chờ máy chủ xác nhận hạm đội...",Theme.TEXT_MUTED);}
    public void showPlacementError(String message){setServerReady(false);instruction(message,Theme.DANGER);btnLeave.setEnabled(true);}
    public void showReadyState(boolean value){opponentReady=value;lblOpponent.setText(value?"●  Đối thủ đã sẵn sàng":"●  Đối thủ đang bố trí");lblOpponent.setForeground(value?Theme.PRIMARY:Theme.TEXT_MUTED);if(ready)instruction(value?"Cả hai đã sẵn sàng. Đang vào trận...":"Bạn đã sẵn sàng. Chờ đối thủ hoàn tất.",Theme.PRIMARY);}
    public void showLeaveError(){btnLeave.setEnabled(true);}
    public void showNotice(String text){instruction(text,Theme.WARNING);}
}
