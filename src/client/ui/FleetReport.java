package client.ui;

import common.config.GameConfig;
import common.model.Coordinate;
import common.model.Ship;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.List;

/**
 * After-action chart of one fleet: the enemy ships released at the end of the match together with
 * every shot mark. Painted directly instead of using a live board, so opening the report costs a
 * single component and a few dozen shapes.
 */
public class FleetReport extends JComponent {
    private final Ship[][] cells=new Ship[10][10];
    private final BoardGridPanel.CellState[][] states=new BoardGridPanel.CellState[10][10];
    private final List<Ship> fleet;

    public FleetReport(List<Ship> fleet,BoardGridPanel source) {
        this.fleet=fleet;
        setOpaque(false);
        if(fleet!=null)for(Ship ship:fleet)for(Coordinate c:ship.getCoordinates())cells[c.getX()][c.getY()]=ship;
        for(int x=0;x<10;x++)for(int y=0;y<10;y++)
            states[x][y]=source==null?BoardGridPanel.CellState.WATER:source.getCellState(x,y);
    }
    @Override protected void paintComponent(Graphics graphics) {
        int w=getWidth(),h=getHeight();
        if(w<40||h<40)return;
        Graphics2D g=Theme.graphics(graphics);
        int top=26,side=Math.min(w,h-top);
        int left=(w-side)/2;
        g.setPaint(new GradientPaint(0,top,new Color(36,172,188,214),0,top+side,new Color(24,34,96,226)));
        g.fillRoundRect(left,top,side,side,14,14);
        double cw=side/10.0, ch=side/10.0;
        for(Ship ship:fleet){
            List<Coordinate> cellsOfShip=ship.getCoordinates();
            int minX=10,maxX=-1,minY=10,maxY=-1;
            for(Coordinate c:cellsOfShip){minX=Math.min(minX,c.getX());maxX=Math.max(maxX,c.getX());minY=Math.min(minY,c.getY());maxY=Math.max(maxY,c.getY());}
            double x=left+minX*cw+2.5, y=top+minY*ch+2.5, bw=(maxX-minX+1)*cw-5, bh=(maxY-minY+1)*ch-5;
            g.setPaint(new GradientPaint((float)x,(float)y,new Color(196,182,255), 0, (float)(y+bh), new Color(122,106,196)));
            g.fill(new RoundRectangle2D.Double(x,y,bw,bh,7,7));
            g.setColor(new Color(238,238,255,190));
            g.setStroke(new BasicStroke(1.2f));
            g.draw(new RoundRectangle2D.Double(x,y,bw,bh,7,7));
            double fx=x+bw/2, fy=y+bh/2;
            g.fill(new RoundRectangle2D.Double(fx-3,fy-3,6,6,2,2));
        }
        for(int x=0;x<10;x++)for(int y=0;y<10;y++){
            double cx=left+(x+0.5)*cw, cy=top+(y+0.5)*ch;
            g.setColor(new Color(150,214,236,44));
            g.draw(new Rectangle2D.Double(left+x*cw,top+y*ch,cw,ch));
            BoardGridPanel.CellState state=states[x][y];
            if(state==BoardGridPanel.CellState.HIT||state==BoardGridPanel.CellState.SUNK){
                g.setColor(Theme.CORAL);g.setStroke(new BasicStroke(2.2f));
                double r=Math.max(3,cw/6);
                g.draw(new Line2D.Double(cx-r,cy-r,cx+r,cy+r));g.draw(new Line2D.Double(cx+r,cy-r,cx-r,cy+r));
                if(state==BoardGridPanel.CellState.SUNK){g.setColor(new Color(236,126,226,220));g.draw(new Ellipse2D.Double(cx-r-3,cy-r-3,2*r+6,2*r+6));}
            } else if(state==BoardGridPanel.CellState.MISS){
                g.setColor(new Color(226,246,255,215));g.fill(new Ellipse2D.Double(cx-2.5,cy-2.5,5,5));
                g.setStroke(new BasicStroke(1.3f));
                for(int i=0;i<4;i++){double a=Math.PI/4+i*Math.PI/2;
                    g.draw(new Line2D.Double(cx+Math.cos(a)*4,cy+Math.sin(a)*4,cx+Math.cos(a)*8,cy+Math.sin(a)*8));}
            }
        }
        g.setColor(Theme.SEA_HORIZON);
        g.setFont(Theme.FONT_GRID.deriveFont(10f));
        for(int x=0;x<10;x++){
            String label=String.valueOf((char)('A'+x));
            FontMetrics fm=g.getFontMetrics();
            g.drawString(label,(float)(left+(x+0.5)*cw-fm.stringWidth(label)/2f),(float)(top-8));
        }
        for(int y=0;y<10;y++){
            String label=String.valueOf(y+1);
            FontMetrics fm=g.getFontMetrics();
            g.drawString(label,(float)(left-4-fm.stringWidth(label)),(float)(top+(y+0.55)*ch));
        }
        g.dispose();
    }
    @Override public Dimension getPreferredSize(){return new Dimension(GameConfig.BOARD_SIZE*22+64,GameConfig.BOARD_SIZE*22+40);}
}
