package at.emu.i8086;

import java.util.HashMap;
import java.util.Map;

/**
 * Compare instructions
 */
public class Cmp implements Cpu.OpcodeConfiguration, Cpu.ClockedOpcodeConfiguration
{
    /*
     *  CMP
     *      Reg/mem and reg       001110dw mrr      disp    disp
     *      Imm with reg/mem      100000sw m111r    disp    disp data data(s:w=01) // sw==11 --> data8 sign extended to data16
     *      Imm wth accumulator   0011110w data     data(w=1) // error in intel manual @p4-24 table 4-12
     *
     */

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

    @Override
    public Map<String, Configuration> getClockedConfiguration()
    {
        Map<String, Configuration> c = new HashMap<>();

        // CMP
        config(c, "0011_100*", "**_***_***", S( 9, CmpRmR.class, "CMP", "[M] <-  R"));
        config(c, "0011_101*", "**_***_***", S( 9, CmpRmR.class, "CMP", " R  <- [M]"));
        config(c, "0011_10**", "11_***_***", S( 3, CmpRmR.class, "CMP", " R  <-  R"), true);

        config(c, "0011_110*",               S( 4, CmpAccImm.class, "CMP", " A   <- I"));
        config(c, "1000_00**", "**_111_***", S(10, CmpRmImm.class,  "CMP", "[M]  <- I"));
        config(c, "1000_00**", "11_111_***", S( 4, CmpRmImm.class,  "CMP", " Rm  <- I"), true);

        return c;
    }



    /**
     *  RegMem with Reg
     */
    public static class CmpRmR extends Cpu.Opcode
    {
        public void execute(Cpu cpu, int opcode)
        {
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
        }
    }



    /**
     *
     * Imm with RegMem
     * (s:w=11) ->> data8 sign extend to data16
     */
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
            }

            Sub.flags(cpu, w, cpu.mrrModValue, imm);
        }
    }


    /**
     * Imm wth Accumulator
     */
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
