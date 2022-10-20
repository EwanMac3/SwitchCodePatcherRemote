import com.formdev.flatlaf.FlatDarculaLaf;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
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
    static JTextArea codeText;
    static String selectedCheat;
    static JTextArea statusLabel;
    static SpringLayout layout;
    static Map<String, Box> cheatBtns;
    static boolean autoOffCheats;
    static Box cheatsBox;
    static Map<String, String> cheatsRawText;
    static Set<String> patchesOn;

    static void fillCheatsBtns(String cheatName) {
        Box cheatBox = Box.createHorizontalBox();
        JButton cheatToggle = new JButton("<html><b>✗ <u>OFF</b></u></html>");
        cheatToggle.setMaximumSize(new Dimension(90, 50));
        cheatToggle.setPreferredSize(new Dimension(90, 50));
        cheatToggle.setMinimumSize(new Dimension(90, 50));
        JButton cheatLabel = new JButton(cheatName);
        cheatLabel.setMaximumSize(new Dimension(410, 50));
        cheatLabel.setPreferredSize(new Dimension(410, 50));
        cheatLabel.setMinimumSize(new Dimension(410, 50));
        cheatBox.add(cheatLabel);
        cheatBox.add(cheatToggle);
        cheatBtns.put(cheatName, cheatBox);
        cheatLabel.addActionListener(e -> {
            selectedCheat = cheatName;
            if (!codePatches.containsKey(selectedCheat)) {
                codeText.setText("//replace this with patch code");
                codeText.repaint();
                return;
            }
            if (cheatsRawText.containsKey(cheatName)) {
                codeText.setText(cheatsRawText.get(cheatName));
            } else {
                StringBuilder newCheat = new StringBuilder();
                List<String[]> cheat = codePatches.get(selectedCheat);
                long lastLine = -999999;
                for (String[] line : cheat) {
                    while (line[0].length() < 8) {
                        line[0] = "0" + line[0];
                    }
                    while (line[1].length() < 8) {
                        line[1] = "0" + line[1];
                    }
                    long curOff = Long.parseLong(line[0], 16);
                    if (curOff == lastLine + 4) {
                        newCheat = new StringBuilder(newCheat.substring(0, newCheat.length() - 1));
                        newCheat.append(line[1]).append("\n");
                    } else {
                        newCheat.append(line[0]).append(" ").append(line[1]).append("\n");
                    }
                    lastLine = curOff;
                }
                codeText.setText(newCheat.toString().trim());
                cheatsRawText.put(cheatName, newCheat.toString());
            }
            codeText.setText(codeText.getText().toUpperCase());
            codeText.repaint();
        });
        cheatLabel.setHorizontalAlignment(SwingConstants.LEFT);
        cheatToggle.addActionListener(actionEvent1 -> {
            if (patching) return;
            if (!codePatches.containsKey(cheatName)) {
                JOptionPane.showMessageDialog(null, "Patch does not exist. Did you save it?");
                return;
            }
            boolean status = cheatToggle.getText().contains("✗");
            cheatToggle.setText(status ? "<html><b>✓ <u>ON</b></u></html>" : "<html><b>✗ <u>OFF</b></u></html>");
            try {
                while (patching) {
                    //wait for ongoing shit to finish
                    //noinspection BusyWait
                    Thread.sleep(50);
                }
                ConsoleManager.patchCode(cheatName, status);
                if (status) patchesOn.add(cheatName);
                else patchesOn.remove(cheatName);
                while (patching) {
                    //wait for ongoing shit to finish, then unpause
                    //noinspection BusyWait
                    Thread.sleep(50);
                }
                ConsoleManager.sendToGdb("c");
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        cheatsBox.add(cheatBox);
        cheatsBox.repaint();
    }


    public static void main(String[] args) throws InterruptedException {
        FlatDarculaLaf.setup();
        layout = new SpringLayout();
        frame = new JFrame("Switch Code Patcher Remote - Release Build 4");
        JButton connectToConsole = new JButton("Connect to console");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setLayout(layout);
        autoOffCheats = true;
        modState = new HashMap<>();
        cheatBtns = new HashMap<>();
        cheatsRawText = new HashMap<>();
        connectToConsole.addActionListener(actionEvent -> {
            consoleIP = JOptionPane.showInputDialog("Enter console IP address");

            new Thread(() -> {
                try {
                    ConsoleManager.startLooper();
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        });
        connectToConsole.setPreferredSize(new Dimension(200, 50));
        Container pane = frame.getContentPane();
        layout.putConstraint(SpringLayout.NORTH, connectToConsole, 0, SpringLayout.NORTH, pane);
        layout.putConstraint(SpringLayout.WEST, connectToConsole, 0, SpringLayout.WEST, pane);
        pane.add(connectToConsole);
        statusLabel = new JTextArea("Status: Idle");
        statusLabel.setLineWrap(true);
        statusLabel.setEditable(false);
        frame.add(statusLabel);
        JButton autoOff = new JButton("Scene Load → Patch OFF");
        autoOff.setPreferredSize(new Dimension(200, 50));
        layout.putConstraint(SpringLayout.NORTH, autoOff, 0, SpringLayout.NORTH, pane);
        layout.putConstraint(SpringLayout.EAST, autoOff, 0, SpringLayout.EAST, pane);

        layout.putConstraint(SpringLayout.NORTH, statusLabel, 0, SpringLayout.SOUTH, autoOff);
        layout.putConstraint(SpringLayout.WEST, statusLabel, -200, SpringLayout.EAST, pane);
        layout.putConstraint(SpringLayout.EAST, statusLabel, 0, SpringLayout.EAST, pane);
        layout.putConstraint(SpringLayout.SOUTH, statusLabel, 0, SpringLayout.SOUTH, pane);

        autoOff.addActionListener(actionEvent -> {
            autoOffCheats = !autoOffCheats;
            if (!ConsoleManager.notSplatoon)
                setLabel(autoOffCheats ? "------\nPatches will turn OFF when scene changes or battle starts.\n------" : "!-!-!-!-!\nPatches will stay on even when scene changes and battle starts. (Are you sure?)\n!-!-!-!-!");
            else
                JOptionPane.showMessageDialog(null, "This is not Splatoon 2 or Splatoon 3, so this button does nothing.");
        });
        frame.setSize(1200, 700);
        frame.add(autoOff);
        frame.setVisible(true);

        while (!nxConnected) {
            //noinspection BusyWait
            Thread.sleep(20);
        }
        JButton addCheat = new JButton("Add patch");
        JButton deleteCheat = new JButton("Remove patch");

        addCheat.addActionListener(actionEvent -> {
            String cheatName = JOptionPane.showInputDialog("What is the name of the patch? (eg. infinite ink, force ready, etc)");
            setLabel("Added. Enter the code and save it.");
            selectedCheat = cheatName;
            codeText.setText("//replace this with patch code");
            fillCheatsBtns(cheatName);
        });
        layout.putConstraint(SpringLayout.NORTH, addCheat, 0, SpringLayout.NORTH, connectToConsole);
        layout.putConstraint(SpringLayout.EAST, addCheat, 0, SpringLayout.EAST, connectToConsole);
        layout.putConstraint(SpringLayout.SOUTH, addCheat, 0, SpringLayout.SOUTH, connectToConsole);
        layout.putConstraint(SpringLayout.WEST, addCheat, 0, SpringLayout.WEST, connectToConsole);
        layout.putConstraint(SpringLayout.NORTH, deleteCheat, 0, SpringLayout.NORTH, addCheat);
        layout.putConstraint(SpringLayout.WEST, deleteCheat, 0, SpringLayout.EAST, addCheat);
        layout.putConstraint(SpringLayout.SOUTH, deleteCheat, 0, SpringLayout.SOUTH, addCheat);

        frame.remove(connectToConsole);
        frame.add(addCheat);
        frame.add(deleteCheat);
        cheatsBox = Box.createVerticalBox();
        JScrollPane cheatListPane = new JScrollPane(cheatsBox);
        cheatListPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        layout.putConstraint(SpringLayout.NORTH, cheatListPane, 0, SpringLayout.SOUTH, addCheat);
        layout.putConstraint(SpringLayout.SOUTH, cheatListPane, 0, SpringLayout.SOUTH, pane);
        layout.putConstraint(SpringLayout.EAST, cheatListPane, 520, SpringLayout.WEST, pane);
        layout.putConstraint(SpringLayout.WEST, cheatListPane, 0, SpringLayout.WEST, pane);
        pane.add(cheatListPane);

        codeText = new JTextArea("Select a patch!");
        codeText.setLineWrap(false);

        layout.putConstraint(SpringLayout.NORTH, codeText, 0, SpringLayout.NORTH, cheatListPane);
        layout.putConstraint(SpringLayout.SOUTH, codeText, 0, SpringLayout.SOUTH, statusLabel);
        layout.putConstraint(SpringLayout.WEST, codeText, 0, SpringLayout.EAST, cheatListPane);
        layout.putConstraint(SpringLayout.EAST, codeText, 0, SpringLayout.WEST, statusLabel);
        pane.add(codeText);

        JButton saveEdit = new JButton("Save current patch");
        saveEdit.addActionListener(e -> {
            if (selectedCheat == null) {
                JOptionPane.showMessageDialog(null, "Select a patch first!");
                return;
            }
            if (processText()) {
                JOptionPane.showMessageDialog(null, "Saved patch " + selectedCheat);
            } else {
                JOptionPane.showMessageDialog(null, "Could not save patch! Check log...");
            }
        });
        layout.putConstraint(SpringLayout.NORTH, saveEdit, 0, SpringLayout.NORTH, deleteCheat);
        layout.putConstraint(SpringLayout.SOUTH, saveEdit, 0, SpringLayout.SOUTH, deleteCheat);
        layout.putConstraint(SpringLayout.WEST, saveEdit, 0, SpringLayout.EAST, deleteCheat);
        pane.add(saveEdit);


        deleteCheat.addActionListener(e -> {
            if (selectedCheat == null) {
                JOptionPane.showMessageDialog(null, "Select a patch and try again");
                return;
            }
            if (JOptionPane.showConfirmDialog(frame, "Delete patch " + selectedCheat + "? It will be disabled.", "Delete Patch", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
                try {
                    if (Main.modState.containsKey(selectedCheat) && Main.modState.get(selectedCheat)) {
                        ConsoleManager.patchCode(selectedCheat, false);
                        while (patching) {
                            //noinspection BusyWait
                            Thread.sleep(10);
                        }
                        ConsoleManager.sendToGdb("c");
                    }
                    codePatches.remove(selectedCheat);
                    cheatsBox.remove(cheatBtns.get(selectedCheat));
                    cheatBtns.remove(selectedCheat);
                    selectedCheat = null;
                    codeText.setText("Select a patch!");
                    SaveManage.save();
                    cheatListPane.repaint();
                } catch (Exception ex) {
                    //die
                }
            }
        });
    }

    static void setLabel(String s) {
        if (log == null) log = "";
        log = s + "\n" + log;
        statusLabel.setText(log);
        frame.invalidate();
        frame.repaint();
    }

    static boolean processText() {
        String cheatInAll = codeText.getText().trim();
        String[] cheatSplit = cheatInAll.split("\n");
        for (String line : cheatSplit) {
            line = line.trim();
            if (line.length() < 17) {
                setLabel("Line missing bytes");
                return false;
            }
            if (line.charAt(8) != ' ') {
                setLabel("No space after offset");
                return false;
            }
            String code = line.substring(9);
            if (code.length() % 8 != 0) {
                setLabel("Code not made up of 4 byte intervals");
                return false;
            }
        }
        List<String[]> newCheat = new ArrayList<>();
        for (String s : cheatSplit) {
            if (s.trim().isEmpty()) continue;
            String cheatOffset = s.substring(0, 8);
            String cheatVal = s.substring(9);
            for (int j = 0; j < cheatVal.length(); j += 8) {
                int calcOff = Integer.parseUnsignedInt(cheatOffset, 16) + (j / 2);
                String val = cheatVal.substring(j, j + 8);
                String offHexStr = Integer.toHexString(calcOff);
                newCheat.add(new String[]{offHexStr, val});
            }
        }
        codePatches.put(selectedCheat, newCheat);
        SaveManage.save();
        return true;
    }
}