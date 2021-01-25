package at.emu.i8086.clocked.cpu;


/**
 * Flag register manipulation opcodes
 */
public class Control implements Cpu.ClockedOpcodeConfiguration {

    @Override
    public void getClockedConfiguration(Cpu.Opcode[] registry)
    {
        config(registry, "1111_1000",  S( 2, Clc.class, "CLC", ""));
        config(registry, "1111_0101",  S( 2, Cmc.class, "CMC", ""));
        config(registry, "1111_1001",  S( 2, Stc.class, "STC", ""));
        config(registry, "1111_1100",  S( 2, Cld.class, "CLD", ""));
        config(registry, "1111_1101",  S( 2, Std.class, "STD", ""));
        config(registry, "1111_1010",  S( 2, Cli.class, "CLI", ""));
        config(registry, "1111_1011",  S( 2, Sti.class, "STI", ""));
        config(registry, "1001_1111",  S( 4, Lahf.class, "LAHF", ""));
        config(registry, "1001_1110",  S( 4, Sahf.class, "SAHF", ""));

        config(registry, "1111_0100",  S( 1, Hlt.class, "HLT", ""));
        config(registry, "1001_1011",  S( 4, Wait.class, "WAIT", ""));

        /**
         * mod=11 --> nop ??
         * mem op --> read
         */
        config(registry, "1101_1***",  "********", S( 8, Esc.class, "ESC", ""));  // displacement
        config(registry, "1101_1000",  "**000***", S( 2, Esc.class, "ESC", ""), true);  // no displacement
        config(registry, "1101_1111",  "**111***", S( 2, Esc.class, "ESC", ""), true);  // no displacement


        config(registry, "1111_0000",  S( 2, Lock.class, "", ""));
        config(registry, "001*_*110",  S( 2, Segment.class, "", ""));
    }

    public static class Clc extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
            cpu.flags &= ~Cpu.FLAG_CF;
        }
    }
    public static class Stc extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
            cpu.flags |= Cpu.FLAG_CF;
        }
    }
    public static class Cmc extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
            cpu.flags ^= Cpu.FLAG_CF;
        }
    }
    public static class Cld extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
            cpu.flags &= ~Cpu.FLAG_DF;
        }
    }
    public static class Std extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
            cpu.flags |= Cpu.FLAG_DF;
        }
    }
    public static class Cli extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
            cpu.flags &= ~Cpu.FLAG_IF;
        }
    }
    public static class Sti extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
            cpu.flags |= Cpu.FLAG_IF;
        }
    }
    public static class Lahf extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
            cpu.writeRegisterUpperByte(Cpu.AX, cpu.flags);
        }
    }
    public static class Sahf extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
            cpu.flags &= 0xFF00;
            cpu.flags |= (cpu.registers[Cpu.AX] >> 8);
        }
    }

    public static class Hlt extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
            cpu.hlt = true;
        }
    }
    public static class Wait extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
            /// section 2.5
        }
    }

    public static class Esc extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
        }
    }

    public static class Lock extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode)
        {
            // switch on prefix flag
            // and run the next opcode,
            // that could be another prefix
            cpu.lockPrefix = true;
            cpu.step();
            cpu.lockPrefix = false;
        }
    }
    public static class Segment extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
            // CS:IP
            // SS:SP  SS:BP
            // DS:BX  DS:DI DS:DI(For other than string operations)
            // ES:DI (For string operations)
            cpu.overrideSegmentIndex = (opcode >> 3) & 0b11;
            cpu.step();
            cpu.overrideSegmentIndex = -1;
        }
    }

}
