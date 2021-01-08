package at.emu.i8086;

import java.util.Map;

/**
 * port io opcodes, integration with external devices
 * via port io
 */
public class Ports implements Cpu.OpcodeConfiguration {

    /**
     * IN
     *   fixed port     1110_010w   data8
     *   variable port  1110_110w
     * OUT
     *  fixed port      1110_011w   data8
     *  variable port   1110_111w
     *
     * @return configuration
     */
    @Override
    public Map<String, ?> getConfiguration() {
        return Map.of(
                "1110_010*", InFix.class,
                "1110_110*", InVar.class,
                "1110_011*", OutFix.class,
                "1110_111*", OutVar.class
        );
    }

    public static class InFix extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_0001) == 0b01;
            int port = cpu.ipRead8();
            cpu.writeRegister(w, Cpu.AX, 0);
        }
    }
    public static class InVar extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_0001) == 0b01;
            int port = cpu.registers[Cpu.DX];
            cpu.writeRegister(w, Cpu.AX, 0);
        }
    }
    public static class OutFix extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_0001) == 0b01;
            int port = cpu.ipRead8();

            if (w) {
                int value = cpu.registers[Cpu.AX];
            } else {
                int value = cpu.registers[Cpu.AX] & 0xFF;
            }
        }
    }
    public static class OutVar extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_0001) == 0b01;
            int port = cpu.registers[Cpu.DX];
            if (w) {
                int value = cpu.registers[Cpu.AX];
            } else {
                int value = cpu.registers[Cpu.AX] & 0xFF;
            }
        }
    }

}
