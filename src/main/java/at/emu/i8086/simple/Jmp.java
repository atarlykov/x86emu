package at.emu.i8086.simple;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of jump opcodes
 */
public class Jmp implements Cpu.OpcodeConfiguration, Cpu.ClockedOpcodeConfiguration
{
    /**
     *   CALL  inc ip, than save to stack [p2-44,59]
     *        direct with segment         1110_1000   ip-inc-lo   ip-inc-hi
     *        indirect with segment       1111_1111   m010r       displo      disphi
     *        direct intersegment         1001_1010   ip-lo       ip-hi       cs-lo   cs-hi
     *        indirect intersegment       1111_1111   m011r       displo      disphi
     *
     *   JMP
     *        direct with segment         1110_1001   ip-inc-lo   op-inc-hi
     *        direct with segment short   1110_1011   ip-inc-8
     *        indirect with segment       111111111   m100r       displo      disphi
     *        direct intersegment         1110_1010   ip-lo       ip-hi       cs-lo   cs-hi
     *        indirect intersegment       111111111   m101r       disp-lo     disp-hi
     *
     *   RET
     *        within segment                  1100_0011
     *        within seg adding immed to sp   1100_0010   data-lo data-hi // parameters discard
     *        intersegment                    1100_1011
     *        intersegmnet adding immed to sp 1100_1010   data-lo dat-hi  // parameters discard
     *
     *
     *   ja/jnbe        (cf or zf) == 0             0111_0111   ip-int-8
     *   jae/jnb/jnc    cf == 0                     0111_0011   ip-int-8
     *   jb/jnae/jc     cf == 1                     0111_0010   ip-int-8
     *   jbe/jna        (cf or zf) == 1             0111_0110   ip-int-8
     *   je/jz          zf == 1                     0111_0100   ip-inc-8
     *   jg/jnle        ((sf ^ of) | zf) == 0       0111_1111   ip-int-8
     *   jge/jnl        (sf ^ of) == 0              0111_1101   ip-int-8
     *   jl/jnge        (sf ^ of) == 1              0111_1100   ip-int-8
     *   jle/jng        ((sf ^ of) | zf) == 1       0111_1110   ip-int-8
     *   jne/jnz        zf == 0                     0111_0101   ip-int-8
     *   jno            of == 0                     0111_0001   ip-int-8
     *   jnp/jpo        pf == 0                     0111_1011   ip-int-8
     *   jns            sf == 0                     0111_1001   ip-int-8
     *   jo             of == 1                     0111_0000   ip-int-8
     *   jp/jpe         pf == 1                     0111_1010   ip-int-8
     *   js             sf == 1                     0111_1000   ip-int-8
     *
     *   LOOP                                       1110_0010   ip-int-8
     *   LOOPE/LOOPZ                                1110_0001   ip-int-8
     *   LOOPNE/LOOPNZ                              1110_0000   ip-int-8
     *   JCXZ                                       1110_0011   ip-int-8
     *
     * @return configuration of opcodes
     */
    @Override
    public Map<String, ?> getConfiguration() {
        Map<String, Object> tmp = new HashMap<>();

        tmp.put("1110_10*1", JmpInSeg.class);
        tmp.put("1110_1010", JmpInterSeg.class);
        tmp.put("1111_1111", Map.of(
                "100", JmpInSegIndirect.class,
                "101", JmpInterSegIndirect.class,
                "010", CallIndirectInSegment.class,
                "011", CallIndirectInterSegment.class

        ));

        tmp.put("0111_0111", JaJnbe.class);
        tmp.put("0111_0011", JaeJnbJnc.class);
        tmp.put("0111_0010", JbJnaeJc.class);
        tmp.put("0111_0110", JbeJna.class);
        tmp.put("0111_0100", JeJz.class);
        tmp.put("0111_1111", JgJnle.class);
        tmp.put("0111_1101", JgeJnl.class);
        tmp.put("0111_1100", JlJnge.class);
        tmp.put("0111_1110", JleJng.class);
        tmp.put("0111_0101", JneJnz.class);
        tmp.put("0111_0001", Jno.class);
        tmp.put("0111_1011", JnpJpo.class);
        tmp.put("0111_1001", Jns.class);
        tmp.put("0111_0000", Jo.class);
        tmp.put("0111_1010", JpJpe.class);
        tmp.put("0111_1000", Js.class);

        tmp.put("1110_1000", CallDirectInSegment.class);
        tmp.put("1001_1010", CallDirectInterSegment.class);

        tmp.put("1100_0011", RetInSegment.class);
        tmp.put("1100_0010", RetInSegmentImm.class);
        tmp.put("1100_1011", RetInterSegment.class);
        tmp.put("1100_1010", RetInterSegmentImm.class);

        tmp.put("1110_0010", Loop.class);
        tmp.put("1110_0001", LoopeLoopz.class);
        tmp.put("1110_0000", LoopneLoopnz.class);
        tmp.put("1110_0011", Jcxz.class);

        return tmp;
    }

