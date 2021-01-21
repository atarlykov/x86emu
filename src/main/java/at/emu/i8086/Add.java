package at.emu.i8086;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of addition operations
 */
public class Add implements Cpu.OpcodeConfiguration, Cpu.ClockedOpcodeConfiguration {

    /**
     * Additions: ADD ADC INC AAA DAA
     *  opcodes: [p4-23],  flags: [p2-35]
     *  ADD (af,cf,of,pf,sf,zf)
     *     Reg/mem with reg to either  000000dw mrr   disp     disp
     *     imm to reg/mem              100000sw m000r disp     disp    data   data(if s:w=01)
     *     imm to accum                0000010w data  data(w=1)
     *  ADC (the same as ADD, but codes,  dst <- dst + src + CF)
     *                                 000100dw --"--
     *       --"--                     100000sw m010r --"--
     *                                 0001010w --"--
     *  INC (af,of,pf,sf,zf   not cf)
     *     reg/mem                     1111111w m000r disp     disp
     *     reg                         01000reg
     *
     *  AAA (af, cf) others=undef      00110111  // AL <- valid unpacked decimal number &0x0F
     *  DAA (af,cf,pf,sf,zf, of=undef) 00100111  // AL <- pair of valid packed decimal
     *
     * @return configuration of opcodes
     */
    @Override
    public Map<String, ?> getConfiguration() {
        return Map.of(
                "0000_00**", AddRmR.class,
                "0000_010*", AddAccImm.class,
                "0001_00**", AdcRmR.class,
                "0001_010*", AdcAccImm.class,
                "1000_00**", Map.of(
                        "000", AddRmImm.class,
                        "010", AdcRmImm.class
                ),
                "1111_111*", Map.of("000", IncRm.class),
                "0100_0***", IncReg.class,
                "0011_0111", Aaa.class,
                "0010_0111", Daa.class
        );
    }

    @Override
    public Map<String, Configuration> getClockedConfiguration()
    {
        Map<String, Configuration> c = new HashMap<>();

        // ADD
        config(c, "0000_000*", "**_***_***", S(16, AddRmR.class, "ADD", "[M] <-  R"));
        config(c, "0000_001*", "**_***_***", S( 9, AddRmR.class, "ADD", " R  <- [M]"));
        config(c, "0000_00**", "11_***_***", S( 3, AddRmR.class, "ADD", " R  <-  R"), true);

        config(c, "0000_010*",               S( 4, AddAccImm.class, "ADD", " A   <- I"));
        config(c, "1000_00**", "**_000_***", S(17, AddRmImm.class,  "ADD", "[M]  <- I"));
        config(c, "1000_00**", "11_000_***", S( 4, AddRmImm.class,  "ADD", " Rm  <- I"), true);

        // ADC
        config(c, "0001_000*", "**_***_***", S(16, AdcRmR.class, "ADC", "[M] <-  R"));
        config(c, "0001_001*", "**_***_***", S( 9, AdcRmR.class, "ADC", " R  <- [M]"));
        config(c, "0001_00**", "11_***_***", S( 3, AdcRmR.class, "ADC", " R  <-  R"), true);

        config(c, "0001_010*",               S( 4, AdcAccImm.class, "ADC", " A   <- I"));
        config(c, "1000_00**", "**_010_***", S(17, AdcRmImm.class,  "ADC", "[M]  <- I"));
        config(c, "1000_00**", "11_010_***", S( 4, AdcRmImm.class,  "ADC", " Rm  <- I"), true);

        // INC
        config(c, "0100_0***",               S( 2, IncReg.class, "INC", "R16"));
        config(c, "1111_111*", "**_000_***", S(15, IncReg.class, "INC", "R"));
        config(c, "1111_1110", "11_000_***", S( 3, IncReg.class, "INC", "R8"), true);
        config(c, "1111_1111", "11_000_***", S( 2, IncReg.class, "INC", "R16"), true);

        // AAA & DAA
        config(c, "0011_0111",               S( 4, Aaa.class, "AAA", ""));
        config(c, "0010_0111",               S( 4, Daa.class, "DAA", ""));

        return c;
    }

