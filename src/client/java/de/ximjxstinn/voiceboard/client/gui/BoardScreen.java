package de.ximjxstinn.voiceboard.client.gui;

import de.ximjxstinn.voiceboard.client.compat.Compat;
import com.mojang.blaze3d.platform.InputConstants;
import de.ximjxstinn.voiceboard.client.VoiceBoardClient;
import de.ximjxstinn.voiceboard.audio.AudioLoader;
import de.ximjxstinn.voiceboard.audio.Downloader;
import de.ximjxstinn.voiceboard.audio.FilterSettings;
import de.ximjxstinn.voiceboard.audio.FilterType;
import de.ximjxstinn.voiceboard.audio.SoundPlayer;
import de.ximjxstinn.voiceboard.config.BoardConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Hauptmenü mit den Reitern Soundeffekte, Voicefilter und Bibliothek.
 */
public class BoardScreen extends Screen {

    private enum Tab {
        SOUNDS("Soundeffekte"), FILTER("Voicefilter"), LIBRARY("Bibliothek");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private static final int NONE = -1;
    private static final int STOP_ALL = -2;
    private static final int FILTER_TOGGLE = -3;
    private static final int GRAY = 0xFFA0A0A0;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int YELLOW = 0xFFFFD040;
    private static final int SKIP_SECONDS = 10;
    private static final Component PAUSED = Component.literal("pausiert");
    private static final int PAUSED_WIDTH = 44;

    private static Tab lastTab = Tab.SOUNDS;

    private Tab tab = lastTab;
    private int scroll;
    private int listening = NONE;

    private int left;
    private int panelWidth;
    private int top;
    private int activeTabX;
    private int activeTabWidth;

    private final List<Label> labels = new ArrayList<>();
    private final List<ProgressBar> bars = new ArrayList<>();
    /** "pausiert"-Hinweis je Zeile (rechtsbündig, x = rechte Kante) und die zugehörige Datei. */
    private final List<Label> pausedLabels = new ArrayList<>();
    private final List<String> pausedFiles = new ArrayList<>();
    private final List<Runnable> perFrame = new ArrayList<>();
    /** Lautstärke-Regler (x, y, Breite, Höhe), die eine 100-%-Markierung bekommen. */
    private final List<int[]> markers = new ArrayList<>();

    private EditBox urlBox;
    private static String urlText = "";
    private int statusY = -1;
    private int seenDownloads = Downloader.finishedCount();

    private record Label(Component text, int x, int y, int color, boolean centered) {
    }

    private record ProgressBar(String file, int x, int y, int width) {
    }

    /** Menü, zu dem "Fertig" zurückkehrt (z. B. Mod Menu), oder null fürs Spiel. */
    private final Screen parent;

    public BoardScreen() {
        this(null);
    }

    public BoardScreen(Screen parent) {
        super(Component.literal("VoiceBoard"));
        this.parent = parent;
    }

    private BoardConfig config() {
        return VoiceBoardClient.config();
    }

    private SoundPlayer player() {
        return VoiceBoardClient.player();
    }

    // ------------------------------------------------------------------ Aufbau

    @Override
    protected void init() {
        build();
    }

    private void rebuild() {
        if (urlBox != null) {
            urlText = urlBox.getValue();
        }
        urlBox = null;
        statusY = -1;
        clearWidgets();
        build();
    }

    private void build() {
        labels.clear();
        bars.clear();
        pausedLabels.clear();
        pausedFiles.clear();
        perFrame.clear();
        markers.clear();

        panelWidth = Math.min(width - 16, 400);
        left = (width - panelWidth) / 2;
        top = 6;

        // Reiter
        Tab[] tabs = Tab.values();
        int tabWidth = (panelWidth - (tabs.length - 1) * 3) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            int x = left + i * (tabWidth + 3);
            Button b = addRenderableWidget(Button.builder(Component.literal(t.label), btn -> switchTab(t))
                    .bounds(x, top, tabWidth, 20).build());
            b.active = t != tab;
            if (t == tab) {
                activeTabX = x;
                activeTabWidth = tabWidth;
            }
        }

        int contentY = top + 28;
        int bottomY = height - 26;

        switch (tab) {
            case SOUNDS -> buildSounds(contentY, bottomY);
            case FILTER -> buildFilter(contentY, bottomY);
            case LIBRARY -> buildLibrary(contentY, bottomY);
        }

