import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SaveManage {
    public static String filePath;

    static void save() {
        Gson gson = new Gson();
        String jsonString = gson.toJson(Main.codePatches);
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
            Main.codePatches = gson.fromJson(json, t);
        } catch (IOException e) {
            Main.codePatches = new HashMap<>();
        }

    }
}
