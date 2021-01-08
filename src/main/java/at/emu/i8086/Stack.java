package at.emu.i8086;

import java.util.Map;

/**
 * Stack operations opcodes
 */
public class Stack implements Cpu.OpcodeConfiguration {
    // Push
    //  Reg/mem     1111_1111   m110r   disp-lo disp-hi
    //  register    0101_0reg
    //  Segment reg 000reg110
    // Pop
    //  Reg/mem     1000_1111   m000r   disp-lo disp-hi
    //  register    0101_1reg
    //  Segment reg 000reg111   / except for 0000_1111
    //
    // Pushf        1001_1100
    // Popf         1001_1101
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