        // Fußleiste
        int bw = (panelWidth - 4) / 2;
        addRenderableWidget(Button.builder(Component.literal("Sound-Ordner öffnen"), b -> openFolder(BoardConfig.soundFolder()))
                .bounds(left, bottomY, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> closeScreen())
                .bounds(left + bw + 4, bottomY, bw, 20).build());
    }

    private void switchTab(Tab t) {
        tab = t;
        lastTab = t;
        scroll = 0;
        listening = NONE;
        rebuild();
    }

    // ------------------------------------------------------------ Soundeffekte

    private void buildSounds(int y, int bottomY) {
        BoardConfig config = config();
        int right = left + panelWidth;

        buildMiniPlayer(y, right - 144 - 6);
        addRenderableWidget(Button.builder(Component.literal("Stopp"), b -> player().stopAll())
                .bounds(right - 80 - 4 - 60, y, 60, 20).build())
                .setTooltip(Tooltip.create(Component.literal("Alle Sounds stoppen")));
        addRenderableWidget(Button.builder(keyLabel(config.stopAllKey, listening == STOP_ALL), b -> startListening(STOP_ALL))
                .bounds(right - 80, y, 80, 20).build());
        y += 24;

        markers.add(new int[]{left, y, panelWidth, 20});
        addRenderableWidget(new PercentSlider(left, y, panelWidth, 20, "Lautstärke für andere", config.masterVolume, BoardConfig.MAX_VOLUME, v -> {
            config.masterVolume = v;
            config.markDirty();
        }));
        y += 24;

        int third = (panelWidth - 8) / 3;
        addRenderableWidget(Button.builder(onOff("Selbst hören", config.hearSelf), b -> {
            config.hearSelf = !config.hearSelf;
            config.save();
            b.setMessage(onOff("Selbst hören", config.hearSelf));
        }).bounds(left, y, third, 20).build());
        addRenderableWidget(new PercentSlider(left + third + 4, y, third, 20, "Mithören", config.monitorVolume, v -> {
            config.monitorVolume = v;
            config.markDirty();
        }));
        addRenderableWidget(Button.builder(onOff("Anzeige", config.showHud), b -> {
            config.showHud = !config.showHud;
            config.save();
            b.setMessage(onOff("Anzeige", config.showHud));
        }).bounds(left + 2 * (third + 4), y, panelWidth - 2 * (third + 4), 20).build());
        y += 28;

        List<BoardConfig.SoundEntry> sounds = config.sounds;
        int listY = y + 14;
        int rowHeight = 24;
        int rows = Math.max(1, (bottomY - 4 - listY) / rowHeight);
        clampScroll(sounds.size(), rows);

        labels.add(new Label(Component.literal("Sounds (" + sounds.size() + ")").withStyle(ChatFormatting.BOLD, ChatFormatting.UNDERLINE),
                left + panelWidth / 2, y, WHITE, true));
        addScrollButtons(y - 3, sounds.size(), rows);

        if (sounds.isEmpty()) {
            labels.add(new Label(Component.literal("Noch keine Sounds."), left + panelWidth / 2, listY + 6, GRAY, true));
            labels.add(new Label(Component.literal("Lege .mp3/.wav-Dateien in den Sound-Ordner"), left + panelWidth / 2, listY + 20, GRAY, true));
            labels.add(new Label(Component.literal("und füge sie unter „Bibliothek“ hinzu."), left + panelWidth / 2, listY + 32, GRAY, true));
            return;
        }

        for (int i = scroll; i < Math.min(sounds.size(), scroll + rows); i++) {
            buildSoundRow(sounds.get(i), i, listY + (i - scroll) * rowHeight);
        }
    }

