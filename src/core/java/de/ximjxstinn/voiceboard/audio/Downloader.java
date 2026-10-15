package de.ximjxstinn.voiceboard.audio;

import de.ximjxstinn.voiceboard.VoiceBoard;
import de.ximjxstinn.voiceboard.config.BoardConfig;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Lädt Sounds per Link in den Sound-Ordner.
 * <ul>
 *     <li>Direkte Links auf .mp3/.wav werden einfach heruntergeladen.</li>
 *     <li>Alles andere (YouTube, TikTok, SoundCloud, ...) läuft über yt-dlp und ffmpeg.
 *     Beide werden beim ersten Mal automatisch in .minecraft/voiceboard/tools geladen.</li>
 * </ul>
 */
public final class Downloader {

    private static final String YTDLP_BASE = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/";
    private static final String FFMPEG_WINDOWS = "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip";
    private static final String FFMPEG_MAC = "https://evermeet.cx/ffmpeg/getrelease/zip";
    private static final String FFPROBE_MAC = "https://evermeet.cx/ffmpeg/getrelease/ffprobe/zip";
    private static final Pattern PROGRESS = Pattern.compile("\\[download\\]\\s+(\\d+(?:\\.\\d+)?)%");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private static volatile boolean busy;
    private static volatile String status = "";
    /** Wird nach jedem fertigen Download hochgezählt, damit das Menü neu lädt. */
    private static final AtomicInteger FINISHED = new AtomicInteger();

    private Downloader() {
    }

    public static boolean isBusy() {
        return busy;
    }

    public static String status() {
        return status;
    }

    public static int finishedCount() {
        return FINISHED.get();
    }

    public static void start(String rawUrl) {
        String url = rawUrl == null ? "" : rawUrl.trim();
        if (busy) {
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            status = "§cBitte einen Link einfügen, der mit https:// beginnt.";
            return;
        }
        busy = true;
        status = "Starte Download…";
        Thread thread = new Thread(() -> {
            try {
                String file = isDirectAudio(url) ? downloadDirect(url) : downloadWithYtDlp(url);
                VoiceBoard.runOnClient(() -> {
                    BoardConfig config = VoiceBoard.config();
                    config.addSound(file);
                    config.save();
                });
                status = "§aFertig: " + file;
                VoiceBoard.notifyPlayer("Sound heruntergeladen: " + file);
            } catch (Exception e) {
                VoiceBoard.LOGGER.error("Download fehlgeschlagen: {}", url, e);
                status = "§cFehler: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            } finally {
                busy = false;
                FINISHED.incrementAndGet();
            }
        }, "VoiceBoard-Download");
        thread.setDaemon(true);
        thread.start();
    }

    // ---------------------------------------------------------- Direkte Links

    private static boolean isDirectAudio(String url) {
        String path = URI.create(url).getPath();
        return path != null && AudioLoader.isSupported(path);
    }

    private static String downloadDirect(String url) throws Exception {
        String path = URI.create(url).getPath();
        String name = sanitize(path.substring(path.lastIndexOf('/') + 1));
        Path target = uniqueTarget(BoardConfig.soundFolder(), name);
        status = "Lade " + name + "…";
        download(url, target);
        return target.getFileName().toString();
    }

    // ------------------------------------------------------------------ yt-dlp

