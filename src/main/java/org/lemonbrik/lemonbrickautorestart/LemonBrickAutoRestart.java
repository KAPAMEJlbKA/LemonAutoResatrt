package org.lemonbrik.lemonbrickautorestart;
import com.google.gson.Gson;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ScheduledFuture;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.text.Text;

public class LemonBrickAutoRestart implements ModInitializer {
    public static Config CONFIG;
    private MinecraftServer server;
    private ScheduledExecutorService executor;
    private static final Logger LOGGER = LogManager.getLogger(LemonBrickAutoRestart.class);
    public static Gson configGson = new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

        @Override
        public void onInitialize() {
            ServerLifecycleEvents.SERVER_STARTING.register(server -> {
                this.server = server;
                LOGGER.info("LemonBrick Autorestarter loaded");

                // Инициализация планировщика задач
                this.executor = Executors.newSingleThreadScheduledExecutor();
                try {
                    loadConfig();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                // Планируем выполнение перезагрузки каждые CONFIG.waitTime секунд
                this.executor.schedule(this::performRestart,CONFIG.waitTime, TimeUnit.MINUTES);
                this.executor.schedule(this::FirstMessageRestart,(CONFIG.waitTime-CONFIG.FirstTimerMessage), TimeUnit.MINUTES);
            });

            ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
                LOGGER.info("Server is stopping. Cleaning up resources...");
                if (executor != null) {
                    executor.shutdown();
                }
            });
        }
    public void FirstMessageRestart(){
        if (this.server != null && this.server.isDedicated()) {
            // Логируем информацию о перезапуске
            LOGGER.info(String.format(CONFIG.FirstMessage,CONFIG.FirstTimerMessage));

            // Отправляем сообщение о перезапуске в чат игрокам
            this.server.getPlayerManager().broadcast(Text.of(String.format(CONFIG.FirstMessage,CONFIG.FirstTimerMessage)), false);

        }
    }

        public void performRestart() {
            if (this.server != null && this.server.isDedicated()) {
                // Логируем информацию о перезапуске
                LOGGER.info(String.format(CONFIG.startRestart,CONFIG.timer));

                // Отправляем сообщение о перезапуске в чат игрокам
                this.server.getPlayerManager().broadcast(Text.of(String.format(CONFIG.startRestart,CONFIG.timer)), false);

                // Запускаем обратный отсчёт до перезапуска
                countdownAndRestart(CONFIG.timer);
            } else {
                LOGGER.warn("Не удалось запустить перезагрузку сервера: сервер не инициализирован или является выделенным (dedicated)");
            }
        }

    private void countdownAndRestart(int countdownSeconds) {
        AtomicInteger remainingSeconds = new AtomicInteger(countdownSeconds); // Создаем AtomicInteger для изменяемого значения
        final ScheduledFuture<?>[] countdownTask = new ScheduledFuture<?>[1]; // Объявляем массив с одним элементом для хранения ScheduledFuture
        countdownTask[0] = this.executor.scheduleAtFixedRate(() -> {
            int currentSeconds = remainingSeconds.getAndDecrement(); // Получаем текущее значение и уменьшаем на 1
            if (currentSeconds > 0) {
                // Отправляем сообщение об оставшемся времени до перезапуска
                this.server.getPlayerManager().broadcast(Text.of(String.format(CONFIG.restartMessage,currentSeconds)), false);
            } else {
                // Если счётчик достиг нуля, отменяем задачу обратного отсчёта и выполняем перезапуск сервера
                countdownTask[0].cancel(false); // Отменяем задачу
                this.executor.shutdown(); // Останавливаем планировщик задач после завершения отсчёта
                restartServer(); // Выполняем перезапуск сервера
            }
        }, 0, 1, TimeUnit.SECONDS);
    }


    private void restartServer() {
            if (this.server != null && this.server.isDedicated()) {
                this.server.stop(false);
            }
        }

        private void loadConfig() throws IOException {
            Path configDir = FabricLoader.getInstance().getConfigDir();
            Path lemonAutoRestartDir = configDir.resolve("LemonBrickAutoRestart");
            Path configFile = lemonAutoRestartDir.resolve("config.json");

            if (!Files.exists(lemonAutoRestartDir)) {
                Files.createDirectories(lemonAutoRestartDir);
            }

            Path configPath = lemonAutoRestartDir.resolve("config.json");

            if(!Files.exists(configPath)) {
                CONFIG = new Config();
                try(Writer writer = new FileWriter(configPath.toFile())) {
                    configGson.toJson(CONFIG, writer);
                }
            }
                try (Reader reader = Files.newBufferedReader(configFile)) {
                    CONFIG = new Gson().fromJson(reader, Config.class);
                }
        }
    }
