package my_mod;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementFrame;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.criterion.CriterionProgress;
import net.minecraft.scoreboard.ScoreAccess;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Formatting;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class AdvancementManager {

    private static final String WEB_APP_URL = "https://script.google.com/macros/s/AKfycbwlPo4V6F013brYVMWb7Ed6UA-KPrJyutPu2OixIyRcZR1YXwZT3BZ2SH_rTMjGeLuPCw/exec";

    private static long lastGoogleUpdate = 0;
    private static int localCheckTimer = 0;
    private static int backgroundSyncTimer = 0;

    // УБРАЛИ: private static final Set<String> ANNOUNCED_IDS = new HashSet<>();
    // ТЕПЕРЬ ВСЕ ХРАНИТСЯ В AdvancementState

    private static boolean isFirstPass = true;

    private static final Map<String, String> RUSSIAN_TRANSLATIONS = new HashMap<>();
    private static boolean translationsLoaded = false;

    public static void register() {
        loadTranslations();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            scanAndProcess(server, false);
        });
    }

    private static void loadTranslations() {
        if (translationsLoaded) return;
        Gson gson = new Gson();
        String[] paths = {"/assets/minecraft/lang/ru_ru.json", "/assets/calm_or_death/lang/ru_ru.json"};
        for (String path : paths) {
            try (InputStream stream = AdvancementManager.class.getResourceAsStream(path)) {
                if (stream != null) {
                    JsonObject json = gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
                    for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                        RUSSIAN_TRANSLATIONS.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            } catch (Exception e) {}
        }
        translationsLoaded = true;
    }

    public static void tick(MinecraftServer server) {
        // === ПРОВЕРКА SCOREBOARD ===

        localCheckTimer++;
        if (localCheckTimer >= 10) {
            localCheckTimer = 0;
            scanAndProcess(server, true);
        }

        backgroundSyncTimer++;
        if (backgroundSyncTimer >= 600) {
            backgroundSyncTimer = 0;
            sendDataToGoogle(server);
        }
    }

    private static void scanAndProcess(MinecraftServer server, boolean triggerGoogleSync) {
        boolean newDataFound = false;

        // ПОЛУЧАЕМ СОСТОЯНИЕ (БАЗУ ДАННЫХ) С ДИСКА
        AdvancementState db = AdvancementState.getServerState(server);

        List<Team> teams = new ArrayList<>(server.getScoreboard().getTeams());
        List<AdvancementEntry> advancements = new ArrayList<>();

        for (AdvancementEntry entry : server.getAdvancementLoader().getAdvancements()) {
            if (entry.value().display().isPresent()) {
                advancements.add(entry);
            }
        }

        for (AdvancementEntry entry : advancements) {
            String advId = entry.id().toString();

            // ПРОВЕРЯЕМ ПО БАЗЕ ДАННЫХ, А НЕ ПО ЛОКАЛЬНОЙ ПЕРЕМЕННОЙ
            if (db.hasAnnounced(advId)) continue;

            Team winnerTeam = null;
            Date earliestDate = null;

            for (Team team : teams) {
                Date teamDate = getTeamCompletionDate(server, team, entry);
                if (teamDate != null) {
                    if (earliestDate == null || teamDate.before(earliestDate)) {
                        earliestDate = teamDate;
                        winnerTeam = team;
                    }
                }
            }

            if (winnerTeam != null) {
                // СОХРАНЯЕМ В БАЗУ ДАННЫХ
                db.addAnnounced(advId);
                newDataFound = true;

                // Если это первый проход после запуска, мы не спамим в чат,
                // НО только если ачивка РЕАЛЬНО была получена давно (до этого запуска).
                // Однако, благодаря db.hasAnnounced(advId), сюда мы попадем ТОЛЬКО
                // если ачивка еще ни разу не была засчитана модом (даже после рестарта).
                // Поэтому флаг isFirstPass теперь нужен только для предотвращения спама
                // при самой ПЕРВОЙ установке мода на старый мир.

                if (!isFirstPass) {
                    int points = getPoints(entry.value().display().get().getFrame());
                    addPoints(server, winnerTeam, points);

                    String name = getTranslatedText(entry.value().display().get().getTitle());
                    announceToChat(server, winnerTeam, name, points);

                    playGlobalSound(server);
                }
            }
        }

        if (isFirstPass) {
            isFirstPass = false;
        }

        if (newDataFound && triggerGoogleSync) {
            sendDataToGoogle(server);
        }
    }

    private static void addPoints(MinecraftServer server, Team team, int points) {
        ScoreboardObjective objective = server.getScoreboard().getNullableObjective(Calm_or_death.SCOREBOARD_ID);
        if (objective != null) {
            ScoreHolder holder = ScoreHolder.fromName(team.getName());
            ScoreAccess score = server.getScoreboard().getOrCreateScore(holder, objective);
            score.setScore(score.getScore() + points);
        }
    }

    private static void announceToChat(MinecraftServer server, Team team, String advName, int points) {
        MutableText message = Text.empty();
        message.append(Text.literal("Команда ").formatted(Formatting.GRAY));
        message.append(Text.literal(team.getName()).formatted(Formatting.GOLD));
        message.append(Text.literal(" выполнила достижение ").formatted(Formatting.GRAY));
        message.append(Text.literal("\"" + advName + "\"").formatted(Formatting.GOLD));
        message.append(Text.literal(" и получает ").formatted(Formatting.GRAY));
        message.append(Text.literal(String.valueOf(points)).formatted(Formatting.GOLD));
        message.append(Text.literal(" очков.").formatted(Formatting.GRAY));

        server.getPlayerManager().broadcast(message, false);
    }

    private static void playGlobalSound(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.getEntityWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 2.0f);
        }
    }

    private static void sendDataToGoogle(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (now - lastGoogleUpdate < 3000) return;
        lastGoogleUpdate = now;

        CompletableFuture.runAsync(() -> {
            try {
                // ДЛЯ ОТПРАВКИ ТОЖЕ БЕРЕМ СОСТОЯНИЕ, чтобы правильно писать статусы
                AdvancementState db = AdvancementState.getServerState(server);

                List<Team> teams = new ArrayList<>(server.getScoreboard().getTeams());
                List<AdvancementEntry> advancements = new ArrayList<>();
                for (AdvancementEntry entry : server.getAdvancementLoader().getAdvancements()) {
                    if (entry.value().display().isPresent()) advancements.add(entry);
                }

                advancements.sort(Comparator.comparingInt(e -> {
                    AdvancementFrame frame = e.value().display().get().getFrame();
                    return switch (frame) {
                        case CHALLENGE -> 1;
                        case GOAL -> 2;
                        case TASK -> 3;
                    };
                }));

                JsonObject root = new JsonObject();
                JsonArray header = new JsonArray();
                header.add("Ачивка");
                header.add("Тип");
                header.add("Очки");
                header.add("Статус");
                root.add("header", header);

                JsonArray rows = new JsonArray();

                for (AdvancementEntry entry : advancements) {
                    JsonArray row = new JsonArray();

                    String name = getTranslatedText(entry.value().display().get().getTitle());
                    String type = translateFrame(entry.value().display().get().getFrame());
                    int points = getPoints(entry.value().display().get().getFrame());

                    row.add(name);
                    row.add(type);
                    row.add(points);

                    // Проверка статуса (для Google таблицы)
                    Team winnerTeam = null;
                    Date earliestDate = null;

                    // Если мы уже знаем, что ачивка выдана (из файла), попробуем найти кому именно,
                    // чтобы красиво отобразить в таблице.
                    // (Логика поиска победителя остается прежней, так как она надежно ищет по датам)
                    for (Team team : teams) {
                        Date teamDate = getTeamCompletionDate(server, team, entry);
                        if (teamDate != null) {
                            if (earliestDate == null || teamDate.before(earliestDate)) {
                                earliestDate = teamDate;
                                winnerTeam = team;
                            }
                        }
                    }

                    if (winnerTeam == null) {
                        row.add("🟢 ДОСТУПНО");
                    } else {
                        row.add("👑 ВЫПОЛНЕНО: " + winnerTeam.getName());
                    }
                    rows.add(row);
                }
                root.add("rows", rows);

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(WEB_APP_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(root.toString()))
                        .build();

                client.send(request, HttpResponse.BodyHandlers.ofString());

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static String getTranslatedText(Text text) {
        if (text.getContent() instanceof TranslatableTextContent translatable) {
            String key = translatable.getKey();
            return RUSSIAN_TRANSLATIONS.getOrDefault(key, text.getString());
        }
        return text.getString();
    }

    private static Date getTeamCompletionDate(MinecraftServer server, Team team, AdvancementEntry entry) {
        Date earliest = null;
        for (String memberName : team.getPlayerList()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(memberName);
            if (player == null) continue;

            AdvancementProgress progress = player.getAdvancementTracker().getProgress(entry);
            if (progress.isDone()) {
                for (String criterion : progress.getObtainedCriteria()) {
                    CriterionProgress criterionProgress = progress.getCriterionProgress(criterion);
                    if (criterionProgress != null && criterionProgress.isObtained()) {
                        Instant instant = criterionProgress.getObtainedTime();
                        if (instant != null) {
                            Date date = Date.from(instant);
                            if (earliest == null || date.before(earliest)) {
                                earliest = date;
                            }
                        }
                    }
                }
            }
        }
        return earliest;
    }

    private static String translateFrame(AdvancementFrame frame) {
        return switch (frame) {
            case CHALLENGE -> "ИСПЫТАНИЕ";
            case GOAL -> "ЦЕЛЬ";
            case TASK -> "ЗАДАЧА";
        };
    }

    private static int getPoints(AdvancementFrame frame) {
        return switch (frame) {
            case CHALLENGE -> 25;
            case GOAL -> 10;
            case TASK -> 5;
        };
    }
}