    /**
     * sets cpu flags based on the result of (dst <- dst + src) operation
     * (af,cf,of,pf,sf,zf) flags are updated
     *
     * @param cpu ref to cpu
     * @param w   word (true) or byte(false) mode
     * @param dst first operand
     * @param src second operand
     * @return sum of operands, used for flags calculation
     */
    public static int flags(Cpu cpu, boolean w, int dst, int src)
    {
        int value = dst + src;

        if (w) {
            Cpu.Opcode.flagsPszc16(cpu, value);

            // todo: optimize
            if (((dst & Cpu.WORD_MASK_SIGN) == (src & Cpu.WORD_MASK_SIGN)) &&
                    ((dst & Cpu.WORD_MASK_SIGN) != (value & Cpu.WORD_MASK_SIGN)))
                cpu.setFlag(Cpu.FLAG_OF);
            else
                cpu.resetFlag(Cpu.FLAG_OF);

        }
        else {
            Cpu.Opcode.flagsPszc8(cpu, value);

            if (((dst & Cpu.BYTE_MASK_SIGN) == (src & Cpu.BYTE_MASK_SIGN)) &&
                    ((dst & Cpu.BYTE_MASK_SIGN) != (value & Cpu.BYTE_MASK_SIGN)))
                cpu.setFlag(Cpu.FLAG_OF);
            else
                cpu.resetFlag(Cpu.FLAG_OF);
        }

        // AF is only based on lower nipple of low byte
        if (0x0F < ((dst & 0x0F) + (src & 0x0F)))
            cpu.setFlag(Cpu.FLAG_AF);
        else
            cpu.resetFlag(Cpu.FLAG_AF);

        return value;
    }

    /**
     * sets cpu flags based on the result of (dst <- dst + src) operation
     * not touching the CF flag (for INC opcode)
     * NOTE: direct copy of {@link #flags} without CF assignment
     *
     * @param cpu ref to cpu
     * @param w   word (true) or byte(false) mode
     * @param dst first operand
     * @param src second operand
     * @return sum of operands, used for flags calculation
     */
    public static int flagsNoCF(Cpu cpu, boolean w, int dst, int src)
    {
        int value = dst + src;

        if (w) {
            Cpu.Opcode.flagsPsz16(cpu, value);

            // todo: optimize
            if (((dst & Cpu.WORD_MASK_SIGN) == (src & Cpu.WORD_MASK_SIGN)) &&
                    ((dst & Cpu.WORD_MASK_SIGN) != (value & Cpu.WORD_MASK_SIGN)))
                cpu.setFlag(Cpu.FLAG_OF);
            else
                cpu.resetFlag(Cpu.FLAG_OF);

        }
        else {
            Cpu.Opcode.flagsPsz8(cpu, value);

            if (((dst & Cpu.BYTE_MASK_SIGN) == (src & Cpu.BYTE_MASK_SIGN)) &&
                    ((dst & Cpu.BYTE_MASK_SIGN) != (value & Cpu.BYTE_MASK_SIGN)))
                cpu.setFlag(Cpu.FLAG_OF);
            else
                cpu.resetFlag(Cpu.FLAG_OF);

        }

        // AF is only based on lower nipple of low byte
        if (0x0F < ((dst & 0x0F) + (src & 0x0F)))
            cpu.setFlag(Cpu.FLAG_AF);
        else
            cpu.resetFlag(Cpu.FLAG_AF);

        return value;
    }

    /**
     * Addition of reg/mem with reg
     */
    public static class AddRmR extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            cpu.readModRegRm(opcode);
            boolean w = (opcode & 0b0000_0001) == 0b01;
            boolean d = (opcode & 0b0000_0010) == 0b10;

            int sum = flags(cpu, w, cpu.mrrRegValue, cpu.mrrModValue);

