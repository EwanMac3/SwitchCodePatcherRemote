import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

public class Main {
    public static String consoleIP;
    static OutputStream gdb;
    static Map<String, List<String[]>> codePatches;
    static String log;
    static String pid;
    static boolean patching;
    static Map<String, Boolean> modState;
    static JFrame frame;
    static boolean nxConnected;
    static int btnBound = 60;
    static JTextArea statusLabel;
    static Map<String, JButton> cheatBtns;
    static Set<String> cheatsWereOn;
    static boolean autoOffCheats;


    static void fillCheatsBtns(String cheatName) {
        JButton cheatToggle = new JButton("<html><u>OFF - </u>" + cheatName + "</html>");
        cheatBtns.put(cheatName, cheatToggle);
        cheatToggle.addActionListener(actionEvent1 -> {
            boolean status = cheatToggle.getText().startsWith("<html><u>OFF - </u>");
            cheatToggle.setText(status ? "<html><u>ON - " + cheatName + "</html>" : "<html><u>OFF - </u>" + cheatName + "</html>");
            cheatsWereOn.remove(cheatName);
            try {
                ConsoleManager.patchCode(cheatName, status);
                while (patching) {
                    Thread.sleep(10);
                }
                ConsoleManager.sendToGdb("c");
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        cheatToggle.setBounds(10, btnBound, 400, 40);
        btnBound += 50;
        frame.add(cheatToggle);
        frame.invalidate();
        frame.repaint();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        frame = new JFrame();
        JButton connectToConsole = new JButton("Connect");
        autoOffCheats = true;
        cheatsWereOn = new HashSet<>();
        modState = new HashMap<>();
        cheatBtns = new HashMap<>();
        connectToConsole.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                consoleIP = JOptionPane.showInputDialog("Enter console IP address");

                new Thread(() -> {
                    try {
                        ConsoleManager.startLooper();
                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
            }
        });
        connectToConsole.setBounds(10, 10, 100, 40);
        frame.add(connectToConsole);
        statusLabel = new JTextArea("Status: Idle");
        statusLabel.setBounds(410, 60, 1000, 2000);
        frame.add(statusLabel);
        JButton autoOff = new JButton("Patches OFF on load?");
        autoOff.setBounds(410, 10, 200, 40);
        autoOff.addActionListener(actionEvent -> {
            autoOffCheats = !autoOffCheats;
            setLabel(autoOffCheats ? "Patches will be turned OFF when scene changes" : "PATCHES WILL NOT BE TURNED OFF WHEN SCENE CHANGES.\nTURN THIS ON AGAIN BEFORE PLAYING W/ OTHER CONSOLES");
        });
        frame.setSize(600, 800);
        frame.add(autoOff);
        frame.setLayout(null);
        frame.setVisible(true);

        while (!nxConnected) {
            Thread.sleep(20);
        }
        JButton addCheat = new JButton("Add patch");
        JButton deleteCheat = new JButton("Remove patch");

        addCheat.addActionListener(actionEvent -> {
            String cheatname = JOptionPane.showInputDialog("What is the name of the patch? (eg. infinite ink, force ready, etc)");
            int uniqueOffsets = Integer.parseInt(JOptionPane.showInputDialog("How many pchtxt lines are there?"));
            List<String[]> newCheat = new ArrayList<>();
            for (int i = 0; i < uniqueOffsets; i++) {
                String cheatIn = JOptionPane.showInputDialog("Enter patch data (1 line at a time)\nEx. 023a4ff5 2000805215000014");
                String cheatOffset = cheatIn.substring(0, 8);
                String cheatVal = cheatIn.substring(9);
                for (int j = 0; j < cheatVal.length(); j += 8) {
                    int calcOff = Integer.parseUnsignedInt(cheatOffset, 16) + (j / 2);
                    String val = cheatVal.substring(j, j + 8);
                    String offHexStr = Integer.toHexString(calcOff);
                    newCheat.add(new String[]{offHexStr, val});
                }
            }
            codePatches.put(cheatname, newCheat);
            SaveManage.save();
            fillCheatsBtns(cheatname);
        });
        deleteCheat.addActionListener(actionEvent -> {
            String[] names = codePatches.keySet().toArray(new String[0]);
            String name = (String) JOptionPane.showInputDialog(null, "What patch do you want to delete? (It will be disabled, too)", "Delete", JOptionPane.QUESTION_MESSAGE, null, names, null);
            if (name != null) {
                try {
                    ConsoleManager.patchCode(name, false);
                    codePatches.remove(name);
                    frame.remove(cheatBtns.get(name));
                    cheatBtns.remove(name);
                    cheatsWereOn.remove(name);
                    ConsoleManager.sendToGdb("c");
                    SaveManage.save();
                    frame.invalidate();
                    frame.repaint();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        addCheat.setBounds(10, 10, 200, 40);
        deleteCheat.setBounds(210, 10, 200, 40);
        frame.remove(connectToConsole);
        frame.add(addCheat);
        frame.add(deleteCheat);

    }

    static void setLabel(String s) {
        if (log == null) log = "";
        log = s + "\n" + log;
        statusLabel.setText(log);
        frame.invalidate();
        frame.repaint();
    }
}
