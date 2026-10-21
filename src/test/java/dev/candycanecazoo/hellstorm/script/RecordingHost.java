package dev.candycanecazoo.hellstorm.script;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class RecordingHost implements ScriptHost {
    final List<String> commands = new ArrayList<>();
    final List<String> logs = new ArrayList<>();
    int round = 1;
    CommandResult nextResult = new CommandResult(true, 1);

    @Override
    public CommandResult runCommand(String command) {
        commands.add(command);
        return nextResult;
    }

    @Override
    public void log(LogLevel level, String source, String message) {
        logs.add(level + " " + source + ": " + message);
    }

    @Override
    public int round() {
        return round;
    }

    @Override
    public List<PlayerInfo> players() {
        return List.of(new PlayerInfo(UUID.fromString("00000000-0000-0000-0000-000000000001"), "Alice", 750, true));
    }

    long count(String fragment) {
        return logs.stream().filter(l -> l.contains(fragment)).count();
    }
}