            if (d) {
                // reg <<- mor r/m
                cpu.writeRegister(w, cpu.mrrRegIndex, sum);
            } else {
                // mod r/m <<- reg
                cpu.writeByModRegRm(w, sum);
            }
        }
    }

    /**
     * Addition of reg/mem with immediate
     */
    public static class AddRmImm extends Cpu.DemuxedOpcode {
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

            int sum = flags(cpu, w, cpu.mrrModValue, imm);

            cpu.writeByModRegRm(w, sum);
        }
    }

    /**
     * Addition of accumulator with immediate
     */
    public static class AddAccImm extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_0001) == 0b01;

            // extract immediate value
            int imm;
            if (w) {
                imm = cpu.ipRead16();
            } else {
                imm = cpu.ipRead8();
            }

            int sum = flags(cpu, w, cpu.registers[Cpu.AX], imm);

            cpu.writeRegister(w, Cpu.AX, sum);
        }
    }

    /**
     * Addition of reg/mem with reg
     */
    public static class AdcRmR extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            cpu.readModRegRm(opcode);
            boolean w = (opcode & 0b0000_0001) == 0b01;
            boolean d = (opcode & 0b0000_0010) == 0b10;

            int cf = (cpu.flags >> Cpu.FLAG_CF) & 1;
            int sum = flags(cpu, w, cpu.mrrRegValue, cpu.mrrModValue + cf);

            if (d) {
                // reg <<- mor r/m
                cpu.writeRegister(w, cpu.mrrRegIndex, sum);
            } else {
                // mod r/m <<- reg
                cpu.writeByModRegRm(w, sum);
            }
        }
    }

    /**
     * Addition of reg/mem with immediate
     */
    public static class AdcRmImm extends Cpu.DemuxedOpcode {
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

            int cf = (cpu.flags >> Cpu.FLAG_CF_POS) & 1;
            int sum = flags(cpu, w, cpu.mrrModValue, imm + cf);

            cpu.writeByModRegRm(w, sum);
        }
    }

    /**
     * Addition of accumulator with immediate
     */
    public static class AdcAccImm extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_0001) == 0b01;

            // extract immediate value
            int imm;
            if (w) {
                imm = cpu.ipRead16();
            } else {
                imm = cpu.ipRead8();
            }

            int cf = (cpu.flags >> Cpu.FLAG_CF_POS) & 1;

            // todo separate CF addition as in sbb ? check logic
            int sum = flags(cpu, w, cpu.registers[Cpu.AX], imm + cf);

            cpu.writeByModRegRm(w, sum);
        }
    }

    /**
     * Implements increment of a reg/mem
     * flags updated (af,of,pf,sf,zf  not cf)
     */
    public static class IncRm extends Cpu.DemuxedOpcode {
        @Override
        void demuxed(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_0001) == 0b01;

            // mod r/m <<- imm
            int sum = flagsNoCF(cpu, w, cpu.mrrModValue, 1);
            cpu.writeByModRegRm(w, sum);
        }
    }

    /**
     * Implements increment of a register
     * flags updated (af,of,pf,sf,zf  not cf)
     */
    public static class IncReg extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            int reg = (opcode & 0b0000_0111);
            int sum = flagsNoCF(cpu, true, cpu.registers[reg], 1);
            cpu.writeRegister(true, reg, sum);
        }
    }

    /**
     * AAA (AL <- valid unpacked decimal number &0x0F)
     * flags updated (af, cf) [others=undef]
     */
    public static class Aaa extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode)
        {
            int ax = cpu.registers[Cpu.AX];

            if ((9 < (ax & 0x0F)) || ((cpu.flags & Cpu.FLAG_AF) != 0)) {
                ax += 0x0106;
                cpu.flags |= (Cpu.FLAG_AF | Cpu.FLAG_CF);
            } else {
                // clear af, cf
                cpu.flags &= (~Cpu.FLAG_AF | ~Cpu.FLAG_CF);
            }
            // clear AL high nipple
            ax &= 0xFF0F;
            cpu.registers[Cpu.AX] = ax;
        }
    }
    /**
     *  DAA (AL <- pair of valid packed decimal)
     *  flags updated (af,cf,pf,sf,zf, of=undef)
     */
    public static class Daa extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode)
        {
            int cf = (cpu.flags & Cpu.FLAG_CF);
            int ax = cpu.registers[Cpu.AX];
            int al = ax & 0xFF;

            cpu.flags &= (~Cpu.FLAG_CF);

            if ((9 < (al & 0x0F)) || ((cpu.flags & Cpu.FLAG_AF) != 0)) {
                al += 6;
                cpu.flags |= Cpu.FLAG_AF | cf | (((al & Cpu.BYTE_MASK_CARRY) >> Cpu.BYTE_POS_CARRY) & Cpu.FLAG_CF);
            } else {
                // clear af
                cpu.flags &= (~Cpu.FLAG_AF);
            }

            if ((0x99 < (ax & 0xFF)) || (cf != 0)) {
                al += 0x60;
                cpu.flags |= (Cpu.FLAG_CF);
            } else {
                cpu.flags &= (~Cpu.FLAG_CF);
            }
            
            ax = (ax & 0xFF00) | (al & 0xFF);
            cpu.registers[Cpu.AX] = ax;
        }
    }

}
