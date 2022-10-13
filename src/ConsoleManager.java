import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConsoleManager {
    static Map<String, String> originalCode;
    static boolean waitingForCodeRead;
    static String baseOffset;
    static String codeForOrig;
    static boolean codePatchQueue;
    static boolean loading;
    static boolean gameReady;

    static void startLooper() throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder("gdb-multiarch");
        Main.setLabel("Connecting to console");
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        Main.gdb = process.getOutputStream();
        Main.pid = String.valueOf(process.pid());
        //connect to GDB on the console
        sendToGdb("target extended-remote " + Main.consoleIP + ":22225");
        try (BufferedReader processOutputReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String readLine;
            boolean gameStarted = false;
            boolean gameAttached = false;
            boolean gotBaseOffset = false;
            waitingForCodeRead = false;
            originalCode = new HashMap<>();
            while ((readLine = processOutputReader.readLine()) != null) {
                // System.out.println(readLine + System.lineSeparator());
                if (readLine.startsWith("(gdb)")) readLine = readLine.substring(5);
                readLine = readLine.trim();
                if (!Main.nxConnected && readLine.equals("Remote debugging using " + Main.consoleIP + ":22225")) {
                    //found console, try to wait for game
                    Main.setLabel("Start the game!");
                    sendToGdb("mon wait application");
                    Main.nxConnected = true;
                    continue;
                }
                if (Main.nxConnected && !gameStarted && readLine.startsWith("Send `attach 0x")) {
                    //game found, try to attach
                    Main.setLabel("Injecting into game");
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
                    Main.setLabel("Reading game info");
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
                    Main.setLabel("Found Splatoon " + (splat3 ? "3" : "2") + " code base");
                    gotBaseOffset = true;
                    //place a breakpoint at the scene loader (when AC is called)
                    if (!splat3) sendToGdb("hbreak *(0x197F7EC + " + baseOffset + ")");
                    else {
                        sendToGdb("hbreak *(0x04077854 + " + baseOffset + ")");
                        Thread.sleep(80);
                        sendToGdb("hbreak *(0x02F23688 + " + baseOffset + ")");
                    }
                    Thread.sleep(20);
                    sendToGdb("c");
                    SaveManage.filePath = "cheats" + (splat3 ? "Thunder" : "Blitz") + ".json";
                    SaveManage.load();
                    for (String name : Main.codePatches.keySet()) {
                        Main.fillCheatsBtns(name);
                    }
                    Main.frame.invalidate();
                    Main.frame.repaint();
                    continue;
                }
                if (waitingForCodeRead && readLine.contains(":\t")) {
                    codeForOrig = readLine.substring(readLine.length() - 8);
                    waitingForCodeRead = false;
                    continue;
                }
                if (readLine.contains(" hit Breakpoint 1,")) {
                    gameLoading();
                    continue;
                }
                if (readLine.contains(" hit Breakpoint 2,")) {
                    gameLoading();
                    Main.setLabel("Match is starting!!");
                    continue;
                }
                if (readLine.endsWith("received signal SIGINT, Interrupt.")) {
                    gameReady = true;
                    continue;
                }
                if (readLine.contains("Program terminated")) {
                    Main.setLabel("Game closed");
                    startLooper();
                    continue;
                }
                if (readLine.contains("\"monitor\" command not supported by this target") || readLine.contains("Connection timed out")) {
                    Main.setLabel("Dead");
                    JOptionPane.showMessageDialog(null, "Something went wrong connecting to console");
                }
            }

        }
    }

    static void gameLoading() {
        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            Main.setLabel("Scene changed, " + (Main.autoOffCheats ? "patches disabled." : "PATCHES WERE NOT DISABLED"));
        }).start();
        new Thread(() -> {
            loading = true;
            if (Main.autoOffCheats) for (String cheats : Main.codePatches.keySet()) {
                if (Main.modState.containsKey(cheats) && Main.modState.get(cheats)) {
                    try {
                        patchCode(cheats, false);
                        Main.cheatBtns.get(cheats).setText("<html><u>OFF - </u>" + cheats + "</html>");
                        Main.cheatsWereOn.add(cheats);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    while (Main.patching) {
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
        Main.gdb.write((s + "\n").getBytes(StandardCharsets.US_ASCII));
        Main.gdb.flush();
       /* if (s.equals("c") || s.startsWith("x")) {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }*/
    }

    static void patchCode(String cheatName, boolean modGame) throws IOException {
        Main.patching = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    //just in case we're waiting on another op
                    while (codePatchQueue) {
                        Thread.sleep(20);
                    }
                    if (!loading) {
                        new ProcessBuilder("kill", "-s", "SIGINT", Main.pid).start().waitFor();
                        while (!gameReady) {
                            Thread.sleep(10);
                        }
                        gameReady = false;
                    }
                    List<String[]> cheat = Main.codePatches.get(cheatName);
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
                            Main.setLabel("Copied code for backup from basegame");
                            codePatchQueue = false;
                        }
                        //tell GDB to overwrite the code at the offset either with our mod or with the original vanilla code
                        Main.setLabel("Writing new code to game");
                        sendToGdb("set {int}(" + baseOffset + "+0x" + line[0] + ") = 0x" + (modGame ? reverseHex(line[1]) : originalCode.get(line[0])));
                        Main.modState.put(cheatName, modGame);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Main.patching = false;
            }
        }).start();
    }
}
