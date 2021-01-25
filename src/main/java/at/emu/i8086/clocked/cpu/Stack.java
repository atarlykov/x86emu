package at.emu.i8086.clocked.cpu;

import java.util.HashMap;
import java.util.Map;

/**
 * Stack operations opcodes
 */
public class Stack implements Cpu.ClockedOpcodeConfiguration
{
    /*
     Push
      Reg/mem     1111_1111   m110r   disp-lo disp-hi
      register    0101_0reg
      Segment reg 000reg110
     Pop
      Reg/mem     1000_1111   m000r   disp-lo disp-hi
      register    0101_1reg
      Segment reg 000reg111   / except for 0000_1111

     Pushf        1001_1100
     Popf         1001_1101
    */

    @Override
    public void getClockedConfiguration(Cpu.Opcode[] registry)
    {
        config(registry, "0101_0***", S(11, PushReg.class,  "PUSH", "R"));
        config(registry, "0101_1***", S( 8, PopReg.class,  "POP", "R"));

        config(registry, "1111_1111", "**_110_***", S(16, PushRm.class,  "PUSH", "[M]"));
        config(registry, "1111_1111", "11_110_***", S(11, PushRm.class,  "PUSH", "R"), true);
        config(registry, "1000_1111", "**_000_***", S(17, PopRm.class,  "POP", "[M]"));
        config(registry, "1000_1111", "11_000_***", S( 8, PopRm.class,  "POP", "R"), true);

        config(registry, "000*_*110", S(10, PushSReg.class,  "PUSH", "S"));
        config(registry, "000*_0111", S( 8, PopSReg.class,  "POP", "S"));
        config(registry, "0001_1111", S( 8, PopSReg.class,  "POP", "S"));

        config(registry, "1001_1100", S(10, Pushf.class,  "PUSH", "F"));
        config(registry, "1001_1101", S( 8, Popf.class,  "POP", "F"));
    }

    public static class PushRm extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
            cpu.push16(cpu.mrrModValue);
        }
    }

    public static class PushReg extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
            int reg = opcode & 0b000_0111;
            cpu.push16(cpu.registers[reg]);
        }
    }

    public static class PushSReg extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
            int reg = (opcode >> 3) & 0b0000_0011;
            cpu.push16(cpu.segments[reg]);
        }
    }

    public static class PopRm extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
            int value = cpu.pop16();
            cpu.writeByModRegRm(true, value);
        }
    }

    public static class PopReg extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
            int reg = opcode & 0b000_0111;
            cpu.registers[reg] = cpu.pop16();
        }
    }

    public static class PopSReg extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
            int reg = (opcode >> 3) & 0b0000_0011;
            cpu.segments[reg] = cpu.pop16();
        }
    }

    public static class Pushf extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
            cpu.push16(cpu.flags);
        }
    }

    public static class Popf extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
            cpu.flags = cpu.pop16();
        }
    }

}
