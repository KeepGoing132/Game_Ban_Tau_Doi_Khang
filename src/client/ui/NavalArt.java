package client.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/** Resolution-independent tactical illustration, painted locally with Java2D. */
public final class NavalArt extends JComponent {
    public NavalArt() { setOpaque(false); setPreferredSize(new Dimension(390,320)); }
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = Theme.graphics(graphics);
        double scale = Math.min(getWidth()/440.0,getHeight()/340.0);
        g.translate((getWidth()-440*scale)/2,(getHeight()-340*scale)/2);g.scale(scale,scale);
        g.setColor(new Color(24,47,61));
        for(int x=10;x<440;x+=26)g.drawLine(x,0,x,340);
        for(int y=0;y<340;y+=26)g.drawLine(10,y,430,y);
        int cx=220,cy=164;
        for(int r=50;r<=150;r+=50){g.setColor(new Color(42,79,89));g.drawOval(cx-r,cy-r,r*2,r*2);}
        g.setColor(new Color(29,71,73));g.fill(new Arc2D.Double(70,14,300,300,22,37,Arc2D.PIE));
        g.setColor(Theme.PRIMARY);g.setStroke(new BasicStroke(1.5f));g.draw(new Arc2D.Double(70,14,300,300,22,37,Arc2D.OPEN));
        g.setColor(new Color(51,86,97));g.drawLine(55,cy,385,cy);g.drawLine(cx,0,cx,329);
        g.rotate(-.54,cx,cy);
        Path2D hull=new Path2D.Double();hull.moveTo(220,42);hull.curveTo(199,72,193,90,193,118);
        hull.lineTo(196,264);hull.lineTo(244,264);hull.lineTo(247,118);hull.curveTo(247,90,241,72,220,42);hull.closePath();
        g.setColor(new Color(21,56,67));g.fill(hull);g.setColor(Theme.PRIMARY);g.setStroke(new BasicStroke(2));g.draw(hull);
        g.setColor(new Color(66,126,137));g.fillRoundRect(203,132,34,68,5,5);
        g.setColor(new Color(155,204,202));g.drawRoundRect(203,132,34,68,5,5);
        g.drawRect(208,144,24,28);g.drawLine(220,136,220,185);g.drawLine(209,151,231,151);
        for(int y:new int[]{102,220}){g.setColor(new Color(42,88,103));g.fillOval(210,y,20,20);g.setColor(Theme.PRIMARY);g.drawOval(210,y,20,20);g.drawLine(217,y+4,217,y-14);g.drawLine(223,y+4,223,y-14);}
        g.setColor(new Color(51,86,97));g.drawLine(201,273,188,306);g.drawLine(220,275,220,320);g.drawLine(239,273,252,306);
        g.rotate(.54,cx,cy);
        
        for(int[] p:new int[][]{{104,103},{340,197},{315,65}}){g.setColor(new Color(48,88,89));g.drawOval(p[0]-9,p[1]-9,18,18);g.setColor(Theme.PRIMARY);g.fillOval(p[0]-3,p[1]-3,6,6);}
        g.dispose();
    }
}
