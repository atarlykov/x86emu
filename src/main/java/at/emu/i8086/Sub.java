package at.emu.i8086;

import java.util.Map;

/**
 * Implementation of subtraction opcodes
 */
public class Sub implements Cpu.OpcodeConfiguration
{
    /**
     * Based on i8086 manual:
     * SUB (af,cf,of,pf,sf,zf)
     *    Reg/mem and reg to either   001010dw mrr   disp     disp
     *    imm from reg/mem            100000sw m101r disp     disp    data   data(if s:w=01)
     *    imm from accum              0010110w data  data(w=1)
     * SBB (af,cf,of,pf,sf,zf)  dst = dst - src - cf, ds
     *    Reg/mem and reg to either   000110dw mrr   disp     disp
     *    imm from reg/mem            100000sw m011r disp     disp    data   data(if s:w=01)
     *    imm from accum              0001110w data  data(w=1)
     * DEC (af,of,pf,sf,zf  not cf)
     *    reg/mem                     1111111w m001r disp     disp
     *    reg                         01001reg
     * NEG (af,cf,of,pf,sf,zf) dst <- 0-dst,   0->0,  -128->-128 OF, -32768->-32768 OF, CF always except dst==0 - reset CF
     *    change sign                 1111011w m011r disp     disp
     *
     * AAS                            0011_1111
     *    changes al to a valid unpacked decimal number, updates af and cf
     * DAS                            0010_1111
     *    changes al to a pair of valid unpacked decimal number, updates (af,cf,pf,sf,zf), of - undefined
     *
     *
     * @return configuration of opcodes
     */
    @Override
    public Map<String, ?> getConfiguration() {
        return Map.of(
                "0010_10**", SubRmR.class,
                "0010_110*", SubAccImm.class,
                "0001_10**", SbbRmR.class,
                "0001_110*", SbbAccImm.class,
                "1000_00**", Map.of(
                        "101", SubRmImm.class,
                        "011", SbbRmImm.class
                ),
                "1111_111*", Map.of("001", DecRm.class),
                "0100_1***", DecReg.class,
                "1111_011*", Map.of("011", NegRm.class),
                "0011_1111", Aas.class,
                "0010_1111", Das.class
        );
    }

    /**
     * sets cpu flags based on the result of (dst <- dst - src) operation
     *
     * @param cpu ref to cpu
     * @param word word (true) or byte(false) mode
     * @param dst dst operand
     * @param src src operand
     */
    public static void flags(Cpu cpu, boolean word, int dst, int src)
    {
        int value = dst - src;

        if (word) {
            int wValue = value & Cpu.WORD_MASK;

            Cpu.Opcode.flagsPszc16(cpu, wValue);

            // this is sub specific
            // todo: optimize
            if (((dst & Cpu.WORD_MASK_SIGN) != (src & Cpu.WORD_MASK_SIGN)) &&
                    ((dst & Cpu.WORD_MASK_SIGN) != (value & Cpu.WORD_MASK_SIGN)))
                cpu.setFlag(Cpu.FLAG_OF);
            else
                cpu.resetFlag(Cpu.FLAG_OF);

        }
        else {
            int wValue = value & Cpu.BYTE_MASK;

            Cpu.Opcode.flagsPszc8(cpu, wValue);

            // this is sub specific
            if (((dst & Cpu.BYTE_MASK_SIGN) != (src & Cpu.BYTE_MASK_SIGN)) &&
                    ((dst & Cpu.BYTE_MASK_SIGN) != (value & Cpu.BYTE_MASK_SIGN)))
                cpu.setFlag(Cpu.FLAG_OF);
            else
                cpu.resetFlag(Cpu.FLAG_OF);
        }

        // AF only base on lower nipple of low byte,
        // check for borrow from the high nibble
        // todo: check for 8|4 bit nibble (here=8 and add=4)
        if ((dst & 0xFF) < (src & 0xFF))
            cpu.setFlag(Cpu.FLAG_AF);
        else
            cpu.resetFlag(Cpu.FLAG_AF);
    }

    /**
     * sets cpu flags based on the result of (dst <- dst - src) operation
     * not touching the CF flag (for DEC opcode)
     * NOTE: direct copy of {@link #flags} without CF assignment
     * todo: use {@link Cpu.Opcode#flagsPsz(Cpu, boolean, int)}
     *
     * @param cpu ref to cpu
     * @param word word (true) or byte(false) mode
     * @param dst dst operand
     * @param src src operand
     */
    public static void flagsNoCF(Cpu cpu, boolean word, int dst, int src)
    {
        int value = dst - src;

        if (word) {
            int wValue = value & Cpu.WORD_MASK;

            Cpu.Opcode.flagsPsz16(cpu, wValue);

            // this is sub specific
            // todo: optimize
            if (((dst & Cpu.WORD_MASK_SIGN) != (src & Cpu.WORD_MASK_SIGN)) &&
                    ((dst & Cpu.WORD_MASK_SIGN) != (value & Cpu.WORD_MASK_SIGN)))
                cpu.setFlag(Cpu.FLAG_OF);
            else
                cpu.resetFlag(Cpu.FLAG_OF);

        }
        else {
            int wValue = value & Cpu.BYTE_MASK;

            Cpu.Opcode.flagsPsz8(cpu, wValue);

            // this is sub specific
            if (((dst & Cpu.BYTE_MASK_SIGN) != (src & Cpu.BYTE_MASK_SIGN)) &&
                    ((dst & Cpu.BYTE_MASK_SIGN) != (value & Cpu.BYTE_MASK_SIGN)))
                cpu.setFlag(Cpu.FLAG_OF);
            else
                cpu.resetFlag(Cpu.FLAG_OF);
        }

        // AF only base on lower nipple of low byte,
        // check for borrow from the high nibble
        // todo: check for 8|4 bit nibble (here=8 and add=4)
        if ((dst & 0xFF) < (src & 0xFF))
            cpu.setFlag(Cpu.FLAG_AF);
        else
            cpu.resetFlag(Cpu.FLAG_AF);
    }