    @Override
    public Map<String, Configuration> getClockedConfiguration()
    {
        Map<String, Configuration> c = new HashMap<>();

        config(c, "1110_10*1",               S(15, 0, JmpInSeg.class,           "JMP", "I16"));
        config(c, "1110_1010",               S(15, 0, JmpInterSeg.class,        "JMP", "I32"));
        config(c, "1111_1111", "**_100_***", S(18, 0, JmpInSegIndirect.class,   "JMP", "[M]"));
        config(c, "1111_1111", "11_100_***", S(11, 0, JmpInSegIndirect.class,   "JMP", "[R]"), true);
        config(c, "1111_1111", "**_101_***", S(24, 0, JmpInterSegIndirect.class,"JMP", "[M32]"));

        config(c, "1110_1000",               S(19, 0, CallDirectInSegment.class,    "CALL", "NEAR"));
        config(c, "1001_1010",               S(28, 0, CallDirectInterSegment.class, "CALL", "FAR"));
        config(c, "1111_1111", "**_010_***", S(21, 0, CallIndirectInSegment.class,  "CALL", "[M]"));
        config(c, "1111_1111", "11_010_***", S(16, 0, CallIndirectInSegment.class,  "CALL", "[R]"), true);
        config(c, "1111_1111", "**_011_***", S(37, 0, CallIndirectInterSegment.class, "CALL", "[M32]"));

        config(c, "1100_0011",               S( 8, 0, RetInSegment.class,         "RET", ""));
        config(c, "1100_0010",               S(12, 0, RetInSegmentImm.class,      "RET", "I16"));
        config(c, "1100_1011",               S(18, 0, RetInterSegment.class,      "RET", ""));
        config(c, "1100_1010",               S(17, 0, RetInterSegmentImm.class,   "RET", "I16"));


        config(c, "0111_0011", J(16, 4, JaeJnbJnc.class,    "JNC", ""));
        config(c, "0111_0111", J(16, 4, JaJnbe.class,       "JA", ""));
        config(c, "0111_0010", J(16, 4, JbJnaeJc.class,     "JB", ""));
        config(c, "0111_0110", J(16, 4, JbeJna.class,       "JBE", ""));
        config(c, "0111_0100", J(16, 4, JeJz.class,         "JZ", ""));
        config(c, "0111_1111", J(16, 4, JgJnle.class,       "JG", ""));
        config(c, "0111_1101", J(16, 4, JgeJnl.class,       "JGE", ""));
        config(c, "0111_1100", J(16, 4, JlJnge.class,       "JL", ""));
        config(c, "0111_1110", J(16, 4, JleJng.class,       "JLE", ""));
        config(c, "0111_0101", J(16, 4, JneJnz.class,       "JNZ", ""));
        config(c, "0111_0001", J(16, 4, Jno.class,          "JNO", ""));
        config(c, "0111_1011", J(16, 4, JnpJpo.class,       "JNP", ""));
        config(c, "0111_1001", J(16, 4, Jns.class,          "JNS", ""));
        config(c, "0111_0000", J(16, 4, Jo.class,           "JO", ""));
        config(c, "0111_1010", J(16, 4, JpJpe.class,        "JP", ""));
        config(c, "0111_1000", J(16, 4, Js.class,           "JS", ""));

        config(c, "1110_0010", J(17, 5, Loop.class,         "LOOP", ""));
        config(c, "1110_0001", J(18, 6, LoopeLoopz.class,   "LOOPZ", ""));
        config(c, "1110_0000", J(19, 5, LoopneLoopnz.class, "LOOPNZ", ""));
        config(c, "1110_0011", J(18, 8, Jcxz.class,         "JCXZ", ""));

        return c;
    }


