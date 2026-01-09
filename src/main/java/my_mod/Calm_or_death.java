package my_mod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.scoreboard.*;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import me.lucko.fabric.api.permissions.v0.Permissions;

public class Calm_or_death implements ModInitializer {
	public static final String SCOREBOARD_ID = "event_points";

	@Override
	public void onInitialize() {
		System.out.println("Мод Командного Ивента загружается!");

		// 1. Регистрация
		ModSounds.register();
		ModItems.registerModItems();
		LootTableModifier.registerModifications();
		FirstBloodHandler.register();
		AdvancementManager.register();

		registerCommands();

		// 2. События
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			ContractManager.sendContractStatus(player);
		});

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

			// === СКРЫТИЕ НИКОВ (ОПЦИЯ) ===
			for (Team team : scoreboard.getTeams()) {
				team.setNameTagVisibilityRule(AbstractTeam.VisibilityRule.NEVER);
			}
			System.out.println("✅ [Calm_or_death] Ники скрыты для всех существующих команд.");

			try {
				server.getCommandManager().getDispatcher().execute("gamerule show_death_messages false", server.getCommandSource());
				server.getCommandManager().getDispatcher().execute("gamerule show_advancement_messages false", server.getCommandSource());
				System.out.println("✅ [Calm_or_death] Правила чата обновлены.");
			} catch (Exception e) {
				System.err.println("❌ Не удалось отключить анонсы: " + e.getMessage());
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ContractManager.tick(server);
			AirdropManager.tick(server);
			AdvancementManager.tick(server);

			// === ПОСТОЯННОЕ ПРИНУДИТЕЛЬНОЕ СКРЫТИЕ НИКОВ ===
			if (server.getTicks() % 1200 == 0) { // Раз в минуту (20 * 60)
				for (Team team : server.getScoreboard().getTeams()) {
					if (team.getNameTagVisibilityRule() != AbstractTeam.VisibilityRule.NEVER) {
						team.setNameTagVisibilityRule(AbstractTeam.VisibilityRule.NEVER);
					}
				}
			}
		});
	}

	private void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

			// === КОМАНДЫ ДЛЯ АДМИНОВ ===

			// /admin_newtarget (Для админов, бесплатно меняет контракт любой команде, если нужно)
			// Я оставил это на случай тестов, но переименовал, чтобы не конфликтовать
			dispatcher.register(CommandManager.literal("admin_newtarget")
					.requires(Permissions.require("calmordeath.command.newtarget", 3))
					.executes(context -> {
						ServerPlayerEntity player = context.getSource().getPlayer();
						if (player != null && player.getScoreboardTeam() != null) {
							ContractManager.reRollContractForTeam(context.getSource().getServer(), player.getScoreboardTeam().getName(), false);
							context.getSource().sendFeedback(() -> Text.literal("✅ Контракт обновлен админом!").formatted(Formatting.GREEN), false);
						}
						return 1;
					}));

			// /airdrop
			dispatcher.register(CommandManager.literal("airdrop")
					.requires(Permissions.require("calmordeath.command.airdrop", 3))
					.executes(context -> {
						AirdropManager.spawnAirdrop(context.getSource().getServer());
						context.getSource().sendFeedback(() -> Text.literal("✈️ Аирдроп вызван принудительно!").formatted(Formatting.YELLOW), true);
						return 1;
					}));

			// /hidenames
			dispatcher.register(CommandManager.literal("hidenames")
					.requires(Permissions.require("calmordeath.command.hidenames", 3))
					.executes(context -> {
						for (Team team : context.getSource().getServer().getScoreboard().getTeams()) {
							team.setNameTagVisibilityRule(AbstractTeam.VisibilityRule.NEVER);
						}
						context.getSource().sendFeedback(() -> Text.literal("👻 Ники скрыты у всех команд!").formatted(Formatting.GRAY), true);
						return 1;
					}));


			// === КОМАНДЫ ДЛЯ ИГРОКОВ ===

			// /newtarget (ПЛАТНАЯ СМЕНА КОНТРАКТА)
			dispatcher.register(CommandManager.literal("newtarget")
					.executes(context -> {
						ServerPlayerEntity player = context.getSource().getPlayer();
						if (player == null) return 0;

						Team team = player.getScoreboardTeam();
						if (team == null) {
							context.getSource().sendError(Text.literal("❌ Вы должны быть в команде!"));
							return 0;
						}

						Scoreboard scoreboard = context.getSource().getServer().getScoreboard();
						ScoreboardObjective objective = scoreboard.getNullableObjective(SCOREBOARD_ID);

						if (objective == null) {
							context.getSource().sendError(Text.literal("❌ Система очков не активна."));
							return 0;
						}

						// Получаем доступ к счету команды
						ScoreHolder holder = ScoreHolder.fromName(team.getName());
						ScoreAccess scoreAccess = scoreboard.getOrCreateScore(holder, objective);
						int currentScore = scoreAccess.getScore();

						// Проверка баланса
						if (currentScore < 50) {
							context.getSource().sendError(Text.literal("❌ Недостаточно баллов! Нужно 50, у вас: " + currentScore).formatted(Formatting.RED));
							return 0;
						}

						// Списание и выполнение
						scoreAccess.setScore(currentScore - 50);
						ContractManager.reRollContractForTeam(context.getSource().getServer(), team.getName(), false);

						// Сообщение игроку (остальным напишет метод reRoll)
						context.getSource().sendFeedback(() ->
								Text.literal("💸 Списано 50 баллов за смену контракта.").formatted(Formatting.GOLD), false);

						return 1;
					}));

			// /contract
			dispatcher.register(CommandManager.literal("contract")
					.executes(context -> {
						ServerPlayerEntity player = context.getSource().getPlayer();
						if (player != null) ContractManager.sendContractStatus(player);
						return 1;
					}));

			// /cooldowns
			dispatcher.register(CommandManager.literal("cooldowns")
					.executes(context -> {
						ServerPlayerEntity player = context.getSource().getPlayer();
						if (player != null) {
							AntiAbuseHandler.sendCooldownStatus(player);
						}
						return 1;
					}));
		});
	}
}