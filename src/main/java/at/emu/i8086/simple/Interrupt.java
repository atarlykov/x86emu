package at.emu.i8086.simple;

import java.util.HashMap;
import java.util.Map;

/**
 * Interrupts related opcodes
 */
public class Interrupt implements Cpu.OpcodeConfiguration, Cpu.ClockedOpcodeConfiguration
{
    /**
     * INT
     *      type specified  1100_1101   data8
     *      type #3         1100_1100
     *
     *  - decrement SP by 2, push flags (like pushf)
     *  - clear TF|IF
     *  - decrement SP by 2, push CS, CS <- second word of interrupt pointer
     *  - decrement SP by 2, push IP, IP <- first word of interrupt pointer
     *
     * INTO                 1100_1110
     *  generates interrupt #4 if OF is set (on overflow)
     *
     * IRET                 1100_1111
     *  - pop IP
     *  - pop CS
     *  - pop flags
     *
     * @return configuration of opcodes
     */
    @Override
    public Map<String, ?> getConfiguration() {
        return Map.of(
                "1100_1101", Int.class,
                "1100_1100", Int3.class,
                "1100_1110", Into.class,
                "1100_1111", Iret.class
        );
    }

    @Override
    public Map<String, Cpu.ClockedOpcodeConfiguration.Configuration> getClockedConfiguration()
    {
        Map<String, Cpu.ClockedOpcodeConfiguration.Configuration> c = new HashMap<>();

        config(c, "1100_1101", S( 52, Int.class, "INT", ""));
        config(c, "1100_1100", S( 51, Int3.class, "INT", "3"));
        config(c, "1100_1110", S( 53, 4, Into.class, "INTO", ""));
        config(c, "1100_1111", S( 24, Iret.class, "IRET", ""));

        return c;
    }

    /**
     * Typed and untyped (#3) interrupts
     */
    public static class Int extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode)
        {
            int type = cpu.ipRead8();
            cpu.interrupt(type);
        }
    }

    /**
     * Typed and untyped (#3) interrupts
     */
    public static class Int3 extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode)
        {
            cpu.interrupt(3);
        }
    }

    /**
     * Interrupt on overflow
     */
    public static class Into extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode)
        {
            if ((cpu.flags & Cpu.FLAG_OF) != 0) {
                cpu.interrupt(4);
            }
        }
    }

    /**
     * return from an interrupt
     */
    public static class Iret extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode)
        {
            cpu.ip = cpu.pop16();
            cpu.registers[Cpu.CS] = cpu.pop16();
            cpu.flags = cpu.pop16();
        }
    }
}
