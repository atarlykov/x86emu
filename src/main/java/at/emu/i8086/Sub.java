package at.emu.i8086;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of subtraction opcodes
 */
public class Sub implements Cpu.OpcodeConfiguration, Cpu.ClockedOpcodeConfiguration
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

    @Override
    public Map<String, Configuration> getClockedConfiguration()
    {
        Map<String, Configuration> c = new HashMap<>();

        // SUB
        config(c, "0010_100*", "**_***_***", S(16, SubRmR.class, "SUB", "[M] <-  R"));
        config(c, "0010_101*", "**_***_***", S( 9, SubRmR.class, "SUB", " R  <- [M]"));
        config(c, "0010_10**", "11_***_***", S( 3, SubRmR.class, "SUB", " R  <-  R"), true);

        config(c, "0010_110*",               S( 4, SubAccImm.class, "SUB", " A   <- I"));
        config(c, "1000_00**", "**_101_***", S(17, SubRmImm.class,  "SUB", "[M]  <- I"));
        config(c, "1000_00**", "11_101_***", S( 4, SubRmImm.class,  "SUB", " Rm  <- I"), true);

        // SBB
        config(c, "0001_100*", "**_***_***", S(16, SbbRmR.class, "SBB", "[M] <-  R"));
        config(c, "0001_101*", "**_***_***", S( 9, SbbRmR.class, "SBB", " R  <- [M]"));
        config(c, "0001_10**", "11_***_***", S( 3, SbbRmR.class, "SBB", " R  <-  R"), true);

        config(c, "0001_110*",               S( 4, SbbAccImm.class, "SBB", " A   <- I"));
        config(c, "1000_00**", "**_011_***", S(17, SbbRmImm.class,  "SBB", "[M]  <- I"));
        config(c, "1000_00**", "11_011_***", S( 4, SbbRmImm.class,  "SBB", " Rm  <- I"), true);

        // DEC
        config(c, "0100_1***",               S( 2, DecReg.class, "DEC", "R16"));
        config(c, "1111_111*", "**_001_***", S(15, DecReg.class, "DEC", "R"));
        config(c, "1111_1110", "11_001_***", S( 3, DecReg.class, "DEC", "R8"), true);
        config(c, "1111_1111", "11_001_***", S( 2, DecReg.class, "DEC", "R16"), true);

        // AAS & DAS
        config(c, "0011_1111",               S( 4, Aas.class, "AAS", ""));
        config(c, "0010_1111",               S( 4, Das.class, "DAS", ""));

        // NEG
        config(c, "1111_011*", "**_011_***", S(16, DecReg.class, "NEG", "[M]"));
        config(c, "1111_011*", "11_011_***", S( 3, DecReg.class, "NEG", "R"), true);

        return c;
    }

    /**
     * sets cpu flags based on the result of (dst <- dst - src) operation
     *
     * @param cpu ref to cpu
     * @param word word (true) or byte(false) mode
     * @param dst dst operand
     * @param src src operand
     * @return (dst - src) used during flags calculation
     */
    public static int flags(Cpu cpu, boolean word, int dst, int src)
    {
        int value = dst - src;

        if (word)
        {
            Cpu.Opcode.flagsPszc16(cpu, value);

            // this is sub specific
            // todo: optimize
            if (((dst & Cpu.WORD_MASK_SIGN) != (src & Cpu.WORD_MASK_SIGN)) &&
                    ((dst & Cpu.WORD_MASK_SIGN) != (value & Cpu.WORD_MASK_SIGN)))
                cpu.setFlag(Cpu.FLAG_OF);
            else
                cpu.resetFlag(Cpu.FLAG_OF);

        }
        else {
            Cpu.Opcode.flagsPszc8(cpu, value);

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

        return value;
    }

    /**
     * sets cpu flags based on the result of (dst <- dst - src) operation
     * not touching the CF flag (for DEC opcode)
     * NOTE: direct copy of {@link #flags} without CF assignment
     *
     * @param cpu ref to cpu
     * @param word word (true) or byte(false) mode
     * @param dst dst operand
     * @param src src operand
     * @return (dst - src) used during flags calculation
     */
    public static int flagsNoCF(Cpu cpu, boolean word, int dst, int src)
    {
        int value = dst - src;

        if (word)
        {
            Cpu.Opcode.flagsPsz16(cpu, value);

            // this is sub specific
            // todo: optimize
            if (((dst & Cpu.WORD_MASK_SIGN) != (src & Cpu.WORD_MASK_SIGN)) &&
                    ((dst & Cpu.WORD_MASK_SIGN) != (value & Cpu.WORD_MASK_SIGN)))
                cpu.setFlag(Cpu.FLAG_OF);
            else
                cpu.resetFlag(Cpu.FLAG_OF);
        }
        else {
            Cpu.Opcode.flagsPsz8(cpu, value);

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

        return value;
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
                int delta = Sub.flags(cpu, w, cpu.mrrRegValue, cpu.mrrModValue);
                cpu.writeRegister(w, cpu.mrrRegIndex, delta);
            } else {
                // mod r/m <<- reg
                int delta = Sub.flags(cpu, w, cpu.mrrModValue, cpu.mrrRegValue);
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
            int delta = Sub.flags(cpu, w, cpu.mrrModValue, imm);

            cpu.writeByModRegRm(true, delta);
        }
    }

    /**
     * Implements subtraction of imm from accumulator
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

            int delta = Sub.flags(cpu, w, ax, imm);
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
                int delta = Sub.flagsNoCF(cpu, w, cpu.mrrRegValue, cpu.mrrModValue);

                if ((cpu.flags & Cpu.FLAG_CF) != 0) {
                    delta = Sub.flags(cpu, w, delta, 1);
                }

                cpu.writeRegister(w, cpu.mrrRegIndex, delta);
            } else {
                // mod r/m <<- reg
                int delta = Sub.flagsNoCF(cpu, w, cpu.mrrModValue, cpu.mrrRegValue);

                if ((cpu.flags & Cpu.FLAG_CF) != 0) {
                    delta = Sub.flags(cpu, w, delta, 1);
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
            int delta = Sub.flagsNoCF(cpu, w, cpu.mrrModValue, imm);

            if ((cpu.flags & Cpu.FLAG_CF) != 0) {
                delta = Sub.flags(cpu, w, delta, 1);
            }

            cpu.writeByModRegRm(true, delta);
        }
    }

    /**
     * Implements sbb subtraction of imm from accumulator
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


            int delta = Sub.flagsNoCF(cpu, w, ax, imm);

            if ((cpu.flags & Cpu.FLAG_CF) != 0) {
                delta = Sub.flags(cpu, w, delta, 1);
            }

            cpu.writeRegister(w, Cpu.AX, delta);
        }
    }

    /**
     * Implements decrement of a reg/mem
     * flags updated (af,of,pf,sf,zf  not cf)
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
     * Implements decrement of a register
     * flags updates (af,of,pf,sf,zf  not cf)
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
     * Implements negation as dst <<- (0 - dst)
     * flags updated (af,cf,of,pf,sf,zf)
     *
     * cases:
     *   0      -> 0
     *   -128   -> -128, OF set
     *   -32768 -> -32768, OF set
     *
     * if dst == 0
     *      reset CF
     * else
     *      set CF
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
     * AAS  (AL <- valid unpacked decimal number &0x0F)
     * flags updated (af, cf) [others ->> undef]
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
     *  DAA (AL <- pair of valid packed decimal)
     *  flags updated (af,cf,pf,sf,zf, of=undef)
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