    /** « 10 s zurück, Pause/Weiter, 10 s vor » und Spulregler für den zuletzt benutzten Sound. */
    private void buildMiniPlayer(int y, int endX) {
        Button back = addRenderableWidget(Button.builder(Component.literal("«"), b -> skipCurrent(-SKIP_SECONDS))
                .bounds(left, y, 20, 20).build());
        back.setTooltip(Tooltip.create(Component.literal(SKIP_SECONDS + " Sekunden zurück")));
        Button pause = addRenderableWidget(Button.builder(Component.literal("II"), b -> {
            SoundPlayer.NowPlaying now = player().current();
            if (now != null) {
                player().setPaused(now.file(), !now.paused());
            }
        }).bounds(left + 22, y, 20, 20).build());
        Button forward = addRenderableWidget(Button.builder(Component.literal("»"), b -> skipCurrent(SKIP_SECONDS))
                .bounds(left + 44, y, 20, 20).build());
        forward.setTooltip(Tooltip.create(Component.literal(SKIP_SECONDS + " Sekunden vor")));
        SeekSlider seek = addRenderableWidget(new SeekSlider(left + 66, y, endX - left - 66, 20, player(), this::fit));
        perFrame.add(() -> {
            SoundPlayer.NowPlaying now = player().current();
            boolean has = now != null;
            back.active = has;
            pause.active = has;
            forward.active = has;
            pause.setMessage(has && now.paused()
                    ? Component.literal("▶").withStyle(ChatFormatting.YELLOW)
                    : Component.literal("II"));
            seek.sync();
        });
    }

    private void skipCurrent(int seconds) {
        SoundPlayer.NowPlaying now = player().current();
        if (now != null) {
            player().skip(now.file(), seconds);
        }
    }

    private void buildSoundRow(BoardConfig.SoundEntry entry, int index, int y) {
        BoardConfig config = config();
        int right = left + panelWidth;

        int deleteX = right - 20;
        int editX = deleteX - 24;
        int sliderWidth = 64;
        int sliderX = editX - 4 - sliderWidth;
        int loopWidth = 52;
        int loopX = sliderX - 4 - loopWidth;
        int keyWidth = 76;
        int keyX = loopX - 4 - keyWidth;
        int nameX = left + 24;
        int nameWidth = keyX - 6 - nameX;

        Button play = addRenderableWidget(Button.builder(Component.literal("▶"), b -> player().toggle(entry))
                .bounds(left, y, 20, 20).build());
        perFrame.add(() -> {
            if (player().isLoading(entry.file)) {
                play.setMessage(Component.literal("…"));
            } else if (player().isPlaying(entry.file)) {
                play.setMessage(Component.literal("II"));
            } else if (player().isPaused(entry.file)) {
                play.setMessage(Component.literal("▶").withStyle(ChatFormatting.YELLOW));
            } else {
                play.setMessage(Component.literal("▶"));
            }
        });

        labels.add(new Label(Component.literal(fit(entry.name, nameWidth - PAUSED_WIDTH)), nameX, y + 6, WHITE, false));
        pausedLabels.add(new Label(PAUSED, nameX + nameWidth, y + 6, YELLOW, false));
        pausedFiles.add(entry.file);
        bars.add(new ProgressBar(entry.file, nameX, y + 17, nameWidth));

        addRenderableWidget(Button.builder(keyLabel(entry.key, listening == index), b -> startListening(index))
                .bounds(keyX, y, keyWidth, 20).build());

        addRenderableWidget(new LoopButton(loopX, y, loopWidth, 20, entry.loop, on -> {
            entry.loop = on;
            config.save();
        }));

        markers.add(new int[]{sliderX, y, sliderWidth, 20});
        addRenderableWidget(new PercentSlider(sliderX, y, sliderWidth, 20, "", entry.volume, BoardConfig.MAX_VOLUME, v -> {
            entry.volume = v;
            config.markDirty();
        }));

        addRenderableWidget(Button.builder(Component.literal("✎"), b -> Compat.setScreen(minecraft, new RenameScreen(this, entry)))
                .bounds(editX, y, 20, 20).build());

        addRenderableWidget(Button.builder(Component.literal("✖").withStyle(ChatFormatting.RED), b -> {
            player().stop(entry.file);
            config.sounds.remove(entry);
            config.save();
            rebuild();
        }).bounds(deleteX, y, 20, 20).build());
    }

    // ------------------------------------------------------------- Voicefilter

