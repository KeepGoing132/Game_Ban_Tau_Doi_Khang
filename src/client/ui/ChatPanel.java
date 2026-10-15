package client.ui;

import common.protocol.JsonUtil;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/** A collapsible overlay: opening chat never resizes either battle map. */
public final class ChatPanel extends Theme.RoundedPanel {
    public interface Actions {void select(int id);void send(String text);void older();void opened();}
    public static final class Peer {
        public final int id;public final String label;
        public Peer(int id,String label){this.id=id;this.label=label;}
        public String toString(){return label;}
    }
    private final Actions actions;
    private final JButton toggle=Theme.button("Tin nhắn  +",false);
    private final JPanel body=Theme.transparent(new BorderLayout(0,8));
    private final JComboBox<Peer> contacts=new JComboBox<>();
    private final JPanel messages=Theme.stack();
    private final JScrollPane history=Theme.scroll(messages);
    private final JTextArea input=new JTextArea(2,20){
        @Override protected void paintComponent(Graphics g){super.paintComponent(g);if(getText().isEmpty()&&!isFocusOwner()){g.setColor(Theme.TEXT_MUTED);g.setFont(getFont());g.drawString("Nhập tin nhắn…",10,26);}}
    };
    private final JButton send=Theme.button("Gửi ↑",true),older=Theme.button("Tin nhắn trước",false);
    private final JLabel status=Theme.label("Chọn người để trò chuyện",Theme.FONT_SMALL,Theme.TEXT_MUTED);
    private final JLabel counter=Theme.muted("0/500");
    private JLayeredPane host;
    private LayoutManager hostLayout,dockLayout;
    private boolean expanded,updating,canSend;
    private int selected=-1;
    private String header="Tin nhắn";
    private int unreadCount;
    private final ComponentAdapter resize=new ComponentAdapter(){public void componentResized(ComponentEvent e){position();}};

