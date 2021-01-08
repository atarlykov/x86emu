package at.emu.i8086;

import java.util.Map;

/**
 * Compare instructions
 */
public class Cmp implements Cpu.OpcodeConfiguration {
    // CMP
    //    Reg/mem and reg       001110dw mrr    disp disp
    //    Imm with reg/mem      100000sw m111r  disp disp data data(s:w=01) // sw==11 --> data8 sign extended to data16
    //    Imm wth accumulator   0011110w data data(w=1) // error in intel manual @p4-24 table 4-12

    /**
     * @return configuration of opcodes
     */
    @Override
    public Map<String, ?> getConfiguration() {
        return Map.of(
                "0011_10**", CmpRmR.class,
                "1000_00**", Map.of("111", CmpRmImm.class),
                "0011_110*", CmpAccImm.class
        );
    }

    //    Reg/mem and reg       001110dw mrr    disp disp
    public static class CmpRmR extends Cpu.Opcode {
        public void execute(Cpu cpu, int opcode) {
            cpu.readModRegRm(opcode);
            boolean w = (opcode & 0b0000_0001) == 1;
            boolean d = (opcode & 0b0000_0010) == 0b10;

            if (d) {
                // reg <<- mor r/m
                Sub.flags(cpu, w, cpu.mrrRegValue, cpu.mrrModRegValue);
            } else {
                // mod r/m <<- reg
                Sub.flags(cpu, w, cpu.mrrModRegValue, cpu.mrrRegValue);
            }

/*
        boolean eq;
        if ((cpu.mrrMod ^ 0b11) == 0) {
            // cmp mrrReg ~ mrrModReg
            if (w) {
                eq = cpu.mrrRegValue == cpu.mrrModRegValue;
            } else {
                if ((cpu.mrrRm & 0b100) == 0) {
                    eq = (cpu.mrrRegValue & 0xFF) == (cpu.mrrModRegValue & 0xFF);
                } else {
                    eq = (cpu.mrrRegValue & 0xFF00) == (cpu.mrrModRegValue & 0xFF00);
                }
            }
        } else {
            // cmp regIdx ~ ea
            if (w) {
                eq = cpu.registers[cpu.mrrRegIndex] == cpu.mem16(cpu.mrrModEA);
            } else {
                if ((cpu.mrrRm & 0b100) == 0) {
                    eq = (cpu.registers[cpu.mrrRegIndex] & 0xFF) == cpu.mem8(cpu.mrrModEA);
                } else {
                    eq = (cpu.registers[cpu.mrrRegIndex] >> 8) == cpu.mem8(cpu.mrrModEA);
                }
            }
        }
*/
        }
    }


    //    Imm with reg/mem      100000sw m111r  disp disp data data(s:w=01) // (s:w=11) --> data8 sign extend to data16
    // 00 - 8/8 8
    // 01 - 16/16 16
    // 10 - 8/8 8
    // 11 - 16/16 8sx
    public static class CmpRmImm extends Cpu.DemuxedOpcode {
        @Override
        public void demuxed(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_0001) == 1;
            boolean imm16 = (opcode & 0b0000_0011) == 0b01;

            // extract immediate value,
            // size depends on s:w==0:1 bits
            int imm;
            if (imm16) {
                imm = cpu.ipRead16();
            } else {
                // we need 8->16 in case if sw=11
                imm = cpu.ipRead8WithSign() & 0xFFFF;
                //if ((opcode ^ 0b0000_0011) == 0) {
                //}
            }

            Sub.flags(cpu, w, cpu.mrrModValue, imm);
        }
    }


    //    Imm wth accumulator   0011110w data data(w=1) // error in intel manual @p4-24 table 4-12
    public static class CmpAccImm extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_0001) == 1;

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
                ax &= 0x00FF;
            }

            Sub.flags(cpu, w, ax, imm);
        }
    }
}