    private void buildFilter(int y, int bottomY) {
        BoardConfig config = config();

        labels.add(new Label(Component.literal("Voicefilter").withStyle(ChatFormatting.BOLD, ChatFormatting.UNDERLINE),
                left + panelWidth / 2, y, WHITE, true));
        y += 14;

        // Hauptschalter + eigene Taste dafür
        int keyW = 80;
        addRenderableWidget(Button.builder(onOff("Voicefilter", config.filterEnabled), b -> {
            config.filterEnabled = !config.filterEnabled;
            config.save();
            rebuild();
        }).bounds(left, y, panelWidth - keyW - 4, 20).build());
        addRenderableWidget(Button.builder(keyLabel(config.filterToggleKey, listening == FILTER_TOGGLE), b -> startListening(FILTER_TOGGLE))
                .bounds(left + panelWidth - keyW, y, keyW, 20).build());
        y += 24;

        FilterType[] types = FilterType.values();
        int cols = 4;
        int bw = (panelWidth - (cols - 1) * 4) / cols;
        for (int i = 0; i < types.length; i++) {
            FilterType type = types[i];
            boolean selected = config.filter == type;
            int x = left + (i % cols) * (bw + 4);
            int yy = y + (i / cols) * 24;
            Component label = Component.literal(selected ? "✔ " + type.label : type.label);
            if (selected && !config.filterEnabled) {
                label = label.copy().withStyle(ChatFormatting.GRAY);
            }
            Button b = addRenderableWidget(Button.builder(label, btn -> {
                config.filter = type;
                if (type != FilterType.NONE) {
                    config.filterEnabled = true; // Filter wählen schaltet automatisch ein
                }
                config.save();
                rebuild();
            }).bounds(x, yy, bw, 20).build());
            b.active = !selected || !config.filterEnabled;
        }
        y += ((types.length + cols - 1) / cols) * 24 + 4;

        addRenderableWidget(new PercentSlider(left, y, panelWidth, 20, "Stärke", config.filterStrength, v -> {
            config.filterStrength = v;
            config.markDirty();
        }));
        y += 24;

        if (buildFilterParams(config.filter, config.filterSettings, y)) {
            y += 24;
        }

        int half = (panelWidth - 4) / 2;
        Button defaults = addRenderableWidget(Button.builder(Component.literal("Standardwerte"), b -> {
            config.filterSettings.resetFor(config.filter);
            config.filterStrength = 100;
            config.save();
            rebuild();
        }).bounds(left, y, half, 20).build());
        defaults.active = config.filter != FilterType.NONE;
        addRenderableWidget(Button.builder(onOff("Senden, wenn stumm", config.playWhileMuted), b -> {
            config.playWhileMuted = !config.playWhileMuted;
            config.save();
            b.setMessage(onOff("Senden, wenn stumm", config.playWhileMuted));
        }).bounds(left + half + 4, y, half, 20).build());
        y += 30;

        if (y + 40 > bottomY) {
            return; // kleines Fenster: Hinweistext weglassen
        }
        int cx = left + panelWidth / 2;
        labels.add(new Label(Component.literal("Der Filter verändert nur deine Stimme,"), cx, y, GRAY, true));
        labels.add(new Label(Component.literal("die Soundboard-Sounds bleiben unverändert."), cx, y + 12, GRAY, true));
        labels.add(new Label(Component.literal("Andere Spieler brauchen nur Simple Voice Chat."), cx, y + 30, GRAY, true));
    }

