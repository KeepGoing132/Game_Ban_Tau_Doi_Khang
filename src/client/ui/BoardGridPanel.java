package client.ui;

import common.config.GameConfig;
import common.model.Coordinate;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;

public class BoardGridPanel extends Theme.RoundedPanel {
    public enum CellState { WATER, SHIP, HIT, MISS, SUNK, PREVIEW_VALID, PREVIEW_INVALID }
    public interface CellClickListener { void onCellClicked(int x,int y); }
    public interface CellHoverListener { void onCellHover(int x,int y); }
    private final JButton[][] cellButtons = new JButton[GameConfig.BOARD_SIZE][GameConfig.BOARD_SIZE];
    private final CellState[][] cellStates = new CellState[GameConfig.BOARD_SIZE][GameConfig.BOARD_SIZE];
    private CellClickListener clickListener;
    private CellHoverListener hoverListener;
    private Coordinate selectedCoordinate;
    private boolean interactionEnabled;
    private final JLabel titleLabel;
    private final JPanel grid;
    private boolean compact;
    private final common.model.Ship[][] fleetCells=new common.model.Ship[10][10];
    private final JLabel statusLabel = Theme.muted("10 × 10  /  HẢI PHẬN");

    public BoardGridPanel(String title,boolean interactive) {
        super(Theme.PANEL_BG);interactionEnabled=interactive;
        setLayout(new BorderLayout(0,10));setBorder(new EmptyBorder(16,16,16,16));
        JPanel header=Theme.transparent(new BorderLayout());
        titleLabel=Theme.label(title,Theme.FONT_BOLD,Theme.TEXT_MAIN);header.add(titleLabel,BorderLayout.WEST);
        add(header,BorderLayout.NORTH);
        grid=Theme.transparent(new GridLayout(11,11,3,3));
        grid.add(new JLabel());
        for(int x=0;x<10;x++)grid.add(axis(String.valueOf((char)('A'+x))));
        for(int y=0;y<10;y++) {
            grid.add(axis(String.valueOf(y+1)));
            for(int x=0;x<10;x++) {
                final int cx=x,cy=y;cellStates[x][y]=CellState.WATER;
                JButton cell=new CellButton(x,y);cellButtons[x][y]=cell;
                cell.setToolTipText(coordinate(x,y));cell.getAccessibleContext().setAccessibleName(coordinate(x,y)+" • Vùng biển");
                cell.setFocusable(interactive);
                if(interactive) {
                    cell.addActionListener(e->{if(interactionEnabled&&clickListener!=null)clickListener.onCellClicked(cx,cy);});
                    cell.addMouseListener(new MouseAdapter(){
                        public void mouseEntered(MouseEvent e){if(interactionEnabled&&hoverListener!=null)hoverListener.onCellHover(cx,cy);}
                        public void mouseExited(MouseEvent e){if(hoverListener!=null)hoverListener.onCellHover(-1,-1);}
                    });
                    String[] keys={"LEFT","RIGHT","UP","DOWN"};int[][] delta={{-1,0},{1,0},{0,-1},{0,1}};
                    for(int i=0;i<4;i++) {
                        final int dx=delta[i][0],dy=delta[i][1];cell.getInputMap().put(KeyStroke.getKeyStroke(keys[i]),keys[i]);
                        cell.getActionMap().put(keys[i],new AbstractAction(){public void actionPerformed(ActionEvent e){cellButtons[Math.max(0,Math.min(9,cx+dx))][Math.max(0,Math.min(9,cy+dy))].requestFocusInWindow();}});
                    }
                    cell.getInputMap().put(KeyStroke.getKeyStroke("ENTER"),"select");
                    cell.getActionMap().put("select",new AbstractAction(){public void actionPerformed(ActionEvent e){cell.doClick(0);}});
                }
                grid.add(cell);
            }
        }
        JPanel square=Theme.transparent(null);
        square.setLayout(new LayoutManager(){
            public void addLayoutComponent(String n,Component c){} public void removeLayoutComponent(Component c){}
            public Dimension preferredLayoutSize(Container c){return new Dimension(660,660);}
            public Dimension minimumLayoutSize(Container c){return new Dimension(220,220);}
            public void layoutContainer(Container c){int side=Math.min(c.getWidth(),c.getHeight());grid.setBounds((c.getWidth()-side)/2,(c.getHeight()-side)/2,side,side);}
        });
        square.add(grid);add(square,BorderLayout.CENTER);statusLabel.setVisible(false);add(statusLabel,BorderLayout.SOUTH);
    }
    private JLabel axis(String text) {JLabel l=Theme.label(text,Theme.FONT_GRID,Theme.TEXT_MUTED);l.setHorizontalAlignment(SwingConstants.CENTER);return l;}
    public static String coordinate(int x,int y){return String.valueOf((char)('A'+x))+(y+1);}
    public void setCaption(String text){statusLabel.setText(text);}
    public void setFleet(java.util.List<common.model.Ship> fleet){for(int x=0;x<10;x++)for(int y=0;y<10;y++)fleetCells[x][y]=null;if(fleet!=null)for(common.model.Ship ship:fleet)for(Coordinate c:ship.getCoordinates()){fleetCells[c.getX()][c.getY()]=ship;setCellState(c.getX(),c.getY(),CellState.SHIP);}repaint();}
    public void setCompact(boolean value){compact=value;setBorder(new EmptyBorder(value?6:16,value?6:16,value?6:16,value?6:16));titleLabel.setFont(value?Theme.FONT_SMALL:Theme.FONT_HEADER);((GridLayout)grid.getLayout()).setHgap(value?1:3);((GridLayout)grid.getLayout()).setVgap(value?1:3);for(Component c:grid.getComponents())if(c instanceof JLabel)c.setFont(value?Theme.FONT_GRID.deriveFont(9f):Theme.FONT_GRID.deriveFont(14f));revalidate();}
    public Point cellCenter(int x,int y,Component relative){JButton c=cellButtons[x][y];return SwingUtilities.convertPoint(c,c.getWidth()/2,c.getHeight()/2,relative);}

