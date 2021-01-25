package at.emu.i8086.clocked.cpu;

/**
 * Implementation of transfer opcodes
 */
public class Transfer implements Cpu.ClockedOpcodeConfiguration {

    /**
     * Based on i8086 manual:
     * MOV
     *  reg/mem to/from reg      1000_10dw  mrr       disp    disp
     *  imm to reg/mem           1100_011w  m000r     disp    disp data data(w=1)
     *  imm to reg               1011_wreg  data      data(w=1)
     *  mem to accumulator       1010_000w  addr.low  addr.hi
     *  accumulator to memory    1010_001w  addr.low  addr.hi
     *  reg/mem to seg reg       1000_1110  m0SRr     disp    disp
     *  seg reg to reg/mem       1000_1100  m0SRr     disp    disp
     *
     * XCHG
     *  reg/mem with reg         1000_011w  mrr       disp    disp
     *  reg with acc             1001_0reg
     *
     * XLAT al <- bx[al]         1101_0111
     * LEA                       1000_1101  mrr       disp    disp
     * LDS                       1100_0101  mrr       disp    disp
     * LES                       1100_0100  mrr       disp    disp
     *
     * CBW                       1001_1000
     * CWD                       1001_1001
     *
     * @return configuration of transfer opcodes
     */

    @Override
    public void getClockedConfiguration(Cpu.Opcode[] registry)
    {
        config(registry, "1000_100*", "**_***_***", S( 9, MovRmR.class, "MOV", "[M] <-  R"));
        config(registry, "1000_101*", "**_***_***", S( 8, MovRmR.class, "MOV", " R  <- [M]"));
        config(registry, "1000_10**", "11_***_***", S( 2, MovRmR.class, "MOV", " R  <-  R"), true);

        config(registry, "1100_0110", "**_000_***", S(10, MovRmImm.class, "MOV", "[M8] <- I8"));
        config(registry, "1100_0110", "11_000_***", S( 4, MovRmImm.class, "MOV", "R8mr <- I8"), true);
        config(registry, "1100_0111", "**_000_***", S(10, MovRmImm.class, "MOV", "[M16] <- I16"));
        config(registry, "1100_0111", "11_000_***", S( 4, MovRmImm.class, "MOV", "R16mr <- I16"), true);
        config(registry, "1011_****",               S( 4, MovRegImm.class, "MOV", "R* <- IMM*"));

        config(registry, "1010_00**",               S(10, MovAccMem.class, "MOV", "A <-> [M]"));

        config(registry, "1000_1110", "**_0**_***", S( 8, MovRmSR.class, "MOV", "SR <- [M16]"));
        config(registry, "1000_1110", "11_0**_***", S( 2, MovRmSR.class, "MOV", "SR <-  R16"), true);
        config(registry, "1000_1100", "**_0**_***", S( 9, MovRmSR.class, "MOV", "[M16] <- SR"));
        config(registry, "1000_1100", "11_0**_***", S( 2, MovRmSR.class, "MOV", " R16  <- SR"), true);


        config(registry, "1000_011*", "**_***_***", S(17, XchgRmR.class, "XCHG", "R <-> [M]"));
        config(registry, "1000_011*", "11_***_***", S( 4, XchgRmR.class, "XCHG", "R <->  R"), true);

        config(registry, "1001_0***",               S( 3, XchgRAcc.class, "XCHG", "A <-> R"));
        config(registry, "1001_0000",               S( 3, Nop.class, "NOP", ""), true);


        config(registry, "1101_0111",               S(11, Xlat.class, "XLAT", ""));

        config(registry, "1000_1101",               S( 2, Lea.class, "LEA", "R16 <- [M16]"));
        config(registry, "1100_0101",               S(16, Lds.class, "LDS", "DS <- [M16]"));
        config(registry, "1100_0100",               S(16, Les.class, "LES", "ES <- [M16]"));

        config(registry, "1001_1000",               S( 2, Cbw.class, "CBW", ""));
        config(registry, "1001_1001",               S( 5, Cwd.class, "CWD", ""));
    }

