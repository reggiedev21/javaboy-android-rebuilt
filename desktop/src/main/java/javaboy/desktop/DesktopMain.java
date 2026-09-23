package javaboy.desktop;

import javaboy.core.*;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Harnais PC : la même intégration que l'application Android, mais en Swing.
 *  Sert à vérifier le cœur sur PC à chaque étape (règle du plan d'action). */
public final class DesktopMain extends JFrame {
    private static final int SCALE = 3;
    private final BufferedImage image = new BufferedImage(GraphicsChip.WIDTH, GraphicsChip.HEIGHT, BufferedImage.TYPE_INT_RGB);
    private final JPanel screen = new JPanel() {
        @Override protected void paintComponent(Graphics g) {
            g.drawImage(image, 0, 0, getWidth(), getHeight(), null);
        }
    };
    private final Joypad joypad = new Joypad();
    private volatile Emulator emulator;
    private Thread emuThread;
    private Path romPath;
    private final JLabel status = new JLabel("Fichier > Ouvrir une ROM");

    public DesktopMain() {
        super("JavaBoy (cœur découplé)");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        screen.setPreferredSize(new Dimension(GraphicsChip.WIDTH * SCALE, GraphicsChip.HEIGHT * SCALE));
        add(screen, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        setJMenuBar(buildMenu());
        installKeys();
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { stopEmulation(); }
        });
        pack();
        setLocationRelativeTo(null);
    }

    private JMenuBar buildMenu() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("Fichier");
        JMenuItem open = new JMenuItem("Ouvrir une ROM…");
        open.addActionListener(e -> chooseRom());
        JMenuItem reset = new JMenuItem("Reset");
        reset.addActionListener(e -> { Emulator em = emulator; if (em != null) em.reset(); });
        JMenuItem quit = new JMenuItem("Quitter");
        quit.addActionListener(e -> { stopEmulation(); System.exit(0); });
        file.add(open); file.add(reset); file.addSeparator(); file.add(quit);
        JMenu view = new JMenu("Couleurs");
        for (int i = 0; i < Emulator.SCHEME_NAMES.length; i++) {
            final int idx = i;
            JMenuItem it = new JMenuItem(Emulator.SCHEME_NAMES[i]);
            it.addActionListener(e -> { Emulator em = emulator; if (em != null) em.setColourScheme(idx); });
            view.add(it);
        }
        bar.add(file); bar.add(view);
        return bar;
    }

    private void chooseRom() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        loadRom(fc.getSelectedFile().toPath());
    }

    private void loadRom(Path path) {
        stopEmulation();
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Erreur de lecture", JOptionPane.ERROR_MESSAGE);
            return;
        }
        switch (Cartridge.load(bytes)) {
            case CartridgeResult.Error err ->
                JOptionPane.showMessageDialog(this, err.message(), "ROM invalide", JOptionPane.ERROR_MESSAGE);
            case CartridgeResult.Ok ok -> {
                if (!ok.checksumValid()) status.setText("Attention : checksum invalide");
                startEmulation(ok.cartridge(), path);
            }
        }
    }

    private void startEmulation(Cartridge cart, Path path) {
        romPath = path;
        readSav(cart);
        AudioOutput audio = JavaSoundOutput.open(Emulator.DEFAULT_SAMPLE_RATE);
        Emulator emu = new Emulator(cart, joypad, audio, Emulator.DEFAULT_SAMPLE_RATE);
        emu.setFrameListener(pixels -> {
            image.setRGB(0, 0, GraphicsChip.WIDTH, GraphicsChip.HEIGHT, pixels, 0, GraphicsChip.WIDTH);
            screen.repaint();
        });
        emu.reset();
        emulator = emu;
        setTitle("JavaBoy — " + cart.getCartName() + " (" + cart.getCartTypeName() + (emu.isGbcMode() ? ", GBC" : "") + ")");
        emuThread = new Thread(() -> runLoop(emu), "emulation");
        emuThread.setDaemon(true);
        emuThread.start();
    }

    /** Boucle d'émulation : une image, puis attente jusqu'à l'échéance de 16,74 ms. */
    private void runLoop(Emulator emu) {
        final long frameNanos = 1_000_000_000L / 60;
        long next = System.nanoTime();
        int frames = 0;
        long lastFps = System.currentTimeMillis();
        while (emulator == emu && !Thread.currentThread().isInterrupted()) {
            emu.runFrame();
            frames++;
            next += frameNanos;
            long wait = next - System.nanoTime();
            if (wait > 0) {
                try { Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000)); } catch (InterruptedException e) { return; }
            } else if (wait < -200_000_000L) {
                next = System.nanoTime(); // trop en retard : on abandonne le rattrapage
            }
            if (System.currentTimeMillis() - lastFps >= 1000) {
                final int f = frames;
                SwingUtilities.invokeLater(() -> status.setText(f + " images/s"));
                frames = 0;
                lastFps = System.currentTimeMillis();
            }
        }
    }

    private void stopEmulation() {
        Emulator emu = emulator;
        if (emu == null) return;
        emulator = null;
        emu.stop();
        try { emuThread.join(1000); } catch (InterruptedException ignored) { }
        writeSav(emu.getCartridge());
    }

    private Path savPath() {
        String n = romPath.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return romPath.resolveSibling((dot > 0 ? n.substring(0, dot) : n) + ".sav");
    }

    private void readSav(Cartridge cart) {
        if (!cart.canSave() || romPath == null) return;
        try { if (Files.exists(savPath())) cart.loadBatteryRam(Files.readAllBytes(savPath())); } catch (IOException ignored) { }
    }

    private void writeSav(Cartridge cart) {
        if (!cart.canSave() || romPath == null) return;
        try { Files.write(savPath(), cart.getBatteryRam()); } catch (IOException ignored) { }
    }

    private void installKeys() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            Joypad.Button b = switch (e.getKeyCode()) {
                case KeyEvent.VK_UP -> Joypad.Button.UP;
                case KeyEvent.VK_DOWN -> Joypad.Button.DOWN;
                case KeyEvent.VK_LEFT -> Joypad.Button.LEFT;
                case KeyEvent.VK_RIGHT -> Joypad.Button.RIGHT;
                case KeyEvent.VK_Z -> Joypad.Button.A;
                case KeyEvent.VK_X -> Joypad.Button.B;
                case KeyEvent.VK_ENTER -> Joypad.Button.START;
                case KeyEvent.VK_BACK_SPACE -> Joypad.Button.SELECT;
                default -> null;
            };
            if (b == null) return false;
            Emulator emu = emulator;
            if (e.getID() == KeyEvent.KEY_PRESSED && emu != null) emu.setButton(b, true);
            else if (e.getID() == KeyEvent.KEY_RELEASED && emu != null) emu.setButton(b, false);
            return true;
        });
    }

    /** Sortie audio PC : javax.sound.sampled derrière l'interface du cœur. */
    static final class JavaSoundOutput implements AudioOutput {
        private final SourceDataLine line;
        private JavaSoundOutput(SourceDataLine line) { this.line = line; }

        static AudioOutput open(int sampleRate) {
            try {
                AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, 8, 2, 2, sampleRate, true);
                SourceDataLine line = AudioSystem.getSourceDataLine(format);
                line.open(format, (sampleRate / 1000) * 200 * 2);
                line.start();
                return new JavaSoundOutput(line);
            } catch (Exception e) {
                System.out.println("Son indisponible : " + e.getMessage());
                return AudioOutput.NONE;
            }
        }
        @Override public int availableBytes() { return line.available(); }
        @Override public void write(byte[] buffer, int length) { line.write(buffer, 0, length); }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            DesktopMain w = new DesktopMain();
            w.setVisible(true);
            if (args.length > 0) w.loadRom(new File(args[0]).toPath());
        });
    }
}