    public void setInteractionEnabled(boolean value){interactionEnabled=value;repaint();}
    public void setCellClickListener(CellClickListener l){clickListener=l;}
    public void setCellHoverListener(CellHoverListener l){hoverListener=l;}
    public void setCellState(int x,int y,CellState state) {
        if(x<0||x>=10||y<0||y>=10)return;cellStates[x][y]=state;
        String[] names={"Vùng biển","Tàu chiến","Bắn trúng","Bắn trượt","Đã chìm","Có thể đặt","Không thể đặt"};
        cellButtons[x][y].getAccessibleContext().setAccessibleName(coordinate(x,y)+" • "+names[state.ordinal()]);
        cellButtons[x][y].repaint();
    }
    public CellState getCellState(int x,int y){return x<0||x>=10||y<0||y>=10?null:cellStates[x][y];}
    public void setSelectedCoordinate(Coordinate c){selectedCoordinate=c;repaint();}
    public Coordinate getSelectedCoordinate(){return selectedCoordinate;}
    public void resetAllCells(){selectedCoordinate=null;for(int x=0;x<10;x++)for(int y=0;y<10;y++)setCellState(x,y,CellState.WATER);}

    private class CellButton extends JButton {
        private final int x,y;
        CellButton(int x,int y){this.x=x;this.y=y;setOpaque(false);setContentAreaFilled(false);setBorderPainted(false);setFocusPainted(false);setRolloverEnabled(true);setMargin(new Insets(0,0,0,0));setPreferredSize(new Dimension(34,34));}
        @Override protected void paintComponent(Graphics graphics){
            Graphics2D g=Theme.graphics(graphics);int w=getWidth(),h=getHeight();CellState state=cellStates[x][y];
            boolean selected=selectedCoordinate!=null&&selectedCoordinate.getX()==x&&selectedCoordinate.getY()==y;
            Color fill=Theme.CELL_WATER;
            switch(state){case SHIP:fill=Theme.CELL_SHIP;break;case HIT:fill=Theme.CELL_HIT;break;case SUNK:fill=Theme.CELL_SUNK;break;case MISS:fill=Theme.CELL_MISS;break;case PREVIEW_VALID:fill=new Color(34,104,95);break;case PREVIEW_INVALID:fill=new Color(112,53,61);break;default:break;}
            if(state==CellState.WATER&&getModel().isRollover()&&interactionEnabled)fill=Theme.CELL_WATER_HOVER;
            g.setPaint(new GradientPaint(0,0,Theme.blend(fill,Color.WHITE,.07f),w,h,fill));g.fillRoundRect(0,0,w-1,h-1,5,5);
            if(state==CellState.WATER && w>26){g.setColor(new Color(107,194,234,35));int wy=h*2/3;g.drawLine(w/4,wy,w/2,wy);g.drawLine(w/2,wy+2,w*3/4,wy+2);}
            g.setColor(Theme.blend(fill,Theme.TEXT_MUTED,.14f));g.drawRoundRect(0,0,w-1,h-1,5,5);
            int cx=w/2,cy=h/2;
            if(state==CellState.SHIP){
                common.model.Ship ship=fleetCells[x][y];g.setColor(Theme.VIOLET);
                if(ship==null){g.fillRoundRect(w/3,h/6,w/3,h*2/3,4,4);g.setColor(new Color(224,226,255));g.drawLine(cx,h/3,cx,h*2/3);}
                else {
                    java.util.List<Coordinate> cells=ship.getCoordinates();boolean horizontal=cells.get(0).getY()==cells.get(1).getY();int min=10,max=-1;for(Coordinate c:cells){int v=horizontal?c.getX():c.getY();min=Math.min(min,v);max=Math.max(max,v);}int pos=horizontal?x:y;
                    if(!horizontal){g.translate(w,0);g.rotate(Math.PI/2);int temp=w;w=h;h=temp;cx=w/2;cy=h/2;}
                    int top=h/4,bottom=h*3/4;int left=pos==min?w/7:0,right=pos==max?w*6/7:w;
                    g.setColor(new Color(178,157,255));g.fillPolygon(new int[]{left,pos==min?left+w/4:left,right,right,pos==min?left+w/4:left},new int[]{cy,top,top,bottom,bottom},5);
                    g.setColor(new Color(103,91,177));g.fillRect(pos==min?w/3:0,h/3,pos==max?w*2/3:w,h/3);
                    if(pos==min+(max-min)/2){g.setColor(new Color(228,224,255));g.fillRoundRect(w/3,h/3,w/3,h/3,3,3);g.setStroke(new BasicStroke(2));g.drawLine(w/2,h/2,w*4/5,h/2);}
                    if(!horizontal){g.rotate(-Math.PI/2);g.translate(-h,0);int temp=w;w=h;h=temp;cx=w/2;cy=h/2;}
                }
            }
            if(state==CellState.HIT||state==CellState.SUNK){g.setColor(Theme.DANGER);g.setStroke(new BasicStroke(2));int r=Math.max(4,w/6);g.drawLine(cx-r,cy-r,cx+r,cy+r);g.drawLine(cx+r,cy-r,cx-r,cy+r);if(state==CellState.SUNK)g.drawOval(cx-r-3,cy-r-3,2*r+6,2*r+6);}
            if(state==CellState.MISS){g.setColor(Theme.TEXT_MUTED);g.fillOval(cx-3,cy-3,6,6);}
            if(state==CellState.PREVIEW_INVALID){g.setColor(Theme.DANGER);g.drawLine(cx-4,cy-4,cx+4,cy+4);g.drawLine(cx+4,cy-4,cx-4,cy+4);}
            if(selected||hasFocus()){g.setColor(selected?Theme.WARNING:Theme.PRIMARY);g.setStroke(new BasicStroke(2));g.drawRoundRect(1,1,w-3,h-3,5,5);if(selected){g.drawOval(cx-6,cy-6,12,12);g.drawLine(cx-9,cy,cx+9,cy);g.drawLine(cx,cy-9,cx,cy+9);}}
            setCursor(Cursor.getPredefinedCursor(interactionEnabled?Cursor.CROSSHAIR_CURSOR:Cursor.DEFAULT_CURSOR));g.dispose();
        }
    }
}