    public ChatPanel(Actions actions) {
        super(Theme.PANEL_BG);this.actions=actions;setLayout(new BorderLayout(0,8));setBorder(new EmptyBorder(8,8,8,8));
        toggle.setHorizontalAlignment(SwingConstants.LEFT);toggle.setFont(Theme.FONT_BOLD.deriveFont(15f));toggle.setPreferredSize(new Dimension(0,36));toggle.setBorder(new EmptyBorder(8,12,8,12));
        toggle.addActionListener(e->setExpanded(!expanded));add(toggle,BorderLayout.NORTH);
        contacts.setFont(Theme.FONT_REGULAR);contacts.setBackground(Theme.PANEL_LIGHT);contacts.setForeground(Theme.TEXT_MAIN);contacts.setMaximumRowCount(8);contacts.setPreferredSize(new Dimension(0,36));contacts.getAccessibleContext().setAccessibleName("Người nhận tin nhắn");
        contacts.addActionListener(e->{if(!updating){Peer peer=(Peer)contacts.getSelectedItem();if(peer!=null)actions.select(peer.id);}});
        JPanel top=Theme.transparent(new BorderLayout(0,5));top.add(contacts,BorderLayout.NORTH);older.setFont(Theme.FONT_SMALL);older.setBorder(new EmptyBorder(4,8,4,8));older.addActionListener(e->actions.older());top.add(older,BorderLayout.SOUTH);body.add(top,BorderLayout.NORTH);
        messages.setBorder(new EmptyBorder(4,2,4,2));history.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);history.getViewport().setBackground(Theme.PANEL_BG);history.getVerticalScrollBar().setUnitIncrement(20);body.add(history,BorderLayout.CENTER);
        input.setLineWrap(true);input.setWrapStyleWord(true);input.setFont(new Font(Font.DIALOG,Font.PLAIN,15));input.setBackground(Theme.BG_DARK);input.setForeground(Theme.TEXT_MAIN);input.setCaretColor(Theme.PRIMARY);input.setBorder(new EmptyBorder(9,10,9,10));input.setToolTipText("Enter để gửi • Shift+Enter để xuống dòng");input.getAccessibleContext().setAccessibleName("Nội dung tin nhắn");
        ((AbstractDocument)input.getDocument()).setDocumentFilter(new DocumentFilter(){
            public void insertString(FilterBypass fb,int offset,String text,AttributeSet a)throws BadLocationException{replace(fb,offset,0,text,a);}
            public void replace(FilterBypass fb,int offset,int length,String text,AttributeSet a)throws BadLocationException{
                String clean=text==null?"":text.replace("\r\n","\n").replace('\r','\n');int room=500-fb.getDocument().getLength()+length;
                if(clean.length()>room){clean=clean.substring(0,Math.max(0,room));if(!clean.isEmpty()&&Character.isHighSurrogate(clean.charAt(clean.length()-1)))clean=clean.substring(0,clean.length()-1);}super.replace(fb,offset,length,clean,a);
            }
        });
        input.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){changed();}public void removeUpdate(DocumentEvent e){changed();}public void changedUpdate(DocumentEvent e){changed();}private void changed(){counter.setText(input.getText().length()+"/500");send.setEnabled(canSend&&!input.getText().trim().isEmpty());}});
        input.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"send-message");input.getActionMap().put("send-message",new AbstractAction(){public void actionPerformed(ActionEvent e){if(send.isEnabled())actions.send(input.getText());}});
        input.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,InputEvent.SHIFT_DOWN_MASK),DefaultEditorKit.insertBreakAction);
        send.setBorder(new EmptyBorder(8,12,8,12));send.addActionListener(e->actions.send(input.getText()));
        JPanel compose=Theme.transparent(new BorderLayout(8,6));JScrollPane editor=Theme.scroll(input);editor.setPreferredSize(new Dimension(0,66));compose.add(editor,BorderLayout.CENTER);compose.add(send,BorderLayout.EAST);
        JPanel foot=Theme.transparent(new BorderLayout(8,0));foot.add(status,BorderLayout.CENTER);foot.add(counter,BorderLayout.EAST);compose.add(foot,BorderLayout.SOUTH);body.add(compose,BorderLayout.SOUTH);add(body,BorderLayout.CENTER);
        body.setVisible(false);setComposer(false,"Chọn người để trò chuyện",false);setVisible(true);
    }
    public void attach(JFrame frame) {
        JLayeredPane next=frame.getLayeredPane();if(host==next){position();return;}
        detach();host=next;hostLayout=host.getLayout();final LayoutManager original=hostLayout;
        dockLayout=new LayoutManager(){
            public void addLayoutComponent(String name,Component component){if(original!=null)original.addLayoutComponent(name,component);}
            public void removeLayoutComponent(Component component){if(original!=null)original.removeLayoutComponent(component);}
            public Dimension preferredLayoutSize(Container parent){return original==null?parent.getSize():original.preferredLayoutSize(parent);}
            public Dimension minimumLayoutSize(Container parent){return original==null?new Dimension(0,0):original.minimumLayoutSize(parent);}
            public void layoutContainer(Container parent){if(original!=null)original.layoutContainer(parent);position();}
        };
        host.setLayout(dockLayout);host.add(this,JLayeredPane.PALETTE_LAYER);host.addComponentListener(resize);position();host.revalidate();host.repaint();
    }
    public void detach(){if(host!=null){host.removeComponentListener(resize);host.remove(this);if(host.getLayout()==dockLayout)host.setLayout(hostLayout);host.revalidate();host.repaint();host=null;}}
    private void position(){if(host==null)return;int width=Math.min(354,Math.max(240,host.getWidth()-32));int height=expanded?Math.min(440,Math.max(250,host.getHeight()-100)):52;setBounds(Math.max(8,host.getWidth()-width-24),Math.max(8,host.getHeight()-height-16),width,height);}
    public void setExpanded(boolean value){expanded=value;body.setVisible(value);setHeader(header,unreadCount);position();revalidate();repaint();if(host!=null)host.repaint();if(value)actions.opened();}
    public boolean isExpanded(){return expanded;}
    public void setHeader(String title,int unread){header=title;unreadCount=unread;String shortTitle=title.length()>25?title.substring(0,24)+"…":title;toggle.setText((unread>0?"● "+unread+"  ":"")+shortTitle+(expanded?"   -":"   +"));toggle.setForeground(unread>0?Theme.WARNING:Theme.PRIMARY);toggle.setToolTipText(title+" · "+(expanded?"Thu gọn khung chat":"Mở khung chat"));}
    public void setContacts(List<Peer> peers,int selectedId) {
        updating=true;try{contacts.removeAllItems();for(Peer peer:peers){contacts.addItem(peer);if(peer.id==selectedId)contacts.setSelectedItem(peer);}if(selectedId<0)contacts.setSelectedIndex(-1);selected=selectedId;}finally{updating=false;}
    }
    public String draft(){return input.getText();}
    public void setDraft(String text){if(!input.getText().equals(text))input.setText(text);}
    public void focusInput(){input.requestFocusInWindow();}
    public void setComposer(boolean enabled,String text,boolean error){canSend=enabled;input.setEnabled(selected>0);send.setEnabled(enabled&&!input.getText().trim().isEmpty());status.setText(text);status.setToolTipText(text);status.setForeground(error?Theme.DANGER:Theme.TEXT_MUTED);}
    public void showMessages(Collection<Map<String,Object>> rows,int ownId,boolean hasMore,boolean loading,boolean scrollDown,boolean prepend) {
        JScrollBar bar=history.getVerticalScrollBar();int old=bar.getValue(),oldMax=bar.getMaximum();boolean atBottom=old+bar.getVisibleAmount()>=oldMax-25;
        messages.removeAll();older.setVisible(hasMore);older.setEnabled(!loading);older.setText(loading?"Đang tải…":"Tin nhắn trước");
        if(rows.isEmpty()){JLabel empty=Theme.label(loading?"Đang tải tin nhắn…":"Bắt đầu cuộc trò chuyện",Theme.FONT_REGULAR,Theme.TEXT_MUTED);empty.setBorder(new EmptyBorder(25,12,25,12));messages.add(empty);}
        SimpleDateFormat time=new SimpleDateFormat("HH:mm · dd/MM");
        for(Map<String,Object> row:rows) {
            boolean mine=JsonUtil.getInt(row,"sender_id",-1)==ownId;JPanel line=Theme.transparent(new BorderLayout());line.setBorder(new EmptyBorder(3,mine?30:0,5,mine?0:30));
            Color color=mine?new Color(44,64,116):Theme.PANEL_LIGHT;JPanel bubble=Theme.card(color,10);bubble.setLayout(new BorderLayout(0,4));
            JLabel label=Theme.label((mine?"Bạn":JsonUtil.getString(row,"sender_name",""))+"  ·  "+time.format(new Date(JsonUtil.getLong(row,"sent_at_ms",0))),Theme.FONT_SMALL,mine?Theme.PRIMARY:Theme.TEXT_MUTED);bubble.add(label,BorderLayout.NORTH);
            JTextArea text=new JTextArea(JsonUtil.getString(row,"body",""));text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);text.setFont(new Font(Font.DIALOG,Font.PLAIN,15));text.setForeground(Theme.TEXT_MAIN);text.setOpaque(false);text.setBorder(null);text.setSize(254,Short.MAX_VALUE);text.setPreferredSize(new Dimension(254,text.getPreferredSize().height));bubble.add(text,BorderLayout.CENTER);
            line.add(bubble,BorderLayout.CENTER);line.setAlignmentX(Component.LEFT_ALIGNMENT);line.setMaximumSize(new Dimension(Integer.MAX_VALUE,line.getPreferredSize().height));messages.add(line);
        }
        messages.revalidate();messages.repaint();SwingUtilities.invokeLater(()->{if(scrollDown)bar.setValue(bar.getMaximum());else if(prepend)bar.setValue(old+Math.max(0,bar.getMaximum()-oldMax));else if(atBottom)bar.setValue(bar.getMaximum());else bar.setValue(old);});
    }
}
