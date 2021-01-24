package at.emu.i8086.simple;

import java.util.HashMap;
import java.util.Map;

/**
 * Stack operations opcodes
 */
public class Stack implements Cpu.OpcodeConfiguration, Cpu.ClockedOpcodeConfiguration
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
    public Map<String, ?> getConfiguration() {
        return Map.of(
                "0101_0***", PushReg.class,
                "0101_1***", PopReg.class,
                "1111_1111", Map.of("110", PushRm.class),
                "1000_1111", Map.of("000", PopRm.class),
                "000*_*110", PushSReg.class,
                "000*_0111", PopSReg.class,
                "0001_1111", PopSReg.class,
                /*0000_1111 (pop cs)  is not used in 186+ */
                "1001_1100", Pushf.class,
                "1001_1101", Popf.class
        );
    }

    @Override
    public Map<String, Cpu.ClockedOpcodeConfiguration.Configuration> getClockedConfiguration()
    {
        Map<String, Cpu.ClockedOpcodeConfiguration.Configuration> c = new HashMap<>();

        config(c, "0101_0***", S(11, PushReg.class,  "PUSH", "R"));
        config(c, "0101_1***", S( 8, PopReg.class,  "POP", "R"));

        config(c, "1111_1111", "**_110_***", S(16, PushRm.class,  "PUSH", "[M]"));
        config(c, "1111_1111", "11_110_***", S(11, PushRm.class,  "PUSH", "R"), true);
        config(c, "1000_1111", "**_000_***", S(17, PopRm.class,  "POP", "[M]"));
        config(c, "1000_1111", "11_000_***", S( 8, PopRm.class,  "POP", "R"), true);

        config(c, "000*_*110", S(10, PushSReg.class,  "PUSH", "S"));
        config(c, "000*_0111", S( 8, PopSReg.class,  "POP", "S"));
        config(c, "0001_1111", S( 8, PopSReg.class,  "POP", "S"));

        config(c, "1001_1100", S(10, Pushf.class,  "PUSH", "F"));
        config(c, "1001_1101", S( 8, Popf.class,  "POP", "F"));

        return c;
    }

    public static class PushRm extends Cpu.DemuxedOpcode {
        @Override
        public void demuxed(Cpu cpu, int opcode) {
            cpu.push16(cpu.mrrModValue);
        }
    }

    public static class PushReg extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            int reg = opcode & 0b000_0111;
            cpu.push16(cpu.registers[reg]);
        }
    }

    public static class PushSReg extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            int reg = (opcode >> 3) & 0b0000_0011;
            cpu.push16(cpu.segments[reg]);
        }
    }

    public static class PopRm extends Cpu.DemuxedOpcode {
        @Override
        public void demuxed(Cpu cpu, int opcode) {
            int value = cpu.pop16();
            cpu.writeByModRegRm(true, value);
        }
    }

    public static class PopReg extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            int reg = opcode & 0b000_0111;
            cpu.registers[reg] = cpu.pop16();
        }
    }

    public static class PopSReg extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            int reg = (opcode >> 3) & 0b0000_0011;
            cpu.segments[reg] = cpu.pop16();
        }
    }

    public static class Pushf extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            cpu.push16(cpu.flags);
        }
    }

    public static class Popf extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            cpu.flags = cpu.pop16();
        }
    }


}