    /**
     * Implements Sub Reg/mem and reg either operations
     */
    public static class SubRmR extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            cpu.readModRegRm(opcode);
            boolean w = (opcode & 0b0000_0001) == 0b01;
            boolean d = (opcode & 0b0000_0010) == 0b10;

            if (d) {
                // reg <<- mor r/m
                int delta = cpu.mrrRegValue - cpu.mrrModValue;
                Sub.flags(cpu, w, cpu.mrrRegValue, cpu.mrrModValue);
                cpu.writeRegister(w, cpu.mrrRegIndex, delta);
            } else {
                // mod r/m <<- reg
                int delta = cpu.mrrModValue - cpu.mrrRegValue;
                Sub.flags(cpu, w, cpu.mrrModValue, cpu.mrrRegValue);
                cpu.writeByModRegRm(true, delta);
            }
        }
    }

    /**
     * Implements subtraction of imm from reg/mem
     * imm from reg/mem            100000sw m101r disp     disp    data   data(if s:w=01)
     */
    public static class SubRmImm extends Cpu.DemuxedOpcode {
        @Override
        public void demuxed(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_0001) == 0b01;
            boolean imm16 = (opcode & 0b0000_0011) == 0b01;

            // extract immediate value,
            // size depends on s:w==0:1 bits
            int imm;
            if (imm16) {
                imm = cpu.ipRead16();
            } else {
                imm = cpu.ipRead8();
            }

            // mod r/m <<- imm
            Sub.flags(cpu, w, cpu.mrrModValue, imm);
            int delta = cpu.mrrModValue - imm;

            cpu.writeByModRegRm(true, delta);
        }
    }

    /**
     * Implements subtraction of imm from accumulator
     * imm from accum              0010110w data  data(w=1)   [(ax|al) - imm]
     */
    public static class SubAccImm extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_0001) == 0b01;

            // accumulator
            int ax = cpu.registers[Cpu.AX];

            // extract immediate value,
            // size depends on w bit
            int imm;
            if (w) {
                imm = cpu.ipRead16();
            } else {
                imm = cpu.ipRead8();
                // use only AL part
                ax &= 0x000F;
            }

            Sub.flags(cpu, w, ax, imm);

            int delta = ax - imm;
            cpu.writeRegister(w, Cpu.AX, delta);
        }
    }


    /**
     * Implements Sbb Reg/mem and reg either operations
     */
    public static class SbbRmR extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            cpu.readModRegRm(opcode);
            boolean w = (opcode & 0b0000_0001) == 0b01;
            boolean d = (opcode & 0b0000_0010) == 0b10;

            if (d) {
                // reg <<- mor r/m
                int delta = cpu.mrrRegValue - cpu.mrrModValue;
                Sub.flagsNoCF(cpu, w, cpu.mrrRegValue, cpu.mrrModValue);

                if ((cpu.flags & Cpu.FLAG_CF) != 0) {
                    Sub.flags(cpu, w, delta, 1);
                    delta -= 1;
                }

                cpu.writeRegister(w, cpu.mrrRegIndex, delta);
            } else {
                // mod r/m <<- reg
                int delta = cpu.mrrModValue - cpu.mrrRegValue;
                Sub.flagsNoCF(cpu, w, cpu.mrrModValue, cpu.mrrRegValue);

                if ((cpu.flags & Cpu.FLAG_CF) != 0) {
                    Sub.flags(cpu, w, delta, 1);
                    delta -= 1;
                }

                cpu.writeByModRegRm(true, delta);
            }
        }
    }

    /**
     * Implements sbb subtraction of imm from reg/mem
     * imm from reg/mem            100000sw m101r disp     disp    data   data(if s:w=01)
     */
    public static class SbbRmImm extends Cpu.DemuxedOpcode {
        @Override
        public void demuxed(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_0001) == 0b01;
            boolean imm16 = (opcode & 0b0000_0011) == 0b01;

            // extract immediate value,
            // size depends on s:w==0:1 bits
            int imm;
            if (imm16) {
                imm = cpu.ipRead16();
            } else {
                imm = cpu.ipRead8();
            }

            // mod r/m <<- imm
            Sub.flagsNoCF(cpu, w, cpu.mrrModValue, imm);
            int delta = cpu.mrrModValue - imm;

            if ((cpu.flags & Cpu.FLAG_CF) != 0) {
                Sub.flags(cpu, w, delta, 1);
                delta -= 1;
            }

            cpu.writeByModRegRm(true, delta);
        }
    }

    /**
     * Implements sbb subtraction of imm from accumulator
     * imm from accum              0010110w data  data(w=1)   [(ax|al) - imm]
     */
    public static class SbbAccImm extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_0001) == 0b01;

            // accumulator
            int ax = cpu.registers[Cpu.AX];

            // extract immediate value,
            // size depends on w bit
            int imm;
            if (w) {
                imm = cpu.ipRead16();
            } else {
                imm = cpu.ipRead8();
                // use only AL part
                ax &= 0x000F;
            }

            Sub.flagsNoCF(cpu, w, ax, imm);
            int delta = ax - imm;

            if ((cpu.flags & Cpu.FLAG_CF) != 0) {
                Sub.flags(cpu, w, delta, 1);
                delta -= 1;
            }

            cpu.writeRegister(w, Cpu.AX, delta);
        }
    }

    /**
     * Implements decrement of a reg/mem, DEC (af,of,pf,sf,zf  not cf)
     * reg/mem                     1111111w m001r disp     disp
     */
    public static class DecRm extends Cpu.DemuxedOpcode {
        @Override
        void demuxed(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_0001) == 0b01;

            Sub.flagsNoCF(cpu, w, cpu.mrrModValue, 1);
            int delta = cpu.mrrModValue - 1;

            cpu.writeByModRegRm(w, delta);

        }
    }

    /**
     * Implements decrement of a register, DEC (af,of,pf,sf,zf  not cf)
     * reg                         01001reg
     */
    public static class DecReg extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            int reg = (opcode & 0b0000_0111);
            Sub.flagsNoCF(cpu, true, cpu.registers[reg], 1);
            int value = cpu.registers[reg] - 1;
            cpu.writeRegister(true, reg, value);
        }
    }

    /**
     * Implements negation as dst <- 0-dst
     * NEG (af,cf,of,pf,sf,zf), dst <- 0-dst:  0->0, -128->-128 OF, -32768->-32768 OF, CF always except dst==0 - reset CF
     * <p>
     * change sign                 1111011w m011r disp     disp
     */
    public static class NegRm extends Cpu.DemuxedOpcode {
        @Override
        void demuxed(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_0001) == 0b01;

            //if ((w && (cpu.mrrModValue == 0x8000)) ||
            //        (!w && (cpu.mrrModValue == 0x0080)))
            //{
            //}
            Sub.flagsNoCF(cpu, w, 0, cpu.mrrModValue);

            int delta = 0 - cpu.mrrModValue;

            if (delta == 0) {
                cpu.resetFlag(Cpu.FLAG_CF);
            } else {
                cpu.setFlag(Cpu.FLAG_CF);
            }

            cpu.writeByModRegRm(w, delta);
        }
    }


    /**
     * AAA (af, cf) others=undef      00110111  // AL <- valid unpacked decimal number &0x0F
     */
    public static class Aas extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode)
        {
            int ax = cpu.registers[Cpu.AX];

            if ((9 < (ax & 0x0F)) || ((cpu.flags & Cpu.FLAG_AF) != 0)) {
                ax -= 6;
                ax = ((((ax >> 8) - 1) & 0xFF) << 8) | (ax & 0x0F);
                cpu.flags |= (Cpu.FLAG_AF | Cpu.FLAG_CF);
            } else {
                // clear af, cf
                cpu.flags &= (~Cpu.FLAG_AF | ~Cpu.FLAG_CF);
                ax &= 0xFF0F;
            }
            cpu.registers[Cpu.AX] = ax;
        }
    }
    /**
     *  DAA (af,cf,pf,sf,zf, of=undef) 00100111  // AL <- pair of valid packed decimal
     */
    public static class Das extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode)
        {
            int cf = (cpu.flags & Cpu.FLAG_CF);
            int ax = cpu.registers[Cpu.AX];
            int al = ax & 0xFF;

            cpu.flags &= (~Cpu.FLAG_CF);

            if ((9 < (al & 0x0F)) || ((cpu.flags & Cpu.FLAG_AF) != 0)) {
                al -= 6;
                cpu.flags |= Cpu.FLAG_AF | cf | (((al & Cpu.BYTE_MASK_CARRY) >> Cpu.BYTE_POS_CARRY) & Cpu.FLAG_CF);
            } else {
                cpu.flags &= (~Cpu.FLAG_AF);
            }

            if ((0x99 < (ax & 0xFF)) || (cf != 0)) {
                al -= 0x60;
                cpu.flags |= (Cpu.FLAG_CF);
            }

            ax = (ax & 0xFF00) | (al & 0xFF);
            cpu.registers[Cpu.AX] = ax;
        }
    }
}
