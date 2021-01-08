package at.emu.i8086;

import java.util.Map;

/**
 * Multiplication opcodes
 */
public class Multiplication implements Cpu.OpcodeConfiguration {

    /**
     * Multiplications
     *
     *  MUL unsigned mul
     *          1111_011w m100r     disp     disp
     *
     *     
     *  IMUL signed mul
     *          1111_011w m101r     disp     disp
     *  AAM
     *      updates pf,sf,zf
     *          1101_0100 data8
     *
     *
     * @return configuration of opcodes
     */
    @Override
    public Map<String, ?> getConfiguration() {
        return Map.of(
                "1111_011*", Map.of(
                        "100", Mul.class,
                        "101", Imul.class
                ),
                "1101_0100", Aam.class
        );
    }

    /**
     * Unsigned multiplication
     *      ax <- al * d8,
     *      dx:ax <- ax * d16,
     *
     *      if ah/dx is not zero
     *          set cf|of
     *      else
     *          clear cf|of
     */
    public static class Mul extends Cpu.DemuxedOpcode {
        @Override
        public void demuxed(Cpu cpu, int opcode)
        {
            boolean w = (opcode & 0b0000_0001) == 0b01;

            if (w) {
                int value = cpu.registers[Cpu.AX] * cpu.mrrModValue;
                if ((value & 0xFFFF0000) != 0) {
                    cpu.flags |= (Cpu.FLAG_OF | Cpu.FLAG_CF);
                } else {
                    cpu.flags &= (~Cpu.FLAG_OF | ~Cpu.FLAG_CF);
                }
                cpu.writeRegisterWord(Cpu.AX, value);
                cpu.writeRegisterWord(Cpu.DX, value >> 16);
            }
            else {
                int value = (cpu.registers[Cpu.AX] & 0xFF) * cpu.mrrModValue;
                if ((value & 0xFF00) != 0) {
                    cpu.flags |= (Cpu.FLAG_OF | Cpu.FLAG_CF);
                } else {
                    cpu.flags &= (~Cpu.FLAG_OF | ~Cpu.FLAG_CF);
                }
                cpu.writeRegisterWord(Cpu.AX, value);
            }
        }
    }

    /**
     * Signed multiplication
     *      if ah/dx is not the sign extension of the lower half
     *          set cf|of
     *      else
     *          clear cf|of
     */
    public static class Imul extends Cpu.DemuxedOpcode {
        @Override
        public void demuxed(Cpu cpu, int opcode)
        {
            boolean w = (opcode & 0b0000_0001) == 0b01;

            cpu.flags &= (~Cpu.FLAG_OF | ~Cpu.FLAG_CF);

            if (w) {
                int value = cpu.registers[Cpu.AX] * cpu.mrrModValue;
                int upper = value >>> 16;

                if ((value & Cpu.WORD_MASK_SIGN) != 0) {
                    if (upper != 0xFFFF) {
                        cpu.flags |= (Cpu.FLAG_OF | Cpu.FLAG_CF);
                    }
                } else {
                    if (upper != 0x0000) {
                        cpu.flags |= (Cpu.FLAG_OF | Cpu.FLAG_CF);
                    }
                }

                cpu.writeRegisterWord(Cpu.AX, value);
                cpu.writeRegisterWord(Cpu.DX, upper);
            }
            else {
                int value = (cpu.registers[Cpu.AX] & 0xFF) * cpu.mrrModValue;
                int upper = value >>> 8;

                if ((value & Cpu.BYTE_MASK_SIGN) != 0) {
                    if (upper != 0xFF) {
                        cpu.flags |= (Cpu.FLAG_OF | Cpu.FLAG_CF);
                    }
                } else {
                    if (upper != 0x00) {
                        cpu.flags |= (Cpu.FLAG_OF | Cpu.FLAG_CF);
                    }
                }

                cpu.writeRegisterWord(Cpu.AX, value);
            }
        }
    }

    /**
     *  AAM
     *      updates pf,sf,zf
     *      1101_0100     base
     */
    public static class Aam extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode)
        {
            int base = cpu.ipRead8();

            int al = cpu.registers[Cpu.AX] & 0xFF;
            int ah = al / base;
            al = al % base;

            Cpu.Opcode.flagsPsz8(cpu, al);

            cpu.writeRegisterWord(Cpu.AX, (ah << 8) | al);
        }
    }


}
