package at.emu.i8086.clocked.cpu;

/**
 * Multiplication opcodes
 */
public class Multiplication implements Cpu.ClockedOpcodeConfiguration {

    /*
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
    public void getClockedConfiguration(Cpu.Opcode[] registry)
    {
        // MUL
        config(registry, "1111_0110", "**_100_***", S( 80, Mul.class, "MUL", "[M8]"));
        config(registry, "1111_0111", "**_100_***", S(132, Mul.class, "MUL", "[M16]"));

        config(registry, "1111_0110", "11_100_***", S( 74, Mul.class, "MUL", "R8"), true);
        config(registry, "1111_0111", "11_100_***", S(126, Mul.class, "MUL", "R16"), true);

        // IMUL
        config(registry, "1111_0110", "**_101_***", S( 95, Imul.class, "IMUL", "[M8]"));
        config(registry, "1111_0111", "**_101_***", S(148, Imul.class, "IMUL", "[M16]"));

        config(registry, "1111_0110", "11_101_***", S( 89, Imul.class, "IMUL", "R8"), true);
        config(registry, "1111_0111", "11_101_***", S(141, Imul.class, "IMUL", "R16"), true);

        // AAM
        config(registry, "1101_0100",               S( 83, Aam.class, "AAM", ""));
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
    public static class Mul extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode)
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
    public static class Imul extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode)
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
    public static class Aam extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode)
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
