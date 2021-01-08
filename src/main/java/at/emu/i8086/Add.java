package at.emu.i8086;

import java.util.Map;

/**
 * Implementation of addition operations
 */
public class Add implements Cpu.OpcodeConfiguration {

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
     * Addition reg/mem with reg
     * Reg/mem with reg to either  000000dw mrr   disp     disp
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
     * Addition reg/mem with immediate
     * imm to reg/mem              100000sw m000r disp     disp    data   data(if s:w=01)
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
     * Addition reg/mem with immediate
     * imm to accum                0000010w data  data(w=1)
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
     * Addition reg/mem with reg
     * Reg/mem with reg to either  000100dw mrr   disp     disp
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
     * Addition reg/mem with immediate
     * imm to reg/mem              100000sw m010r disp     disp    data   data(if s:w=01)
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
     * Addition reg/mem with immediate
     * imm to accum                0001010w data  data(w=1)
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
     * Implements increment of a reg/mem, INC (af,of,pf,sf,zf  not cf)
     * reg/mem                     1111111w m000r disp     disp
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
     * Implements increment of a register, DEC (af,of,pf,sf,zf  not cf)
     * reg                         01000reg
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
     * AAA (af, cf) others=undef      00110111  // AL <- valid unpacked decimal number &0x0F
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
     *  DAA (af,cf,pf,sf,zf, of=undef) 00100111  // AL <- pair of valid packed decimal
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