    /** Eigene Regler für den gewählten Filter. @return true, wenn eine Zeile angelegt wurde */
    private boolean buildFilterParams(FilterType type, FilterSettings fs, int y) {
        BoardConfig config = config();
        int half = (panelWidth - 4) / 2;
        switch (type) {
            case PITCH_HIGH -> addRenderableWidget(new ValueSlider(left, y, panelWidth, 20, "Tonhöhe", "%", 110, 200, fs.pitchHigh, v -> {
                fs.pitchHigh = v;
                config.markDirty();
            }));
            case PITCH_LOW -> addRenderableWidget(new ValueSlider(left, y, panelWidth, 20, "Tonhöhe", "%", 50, 95, fs.pitchLow, v -> {
                fs.pitchLow = v;
                config.markDirty();
            }));
            case BABY -> addRenderableWidget(new ValueSlider(left, y, panelWidth, 20, "Tonhöhe", "%", 130, 220, fs.babyPitch, v -> {
                fs.babyPitch = v;
                config.markDirty();
            }));
            case ROBOT -> addRenderableWidget(new ValueSlider(left, y, panelWidth, 20, "Frequenz", "Hz", 20, 200, fs.robotFrequency, v -> {
                fs.robotFrequency = v;
                config.markDirty();
            }));
            case ECHO -> {
                addRenderableWidget(new ValueSlider(left, y, half, 20, "Verzögerung", "ms", 50, 1000, fs.echoDelay, v -> {
                    fs.echoDelay = v;
                    config.markDirty();
                }));
                addRenderableWidget(new ValueSlider(left + half + 4, y, half, 20, "Wiederholung", "%", 0, 90, fs.echoFeedback, v -> {
                    fs.echoFeedback = v;
                    config.markDirty();
                }));
            }
            case HALL -> addRenderableWidget(new ValueSlider(left, y, panelWidth, 20, "Raumgröße", "%", 0, 100, fs.hallSize, v -> {
                fs.hallSize = v;
                config.markDirty();
            }));
            case RADIO -> addRenderableWidget(new ValueSlider(left, y, panelWidth, 20, "Verzerrung", "%", 0, 100, fs.radioDistortion, v -> {
                fs.radioDistortion = v;
                config.markDirty();
            }));
            case PHONE -> addRenderableWidget(new ValueSlider(left, y, panelWidth, 20, "Qualität", "%", 0, 100, fs.phoneQuality, v -> {
                fs.phoneQuality = v;
                config.markDirty();
            }));
            case VILLAGER -> addRenderableWidget(new ValueSlider(left, y, panelWidth, 20, "Nasal", "%", 0, 100, fs.villagerNasal, v -> {
                fs.villagerNasal = v;
                config.markDirty();
            }));
            case ENDERMAN -> addRenderableWidget(new ValueSlider(left, y, panelWidth, 20, "Tonhöhe", "%", 50, 80, fs.endermanPitch, v -> {
                fs.endermanPitch = v;
                config.markDirty();
            }));
            case ZOMBIE -> addRenderableWidget(new ValueSlider(left, y, panelWidth, 20, "Knurren", "%", 0, 100, fs.zombieGrowl, v -> {
                fs.zombieGrowl = v;
                config.markDirty();
            }));
            case GHAST -> addRenderableWidget(new ValueSlider(left, y, panelWidth, 20, "Hall", "%", 0, 100, fs.ghastHall, v -> {
                fs.ghastHall = v;
                config.markDirty();
            }));
            case IRON_GOLEM -> addRenderableWidget(new ValueSlider(left, y, panelWidth, 20, "Metall", "%", 0, 100, fs.golemMetal, v -> {
                fs.golemMetal = v;
                config.markDirty();
            }));
            case ALIEN -> addRenderableWidget(new ValueSlider(left, y, panelWidth, 20, "Geschwindigkeit", "%", 0, 100, fs.alienSpeed, v -> {
                fs.alienSpeed = v;
                config.markDirty();
            }));
            case UNDERWATER -> addRenderableWidget(new ValueSlider(left, y, panelWidth, 20, "Tiefe", "%", 0, 100, fs.underwaterDepth, v -> {
                fs.underwaterDepth = v;
                config.markDirty();
            }));
            default -> {
                return false;
            }
        }
        return true;
    }

    // -------------------------------------------------------------- Bibliothek

    private void buildLibrary(int y, int bottomY) {
        BoardConfig config = config();
        List<String> files = scanFolder();

        int downloadWidth = 100;
        urlBox = new EditBox(font, left + 1, y + 1, panelWidth - downloadWidth - 6, 18, Component.literal("Link"));
        urlBox.setMaxLength(2048);
        urlBox.setValue(urlText);
        addRenderableWidget(urlBox);
        Button download = addRenderableWidget(Button.builder(Component.literal("Herunterladen"), b -> {
            urlText = urlBox.getValue();
            Downloader.start(urlText);
            if (Downloader.isBusy()) {
                urlText = "";
            }
            rebuild();
        }).bounds(left + panelWidth - downloadWidth, y, downloadWidth, 20).build());
        download.active = !Downloader.isBusy();
        y += 22;
        statusY = y;
        y += 12;

        int half = (panelWidth - 4) / 2;
        addRenderableWidget(Button.builder(Component.literal("Neu laden"), b -> {
            player().clearCache();
            rebuild();
        }).bounds(left, y, half, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Alle hinzufügen"), b -> {
            for (String f : files) {
                if (!config.contains(f)) {
                    config.sounds.add(new BoardConfig.SoundEntry(f, BoardConfig.stripExtension(f)));
                }
            }
            config.save();
            rebuild();
        }).bounds(left + half + 4, y, half, 20).build());
        y += 30;

