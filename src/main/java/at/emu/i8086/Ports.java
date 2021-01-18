package at.emu.i8086;

import java.util.Map;

/**
 * port io opcodes, integration with external devices
 * via port io
 */
public class Ports implements Cpu.OpcodeConfiguration {

    /**
     * IN
     *   imm port       1110_010w   data8
     *   port in dx     1110_110w
     * OUT
     *  imm port        1110_011w   data8
     *  port in dx      1110_111w
     *
     * @return configuration
     */
    @Override
    public Map<String, ?> getConfiguration() {
        return Map.of(
                "1110_010*", InImm.class,
                "1110_110*", InDx.class,
                "1110_011*", OutImm.class,
                "1110_111*", OutDx.class
        );
    }

    public static class InImm extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_0001) == 0b01;
            int port = cpu.ipRead8();
            int ax = cpu.pin(w, port);
            cpu.writeRegister(w, Cpu.AX, ax);
        }
    }
    public static class InDx extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_0001) == 0b01;
            int port = cpu.registers[Cpu.DX];
            int ax = cpu.pin(w, port);
            cpu.writeRegister(w, Cpu.AX, ax);
        }
    }
    public static class OutImm extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_0001) == 0b01;
            int port = cpu.ipRead8();
            // word ->> 0, !word ->> 1
            int wm = (opcode & 0b0000_0001) ^ 0b0001;
            int ax = cpu.registers[Cpu.AX] & (0xFFFF >> (wm << 3));
            cpu.pout(w, port, ax);
        }
    }
    public static class OutDx extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_0001) == 0b01;
            int port = cpu.registers[Cpu.DX];
            // word ->> 0, !word ->> 1
            int wm = (opcode & 0b0000_0001) ^ 0b0001;
            int ax = cpu.registers[Cpu.AX] & (0xFFFF >> (wm << 3));
            cpu.pout(w, port, ax);
        }
    }

}
