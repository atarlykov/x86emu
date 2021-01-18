package at.emu.i8086;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of transfer opcodes
 */
public class Transfer implements Cpu.OpcodeConfiguration {

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
    public Map<String, ?> getConfiguration()
    {
        Map<String, Object> tmp = new HashMap<>();
        tmp.putAll(Map.of(
                "1000_10**", MovRmR.class,
                "1100_011*", Map.of("000", MovRmImm.class),
                "1011_****", MovRegImm.class,
                "1010_00**", MovAccMem.class,
                "1000_11*0", Map.of("0**", MovRmSR.class),
                "1000_011*", XchgRmR.class
        ));
        tmp.putAll(Map.of(
                "1001_0**1", XchgRAcc.class,
                "1001_0010", XchgRAcc.class,
                "1001_0100", XchgRAcc.class,
                "1001_0110", XchgRAcc.class,
                "1001_0000", Nop.class
        ));

        tmp.putAll(Map.of(
                "1101_0111", Xlat.class,
                "1000_1101", Lea.class,
                "1100_0101", Lds.class,
                "1100_0100", Les.class
        ));

        tmp.putAll(Map.of(
                "1001_1000", Cbw.class,
                "1001_1001", Cwd.class
        ));

        return tmp;
    }

    /**
     * Register/memory to/ from register
     */
    public static class MovRmR extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            cpu.readModRegRm(opcode);
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
    public static class MovRmImm extends Cpu.DemuxedOpcode {
        @Override
        void demuxed(Cpu cpu, int opcode) {
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
    public static class MovRegImm extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
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
    public static class MovAccMem extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
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
    public static class MovRmSR extends Cpu.DemuxedOpcode {
        @Override
        void demuxed(Cpu cpu, int opcode) {
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
    public static class XchgRmR extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            cpu.readModRegRm(opcode);
            boolean w = (opcode & 0b0000_0001) == 0b01;

            cpu.writeRegister(w, cpu.mrrRegIndex, cpu.mrrModValue);
            cpu.writeByModRegRm(w, cpu.mrrRegValue);
        }
    }

    /**
     * Xchg of a register with accumulator
     */
    public static class XchgRAcc extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            int reg = (opcode & 0b0000_0111);

            int x = cpu.registers[Cpu.AX];
            cpu.registers[Cpu.AX] = cpu.registers[reg];
            cpu.registers[reg] = x;
        }
    }

    /**
     * no operation / xchg ax,ax
     */
    public static class Nop extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
        }
    }

    /**
     * xlat (al <- bx[al])
     */
    public static class Xlat extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            int value = cpu.mread8(cpu.registers[Cpu.BX]);
            cpu.writeRegisterLowByte(Cpu.AX, value);
        }
    }

    public static class Lea extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            cpu.readModRegRm(opcode);
            cpu.writeRegisterWord(cpu.mrrRegIndex, cpu.mrrModEA);
        }
    }

    public static class Lds extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            cpu.readModRegRm(opcode);

            cpu.writeSegment(Cpu.DS, cpu.mrrModValue);
            int offset = cpu.mread16(cpu.mrrModEA + 2);
            cpu.writeRegisterWord(cpu.mrrRegIndex, offset);
        }
    }

    public static class Les extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            cpu.readModRegRm(opcode);
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
    public static class Cbw extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            int ax = cpu.registers[Cpu.AX];
            int al = ax & 0x00FF;
            int ah =  - ((ax >> Cpu.BYTE_POS_SIGN) & 0x01);
            cpu.writeRegisterWord(Cpu.AX, (ah << 8) | al);
        }
    }
    /**
     * Convert word to double word with sign
     */
    public static class Cwd extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            int ax = cpu.registers[Cpu.AX];
            int dx =  - ((ax >> Cpu.WORD_POS_SIGN) & 0x01);
            cpu.writeRegisterWord(Cpu.DX, dx);
        }
    }

}
