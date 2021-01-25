package at.emu.i8086.clocked.cpu;

import java.util.HashMap;
import java.util.Map;

/**
 * Division opcodes
 */
public class Division implements Cpu.ClockedOpcodeConfiguration {

    /*
     *  DIV unsigned div
     *          1111_011w m110r     disp     disp
     *      clocks: R8 -> 80..90
     *              R16 -> 144..162
     *              M8 -> 88..96 + EA
     *              M16 -> 150..168 + EA
     *
     *  IDIV signed div
     *          1111_011w m111r     disp     disp
     *      clocks: R8 ->  101..112
     *              R16 -> 165..184
     *              M8 ->  107..118 + EA
     *              M16 -> 171..190 + EA
     *
     *  AAD updates pf,sf,zf
     *          1101_0101 data8
     *
     *
     */

    @Override
    public void getClockedConfiguration(Cpu.Opcode[] registry)
    {
        // DIV
        config(registry, "1111_0110", "**_110_***", S( 92, Div.class, "DIV", "[M8]"));
        config(registry, "1111_0111", "**_110_***", S(159, Div.class, "DIV", "[M16]"));

        config(registry, "1111_0110", "11_110_***", S( 85, Div.class, "DIV", "R8"), true);
        config(registry, "1111_0111", "11_110_***", S(153, Div.class, "DIV", "R16"), true);

        // IDIV
        config(registry, "1111_0110", "**_111_***", S(113, Idiv.class, "IDIV", "[M8]"));
        config(registry, "1111_0111", "**_111_***", S(181, Idiv.class, "IDIV", "[M16]"));

        config(registry, "1111_0110", "11_111_***", S(107, Idiv.class, "IDIV", "R8"), true);
        config(registry, "1111_0111", "11_111_***", S(175, Idiv.class, "IDIV", "R16"), true);

        // AAD
        config(registry, "1101_0101",               S( 60, Aad.class, "AAD", ""));
    }

    /**
     * Unsigned division
     *      (ah~reminder, al~quotient) <<- ax / source8
     *      (dx~reminder, ax~quotient) <<- dx:ax / source16
     *      if quotient exceeds 0xFF/0xFFFF (x/0) ->>
     *          - interrupt 0 is generated
     *          - quotient & reminder are undefined
     *      af,cf,of,pf,sf,zf are undefined
     */
    public static class Div extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode)
        {
            boolean w = (opcode & 0b0000_0001) == 0b01;

            if (cpu.mrrModValue == 0) {
                cpu.interrupt(Cpu.INT_0_DIVIDE_ERROR);
                return;
            }

            if (w) {
                int dividend = (cpu.registers[Cpu.DX] << 16) | cpu.registers[Cpu.AX];
                int quotient = dividend / cpu.mrrModValue;
                int reminder = dividend % cpu.mrrModValue;
                if (0xFFFF < quotient) {
                    cpu.interrupt(Cpu.INT_0_DIVIDE_ERROR);
                    return;
                }
                cpu.writeRegisterWord(Cpu.AX, quotient);
                cpu.writeRegisterWord(Cpu.DX, reminder);
            }
            else {
                int dividend = cpu.registers[Cpu.AX];
                int quotient = dividend / cpu.mrrModValue;
                int reminder = dividend % cpu.mrrModValue;
                if (0xFF < quotient) {
                    cpu.interrupt(Cpu.INT_0_DIVIDE_ERROR);
                    return;
                }
                cpu.writeRegisterWord(Cpu.AX, (reminder << 8) | (quotient));
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
    public static class Idiv extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode)
        {
            boolean w = (opcode & 0b0000_0001) == 0b01;

            if (cpu.mrrModValue == 0) {
                cpu.interrupt(Cpu.INT_0_DIVIDE_ERROR);
                return;
            }

            if (w) {
                int dividend = (cpu.registers[Cpu.DX] << 16) | cpu.registers[Cpu.AX];
                int quotient = dividend / cpu.mrrModValue;
                int reminder = dividend % cpu.mrrModValue;

                if ((quotient < 0xFFFF8000) || (0x7FFF < quotient)) {
                    cpu.interrupt(Cpu.INT_0_DIVIDE_ERROR);
                    return;
                }

                cpu.writeRegisterWord(Cpu.AX, quotient);
                cpu.writeRegisterWord(Cpu.DX, reminder);
            }
            else {
                int dividend = cpu.registers[Cpu.AX];
                int quotient = dividend / cpu.mrrModValue;
                int reminder = dividend % cpu.mrrModValue;
                if ((quotient < 0xFFFFFF80) || (0xFF < quotient)) {
                    cpu.interrupt(Cpu.INT_0_DIVIDE_ERROR);
                    return;
                }
                cpu.writeRegisterWord(Cpu.AX, (reminder << 8) | (quotient));
            }
        }
    }

    /**
     *  AAD updates pf,sf,zf
     */
    public static class Aad extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode)
        {
            int base = cpu.ipRead8();

            int ax = cpu.registers[Cpu.AX];
            int al = ax & 0x00FF;
            int ah = ax & 0xFF00;

            al = al + (ah * base) & 0xFF;
            Cpu.Opcode.flagsPsz8(cpu, al);

            cpu.writeRegisterWord(Cpu.AX, al);
        }
    }


}
