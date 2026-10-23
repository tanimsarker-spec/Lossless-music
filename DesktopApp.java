package com.tanimsarker.lossless;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DesktopApp {
    static final Color BG = Color.BLACK, PANEL = new Color(18, 20, 22), SILVER = new Color(201, 206, 214),
            MUTE = new Color(125, 133, 144), BLUE = new Color(61, 139, 255);
    static final List<File> files = new ArrayList<>();
    static final DefaultListModel<String> model = new DefaultListModel<>();
    static final JList<String> list = new JList<>(model);
    static final JLabel title = label("Open music files to begin", 16, SILVER), quality = label(" ", 12, BLUE),
            time = label("0:00 / 0:00", 12, MUTE);
    static final JSlider seek = new JSlider(0, 1000, 0);
    static final JButton play = new JButton("Play");
    static int idx = -1;
    static boolean dragging = false;

    static JLabel label(String s, int size, Color c) {
        JLabel l = new JLabel(s);
        l.setForeground(c);
        l.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size));
        return l;
    }

    public static void main(String[] args) { SwingUtilities.invokeLater(DesktopApp::ui); }

    static void ui() {
        JFrame f = new JFrame("LOSSLESS");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));

        JPanel head = new JPanel(); head.setBackground(BG);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JLabel logo = label("L O S S L E S S", 20, SILVER), sig = label("TanimSarker", 11, MUTE);
        logo.setAlignmentX(0.5f); sig.setAlignmentX(0.5f);
        head.add(logo); head.add(sig);
        root.add(head, BorderLayout.NORTH);

        list.setBackground(PANEL); list.setForeground(SILVER); list.setSelectionBackground(BLUE);
        list.addMouseListener(new java.awt.event.MouseAdapter() { public void mouseClicked(java.awt.event.MouseEvent e) { if (e.getClickCount() == 2) load(list.getSelectedIndex()); } });
        root.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel south = new JPanel(); south.setBackground(BG);
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        title.setAlignmentX(0); quality.setAlignmentX(0);
        south.add(title); south.add(quality);
        seek.setBackground(BG);
        seek.addChangeListener(e -> { if (seek.getValueIsAdjusting()) { dragging = true; }
            else if (dragging) { Engine.seek(seek.getValue() / 1000.0 * Engine.duration()); dragging = false; } });
        south.add(seek); south.add(time);

        JPanel btns = new JPanel(); btns.setBackground(BG);
        JButton open = new JButton("Open files"), prev = new JButton("Prev"), next = new JButton("Next");
        open.addActionListener(e -> choose());
        prev.addActionListener(e -> step(-1)); next.addActionListener(e -> step(1));
        play.addActionListener(e -> toggle());
        btns.add(open); btns.add(prev); btns.add(play); btns.add(next);
        south.add(btns);

        JCheckBox eq = new JCheckBox("Equalizer on (off = bypass, samples untouched)");
        eq.setBackground(BG); eq.setForeground(SILVER);
        eq.addActionListener(e -> Engine.eq(eq.isSelected()));
        south.add(eq);
        JPanel bands = new JPanel(new GridLayout(1, 11, 4, 0)); bands.setBackground(BG);
        for (int i = 0; i < 10; i++) {
            final int b = i;
            JSlider s = new JSlider(JSlider.VERTICAL, -12, 12, 0);
            s.setBackground(BG); s.setPreferredSize(new Dimension(40, 120));
            s.addChangeListener(e -> Engine.band(b, s.getValue()));
            JPanel p = new JPanel(new BorderLayout()); p.setBackground(BG);
            p.add(s, BorderLayout.CENTER); p.add(label(Engine.BAND_LABELS[i], 11, MUTE), BorderLayout.SOUTH);
            bands.add(p);
        }
        JSlider pre = new JSlider(JSlider.VERTICAL, -12, 6, 0);
        pre.setBackground(BG); pre.addChangeListener(e -> Engine.preamp(pre.getValue()));
        JPanel pp = new JPanel(new BorderLayout()); pp.setBackground(BG);
        pp.add(pre, BorderLayout.CENTER); pp.add(label("Pre", 11, MUTE), BorderLayout.SOUTH);
        bands.add(pp);
        south.add(bands);
        root.add(south, BorderLayout.SOUTH);

        f.setContentPane(root);
        f.setSize(560, 720);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        new Timer(250, e -> tick()).start();
    }

    static void choose() {
        JFileChooser c = new JFileChooser();
        c.setMultiSelectionEnabled(true);
        if (c.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;
        boolean first = files.isEmpty();
        for (File x : c.getSelectedFiles()) { files.add(x); model.addElement(x.getName()); }
        if (first && !files.isEmpty()) load(0);
    }

    static void load(int i) {
        if (i < 0 || i >= files.size()) return;
        if (Engine.open(files.get(i).getAbsolutePath()) != 0) {
            title.setText("Could not open " + files.get(i).getName());
            return;
        }
        idx = i;
        list.setSelectedIndex(i);
        title.setText(files.get(i).getName());
        quality.setText(Engine.describe());
        Engine.play();
        play.setText("Pause");
    }

    static void toggle() {
        if (idx < 0) return;
        if (Engine.playing() == 1) { Engine.pause(); play.setText("Play"); }
        else { Engine.play(); play.setText("Pause"); }
    }

    static void step(int d) { if (!files.isEmpty()) load((idx + d + files.size()) % files.size()); }

    static void tick() {
        if (idx < 0) return;
        double p = Engine.position(), d = Engine.duration();
        if (!dragging && d > 0) seek.setValue((int) (p / d * 1000));
        time.setText(Engine.fmt(p) + " / " + Engine.fmt(d));
        if (Engine.finished() == 1) {
            if (idx + 1 < files.size()) load(idx + 1); else { play.setText("Play"); }
        }
    }
}
