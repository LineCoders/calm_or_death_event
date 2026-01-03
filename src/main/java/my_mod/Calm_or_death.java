package my_mod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents; // ВАЖНЫЙ ИМПОРТ ДЛЯ ВХОДА
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.scoreboard.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class Calm_or_death implements ModInitializer {
	public static final String SCOREBOARD_ID = "event_points";

	@Override
	public void onInitialize() {
		System.out.println("Мод Командного Ивента загружается!");
		ModItems.registerModItems();

		FirstBloodHandler.register();

		// === 1. КОМАНДА /newtarget (Для админов/тестов) ===
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("newtarget")
					.executes(context -> {
						ServerPlayerEntity player = context.getSource().getPlayer();
						if (player != null) {
							AbstractTeam team = player.getScoreboardTeam();
							if (team != null) {
								ContractManager.reRollContractForTeam(context.getSource().getServer(), team.getName(), false);
								context.getSource().sendFeedback(() -> Text.literal("✅ Контракт обновлен принудительно!").formatted(Formatting.GREEN), false);
							}
						}
						return 1;
					}));
		});

		// === 2. КОМАНДА /contract (ДЛЯ ИГРОКОВ) ===
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("contract")
					.executes(context -> {
						ServerPlayerEntity player = context.getSource().getPlayer();
						if (player != null) {
							// Показываем игроку инфо о его контракте
							ContractManager.sendContractStatus(player);
						}
						return 1;
					}));
		});

		// === 3. СООБЩЕНИЕ ПРИ ВХОДЕ ИГРОКА ===
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			// Делаем небольшую задержку, чтобы игрок успел прогрузиться и увидеть чат
			// Но в простом варианте отправляем сразу
			ContractManager.sendContractStatus(player);
		});

		// 4. ИНИЦИАЛИЗАЦИЯ ПРИ СТАРТЕ
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Scoreboard scoreboard = server.getScoreboard();
			ScoreboardObjective objective = scoreboard.getNullableObjective(SCOREBOARD_ID);

			if (objective == null) {
				objective = scoreboard.addObjective(
						SCOREBOARD_ID,
						ScoreboardCriterion.DUMMY,
						Text.literal("Баллы Команд").formatted(Formatting.GOLD),
						ScoreboardCriterion.RenderType.INTEGER,
						true,
						null
				);
			}
			scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, objective);
		});

		// 5. ТИКЕР (АЧИВКИ + КОНТРАКТЫ)
		ServerTickEvents.END_SERVER_TICK.register(server -> {

			ContractManager.tick(server);

			if (server.getTicks() % 20 == 0) {
				for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
					// Ачивки
					checkRaceAdvancement(server, player, "minecraft:story/mine_stone", 10, "Каменный век");
					checkRaceAdvancement(server, player, "minecraft:adventure/kill_a_mob", 15, "Охотник на монстров");
					checkRaceAdvancement(server, player, "minecraft:story/enter_the_nether", 20, "Огненные недра");
					checkRaceAdvancement(server, player, "minecraft:end/kill_dragon", 100, "Освободи Энд");
				}
			}
		});
	}

	private void checkRaceAdvancement(MinecraftServer server, ServerPlayerEntity player, String advancementId, int points, String eventName) {
		String[] parts = advancementId.split(":");
		if (parts.length != 2) return;

		Identifier id = Identifier.of(parts[0], parts[1]);
		AdvancementEntry advancement = server.getAdvancementLoader().get(id);

		if (advancement == null) return;

		AdvancementProgress progress = player.getAdvancementTracker().getProgress(advancement);

		if (progress.isDone()) {
			String trackingName = "#done_" + id.getPath().replace("/", "_");
			Scoreboard scoreboard = server.getScoreboard();
			ScoreboardObjective objective = scoreboard.getNullableObjective(SCOREBOARD_ID);
			if (objective == null) return;

			ScoreHolder trackerHolder = ScoreHolder.fromName(trackingName);
			ScoreAccess trackerScore = scoreboard.getOrCreateScore(trackerHolder, objective);

			if (trackerScore.getScore() > 0) return;

			AbstractTeam team = player.getScoreboardTeam();
			if (team != null) {
				trackerScore.setScore(1);
				String teamName = team.getName();
				ScoreHolder teamHolder = ScoreHolder.fromName(teamName);
				ScoreAccess teamScore = scoreboard.getOrCreateScore(teamHolder, objective);
				teamScore.setScore(teamScore.getScore() + points);

				server.getPlayerManager().broadcast(
						Text.literal("Команда ").formatted(Formatting.GRAY)
								.append(Text.literal(teamName).formatted(Formatting.GOLD))
								.append(Text.literal(" выполнила достижение ").formatted(Formatting.GRAY))
								.append(Text.literal("\"" + eventName + "\"").formatted(Formatting.GOLD))
								.append(Text.literal(" и получила ").formatted(Formatting.GRAY))
								.append(Text.literal(points + " очков!").formatted(Formatting.GOLD)),
						false
				);
			}
		}
	}
}