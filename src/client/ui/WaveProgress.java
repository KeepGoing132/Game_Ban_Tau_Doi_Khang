package client.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

public class WaveProgress extends JProgressBar {
    public WaveProgress(int minimum,int maximum) { super(minimum,maximum); setOpaque(false); setBorderPainted(false); }
    @Override protected void paintComponent(Graphics graphics) {
        int w=getWidth(),h=getHeight();if(w<6||h<3)return;
        Graphics2D g=Theme.graphics(graphics);
        double t=OceanScene.isAnimated()?OceanScene.time():0;
        g.setColor(new Color(5,22,44,210));g.fillRoundRect(0,0,w-1,h-1,h,h);
        int span=getMaximum()-getMinimum();
        double p=span<=0?0:OceanScene.clamp01((getValue()-getMinimum())/(double)span);
        Color water=getForeground()==null?Theme.PRIMARY:getForeground();
        double level=h*(1-p);
        if(p>0.002) {
            int steps=Math.max(8,w/7);
            Path2D surface=new Path2D.Double();
            for(int i=0;i<=steps;i++) {
                double x=i*w/(double)steps,y=level+1.3*Math.sin(2*Math.PI*2*i/steps+t*2.3)+0.7*Math.sin(2*Math.PI*5*i/steps-t*3.2);
                if(i==0)surface.moveTo(x,y);else surface.lineTo(x,y);
            }
            Path2D body=(Path2D)surface.clone();body.lineTo(w,h);body.lineTo(0,h);body.closePath();
            g.setPaint(new GradientPaint(0,(float)level,new Color(water.getRed(),water.getGreen(),water.getBlue(),215),
                    0,h,new Color(water.getRed()/3,water.getGreen()/2,water.getBlue()/2,240)));
            g.fill(body);
            g.setColor(new Color(Theme.SEA_FOAM.getRed(),Theme.SEA_FOAM.getGreen(),Theme.SEA_FOAM.getBlue(),190));
            g.setStroke(new BasicStroke(1.2f));g.draw(surface);
            OceanScene.paintBubbles(g,w,level,h+5,t,4,0.30,17);
        }
        g.setColor(new Color(126,202,226,72));g.drawRoundRect(0,0,w-1,h-1,h,h);
        g.setColor(new Color(255,255,255,36));g.fillRoundRect(1,1,w-2,Math.max(1,h/3),h,h);
        g.dispose();
    }
    @Override public void setForeground(Color color) { super.setForeground(color); repaint(); }
}
