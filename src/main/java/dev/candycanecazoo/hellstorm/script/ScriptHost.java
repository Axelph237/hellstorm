package dev.candycanecazoo.hellstorm.script;

import java.util.List;
import java.util.UUID;

/** What a script can reach outside the sandbox. The Forge side runs real commands; tests record calls. */
public interface ScriptHost {
    record CommandResult(boolean success, int result) {}

    record PlayerInfo(UUID id, String name, int points, boolean alive) {}

    enum LogLevel { INFO, WARN, ERROR }

    CommandResult runCommand(String command);

    void log(LogLevel level, String source, String message);

    int round();

    List<PlayerInfo> players();
}
