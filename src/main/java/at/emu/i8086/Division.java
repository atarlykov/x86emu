package at.emu.i8086;

import java.util.Map;

/**
 * Division opcodes
 */
public class Division implements Cpu.OpcodeConfiguration {

    /**
     *  DIV unsigned div
     *          1111_011w m110r     disp     disp
     *
     *  IDIV signed div
     *          1111_011w m111r     disp     disp
     *
     *  AAD updates pf,sf,zf
     *          1101_0101 data8
     *
     *
     * @return configuration of opcodes
     */
    @Override
    public Map<String, ?> getConfiguration() {
        return Map.of(
                "1111_011*", Map.of(
                        "110", Div.class,
                        "111", Idiv.class
                ),
                "1101_0101", Aad.class
        );
    }


    /**
     * Unsigned division
     *      (ah~reminder, al~quotient) <- ax / source8
     *      (dx~reminder, ax~quotient) <- dx:ax / source16
     *      if quotient exceeds 0xFF/0xFFFF (x/0) --> interrupt 0 is generated, q/r undefined
     *      af,cf,of,pf,sf,zf are undefined
     */
    public static class Div extends Cpu.DemuxedOpcode {
        @Override
        public void demuxed(Cpu cpu, int opcode)
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
    public static class Idiv extends Cpu.DemuxedOpcode {
        @Override
        public void demuxed(Cpu cpu, int opcode)
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
    public static class Aad extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode)
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