    /**
     * Register/memory to/ from register
     */
    public static class MovRmR extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode)
        {
            boolean w = (opcode & 0b0000_0001) == 0b01;
            boolean d = (opcode & 0b0000_0010) == 0b10;

            if (d) {
                // reg <<- mor r/m
                cpu.writeRegister(w, cpu.mrrRegIndex, cpu.mrrModValue);
            } else {
                // mod r/m <<- reg
                cpu.writeByModRegRm(w, cpu.mrrRegValue);
            }
        }
    }

    /**
     * Immediate value to register/memory
     */
    public static class MovRmImm extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_0001) == 0b01;

            int imm;
            if (w) {
                imm = cpu.ipRead16();
            } else {
                imm = cpu.ipRead8();
            }

            cpu.writeByModRegRm(w, imm);
        }
    }

    /**
     * Immediate value to register
     */
    public static class MovRegImm extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_1000) != 0b0000;
            int regIndex = opcode & 0b0111;

            int imm;
            if (w) {
                imm = cpu.ipRead16();
            } else {
                imm = cpu.ipRead8();
            }

            cpu.writeRegister(w, regIndex, imm);
        }
    }

    /**
     * Implements (acc <- mem) and (mem <- acc) commands
     */
    public static class MovAccMem extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_0001) != 0b0000;
            // here 'd' is not standard 'd', but:
            // 0 - mem to acc
            // 1 - acc to mem
            boolean d = (opcode & 0b0000_0010) != 0b0000;
            int displacement = cpu.ipRead16();

            if (d) {
                cpu.mwrite(w, displacement, cpu.registers[Cpu.AX]);
            } else {
                int eaValue = cpu.mread(w, displacement);
                cpu.writeRegister(w, Cpu.AX, eaValue);
            }
        }
    }

    /**
     * Implements (sr <- mem) and (mem <- sr) commands
     */
    public static class MovRmSR extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
            // here 'd' is not standard 'd', but:
            // 0 - segment reg to r/m
            // 1 - r/m to segment reg
            boolean d = (opcode & 0b0000_0010) != 0b0000;

            // decoded mrr* are incorrect here as last bit of opcode is zero
            // and Cpu.readModRegRm() considers this as byte mode,
            // but this instruction is always in word mode.
            // cpu.mrrReg is correct as -reg- part has 0SR format,
            // cpu.mrrModRegIndex must be fixed,
            // cpu.mrrModEA is correct in non reg mode

            if (d) {
                // seg <<- mod r/m
                int value;
                if ((cpu.mrrMod ^ 0b11) == 0) {
                    // register mode, fix to original word type index
                    value = cpu.registers[cpu.mrrRm];
                } else {
                    value = cpu.mread(true, cpu.mrrModEA);
                }
                cpu.writeSegment(cpu.mrrReg, value);
            }
            else {
                // mod r/m <<- seg
                if ((cpu.mrrMod ^ 0b11) == 0) {
                    // register mode, fix to original word type index
                    cpu.mrrModRegIndex = cpu.mrrRm;
                }
                cpu.writeByModRegRm(true, cpu.segments[cpu.mrrReg]);
            }
        }
    }


    /**
     * Xchg of register/memory with register
     */
    public static class XchgRmR extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode)
        {
            boolean w = (opcode & 0b0000_0001) == 0b01;

            cpu.writeRegister(w, cpu.mrrRegIndex, cpu.mrrModValue);
            cpu.writeByModRegRm(w, cpu.mrrRegValue);
        }
    }

    /**
     * Xchg of a register with accumulator
     */
    public static class XchgRAcc extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
            int reg = (opcode & 0b0000_0111);

            int x = cpu.registers[Cpu.AX];
            cpu.registers[Cpu.AX] = cpu.registers[reg];
            cpu.registers[reg] = x;
        }
    }

    /**
     * no operation / xchg ax,ax
     */
    public static class Nop extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
        }
    }

    /**
     * xlat (al <- bx[al])
     */
    public static class Xlat extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
            int value = cpu.mread8(cpu.registers[Cpu.BX]);
            cpu.writeRegisterLowByte(Cpu.AX, value);
        }
    }

    public static class Lea extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode)
        {
            cpu.writeRegisterWord(cpu.mrrRegIndex, cpu.mrrModEA);
        }
    }

    public static class Lds extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode)
        {
            cpu.writeSegment(Cpu.DS, cpu.mrrModValue);
            int offset = cpu.mread16(cpu.mrrModEA + 2);
            cpu.writeRegisterWord(cpu.mrrRegIndex, offset);
        }
    }

    public static class Les extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode)
        {
            // decoded mrr* are incorrect here as last bit of opcode is zero
            // and Cpu.readModRegRm() considers this as byte mode,
            // but this instruction is always in word mode.
            int seg = cpu.mread16(cpu.mrrModEA);
            cpu.writeSegment(Cpu.ES, seg);
            int offset = cpu.mread16(cpu.mrrModEA + 2);
            cpu.writeRegisterWord(cpu.mrrRegIndex, offset);
        }
    }

    /**
     * Convert byte to word with sign
     */
    public static class Cbw extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
            int ax = cpu.registers[Cpu.AX];
            int al = ax & 0x00FF;
            int ah =  - ((ax >> Cpu.BYTE_POS_SIGN) & 0x01);
            cpu.writeRegisterWord(Cpu.AX, (ah << 8) | al);
        }
    }
    
    /**
     * Convert word to double word with sign
     */
    public static class Cwd extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode) {
            int ax = cpu.registers[Cpu.AX];
            int dx =  - ((ax >> Cpu.WORD_POS_SIGN) & 0x01);
            cpu.writeRegisterWord(Cpu.DX, dx);
        }
    }
}
