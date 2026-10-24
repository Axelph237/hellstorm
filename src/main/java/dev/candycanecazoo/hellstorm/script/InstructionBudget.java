package dev.candycanecazoo.hellstorm.script;

import org.luaj.vm2.Lua;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.DebugLib;

import java.util.Arrays;

/**
 * Counts Lua instructions and stops a call that runs past its budget. Installed as the interpreter's debug hook,
 * with the {@code debug} table itself removed from the script globals.
 *
 * <p>It also meters {@code ..}, which builds strings without calling any library function: a concatenation longer than the
 * string cap fails, and each one costs an extra instruction per 64 bytes it builds, so copying big strings in a loop runs
 * the budget down.
 */
final class InstructionBudget extends DebugLib {
    /** An Error, not a LuaError: scripts can't catch it with pcall, so a runaway loop can't swallow its own stop. */
    static final class Exceeded extends Error {
        Exceeded(long budget) {
            super("instruction budget of " + budget + " exceeded", null, false, false);
        }
    }

    private final int maxStringLength;
    private long budget;
    private long remaining;
    /** The code and registers of each Lua call in progress, innermost last, mirroring DebugLib's own call stack. */
    private int[][] code = new int[16][];
    private LuaValue[][] registers = new LuaValue[16][];
    private int calls;

    InstructionBudget(int maxStringLength) {
        this.maxStringLength = maxStringLength;
    }

    void reset(long budget) {
        this.budget = budget;
        this.remaining = budget;
    }

    @Override
    public void onCall(LuaClosure closure, Varargs varargs, LuaValue[] stack) {
        super.onCall(closure, varargs, stack);
        push(closure.p.code, stack);
    }

    @Override
    public void onCall(LuaFunction function) {
        super.onCall(function);
        push(null, null);
    }

    @Override
    public void onReturn() {
        super.onReturn();
        if (calls > 0) {
            calls--;
            code[calls] = null;
            registers[calls] = null;
        }
    }

    @Override
    public void onInstruction(int pc, Varargs v, int top) {
        if (--remaining < 0) {
            throw new Exceeded(budget);
        }
        super.onInstruction(pc, v, top);
        int instruction = code[calls - 1][pc];
        if (Lua.GET_OPCODE(instruction) == Lua.OP_CONCAT) {
            LuaValue[] stack = registers[calls - 1];
            long length = 0;
            for (int r = Lua.GETARG_B(instruction), last = Lua.GETARG_C(instruction); r <= last; r++) {
                if (stack[r] instanceof LuaString s) {
                    length += s.length();
                }
            }
            if (length > maxStringLength) {
                throw new LuaError("string too long: .. would build " + length + " bytes, over " + maxStringLength);
            }
            remaining -= length >> 6;
        }
    }

    private void push(int[] instructions, LuaValue[] stack) {
        if (calls == code.length) {
            code = Arrays.copyOf(code, calls * 2);
            registers = Arrays.copyOf(registers, calls * 2);
        }
        code[calls] = instructions;
        registers[calls] = stack;
        calls++;
    }
}
