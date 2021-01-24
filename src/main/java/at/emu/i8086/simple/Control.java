package at.emu.i8086.simple;

import java.util.HashMap;
import java.util.Map;

/**
 * Flag register manipulation opcodes
 */
public class Control implements Cpu.OpcodeConfiguration, Cpu.ClockedOpcodeConfiguration {

    @Override
    public Map<String, ?> getConfiguration()
    {
        Map<String, Object> tmp = new HashMap<>();
        tmp.putAll(Map.of(
                "1111_1000", Clc.class,
                "1111_0101", Cmc.class,
                "1111_1001", Stc.class,
                "1111_1100", Cld.class,
                "1111_1101", Std.class,
                "1111_1010", Cli.class,
                "1111_1011", Sti.class,
                "1001_1111", Lahf.class,
                "1001_1110", Sahf.class
        ));
        tmp.putAll(Map.of(
                "1111_0100", Hlt.class,
                "1001_1011", Wait.class,
                "1101_1***", Esc.class,
                "1111_0000", Lock.class,
                "001*_*110", Segment.class
        ));
        return tmp;
    }

    @Override
    public Map<String, Configuration> getClockedConfiguration()
    {
        Map<String, Configuration> c = new HashMap<>();

        config(c, "1111_1000",  S( 2, Clc.class, "CLC", ""));
        config(c, "1111_0101",  S( 2, Cmc.class, "CMC", ""));
        config(c, "1111_1001",  S( 2, Stc.class, "STC", ""));
        config(c, "1111_1100",  S( 2, Cld.class, "CLD", ""));
        config(c, "1111_1101",  S( 2, Std.class, "STD", ""));
        config(c, "1111_1010",  S( 2, Cli.class, "CLI", ""));
        config(c, "1111_1011",  S( 2, Sti.class, "STI", ""));
        config(c, "1001_1111",  S( 4, Lahf.class, "LAHF", ""));
        config(c, "1001_1110",  S( 4, Sahf.class, "SAHF", ""));

        config(c, "1111_0100",  S( 1, Hlt.class, "HLT", ""));
        config(c, "1001_1011",  S( 4, Wait.class, "WAIT", ""));

        /**
         * mod=11 --> nop ??
         * mem op --> read
         */
        config(c, "1101_1***",  "********", S( 8, Esc.class, "ESC", ""));  // displacement
        config(c, "1101_1000",  "**000***", S( 2, Esc.class, "ESC", ""), true);  // no displacement
        config(c, "1101_1111",  "**111***", S( 2, Esc.class, "ESC", ""), true);  // no displacement






        config(c, "1111_0000",  S( 2, Lock.class, "", ""));
        config(c, "001*_*110",  S( 2, Segment.class, "", ""));

        return c;
    }

    public static class Clc extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            cpu.flags &= ~Cpu.FLAG_CF;
        }
    }
    public static class Stc extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            cpu.flags |= Cpu.FLAG_CF;
        }
    }
    public static class Cmc extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            cpu.flags ^= Cpu.FLAG_CF;
        }
    }
    public static class Cld extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            cpu.flags &= ~Cpu.FLAG_DF;
        }
    }
    public static class Std extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            cpu.flags |= Cpu.FLAG_DF;
        }
    }
    public static class Cli extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            cpu.flags &= ~Cpu.FLAG_IF;
        }
    }
    public static class Sti extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            cpu.flags |= Cpu.FLAG_IF;
        }
    }
    public static class Lahf extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            cpu.writeRegisterUpperByte(Cpu.AX, cpu.flags);
        }
    }
    public static class Sahf extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            cpu.flags &= 0xFF00;
            cpu.flags |= (cpu.registers[Cpu.AX] >> 8);
        }
    }



    public static class Hlt extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            cpu.hlt = true;
        }
    }
    public static class Wait extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            /// section 2.5
        }
    }

    public static class Esc extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            cpu.readModRegRm(opcode);
        }
    }
    public static class Lock extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode)
        {
            // switch on prefix flag
            // and run the next opcode,
            // that could be another prefix
            cpu.lockPrefix = true;
            cpu.step();
            cpu.lockPrefix = false;
        }
    }
    public static class Segment extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
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