    /**
     * Direct jump inside segment
     * direct with segment         1110_1001   ip-inc-lo   op-inc-hi                   // near label  +/-32k
     * direct with segment short   1110_1011   ip-inc-8                                // short label +/-127
     */
    public static class JmpInSeg extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            boolean near = (opcode & 0b0000_0010) == 0;

            if (near) {
                int unsigned = cpu.ipRead16();
                cpu.ip = (cpu.ip + unsigned) & 0xFFFF;
            } else {
                // short label, +-127
                int unsigned = cpu.ipRead8WithSign();
                cpu.ip += (byte) unsigned;
            }
        }
    }

    /**
     * Direct jump inside segment
     * indirect with segment       111111111   m100r       displo      disphi          // ip <- reg, ip <- [disp]
     */
    public static class JmpInSegIndirect extends Cpu.DemuxedOpcode {
        @Override
        public void demuxed(Cpu cpu, int opcode) {
            cpu.ip = cpu.mrrModValue;
        }
    }

    /**
     * Direct inter segment jump
     * direct intersegment         1110_1010   ip-lo       ip-hi       cs-lo   cs-hi   //
     */
    public static class JmpInterSeg extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            int ip = cpu.ipRead16();
            int cs = cpu.ipRead16();

            cpu.segments[Cpu.CS] = cs;
            cpu.ip = ip;
        }
    }

    /**
     * Direct inter segment jump
     * indirect intersegment       111111111   m101r       disp-lo     disp-hi         // jmp to [*(mrr)] which is ip:cs
     */
    public static class JmpInterSegIndirect extends Cpu.DemuxedOpcode {
        @Override
        public void demuxed(Cpu cpu, int opcode) {
            // depends on mode, it's [reg] or [displacement]
            cpu.segments[Cpu.CS] = cpu.mread16(cpu.mrrModEA + 2);
            cpu.ip = cpu.mrrModValue;
        }
    }


    // ja/jnbe  (cf or zf) == 0             0111_0111   ip-int-8
    // jae/jnb/jnc  cf == 0                 0111_0011   ip-int-8
    // jb/jnae/jc  cf == 1                  0111_0010   ip-int-8
    // jbe/jna  (cf or zf) == 1             0111_0110   ip-int-8
    // je/jz    zf == 1                     0111_0100   ip-inc-8
    // jg/jnle  ((sf ^ of) | zf) == 0       0111_1111   ip-int-8
    // jge/jnl  (sf ^ of) == 0              0111_1101   ip-int-8
    // jl/jnge  (sf ^ of) == 1              0111_1100   ip-int-8
    // jle/jng  ((sf ^ of) | zf) == 1       0111_1110   ip-int-8
    // jne/jnz  zf == 0                     0111_0101   ip-int-8
    // jno      of == 0                     0111_0001   ip-int-8
    // jnp/jpo  pf == 0                     0111_1011   ip-int-8
    // jns      sf == 0                     0111_1001   ip-int-8
    // jo       of == 1                     0111_0000   ip-int-8
    // jp/jpe   pf == 1                     0111_1010   ip-int-8
    // js       sf == 1                     0111_1000   ip-int-8

    public static class JaJnbe extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            byte delta = (byte) cpu.ipRead8WithSign();
            if ( (((cpu.flags >> Cpu.FLAG_ZF_POS) | (cpu.flags >> Cpu.FLAG_CF_POS)) & 0x01) == 0) {
                cpu.ip = (cpu.ip + delta) & 0xFFFF;
            }
        }
    }

    public static class JaeJnbJnc extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            byte delta = (byte) cpu.ipRead8WithSign();
            if ((cpu.flags & Cpu.FLAG_CF) == 0) {
                cpu.ip = (cpu.ip + delta) & 0xFFFF;
            }
        }
    }

    public static class JbJnaeJc extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            byte delta = (byte) cpu.ipRead8WithSign();
            if ((cpu.flags & Cpu.FLAG_CF) != 0) {
                cpu.ip = (cpu.ip + delta) & 0xFFFF;
            }
        }
    }

    public static class JbeJna extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            byte delta = (byte) cpu.ipRead8WithSign();
            if ( (( (cpu.flags >> Cpu.FLAG_ZF_POS) | (cpu.flags >> Cpu.FLAG_CF_POS) ) & 0x01) != 0) {
                cpu.ip = (cpu.ip + delta) & 0xFFFF;
            }
        }
    }

    public static class JeJz extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            byte delta = (byte) cpu.ipRead8WithSign();
            if ((cpu.flags & Cpu.FLAG_ZF) != 0) {
                cpu.ip = (cpu.ip + delta) & 0xFFFF;
            }
        }
    }

    public static class JgJnle extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            byte delta = (byte) cpu.ipRead8WithSign();
            //((sf ^ of) | zf) == 0
            if ((
                    (
                            ((cpu.flags >> Cpu.FLAG_SF_POS) ^ (cpu.flags >> Cpu.FLAG_OF_POS)) |
                                    (cpu.flags >> Cpu.FLAG_ZF_POS)
                    ) & 1) == 0) {
                cpu.ip = (cpu.ip + delta) & 0xFFFF;
            }
        }
    }

    public static class JgeJnl extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            byte delta = (byte) cpu.ipRead8WithSign();
            //(sf ^ of) == 0
            if (
                    (((cpu.flags >> Cpu.FLAG_SF_POS) ^ (cpu.flags >> Cpu.FLAG_OF_POS)) & 1) == 0
            ) {
                cpu.ip = (cpu.ip + delta) & 0xFFFF;
            }
        }
    }

    public static class JlJnge extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            byte delta = (byte) cpu.ipRead8WithSign();
            //(sf ^ of) == 1
            if (
                    (((cpu.flags >> Cpu.FLAG_SF_POS) ^ (cpu.flags >> Cpu.FLAG_OF_POS)) & 1) != 0
            ) {
                cpu.ip = (cpu.ip + delta) & 0xFFFF;
            }
        }
    }

    public static class JleJng extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            byte delta = (byte) cpu.ipRead8WithSign();
            // ((sf ^ of) | zf) == 1
            if ((
                    (
                            ((cpu.flags >> Cpu.FLAG_SF_POS) ^ (cpu.flags >> Cpu.FLAG_OF_POS)) |
                                    (cpu.flags >> Cpu.FLAG_ZF_POS)
                    ) & 1) != 0) {
                cpu.ip = (cpu.ip + delta) & 0xFFFF;
            }
        }
    }

    public static class JneJnz extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            byte delta = (byte) cpu.ipRead8WithSign();
            if ((cpu.flags & Cpu.FLAG_ZF) == 0) {
                cpu.ip = (cpu.ip + delta) & 0xFFFF;
            }
        }
    }

    public static class Jno extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            byte delta = (byte) cpu.ipRead8WithSign();
            if ((cpu.flags & Cpu.FLAG_OF) == 0) {
                cpu.ip = (cpu.ip + delta) & 0xFFFF;
            }
        }
    }

    public static class JnpJpo extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            byte delta = (byte) cpu.ipRead8WithSign();
            if ((cpu.flags & Cpu.FLAG_PF) == 0) {
                cpu.ip = (cpu.ip + delta) & 0xFFFF;
            }
        }
    }

    public static class Jns extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            byte delta = (byte) cpu.ipRead8WithSign();
            if ((cpu.flags & Cpu.FLAG_SF) == 0) {
                cpu.ip = (cpu.ip + delta) & 0xFFFF;
            }
        }
    }

    public static class Jo extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            byte delta = (byte) cpu.ipRead8WithSign();
            if ((cpu.flags & Cpu.FLAG_OF) != 0) {
                cpu.ip = (cpu.ip + delta) & 0xFFFF;
            }
        }
    }

    public static class JpJpe extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            byte delta = (byte) cpu.ipRead8WithSign();
            if ((cpu.flags & Cpu.FLAG_PF) != 0) {
                cpu.ip = (cpu.ip + delta) & 0xFFFF;
            }
        }
    }

    public static class Js extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            byte delta = (byte) cpu.ipRead8WithSign();
            if ((cpu.flags & Cpu.FLAG_SF) != 0) {
                cpu.ip = (cpu.ip + delta) & 0xFFFF;
            }
        }
    }


    public static class CallDirectInSegment extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            int delta = cpu.ipRead16();
            cpu.push16(cpu.ip);
            cpu.ip = (cpu.ip + delta) & 0xFFFF;
        }
    }

    public static class CallIndirectInSegment extends Cpu.DemuxedOpcode {
        @Override
        public void demuxed(Cpu cpu, int opcode) {
            cpu.push16(cpu.ip);
            // due to manual: offset is obtained from memory word or 16 bit register
            // referenced in the instruction and replaces ip
            cpu.ip = cpu.mrrModValue & 0xFFFF;
            // todo: reg vs [reg], displacement vs [displacement]
            throw new RuntimeException("todo: reg vs [reg], displacement vs [displacement]");
        }
    }

    public static class CallDirectInterSegment extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            int ip = cpu.ipRead16();
            int cs = cpu.ipRead16();

            cpu.push16(cpu.segments[Cpu.CS]);
            cpu.segments[Cpu.CS] = cs;
            cpu.push16(cpu.ip);
            cpu.ip = ip & 0xFFFF;
        }
    }

    public static class CallIndirectInterSegment extends Cpu.DemuxedOpcode {
        @Override
        public void demuxed(Cpu cpu, int opcode) {
            // manual: push cs, push ip, double word memory pointed by the instruction is ip:cs
            // depends on mode, it's [reg] or [displacement]

            int cs = cpu.mread16(cpu.mrrModEA + 2);
            cpu.push16(cpu.segments[Cpu.CS]);
            cpu.segments[Cpu.CS] = cs;

            cpu.push16(cpu.ip);
            int ip;
            if ((cpu.mrrMod ^ 0b11) == 0) {
                // mod r/m is a register, use it as ds:[reg]
                ip = cpu.mread16(cpu.mrrModValue);
            } else {
                // mod r/m is memory, use as ds:[displacement] which == mrrModValue
                //ip = cpu.mem(true, Cpu.DS, cpu.mrrModEA);
                ip = cpu.mrrModValue;
            }
            cpu.ip = ip & 0xFFFF;

            //todo:  check all (not only call) indirects for mrrModValue (EAValue) used as ip without check for mode
            throw new RuntimeException("todo: reg vs [reg], displacement vs [displacement]");
        }
    }


    public static class RetInSegment extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            cpu.ip = cpu.pop16();
        }
    }

    public static class RetInSegmentImm extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            int offset = cpu.ipRead16();
            cpu.ip = (cpu.pop16() + offset) & 0xFFFF;
        }
    }

    public static class RetInterSegment extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            cpu.ip = cpu.pop16();
            cpu.registers[Cpu.CS] = cpu.pop16();
        }
    }

    public static class RetInterSegmentImm extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            int offset = cpu.ipRead16();
            cpu.ip = (cpu.pop16() + offset) & 0xFFFF;
            cpu.registers[Cpu.CS] = cpu.pop16();
        }
    }

    /**
     * Loop, decrement cx and jump of not zero
     */
    public static class Loop extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode)
        {
            int offset = cpu.ipRead8WithSign();

            int cx = cpu.registers[Cpu.CX] - 1;
            cpu.writeRegisterWord(Cpu.CX, cx);

            if (cx != 0) {
                cpu.ip = (cpu.ip + offset) & 0xFFFF;
            }
        }
    }

    /**
     * Loope/loopz, decrement cx and jump of not zero and ZF is set
     */
    public static class LoopeLoopz extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode)
        {
            int offset = cpu.ipRead8WithSign();

            int cx = cpu.registers[Cpu.CX] - 1;
            cpu.writeRegisterWord(Cpu.CX, cx);

            if ((cx != 0) && ((cpu.flags & Cpu.FLAG_ZF) != 0)) {
                cpu.ip = (cpu.ip + offset) & 0xFFFF;
            }
        }
    }

    /**
     * Loopne/loopnz, decrement cx and jump of not zero and ZF is not set
     */
    public static class LoopneLoopnz extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode)
        {
            int offset = cpu.ipRead8WithSign();

            int cx = cpu.registers[Cpu.CX] - 1;
            cpu.writeRegisterWord(Cpu.CX, cx);

            if ((cx != 0) && ((cpu.flags & Cpu.FLAG_ZF) == 0)) {
                cpu.ip = (cpu.ip + offset) & 0xFFFF;
            }
        }
    }

    /**
     * Jump if cx is zero
     */
    public static class Jcxz extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode)
        {
            int offset = cpu.ipRead8WithSign();
            int cx = cpu.registers[Cpu.CX];
            if (cx == 0) {
                cpu.ip = (cpu.ip + offset) & 0xFFFF;
            }
        }
    }

}
