import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Main {
    public static String consoleIP;
    static OutputStream gdb;
    static Map<String, List<String[]>> codePatches;
    static String log;
    static Map<String, String> originalCode;
    static boolean waitingForCodeRead;
    static String baseOffset;
    static String codeForOrig;
    static boolean codePatchQueue;
    static String pid;
    static boolean loading;
    static boolean patching;
    static Map<String, Boolean> modState;
    static boolean gameReady;
    static JFrame frame;
    static boolean nxConnected;
    static int btnBound = 60;
    static JTextArea statusLabel;
    static Map<String, JButton> cheatBtns;
    static Set<String> cheatsWereOn;
    static boolean autoOffCheats;
    static String filePath;

    static void save() {
        Gson gson = new Gson();
        String jsonString = gson.toJson(codePatches);
        try {
            Files.writeString(Path.of(filePath), jsonString);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static void load() {
        Gson gson = new Gson();
        try {
            String json = Files.readString(Path.of(filePath));
            Type t = new TypeToken<Map<String, List<String[]>>>() {
            }.getType();
            codePatches = gson.fromJson(json, t);
        } catch (IOException e) {
            codePatches = new HashMap<>();
        }

    }

    static void fillCheatsBtns(String cheatName) {
        JButton cheatToggle = new JButton("<html><u>OFF - </u>" + cheatName + "</html>");
        cheatBtns.put(cheatName, cheatToggle);
        cheatToggle.addActionListener(actionEvent1 -> {
            boolean status = cheatToggle.getText().startsWith("<html><u>OFF - </u>");
            cheatToggle.setText(status ? "<html><u>ON - </u>" + cheatName + "</html>" : "<html><u>OFF - </u>" + cheatName + "</html>");
            cheatsWereOn.remove(cheatName);
            try {
                patchCode(cheatName, status);
                while (patching) {
                    Thread.sleep(10);
                }
                sendToGdb("c");
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
                        startLooper();
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
                String cheatIn = JOptionPane.showInputDialog("Enter patch data\nEx. 023a4ff5 2000805215000014");
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
            save();
            fillCheatsBtns(cheatname);
        });
        deleteCheat.addActionListener(actionEvent -> {
            String[] names = codePatches.keySet().toArray(new String[0]);
            String name = (String) JOptionPane.showInputDialog(null, "What patch do you want to delete? (It will be disabled, too)", "Delete", JOptionPane.QUESTION_MESSAGE, null, names, null);
            if (name != null) {
                try {
                    patchCode(name, false);
                    codePatches.remove(name);
                    frame.remove(cheatBtns.get(name));
                    cheatBtns.remove(name);
                    cheatsWereOn.remove(name);
                    sendToGdb("c");
                    save();
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

    static void startLooper() throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder("gdb-multiarch");
        setLabel("Connecting to console");
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        gdb = process.getOutputStream();
        pid = String.valueOf(process.pid());
        //connect to GDB on the console
        sendToGdb("target extended-remote " + consoleIP + ":22225");
        try (BufferedReader processOutputReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String readLine;
            boolean gameStarted = false;
            boolean gameAttached = false;
            boolean gotBaseOffset = false;
            waitingForCodeRead = false;
            originalCode = new HashMap<>();
           /* new Thread(new Runnable() {
                @Override
                public void run() {
                    boolean last = false;
                    while (true) {
                        JOptionPane.showMessageDialog(null, "Click to toggle mod");
                        last = !last;
                        try {
                            patchCode("Swim Everywhere", last);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }).start();*/
            while ((readLine = processOutputReader.readLine()) != null) {
                // System.out.println(readLine + System.lineSeparator());
                if (readLine.startsWith("(gdb)")) readLine = readLine.substring(5);
                readLine = readLine.trim();
                if (!nxConnected && readLine.equals("Remote debugging using " + consoleIP + ":22225")) {
                    //found console, try to wait for game
                    setLabel("Start the game!");
                    sendToGdb("mon wait application");
                    nxConnected = true;
                    continue;
                }
                if (nxConnected && !gameStarted && readLine.startsWith("Send `attach 0x")) {
                    //game found, try to attach
                    setLabel("Injecting into game");
                    Thread.sleep(100);
                    gameStarted = true;
                    String attachCmd = readLine.replace("Send `", "");
                    attachCmd = attachCmd.replace("` to attach.", "");
                    sendToGdb(attachCmd);
                    continue;
                }
                if (gameStarted && !gameAttached && readLine.startsWith("warning: ") && readLine.endsWith(": No such file or directory.")) {
                    //cleanup + print info for base
                    gameAttached = true;
                    setLabel("Reading game info");
                    sendToGdb("delete");
                    sendToGdb("y");
                    sendToGdb("mon get info");
                    continue;
                }
                if (gameAttached && !gotBaseOffset && readLine.endsWith(" Blitz.nss") || readLine.endsWith(" Thunder.nss")) {
                    boolean splat3 = readLine.endsWith(" Thunder.nss");
                    //get base of game and store it
                    baseOffset = readLine.substring(0, 12);
                    baseOffset = baseOffset.trim();
                    setLabel("Found Splatoon " + (splat3 ? "3" : "2") + " code base");
                    gotBaseOffset = true;
                    //place a breakpoint at the scene loader (when AC is called)
                    if (!splat3) sendToGdb("hbreak *(0x197F7EC + " + baseOffset + ")");
                    else {
                        setLabel("Splatoon 3 anticheat safety isn't ready yet :( Have fun");
                    }
                    sendToGdb("c");
                    filePath = "/home/ewan/Desktop/cheats" + (splat3 ? "Thunder" : "Blitz") + ".json";
                    load();
                    for (String name : codePatches.keySet()) {
                        fillCheatsBtns(name);
                    }
                    frame.invalidate();
                    frame.repaint();
                    continue;
                }
                if (waitingForCodeRead && readLine.contains(":\t")) {
                    codeForOrig = readLine.substring(readLine.length() - 8);
                    waitingForCodeRead = false;
                    continue;
                }
                if (readLine.contains("Thread 1 \"Thread_0x0000000000\" hit Breakpoint 1,")) {
                    new Thread(() -> {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        setLabel("Scene changed, " + (autoOffCheats ? "patches disabled." : "PATCHES WERE NOT DISABLED"));
                    }).start();
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            loading = true;
                            if (autoOffCheats) for (String cheats : codePatches.keySet()) {
                                if (modState.containsKey(cheats) && modState.get(cheats)) {
                                    try {
                                        patchCode(cheats, false);
                                        cheatBtns.get(cheats).setText("<html><u>OFF - </u>" + cheats + "</html>");
                                        cheatsWereOn.add(cheats);
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                    while (patching) {
                                        try {
                                            Thread.sleep(10);
                                        } catch (InterruptedException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }
                                }

                            }
                            loading = false;
                            try {
                                Thread.sleep(150);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            try {
                                sendToGdb("c");
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }).start();
                    continue;
                }
                if (readLine.endsWith("received signal SIGINT, Interrupt.")) {
                    gameReady = true;
                    continue;
                }
                if (readLine.contains("Program terminated")) {
                    setLabel("Game closed");
                    startLooper();
                    continue;
                }
                if (readLine.contains("\"monitor\" command not supported by this target") || readLine.contains("Connection timed out")) {
                    setLabel("Dead");
                    JOptionPane.showMessageDialog(null, "Something went wrong connecting to console");
                }
            }

        }
    }

    static void patchCode(String cheatName, boolean modGame) throws IOException {
        patching = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    //just in case we're waiting on another op
                    while (codePatchQueue) {
                        Thread.sleep(20);
                    }
                    if (!loading) {
                        new ProcessBuilder("kill", "-s", "SIGINT", pid).start().waitFor();
                        while (!gameReady) {
                            Thread.sleep(10);
                        }
                        gameReady = false;
                    }
                    List<String[]> cheat = codePatches.get(cheatName);
                    for (String[] line : cheat) {
                        //check if we made a copy of the original code for anticheat
                        if (!originalCode.containsKey(line[0])) {
                            //ask gdb for original code
                            codePatchQueue = true;
                            waitingForCodeRead = true;
                            sendToGdb("x (" + baseOffset + "+0x" + line[0] + ")");
                            while (waitingForCodeRead) {
                                Thread.sleep(20);
                            }
                            originalCode.put(line[0], codeForOrig);
                            setLabel("Copied code for backup from basegame");
                            codePatchQueue = false;
                        }
                        //tell GDB to overwrite the code at the offset either with our mod or with the original vanilla code
                        setLabel("Writing new code to game");
                        sendToGdb("set {int}(" + baseOffset + "+0x" + line[0] + ") = 0x" + (modGame ? reverseHex(line[1]) : originalCode.get(line[0])));
                        modState.put(cheatName, modGame);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                patching = false;
            }
        }).start();
    }

    static String reverseHex(String originalHex) {
        int lengthInBytes = originalHex.length() / 2;
        char[] chars = new char[lengthInBytes * 2];
        for (int index = 0; index < lengthInBytes; index++) {
            int reversedIndex = lengthInBytes - 1 - index;
            chars[reversedIndex * 2] = originalHex.charAt(index * 2);
            chars[reversedIndex * 2 + 1] = originalHex.charAt(index * 2 + 1);
        }
        return new String(chars);
    }

    static void sendToGdb(String s) throws IOException {

        try {
            // Thread.sleep(100);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // System.out.println("(gdb) " + s);
        gdb.write((s + "\n").getBytes(StandardCharsets.US_ASCII));
        gdb.flush();
       /* if (s.equals("c") || s.startsWith("x")) {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }*/
    }
}
