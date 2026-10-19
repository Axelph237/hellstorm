package dev.candycanecazoo.packapunch.script;

import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.DebugLib;

/**
 * Counts Lua instructions and stops a call that runs past its budget. Installed as the interpreter's debug hook,
 * with the {@code debug} table itself removed from the script globals.
 */
final class InstructionBudget extends DebugLib {
    /** An Error, not a LuaError: scripts can't catch it with pcall, so a runaway loop can't swallow its own stop. */
    static final class Exceeded extends Error {
        Exceeded(long budget) {
            super("instruction budget of " + budget + " exceeded", null, false, false);
        }
    }

    private long budget;
    private long remaining;

    void reset(long budget) {
        this.budget = budget;
        this.remaining = budget;
    }

    @Override
    public void onInstruction(int pc, Varargs v, int top) {
        if (--remaining < 0) {
            throw new Exceeded(budget);
        }
    }
}