        int listY = y + 14;
        int rowHeight = 22;
        int rows = Math.max(1, (bottomY - 4 - listY) / rowHeight);
        clampScroll(files.size(), rows);

        labels.add(new Label(Component.literal("Dateien im Sound-Ordner (" + files.size() + ")").withStyle(ChatFormatting.BOLD, ChatFormatting.UNDERLINE),
                left + panelWidth / 2, y, WHITE, true));
        addScrollButtons(y - 3, files.size(), rows);

        if (files.isEmpty()) {
            labels.add(new Label(Component.literal("Der Ordner ist leer."), left + panelWidth / 2, listY + 6, GRAY, true));
            labels.add(new Label(Component.literal("Klick auf „Sound-Ordner öffnen“, lege .mp3/.wav-"), left + panelWidth / 2, listY + 20, GRAY, true));
            labels.add(new Label(Component.literal("Dateien hinein und drück dann „Neu laden“."), left + panelWidth / 2, listY + 32, GRAY, true));
            return;
        }

        int buttonWidth = 110;
        for (int i = scroll; i < Math.min(files.size(), scroll + rows); i++) {
            String file = files.get(i);
            int rowY = listY + (i - scroll) * rowHeight;
            boolean added = config.contains(file);
            labels.add(new Label(Component.literal(fit(file, panelWidth - buttonWidth - 8)), left, rowY + 6, WHITE, false));
            Button b = addRenderableWidget(Button.builder(Component.literal(added ? "✔ Hinzugefügt" : "Hinzufügen"), btn -> {
                config.sounds.add(new BoardConfig.SoundEntry(file, BoardConfig.stripExtension(file)));
                config.save();
                rebuild();
            }).bounds(left + panelWidth - buttonWidth, rowY, buttonWidth, 20).build());
            b.active = !added;
        }
    }

    private List<String> scanFolder() {
        Path folder = BoardConfig.soundFolder();
        try {
            Files.createDirectories(folder);
            try (Stream<Path> stream = Files.list(folder)) {
                return stream.filter(Files::isRegularFile)
                        .map(p -> p.getFileName().toString())
                        .filter(AudioLoader::isSupported)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();
            }
        } catch (Exception e) {
            VoiceBoardClient.LOGGER.error("Konnte Sound-Ordner nicht lesen", e);
            return List.of();
        }
    }

    // ------------------------------------------------------------------ Helfer

    private void clampScroll(int total, int rows) {
        scroll = Math.max(0, Math.min(scroll, total - rows));
    }

    private void addScrollButtons(int y, int total, int rows) {
        if (total <= rows) {
            return;
        }
        int right = left + panelWidth;
        Button up = addRenderableWidget(Button.builder(Component.literal("▲"), b -> {
            scroll--;
            rebuild();
        }).bounds(right - 42, y, 20, 14).build());
        up.active = scroll > 0;
        Button down = addRenderableWidget(Button.builder(Component.literal("▼"), b -> {
            scroll++;
            rebuild();
        }).bounds(right - 20, y, 20, 14).build());
        down.active = scroll + rows < total;
    }

    private static Component onOff(String label, boolean on) {
        return Component.literal(label + ": ").append(on
                ? Component.literal("An").withStyle(ChatFormatting.GREEN)
                : Component.literal("Aus").withStyle(ChatFormatting.RED));
    }

    private static Component keyLabel(BoardConfig.KeyBind bind, boolean listening) {
        if (listening) {
            return Component.literal("> ").append(Component.literal("Taste?").withStyle(ChatFormatting.UNDERLINE))
                    .append(" <").withStyle(ChatFormatting.YELLOW);
        }
        if (bind.isBoundOnlyElsewhere()) {
            // In der anderen Minecraft-Version belegt – Tastencodes sind dort anders
            return Component.literal("Neu belegen").withStyle(ChatFormatting.GOLD);
        }
        MutableComponent name = VoiceBoardClient.keyName(bind).copy();
        return bind.isBound() ? name : name.withStyle(ChatFormatting.GRAY);
    }

    /** Kürzt Text mit "…", damit er in die gegebene Breite passt. */
    String fit(String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(Component.literal(text)) <= maxWidth) return text;
        String s = text;
        while (!s.isEmpty() && font.width(Component.literal(s + "…")) > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }

    private void startListening(int target) {
        listening = target;
        rebuild();
    }

    private BoardConfig.KeyBind listeningBind() {
        if (listening == STOP_ALL) return config().stopAllKey;
        if (listening == FILTER_TOGGLE) return config().filterToggleKey;
        if (listening >= 0 && listening < config().sounds.size()) return config().sounds.get(listening).key;
        return null;
    }

    private void closeScreen() {
        config().save();
        Compat.setScreen(minecraft, parent);
    }

    private static void openFolder(Path folder) {
        try {
            Files.createDirectories(folder);
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String path = folder.toAbsolutePath().toString();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("explorer", path);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", path);
            } else {
                pb = new ProcessBuilder("xdg-open", path);
            }
            pb.start();
        } catch (Exception e) {
            VoiceBoardClient.LOGGER.error("Konnte Ordner nicht öffnen: {}", folder, e);
            VoiceBoardClient.notifyPlayer("Ordner: " + folder.toAbsolutePath());
        }
    }

    // ------------------------------------------------------------- Eingaben

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        BoardConfig.KeyBind bind = listeningBind();
        if (bind != null) {
            if (keyEvent.isEscape()) {
                bind.clear();
            } else {
                InputConstants.Key key = InputConstants.getKey(keyEvent);
                bind.set(key.getType().name(), key.getValue());
            }
            listening = NONE;
            config().save();
            rebuild();
            return true;
        }
        if (keyEvent.isEscape()) {
            closeScreen();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (listening != NONE) {
            listening = NONE;
            rebuild();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    // Absichtlich ohne @Override: falls sich die Signatur ändert, kompiliert die Mod trotzdem
    // (dann funktioniert nur das Mausrad nicht, die ▲▼-Knöpfe aber schon).
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.FILTER || scrollY == 0) {
            return false;
        }
        scroll += scrollY > 0 ? -1 : 1;
        rebuild();
        return true;
    }

    // Spiel im Einzelspieler nicht pausieren, damit der Sprachchat weiterläuft.
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------- Zeichnen

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (Downloader.finishedCount() != seenDownloads) {
            seenDownloads = Downloader.finishedCount();
            if (tab != Tab.FILTER) {
                rebuild();
            }
        }
        for (Runnable r : perFrame) {
            r.run();
        }

        super.extractRenderState(g, mouseX, mouseY, delta);

        // Linie unter dem aktiven Reiter
        g.fill(activeTabX + 8, top + 21, activeTabX + activeTabWidth - 8, top + 23, WHITE);

        for (Label label : labels) {
            if (label.centered()) {
                g.centeredText(font, label.text(), label.x(), label.y(), label.color());
            } else {
                g.text(font, label.text(), label.x(), label.y(), label.color(), true);
            }
        }

        if (statusY >= 0) {
            String status = Downloader.status();
            if (status.isEmpty()) {
                status = "YouTube-Links brauchen beim ersten Mal etwas länger (Werkzeuge werden geladen).";
            }
            g.text(font, Component.literal(fit(status, panelWidth)), left, statusY, GRAY, true);
        }

        // Kleine Markierung bei 100 % auf den Lautstärke-Reglern (oben und unten, damit die Zahl frei bleibt)
        for (int[] m : markers) {
            int mx = m[0] + 4 + Math.round((m[2] - 8) * (100F / BoardConfig.MAX_VOLUME));
            g.fill(mx, m[1] + 1, mx + 1, m[1] + 4, 0xFFB4B4B4);
            g.fill(mx, m[1] + m[3] - 4, mx + 1, m[1] + m[3] - 1, 0xFFB4B4B4);
        }

        for (ProgressBar bar : bars) {
            float progress = player().progress(bar.file());
            if (progress >= 0F) {
                int color = player().isPaused(bar.file()) ? YELLOW : 0xFF55FF55;
                g.fill(bar.x(), bar.y(), bar.x() + bar.width(), bar.y() + 2, 0x60FFFFFF);
                g.fill(bar.x(), bar.y(), bar.x() + Math.round(bar.width() * progress), bar.y() + 2, color);
            }
        }

        for (int i = 0; i < pausedLabels.size(); i++) {
            if (player().isPaused(pausedFiles.get(i))) {
                Label l = pausedLabels.get(i);
                g.text(font, l.text(), l.x() - font.width(l.text()), l.y(), l.color(), true);
            }
        }
    }
}