    private static String downloadWithYtDlp(String url) throws Exception {
        Path tools = toolsFolder();
        Files.createDirectories(tools);
        Path ytdlp = ensureYtDlp(tools);
        String ffmpegLocation = ensureFfmpeg(tools);

        Path sounds = BoardConfig.soundFolder();
        Files.createDirectories(sounds);

        List<String> cmd = new ArrayList<>(List.of(
                ytdlp.toAbsolutePath().toString(),
                "--no-playlist",
                "-x", "--audio-format", "mp3", "--audio-quality", "4",
                "--windows-filenames",
                "--newline", "--progress", "--encoding", "utf-8",
                "--print", "after_move:filepath",
                "-o", sounds.toAbsolutePath() + "/%(title).80s.%(ext)s"
        ));
        if (ffmpegLocation != null) {
            cmd.add("--ffmpeg-location");
            cmd.add(ffmpegLocation);
        }
        cmd.add(url);

        status = "Lade herunter…";
        long startTime = System.currentTimeMillis();
        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String resultPath = null;
        String lastLine = "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                Matcher m = PROGRESS.matcher(line);
                if (m.find()) {
                    status = "Lade herunter… " + m.group(1) + " %";
                } else if (line.contains("[ExtractAudio]")) {
                    status = "Wandle in MP3 um…";
                } else if (line.toLowerCase(Locale.ROOT).endsWith(".mp3") && exists(line)) {
                    resultPath = line;
                }
                if (line.startsWith("ERROR")) {
                    lastLine = line;
                }
            }
        }
        if (!process.waitFor(10, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new Exception("Zeitüberschreitung");
        }
        if (process.exitValue() == 0 && resultPath == null) {
            resultPath = newestMp3Since(sounds, startTime);
        }
        if (process.exitValue() != 0 || resultPath == null) {
            throw new Exception(lastLine.isEmpty() ? "yt-dlp konnte den Link nicht laden" : shorten(lastLine));
        }
        return Path.of(resultPath).getFileName().toString();
    }

    private static Path ensureYtDlp(Path tools) throws Exception {
        String os = os();
        String asset = os.equals("windows") ? "yt-dlp.exe" : os.equals("mac") ? "yt-dlp_macos" : "yt-dlp_linux";
        Path target = tools.resolve(os.equals("windows") ? "yt-dlp.exe" : "yt-dlp");
        if (Files.exists(target)) {
            return target;
        }
        status = "Lade yt-dlp (nur beim ersten Mal)…";
        download(YTDLP_BASE + asset, target);
        target.toFile().setExecutable(true);
        return target;
    }

    /**
     * @return Ordner mit ffmpeg für --ffmpeg-location, oder null, wenn ffmpeg schon im System installiert ist
     */
    private static String ensureFfmpeg(Path tools) throws Exception {
        String os = os();
        String exe = os.equals("windows") ? "ffmpeg.exe" : "ffmpeg";
        String probe = os.equals("windows") ? "ffprobe.exe" : "ffprobe";
        if (Files.exists(tools.resolve(exe)) && Files.exists(tools.resolve(probe))) {
            return tools.toAbsolutePath().toString();
        }
        if (commandWorks("ffmpeg", "-version") && commandWorks("ffprobe", "-version")) {
            return null;
        }
        switch (os) {
            case "windows" -> {
                status = "Lade ffmpeg (nur beim ersten Mal, ca. 100 MB)…";
                Path zip = tools.resolve("ffmpeg-download.zip");
                download(FFMPEG_WINDOWS, zip);
                status = "Entpacke ffmpeg…";
                extract(zip, tools, exe, probe);
                Files.deleteIfExists(zip);
            }
            case "mac" -> {
                status = "Lade ffmpeg (nur beim ersten Mal)…";
                Path zip = tools.resolve("ffmpeg-download.zip");
                download(FFMPEG_MAC, zip);
                extract(zip, tools, exe);
                download(FFPROBE_MAC, zip);
                extract(zip, tools, probe);
                Files.deleteIfExists(zip);
                tools.resolve(exe).toFile().setExecutable(true);
                tools.resolve(probe).toFile().setExecutable(true);
            }
            default -> throw new Exception("Bitte ffmpeg installieren (z. B. sudo apt install ffmpeg)");
        }
        return tools.toAbsolutePath().toString();
    }

    // ------------------------------------------------------------------ Helfer

    private static void download(String url, Path target) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "VoiceBoard-Minecraft-Mod")
                .timeout(Duration.ofMinutes(10))
                .GET().build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new Exception("Download fehlgeschlagen (HTTP " + response.statusCode() + ")");
        }
        Path temp = target.resolveSibling(target.getFileName() + ".part");
        try (InputStream in = response.body()) {
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Entpackt die genannten Programme (egal in welchem Unterordner) direkt in den Zielordner. */
    private static void extract(Path zip, Path dir, String... names) throws Exception {
        List<String> wanted = List.of(names);
        int found = 0;
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String entryName = entry.getName();
                String fileName = entryName.substring(entryName.lastIndexOf('/') + 1);
                if (wanted.contains(fileName)) {
                    Files.copy(in, dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                    found++;
                }
            }
        }
        if (found == 0) {
            throw new Exception("ffmpeg nicht im Download gefunden");
        }
    }

    private static boolean exists(String path) {
        try {
            return Files.exists(Path.of(path));
        } catch (Exception e) {
            return false;
        }
    }

    /** Notlösung, falls der Dateiname nicht aus der Ausgabe gelesen werden konnte. */
    private static String newestMp3Since(Path folder, long since) {
        try (var stream = Files.list(folder)) {
            return stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp3"))
                    .filter(p -> p.toFile().lastModified() >= since - 2000)
                    .max(java.util.Comparator.comparingLong(p -> p.toFile().lastModified()))
                    .map(p -> p.toAbsolutePath().toString())
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean commandWorks(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static Path toolsFolder() {
        return de.ximjxstinn.voiceboard.config.Storage.toolsFolder();
    }

    private static String os() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("win")) return "windows";
        if (name.contains("mac")) return "mac";
        return "linux";
    }

    private static String sanitize(String name) {
        String clean = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return clean.isEmpty() ? "sound.mp3" : clean;
    }

    private static Path uniqueTarget(Path folder, String name) throws Exception {
        Files.createDirectories(folder);
        Path target = folder.resolve(name);
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; Files.exists(target); i++) {
            target = folder.resolve(base + " (" + i + ")" + ext);
        }
        return target;
    }

    private static String shorten(String s) {
        return s.length() > 90 ? s.substring(0, 90) + "…" : s;
    }
}
