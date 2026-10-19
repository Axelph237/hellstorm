package dev.candycanecazoo.packapunch.map.validate;

import java.util.List;

public record ValidationReport(List<Problem> problems) {
    public ValidationReport {
        problems = List.copyOf(problems);
    }

    public boolean playable() {
        return problems.stream().noneMatch(p -> p.severity() == Problem.Severity.ERROR);
    }

    public boolean has(String code) {
        return problems.stream().anyMatch(p -> p.code().equals(code));
    }
}
