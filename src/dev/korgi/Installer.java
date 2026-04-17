package dev.korgi;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.korgi.json.JSONObject;
import dev.korgi.utils.InstallConstants;

public class Installer {

    public static void runInstall() throws IOException, InterruptedException {
        File internalConfig = new File("./internal_config.json");
        JSONObject internalConfigData = new JSONObject();
        String installPath = InstallerGUI.installPath + "Korgi/" + InstallConstants.appName + "/";
        internalConfigData.set("path", installPath);
        internalConfig.createNewFile();
        FileOutputStream stream = new FileOutputStream(internalConfig);
        byte[] output = internalConfigData.toJSONString().getBytes();
        stream.write(output, 0, output.length);
        stream.close();
        ProcessBuilder builder = new ProcessBuilder("./runjava");
        builder.redirectErrorStream(true);
        Process process = builder.start();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.contains("Finished")) {
                break;
            }
        }
        process.destroyForcibly();

        ProcessBuilder jarBuilder = new ProcessBuilder(
                "jar",
                "cfe",
                "korgi_main.jar",
                "dev.korgi.Main",
                "-C", "./bin", ".",
                "-C", "./lib", "processing",
                "-C", "./lib", "font",
                "-C", "./lib", "icon",
                "-C", "./lib", "META-INF");

        jarBuilder.inheritIO();
        Process jarProcess = jarBuilder.start();
        jarProcess.waitFor();

        Files.createDirectories(Path.of(installPath));
        Files.move(Path.of("./korgi_main.jar"), Path.of(installPath +
                "korgi_main.jar").toAbsolutePath());
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac")) {
            Files.copy(Path.of("./natives/mac/build/Shaders.metallib"),
                    Path.of(installPath, "Shaders.metallib").toAbsolutePath());
            Files.copy(Path.of("./natives/mac/build/EntityShader.metallib"),
                    Path.of(installPath, "EntityShader.metallib").toAbsolutePath());
            Files.copy(Path.of("./natives/mac/build/libkorgikompute-mac.dylib"),
                    Path.of(installPath, "libkorgikompute-mac.dylib").toAbsolutePath());
        } else if (os.contains("win")) {
            Files.copy(Path.of("./natives/win/build/glfw3.dll"),
                    Path.of(installPath, "glfw3.dll").toAbsolutePath());
            Files.copy(Path.of("./natives/win/build/korgikompute-win.dll"),
                    Path.of(installPath, "korgikompute-win.dll").toAbsolutePath());
            Files.copy(Path.of("./natives/win/src/Shaders.comp.glsl"),
                    Path.of(installPath, "Shaders.comp.glsl").toAbsolutePath());
            Files.copy(Path.of("./natives/win/src/EntityShader.comp.glsl"),
                    Path.of(installPath, "EntityShader.comp.glsl").toAbsolutePath());
        }
        Files.copy(internalConfig.toPath(), Path.of(installPath, "internal_config.json").toAbsolutePath());
        deepCopy(Path.of("./texture"), Path.of(installPath, "texture"));
        deepCopy(Path.of("./models"), Path.of(installPath, "models"));
    }

    private static void deepCopy(Path source, Path target) throws IOException {
        Files.walk(source).forEach(path -> {
            try {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative);

                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

}
