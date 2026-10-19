package client.ui;

import common.protocol.JsonUtil;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class LeaderboardDialog extends JDialog {
    public LeaderboardDialog(Frame parent,List<Object> data) {
        super(parent,"Battleship • Bảng xếp hạng",true);setSize(830,610);setMinimumSize(new Dimension(720,500));setLocationRelativeTo(parent);
        JPanel root=Theme.transparent(new BorderLayout(0,22));root.setOpaque(true);root.setBackground(Theme.BG_DARK);root.setBorder(new EmptyBorder(28,28,24,28));setContentPane(root);
        JPanel top=Theme.stack();Theme.addStack(top,Theme.heading("HỒ SƠ CHỈ HUY", "Bảng vàng hải chiến", "Mỗi chiến thắng là một bước tiến. Mỗi chỉ huy là một chiến lược."),22);
        JPanel podium=Theme.transparent(new GridLayout(1,3,12,0));
        Color[] medal={Theme.GOLD,new Color(212,224,240),new Color(226,150,110)};
        String[] medalName={"DẪN ĐẦU","BÁM ĐUỔI","BỆ PHÓNG"};
        for(int i=0;i<3;i++) {
            Map<String,Object> m=null;if(data!=null&&i<data.size()&&data.get(i) instanceof Map){@SuppressWarnings("unchecked") Map<String,Object> entry=(Map<String,Object>)data.get(i);m=entry;}
            Color accent=medal[i];
            JPanel card=Theme.vividCard(Theme.blend(Theme.PANEL_BG,accent,.22f),Theme.blend(Theme.PANEL_LIGHT,accent,.14f),16);
            card.setLayout(new BorderLayout(0,8));
            card.add(Theme.label("0"+(i+1)+"  /  "+medalName[i],Theme.FONT_SMALL.deriveFont(Font.BOLD),accent),BorderLayout.NORTH);
            JLabel name=Theme.label(m==null?"Chờ chỉ huy":JsonUtil.getString(m,"username",""),Theme.FONT_HEADER,Theme.SEA_FOAM);card.add(name,BorderLayout.CENTER);
            card.add(Theme.muted(m==null?"Hãy ghi dấu ấn của bạn":JsonUtil.getInt(m,"total_points",0)+" điểm  •  "+JsonUtil.getInt(m,"total_wins",0)+" trận thắng"),BorderLayout.SOUTH);podium.add(card);
        }
        podium.setMaximumSize(new Dimension(Integer.MAX_VALUE,110));Theme.addStack(top,podium,0);root.add(top,BorderLayout.NORTH);
        DefaultTableModel model=new DefaultTableModel(new String[]{"Hạng","Chỉ huy","Điểm","Thắng","Số trận","Tỷ lệ thắng"},0){public boolean isCellEditable(int r,int c){return false;}};
        if(data!=null)for(Object item:data)if(item instanceof Map){@SuppressWarnings("unchecked") Map<String,Object> m=(Map<String,Object>)item;model.addRow(new Object[]{String.format("%02d",JsonUtil.getInt(m,"rank",0)),JsonUtil.getString(m,"username",""),JsonUtil.getInt(m,"total_points",0),JsonUtil.getInt(m,"total_wins",0),JsonUtil.getInt(m,"total_games",0),String.format(java.util.Locale.ROOT,"%.1f%%",JsonUtil.getDouble(m,"win_rate",0))});}
        JTable table=new JTable(model);Theme.styleTable(table);table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.setDefaultRenderer(Object.class,new DefaultTableCellRenderer(){
            public Component getTableCellRendererComponent(JTable t,Object v,boolean s,boolean f,int r,int c){
                super.getTableCellRendererComponent(t,v,s,f,r,c);
                Color color=c==0?(r<3?Theme.GOLD:Theme.TEXT_MUTED):c==1?Theme.SEA_FOAM:c==2?Theme.AMBER:c==3?Theme.MINT:c==4?Theme.AZURE:Theme.ORCHID;
                setFont(c==0||c==2?Theme.FONT_BOLD:Theme.FONT_REGULAR);
                setForeground(s?Theme.PRIMARY:color);
                setBackground(s?Theme.PANEL_LIGHT:(r%2==0?Theme.PANEL_BG:Theme.blend(Theme.PANEL_BG,Theme.PANEL_LIGHT,.35f)));
                setBorder(new EmptyBorder(0,12,0,12));return this;}
        });
        JPanel tableCard=Theme.card(Theme.PANEL_BG,10);tableCard.setLayout(new BorderLayout());tableCard.add(Theme.scroll(table));root.add(tableCard,BorderLayout.CENTER);
        JPanel footer=Theme.transparent(new BorderLayout());footer.add(Theme.muted("Thắng +3 điểm  •  Thống kê từ các trận đã hoàn tất"),BorderLayout.WEST);JButton close=Theme.button("Trở về sảnh",false);close.addActionListener(e->dispose());footer.add(close,BorderLayout.EAST);root.add(footer,BorderLayout.SOUTH);
        getRootPane().registerKeyboardAction(e->dispose(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
    }
}
