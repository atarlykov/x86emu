package at.emu.i8086;

import java.util.HashMap;
import java.util.Map;

/**
 * Logic operations opcodes
 */
public class Logic implements Cpu.OpcodeConfiguration, Cpu.ClockedOpcodeConfiguration
{
    /*
     logical:
      of, cf  - cleared
      af      - undefined
      sf,zf,pf- reflect result

     shift/rotate
          v=0     shift/rotate count = 1
          v=1     shift/rotate count in CL
      flags
          pf,sf,zf - normally as in logical (reflect result
          af - undefined
          of - multi bit, undefined
             - single, set if sign bit changed, cleared otherwise
          cf - last bit shifted out

     Shl/Sal          1101_00vw   m100r   disp-lo disp-hi
     Shr              1101_00vw   m101r   disp-lo disp-hi
     Sar              1101_00vw   m111r   disp-lo disp-hi
     rotates affects only CF and OF (multi bit - undefined)
     Rol              1101_00vw   m000r   disp-lo disp-hi
     Ror              1101_00vw   m001r   disp-lo disp-hi
     Rcl              1101_00vw   m010r   disp-lo disp-hi
     Rcr              1101_00vw   m011r   disp-lo disp-hi


     Not              1111_011w   m010r   disp-lo disp-hi
          flags not affected

     And   of,cf - cleared, af - undefined, psz - usual
      R/m with reg    0010_00dw   mrr     disp-lo disp-hi
      Imm to r/m      1000_000w   m100r   disp-lo disp-hi data data(w=1)
      Imm to accum    0010_010w   data    data(w=1)
      Imm to r/m sign extend
                      1000_0011   m100r disp disp data data(w)   // from opcodes list
      and ax, ffff  ---> 83 e0 ff (intel manual: not used)
      and ax, 11    ---> 83 e0 11 (intel manual: not used)
      and ax, -127  ---> 83 e0 80 (intel manual: not used)
      and bx, -128  ---> 83 e3 80
      and al, ff         24 ff
      and ax, ff         25 ff 00
      and ax, 1234       25 32 12

     Test  of,cf - cleared, af - undefined, psz - usual
      R/m and reg     1000_010w   mrr     disp-lo disp-hi         // error in manual: 0001_00dw
      Imm and r/m     1111_011w   m000r   disp-lo disp-hi data    data(w=1)
      Imm and accum   1010_100w   data

     Or  of,cf - cleared, af - undefined, psz - usual
      R/m and reg     0000_10dw   mrr     disp-lo disp-hi
      Imm to r/m      1000_000w   m001r   disp-lo disp-hi data    data(w=1)
      Imm to accum    0000_110w   data    data(w=1)
     --> compile or: ax,ffff --> 83 c8 ff (not used)
                 or: cx,ffff --> 83 c9 ff (not used
                 or bl, 0xff     80 cb ff
                 or bx, 0xff     81 cb ff
                 or bl, 0xffff   80 cb ff
                 or bx, 0xffff   83 cb ff !!!

     Xor  of,cf - cleared, af - undefined, psz - usual
      R/m and reg     0011_00dw   mrr     disp-lo disp-hi
      Imm to r/m      0011_010w   data    disp-lo disp-hi data    data(w=1)    !!! trash !!!
      Imm to r/m      1000_000w   m110r   disp-lo disp-hi data    data(w=1)    !!! from opcodes list !!!
      Imm to accum    0011_010w   data    data(w=1)
      Imm to r/m sign extend
                      1000_0011   m110r disp disp data data(w)   // from opcodes list
     compiler
      xor al, ff      34 ff
      xor ax, ff      35 ff 00
      xor ax, f00f    35 0f f0
      xor bl, ff      80 f3 ff
      xor bx, ff      81 f3 ff 00
      xor ax, 00      83 f0 00
      xor ax, ffff    83 f0 ff (not used, intel manual)
      xor cx, ffff    83 f1 ff
      xor bx, -128    83 f3 80
      xor ax, -128    83 f0 80
    */


    @Override
    public Map<String, ?> getConfiguration()
    {
        Map<String, Object> tmp = new HashMap<>();
        tmp.put("1101_00**", Map.of(
                "100", ShlSal.class,
                "101", Shr.class,
                "111", Sar.class,

                "000", Rol.class,
                "001", Ror.class,
                "010", Rcl.class,
                "011", Rcr.class));

        tmp.put("0010_00**", AndRmR.class);
        tmp.put("0010_010*", AndAccImm.class);

        tmp.put("1000_010*", TestRmR.class);
        tmp.put("1010_100*", TestAccImm.class);

        tmp.put("0011_00**", XorRmR.class);
        tmp.put("0011_010*", XorAccImm.class);

        tmp.put("0000_10**", OrRmR.class);
        tmp.put("0000_110*", OrAccImm.class);


        tmp.put("1000_000*",
                Map.of(
                        "100", AndRmImm.class,
                        "110", XorRmImm.class,
                        "001", OrRmImm.class));
        tmp.put("1111_011*",
                Map.of(
                        "010", Not.class,
                        "000", TestRmImm.class));

        tmp.put("1000_0011",
                Map.of(
                        "100", AndRmImmSignExt.class,  // ?
                        "110", XorRmImmSignExt.class,  // ?
                        "001", OrRmImmSignExt.class)); // ?

        return tmp;
    }

    @Override
    public Map<String, Configuration> getClockedConfiguration()
    {
        Map<String, Configuration> c = new HashMap<>();

        // All variants (XXX X,CL) take 4 additional clocks/bit

        // SHL
        config(c, "1101_001*", "**_100_***", C(20, 4, ShlSal.class,   "SHL", "[M],CL"));
        config(c, "1101_001*", "11_100_***", C(15, 4, ShlSal.class,   "SHL", "R,CL"), true);
        config(c, "1101_000*", "**_100_***", S( 8, 0, ShlSal.class,   "SHL", "[M],1"));
        config(c, "1101_000*", "11_100_***", S( 2, 0, ShlSal.class,   "SHL", "R,1"), true);
        // SHR
        config(c, "1101_001*", "**_101_***", C(20, 4, Shr.class,   "SHR", "[M],CL"));
        config(c, "1101_001*", "11_101_***", C(15, 4, Shr.class,   "SHR", "R,CL"), true);
        config(c, "1101_000*", "**_101_***", S( 8, 0, Shr.class,   "SHR", "[M],1"));
        config(c, "1101_000*", "11_101_***", S( 2, 0, Shr.class,   "SHR", "R,1"), true);
        // SAR
        config(c, "1101_001*", "**_111_***", C(20, 4, Sar.class,   "SAR", "[M],CL"));
        config(c, "1101_001*", "11_111_***", C(15, 4, Sar.class,   "SAR", "R,CL"), true);
        config(c, "1101_000*", "**_111_***", S( 8, 0, Sar.class,   "SAR", "[M],1"));
        config(c, "1101_000*", "11_111_***", S( 2, 0, Sar.class,   "SAR", "R,1"), true);

        // ROL
        config(c, "1101_001*", "**_000_***", C(20, 4, Rol.class,   "ROL", "[M],CL"));
        config(c, "1101_001*", "11_000_***", C(15, 4, Rol.class,   "ROL", "R,CL"), true);
        config(c, "1101_000*", "**_000_***", S( 8, 0, Rol.class,   "ROL", "[M],1"));
        config(c, "1101_000*", "11_000_***", S( 2, 0, Rol.class,   "ROL", "R,1"), true);
        // ROR
        config(c, "1101_001*", "**_001_***", C(20, 4, Ror.class,   "ROR", "[M],CL"));
        config(c, "1101_001*", "11_001_***", C(15, 4, Ror.class,   "ROR", "R,CL"), true);
        config(c, "1101_000*", "**_001_***", S( 8, 0, Ror.class,   "ROR", "[M],1"));
        config(c, "1101_000*", "11_001_***", S( 2, 0, Ror.class,   "ROR", "R,1"), true);
        // RCL
        config(c, "1101_001*", "**_010_***", C(20, 4, Rcl.class,   "RCL", "[M],CL"));
        config(c, "1101_001*", "11_010_***", C(15, 4, Rcl.class,   "RCL", "R,CL"), true);
        config(c, "1101_000*", "**_010_***", S( 8, 0, Rcl.class,   "RCL", "[M],1"));
        config(c, "1101_000*", "11_010_***", S( 2, 0, Rcl.class,   "RCL", "R,1"), true);
        // RCR
        config(c, "1101_001*", "**_011_***", C(20, 4, Rcr.class,   "RCR", "[M],CL"));
        config(c, "1101_001*", "11_011_***", C(15, 4, Rcr.class,   "RCR", "R,CL"), true);
        config(c, "1101_000*", "**_011_***", S( 8, 0, Rcr.class,   "RCR", "[M],1"));
        config(c, "1101_000*", "11_011_***", S( 2, 0, Rcr.class,   "RCR", "R,1"), true);


        // AND
        config(c, "0010_001*", "**_***_***", S( 9, AndRmR.class,   "AND", "R, M"));
        config(c, "0010_001*", "11_***_***", S( 3, AndRmR.class,   "AND", "R, R"), true);
        config(c, "0010_000*", "**_***_***", S(16, AndRmR.class,   "AND", "M, R"));
        config(c, "0010_000*", "11_***_***", S( 3, AndRmR.class,   "AND", "R, R"), true);

        config(c, "0010_010*",               S( 4, AndAccImm.class,         "AND", "A, I"));
        config(c, "1000_000*", "**_100_***",  S(17, AndRmImm.class,         "AND", "M, I"));
        config(c, "1000_000*", "11_100_***",  S( 4, AndRmImm.class,         "AND", "R, I"), true);
        config(c, "1000_0011", "**_100_***",  S( 4, AndRmImmSignExt.class,  "AND", "A, Ix"));

        // TEST
        config(c, "1000_010*", "**_***_***", S( 9, TestRmR.class,   "TEST", "R, M"));
        config(c, "1000_010*", "11_***_***", S( 3, TestRmR.class,   "TEST", "R, R"), true);

        config(c, "1010_100*",               S( 4, TestAccImm.class,         "TEST", "A, I"));
        config(c, "1111_011*", "**_000_***",  S(11, TestRmImm.class,          "TEST", "M, I"));
        config(c, "1111_011*", "11_000_***",  S( 5, TestRmImm.class,          "TEST", "R, I"), true);
        //config(c, "1000_0011", "**_000_***",  S( 4, TestRmImmSignExt.class,   "TEST", "A, I"));

        // XOR
        config(c, "0011_001*", "**_***_***", S( 9, XorRmR.class,   "XOR", "R, M"));
        config(c, "0011_001*", "11_***_***", S( 3, XorRmR.class,   "XOR", "R, R"), true);
        config(c, "0011_000*", "**_***_***", S(16, XorRmR.class,   "XOR", "M, R"));
        config(c, "0011_000*", "11_***_***", S( 3, XorRmR.class,   "XOR", "R, R"), true);

        config(c, "0011_010*",                S( 4, XorAccImm.class,         "XOR", "A, I"));
        config(c, "1000_000*", "**_110_***",  S(17, XorRmImm.class,          "XOR", "M, I"));
        config(c, "1000_000*", "11_110_***",  S( 4, XorRmImm.class,          "XOR", "R, I"), true);
        config(c, "1000_0011", "**_110_***",  S( 4, XorRmImmSignExt.class,   "XOR", "A, Ix"));

        // OR
        config(c, "0000_101*", "**_***_***", S( 9, OrRmR.class,   "OR", "R, M"));
        config(c, "0000_101*", "11_***_***", S( 3, OrRmR.class,   "OR", "R, R"), true);
        config(c, "0000_100*", "**_***_***", S(16, OrRmR.class,   "OR", "M, R"));
        config(c, "0000_100*", "11_***_***", S( 3, OrRmR.class,   "OR", "R, R"), true);

        config(c, "0000_110*",                S( 4, OrAccImm.class,         "OR", "A, I"));
        config(c, "1000_000*", "**_001_***",  S(17, OrRmImm.class,          "OR", "M, I"));
        config(c, "1000_000*", "11_001_***",  S( 4, OrRmImm.class,          "OR", "R, I"), true);
        config(c, "1000_0011", "**_001_***",  S( 4, OrRmImmSignExt.class,   "OR", "A, Ix"));

        // NOT
        config(c, "1111_011*", "**_010_***",  S(16, Not.class,          "NOT", "[M]]"));
        config(c, "1111_011*", "11_010_***",  S( 3, Not.class,          "NOT", "R"), true);

        return c;
    }


    public static class Not extends Cpu.DemuxedOpcode {
        @Override
        public void demuxed(Cpu cpu, int opcode)
        {
            cpu.writeByModRegRm(true, ~cpu.mrrModValue);
        }
    }

    public static class ShlSal extends Cpu.DemuxedOpcode {
        @Override
        public void demuxed(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_0001) != 0b00;
            boolean v = (opcode & 0b0000_0010) != 0b00;

            if (v) {
                // CL bit shift
                int count = cpu.registers[Cpu.CX] & 0x00FF;
                int value = cpu.mrrModValue << count;

                if (w) {
                    //          (clear CF
                    cpu.flags = (cpu.flags & ~Cpu.FLAG_CF)
                            // (shifted out bit to CF
                            | ((value & Cpu.WORD_MASK_CARRY) >> (Cpu.WORD_POS_CARRY - Cpu.FLAG_CF_POS));

                    Cpu.Opcode.flagsPsz16(cpu, value);
                } else {
                    // byte mode
                    //          (clear CF
                    cpu.flags = (cpu.flags & ~Cpu.FLAG_CF)
                            // (shifted out bit to CF
                            | ((value & Cpu.BYTE_MASK_CARRY) >> (Cpu.BYTE_POS_CARRY - Cpu.FLAG_CF_POS));

                    Cpu.Opcode.flagsPsz8(cpu, value);
                }
                // write result to reg/mem pointed by mrr
                cpu.writeByModRegRm(w, value);
            } else {
                // one bit shift, sign bit is shifted out
                int value = cpu.mrrModValue << 1;

                if (w) {
                    //          (clear CF and OF
                    cpu.flags = (cpu.flags & (~Cpu.FLAG_CF & ~Cpu.FLAG_OF))
                            // (shifted out bit (sign) to CF
                            | ((cpu.mrrModValue & Cpu.WORD_MASK_SIGN) >> (Cpu.WORD_POS_SIGN - Cpu.FLAG_CF_POS))
                            // (two upper bit check for sign change to OF
                            | ((((cpu.mrrModValue >> Cpu.WORD_POS_SIGN) ^ (cpu.mrrModValue >> (Cpu.WORD_POS_SIGN - 1))) & 0x01) << Cpu.FLAG_OF_POS);

                    Cpu.Opcode.flagsPsz16(cpu, value);
                } else {
                    // byte mode (1 bit shift)
                    //          (clear CF and OF
                    cpu.flags = (cpu.flags & (~Cpu.FLAG_CF & ~Cpu.FLAG_OF))
                            // (shifted out bit (sign) to CF
                            | ((cpu.mrrModValue & Cpu.BYTE_MASK_SIGN) >> (Cpu.BYTE_POS_SIGN - Cpu.FLAG_CF_POS))
                            // (two upper bit check for sign change
                            | (((cpu.mrrModValue >> Cpu.BYTE_POS_SIGN) ^ (cpu.mrrModValue >> (Cpu.BYTE_POS_SIGN - 1)) & 0x01) << Cpu.FLAG_OF_POS);

                    Cpu.Opcode.flagsPsz8(cpu, value);
                }
                // write result to reg/mem pointed by mrr
                cpu.writeByModRegRm(w, value);
            }
        }
    }

    public static class Shr extends Cpu.DemuxedOpcode {
        @Override
        public void demuxed(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_0001) == 0b01;
            boolean v = (opcode & 0b0000_0010) == 0b10;

            int value;
            if (v) {
                int count = cpu.registers[Cpu.CX] & 0x00FF;
                value = cpu.mrrModValue >> count;

                //          (clear CF
                cpu.flags = (cpu.flags & ~Cpu.FLAG_CF)
                        // (shifted out bit (sign) to CF
                        | (((cpu.mrrModValue >> (count - 1)) & 0x01) << Cpu.FLAG_CF_POS);
            } else {
                // one bit shift, sign bit is shifted out
                value = cpu.mrrModValue >> 1;

                //          (clear CF and OF
                cpu.flags = (cpu.flags & (~Cpu.FLAG_CF & ~Cpu.FLAG_OF))
                        // (shifted out bit (sign) to CF
                        | ((cpu.mrrModValue & 0x01) << Cpu.FLAG_CF_POS)
                        // (two upper bit check for sign change
                        | ((cpu.mrrModValue ^ (cpu.mrrModValue >> 1) & 0x01) << Cpu.FLAG_OF_POS);
            }

            Cpu.Opcode.flagsPsz(cpu, w, value);
            // write result to reg/mem pointed by mrr
            cpu.writeByModRegRm(w, value);
        }
    }

    public static class Sar extends Cpu.DemuxedOpcode {
        @Override
        public void demuxed(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_0001) == 0b01;
            boolean v = (opcode & 0b0000_0010) == 0b10;

            int value;
            if (v) {
                int count = cpu.registers[Cpu.CX] & 0x00FF;

                if (w) {
                    if (16 < count) {
                        count = 16;
                    }
                    value = cpu.mrrModValue & Cpu.WORD_MASK;
                    value = ((0 - ((value & Cpu.WORD_MASK_SIGN) >> Cpu.WORD_POS_SIGN)) & ~Cpu.WORD_MASK) | value;
                    value >>>= count;
                } else {
                    if (8 < count) {
                        count = 8;
                    }
                    value = cpu.mrrModValue & Cpu.BYTE_MASK;
                    value = ((0 - ((value & Cpu.BYTE_MASK_SIGN) >> Cpu.BYTE_POS_SIGN)) & ~Cpu.BYTE_MASK) | value;
                    value >>>= count;
                }

                //          (clear CF
                cpu.flags = (cpu.flags & ~Cpu.FLAG_CF)
                        // (shifted out bit (sign) to CF
                        | (((cpu.mrrModValue >> (count - 1)) & 0x01) << Cpu.FLAG_CF_POS);
            } else {
                // one bit shift, sign bit is shifted out
                if (w) {
                    value = cpu.mrrModValue & Cpu.WORD_MASK;
                    value = (value & Cpu.WORD_MASK_SIGN) | (value >> 1);
                } else {
                    value = cpu.mrrModValue & Cpu.BYTE_MASK;
                    value = (value & Cpu.BYTE_MASK_SIGN) | (value >> 1);
                }

                //          (clear CF and OF (sign doesn't change, clear OF)
                cpu.flags = (cpu.flags & (~Cpu.FLAG_CF & ~Cpu.FLAG_OF))
                        // (shifted out bit (sign) to CF
                        | ((cpu.mrrModValue & 0x01) << Cpu.FLAG_CF_POS);
            }

            Cpu.Opcode.flagsPsz(cpu, w, value);
            // write result to reg/mem pointed by mrr
            cpu.writeByModRegRm(w, value);
        }
    }

    public static class Rol extends Cpu.DemuxedOpcode {
        @Override
        void demuxed(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_0001) != 0b00;
            boolean v = (opcode & 0b0000_0010) != 0b00;

            if (v) {
                // CL bit shift
                int count = cpu.registers[Cpu.CX] & 0x00FF;

                int value;

                if (w) {
                    if (16 < count) {
                        count = 16;
                    }
                    value = (cpu.mrrModValue << count) | ((cpu.mrrModValue >> 16 - count));
                } else {
                    if (8 < count) {
                        count = 8;
                    }
                    value = (cpu.mrrModValue << count) | ((cpu.mrrModValue >> 8 - count));
                }

                //          (clear CF
                cpu.flags = (cpu.flags & ~Cpu.FLAG_CF)
                        // (shifted out bit to CF
                        | ((value & 0x01) << Cpu.FLAG_CF_POS);


                Cpu.Opcode.flagsPsz(cpu, w, value);

                // write result to reg/mem pointed by mrr
                cpu.writeByModRegRm(w, value);
            } else {
                // one bit shift, sign bit is shifted out
                int value;
                if (w) {
                    // mrr must have cleared upper word
                    value = (cpu.mrrModValue << 1) | (cpu.mrrModValue >> Cpu.WORD_POS_SIGN);

                    //          (clear CF and OF
                    cpu.flags = (cpu.flags & (~Cpu.FLAG_CF & ~Cpu.FLAG_OF))
                            // (shifted out bit (sign) to CF
                            | ((value & 0x0001) << Cpu.FLAG_CF_POS)
                            // (two upper bit check for sign change to OF
                            | ((((cpu.mrrModValue >> Cpu.WORD_POS_SIGN) ^ (cpu.mrrModValue >> (Cpu.WORD_POS_SIGN - 1))) & 0x01) << Cpu.FLAG_OF_POS);

                    Cpu.Opcode.flagsPsz16(cpu, value);
                } else {
                    // mrr must have cleared upper word
                    value = (cpu.mrrModValue << 1) | (cpu.mrrModValue >> Cpu.BYTE_POS_SIGN);

                    // byte mode (1 bit shift)
                    //          (clear CF and OF
                    cpu.flags = (cpu.flags & (~Cpu.FLAG_CF & ~Cpu.FLAG_OF))
                            // (shifted out bit (sign) to CF
                            | ((value & 0x0001) << Cpu.FLAG_CF_POS)
                            // (two upper bit check for sign change
                            | (((cpu.mrrModValue >> Cpu.BYTE_POS_SIGN) ^ (cpu.mrrModValue >> (Cpu.BYTE_POS_SIGN - 1)) & 0x01) << Cpu.FLAG_OF_POS);

                    Cpu.Opcode.flagsPsz8(cpu, value);
                }
                // write result to reg/mem pointed by mrr
                cpu.writeByModRegRm(w, value);
            }
        }
    }

    public static class Ror extends Cpu.DemuxedOpcode {
        @Override
        void demuxed(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_0001) != 0b00;
            boolean v = (opcode & 0b0000_0010) != 0b00;

            if (v) {
                // CL bit shift
                int count = cpu.registers[Cpu.CX] & 0x00FF;
                int value;
                if (w) {
                    if (16 < count) {
                        count = 16;
                    }
                    value = (cpu.mrrModValue >> count) | ((cpu.mrrModValue << 16 - count));

                    //          (clear CF
                    cpu.flags = (cpu.flags & ~Cpu.FLAG_CF)
                            // (shifted out bit to CF
                            | ((value & Cpu.WORD_MASK_SIGN) >> (Cpu.WORD_POS_SIGN - Cpu.FLAG_CF_POS));
                } else {
                    if (8 < count) {
                        count = 8;
                    }
                    value = (cpu.mrrModValue >> count) | ((cpu.mrrModValue << 8 - count));
                    //          (clear CF
                    cpu.flags = (cpu.flags & ~Cpu.FLAG_CF)
                            // (shifted out bit to CF
                            | ((value & Cpu.BYTE_MASK_SIGN) >> (Cpu.BYTE_POS_SIGN - Cpu.FLAG_CF_POS));
                }

                Cpu.Opcode.flagsPsz(cpu, w, value);

                // write result to reg/mem pointed by mrr
                cpu.writeByModRegRm(w, value);
            } else {
                // one bit shift, sign bit is shifted out
                int value;
                if (w) {
                    // mrr must have cleared upper word
                    value = (cpu.mrrModValue >> 1) | (cpu.mrrModValue << Cpu.WORD_POS_SIGN);

                    //          (clear CF and OF
                    cpu.flags = (cpu.flags & (~Cpu.FLAG_CF & ~Cpu.FLAG_OF))
                            // (shifted out bit (sign) to CF
                            | ((cpu.mrrModValue & 0x0001) << Cpu.FLAG_CF_POS)
                            // (two upper bit check for sign change to OF
                            | ((((cpu.mrrModValue >> Cpu.WORD_POS_SIGN) ^ (cpu.mrrModValue >> (Cpu.WORD_POS_SIGN - 1))) & 0x01) << Cpu.FLAG_OF_POS);

                    Cpu.Opcode.flagsPsz16(cpu, value);
                } else {
                    // mrr must have cleared upper word
                    value = (cpu.mrrModValue >> 1) | (cpu.mrrModValue << Cpu.BYTE_POS_SIGN);

                    // byte mode (1 bit shift)
                    //          (clear CF and OF
                    cpu.flags = (cpu.flags & (~Cpu.FLAG_CF & ~Cpu.FLAG_OF))
                            // (shifted out bit (sign) to CF
                            | ((cpu.mrrModValue & 0x0001) << Cpu.FLAG_CF_POS)
                            // (two upper bit check for sign change
                            | (((cpu.mrrModValue >> Cpu.BYTE_POS_SIGN) ^ (cpu.mrrModValue >> (Cpu.BYTE_POS_SIGN - 1)) & 0x01) << Cpu.FLAG_OF_POS);

                    Cpu.Opcode.flagsPsz8(cpu, value);
                }
                // write result to reg/mem pointed by mrr
                cpu.writeByModRegRm(w, value);
            }
        }
    }

    public static class Rcl extends Cpu.DemuxedOpcode {
        @Override
        void demuxed(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_0001) != 0b00;
            boolean v = (opcode & 0b0000_0010) != 0b00;

            if (v) {
                // CL bit shift
                int count = cpu.registers[Cpu.CX] & 0x00FF;

                int value;

                if (w) {
                    if (16 < count) {
                        count = 16;
                    }
                    value = (cpu.mrrModValue << count)
                            | ((cpu.mrrModValue >> 16 - count + 1))
                            | ((cpu.flags & Cpu.FLAG_CF) << (count - 1));

                    cpu.flags = (cpu.flags & ~Cpu.FLAG_CF)
                            // (shifted out bit to CF
                            | ((value & Cpu.WORD_MASK_CARRY) >> (Cpu.WORD_POS_CARRY - Cpu.FLAG_CF_POS));
                } else {
                    if (8 < count) {
                        count = 8;
                    }
                    value = (cpu.mrrModValue << count)
                            | ((cpu.mrrModValue >> 8 - count + 1))
                            | ((cpu.flags & Cpu.FLAG_CF) << (count - 1));

                    cpu.flags = (cpu.flags & ~Cpu.FLAG_CF)
                            // (shifted out bit to CF
                            | ((value & Cpu.BYTE_MASK_CARRY) >> (Cpu.BYTE_POS_CARRY - Cpu.FLAG_CF_POS));
                }

                Cpu.Opcode.flagsPsz(cpu, w, value);

                // write result to reg/mem pointed by mrr
                cpu.writeByModRegRm(w, value);
            } else {
                // one bit shift, sign bit is shifted out
                // mrr must have cleared upper word
                int value = (cpu.mrrModValue << 1) | ((cpu.flags & Cpu.FLAG_CF) >> Cpu.FLAG_CF_POS);

                if (w) {
                    //          (clear CF and OF
                    cpu.flags = (cpu.flags & (~Cpu.FLAG_CF & ~Cpu.FLAG_OF))
                            // (shifted out bit (sign) to CF
                            | ((value & Cpu.WORD_MASK_CARRY) >> (Cpu.WORD_POS_CARRY - Cpu.FLAG_CF_POS))
                            // (two upper bit check for sign change to OF
                            | ((((cpu.mrrModValue >> Cpu.WORD_POS_SIGN) ^ (cpu.mrrModValue >> (Cpu.WORD_POS_SIGN - 1))) & 0x01) << Cpu.FLAG_OF_POS);

                    Cpu.Opcode.flagsPsz16(cpu, value);
                } else {
                    // byte mode (1 bit shift)
                    //          (clear CF and OF
                    cpu.flags = (cpu.flags & (~Cpu.FLAG_CF & ~Cpu.FLAG_OF))
                            // (shifted out bit (sign) to CF
                            | ((value & Cpu.BYTE_MASK_CARRY) >> (Cpu.BYTE_POS_CARRY - Cpu.FLAG_CF_POS))
                            // (two upper bit check for sign change
                            | (((cpu.mrrModValue >> Cpu.BYTE_POS_SIGN) ^ (cpu.mrrModValue >> (Cpu.BYTE_POS_SIGN - 1)) & 0x01) << Cpu.FLAG_OF_POS);

                    Cpu.Opcode.flagsPsz8(cpu, value);
                }
                // write result to reg/mem pointed by mrr
                cpu.writeByModRegRm(w, value);
            }
        }
    }

    public static class Rcr extends Cpu.DemuxedOpcode {
        @Override
        void demuxed(Cpu cpu, int opcode) {
            boolean w = (opcode & 0b0000_0001) != 0b00;
            boolean v = (opcode & 0b0000_0010) != 0b00;

            if (v) {
                // CL bit shift
                int count = cpu.registers[Cpu.CX] & 0x00FF;
                int value;
                if (w) {
                    if (16 < count) {
                        count = 16;
                    }
                    value = (cpu.mrrModValue >> count)
                            | ((cpu.mrrModValue << 16 - count + 1))
                            | ((cpu.flags & Cpu.FLAG_CF) << (16 - count));

                    //          (clear CF
                    cpu.flags = (cpu.flags & ~Cpu.FLAG_CF)
                            // (shifted out bit to CF
                            | ((value & Cpu.WORD_MASK_SIGN) >> (Cpu.WORD_POS_SIGN - Cpu.FLAG_CF_POS));
                } else {
                    if (8 < count) {
                        count = 8;
                    }
                    value = (cpu.mrrModValue >> count)
                            | ((cpu.mrrModValue << 8 - count + 1))
                            | ((cpu.flags & Cpu.FLAG_CF) << (8 - count));

                    //          (clear CF
                    cpu.flags = (cpu.flags & ~Cpu.FLAG_CF)
                            // (shifted out bit to CF
                            | ((value & Cpu.BYTE_MASK_SIGN) >> (Cpu.BYTE_POS_SIGN - Cpu.FLAG_CF_POS));
                }

                Cpu.Opcode.flagsPsz(cpu, w, value);

                // write result to reg/mem pointed by mrr
                cpu.writeByModRegRm(w, value);
            } else {
                // one bit shift, sign bit is shifted out
                int value;
                if (w) {
                    // mrr must have cleared upper word
                    value = (cpu.mrrModValue >> 1) | ((cpu.flags & Cpu.FLAG_CF) << Cpu.WORD_POS_SIGN);

                    //          (clear CF and OF
                    cpu.flags = (cpu.flags & (~Cpu.FLAG_CF & ~Cpu.FLAG_OF))
                            // (shifted out bit (sign) to CF
                            | ((cpu.mrrModValue & 0x0001) << Cpu.FLAG_CF_POS)
                            // (two upper bit check for sign change to OF
                            | ((((cpu.mrrModValue >> Cpu.WORD_POS_SIGN) ^ (cpu.mrrModValue >> (Cpu.WORD_POS_SIGN - 1))) & 0x01) << Cpu.FLAG_OF_POS);

                    Cpu.Opcode.flagsPsz16(cpu, value);
                } else {
                    // mrr must have cleared upper word
                    value = (cpu.mrrModValue >> 1) | ((cpu.flags & Cpu.FLAG_CF) << Cpu.BYTE_POS_SIGN);

                    // byte mode (1 bit shift)
                    //          (clear CF and OF
                    cpu.flags = (cpu.flags & (~Cpu.FLAG_CF & ~Cpu.FLAG_OF))
                            // (shifted out bit (sign) to CF
                            | ((cpu.mrrModValue & 0x0001) << Cpu.FLAG_CF_POS)
                            // (two upper bit check for sign change
                            | (((cpu.mrrModValue >> Cpu.BYTE_POS_SIGN) ^ (cpu.mrrModValue >> (Cpu.BYTE_POS_SIGN - 1)) & 0x01) << Cpu.FLAG_OF_POS);

                    Cpu.Opcode.flagsPsz8(cpu, value);
                }
                // write result to reg/mem pointed by mrr
                cpu.writeByModRegRm(w, value);
            }
        }
    }

    public static class AndRmR extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            final boolean w = (opcode & 0b0000_0001) != 0;
            final boolean d = (opcode & 0b0000_0010) != 0;
            cpu.readModRegRm(opcode);

            final int value = cpu.mrrRegValue & cpu.mrrModValue;

            if (d) {
                // reg <<- mor r/m
                cpu.writeRegister(w, cpu.mrrRegIndex, value);
            } else {
                // mod r/m <<- reg
                cpu.writeByModRegRm(w, value);
            }

            cpu.flags &= (~Cpu.FLAG_OF | ~Cpu.FLAG_CF);
            Cpu.Opcode.flagsPsz(cpu, w, value);
        }
    }

    public static class AndRmImm extends Cpu.DemuxedOpcode {
        @Override
        public void demuxed(Cpu cpu, int opcode) {
            final boolean w = (opcode & 0b0000_0001) == 0b01;

            final int imm;
            if (w) {
                imm = cpu.ipRead16();
            } else {
                imm = cpu.ipRead8();
            }

            final int value = imm & cpu.mrrModValue;
            cpu.flags &= (~Cpu.FLAG_OF | ~Cpu.FLAG_CF);
            Cpu.Opcode.flagsPsz(cpu, w, value);
            cpu.writeByModRegRm(w, value);
        }
    }

    public static class AndAccImm extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            final boolean w = (opcode & 0b0000_0001) == 0b01;

            final int imm;
            if (w) {
                imm = cpu.ipRead16();
            } else {
                imm = cpu.ipRead8();
            }

            final int value = cpu.registers[Cpu.AX] & imm;
            cpu.flags &= (~Cpu.FLAG_OF | ~Cpu.FLAG_CF);
            Cpu.Opcode.flagsPsz(cpu, w, value);
            cpu.writeRegister(w, Cpu.AX, value);
        }
    }

    public static class AndRmImmSignExt extends Cpu.DemuxedOpcode {
        @Override
        public void demuxed(Cpu cpu, int opcode) {
            final boolean w = (opcode & 0b0000_0001) == 0b01;

            //  and ax, ffff  ---> 83 e0 ff (intel manual: not used)
            //  and ax, 11    ---> 83 e0 11 (intel manual: not used)
            //  and ax, -128  ---> 83 e0 80 (intel manual: not used)
            //  and bx, -128  ---> 83 e3 80
            // todo: test
            final int imm = cpu.ipRead8WithSign();
            final int value = imm & cpu.mrrModValue;

            cpu.flags &= (~Cpu.FLAG_OF | ~Cpu.FLAG_CF);
            Cpu.Opcode.flagsPsz(cpu, w, value);
            cpu.writeByModRegRm(w, value);
        }
    }

    public static class TestRmR extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            final boolean w = (opcode & 0b0000_0001) == 0b01;
            cpu.readModRegRm(opcode);

            final int value = cpu.mrrRegValue & cpu.mrrModValue;

            cpu.flags &= (~Cpu.FLAG_OF | ~Cpu.FLAG_CF);
            Cpu.Opcode.flagsPsz(cpu, w, value);
        }
    }

    public static class TestRmImm extends Cpu.DemuxedOpcode {
        @Override
        public void demuxed(Cpu cpu, int opcode) {
            final boolean w = (opcode & 0b0000_0001) == 0b01;

            final int imm;
            if (w) {
                imm = cpu.ipRead16();
            } else {
                imm = cpu.ipRead8();
            }

            final int value = imm & cpu.mrrModValue;
            cpu.flags &= (~Cpu.FLAG_OF | ~Cpu.FLAG_CF);
            Cpu.Opcode.flagsPsz(cpu, w, value);
        }
    }

    public static class TestAccImm extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            final boolean w = (opcode & 0b0000_0001) == 0b01;

            final int imm;
            if (w) {
                imm = cpu.ipRead16();
            } else {
                imm = cpu.ipRead8();
            }

            final int value = cpu.registers[Cpu.AX] & imm;
            cpu.flags &= (~Cpu.FLAG_OF | ~Cpu.FLAG_CF);
            Cpu.Opcode.flagsPsz(cpu, w, value);
        }
    }

    public static class TestRmImmSignExt extends Cpu.DemuxedOpcode {
        @Override
        public void demuxed(Cpu cpu, int opcode) {
            final boolean w = (opcode & 0b0000_0001) == 0b01;
            final int imm = cpu.ipRead8WithSign();
            final int value = imm & cpu.mrrModValue;

            cpu.flags &= (~Cpu.FLAG_OF | ~Cpu.FLAG_CF);
            Cpu.Opcode.flagsPsz(cpu, w, value);
        }
    }

    public static class XorRmR extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            final boolean w = (opcode & 0b0000_0001) == 0b01;
            final boolean d = (opcode & 0b0000_0010) == 0b10;
            cpu.readModRegRm(opcode);

            final int value = cpu.mrrRegValue ^ cpu.mrrModValue;

            if (d) {
                // reg <<- mor r/m
                cpu.writeRegister(w, cpu.mrrRegIndex, value);
            } else {
                // mod r/m <<- reg
                cpu.writeByModRegRm(w, value);
            }

            cpu.flags &= (~Cpu.FLAG_OF | ~Cpu.FLAG_CF);
            Cpu.Opcode.flagsPsz(cpu, w, value);
        }
    }

    public static class XorRmImm extends Cpu.DemuxedOpcode {
        @Override
        public void demuxed(Cpu cpu, int opcode) {
            final boolean w = (opcode & 0b0000_0001) == 0b01;

            final int imm;
            if (w) {
                imm = cpu.ipRead16();
            } else {
                imm = cpu.ipRead8();
            }

            final int value = imm ^ cpu.mrrModValue;
            cpu.flags &= (~Cpu.FLAG_OF | ~Cpu.FLAG_CF);
            Cpu.Opcode.flagsPsz(cpu, w, value);
            cpu.writeByModRegRm(w, value);
        }
    }

    public static class XorAccImm extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            final boolean w = (opcode & 0b0000_0001) == 0b01;

            final int imm;
            if (w) {
                imm = cpu.ipRead16();
            } else {
                imm = cpu.ipRead8();
            }

            final int value = cpu.registers[Cpu.AX] ^ imm;
            cpu.flags &= (~Cpu.FLAG_OF | ~Cpu.FLAG_CF);
            Cpu.Opcode.flagsPsz(cpu, w, value);
            cpu.writeRegister(w, Cpu.AX, value);
        }
    }

    public static class XorRmImmSignExt extends Cpu.DemuxedOpcode {
        @Override
        public void demuxed(Cpu cpu, int opcode) {
            final boolean w = (opcode & 0b0000_0001) == 0b01;

            // todo: test
            final int imm = cpu.ipRead8WithSign();
            final int value = imm ^ cpu.mrrModValue;

            cpu.flags &= (~Cpu.FLAG_OF | ~Cpu.FLAG_CF);
            Cpu.Opcode.flagsPsz(cpu, w, value);
            cpu.writeByModRegRm(w, value);
        }
    }

    public static class OrRmR extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            final boolean w = (opcode & 0b0000_0001) == 0b01;
            final boolean d = (opcode & 0b0000_0010) == 0b10;
            cpu.readModRegRm(opcode);

            final int value = cpu.mrrRegValue | cpu.mrrModValue;

            if (d) {
                // reg <<- mor r/m
                cpu.writeRegister(w, cpu.mrrRegIndex, value);
            } else {
                // mod r/m <<- reg
                cpu.writeByModRegRm(w, value);
            }

            cpu.flags &= (~Cpu.FLAG_OF | ~Cpu.FLAG_CF);
            Cpu.Opcode.flagsPsz(cpu, w, value);
        }
    }

    public static class OrRmImm extends Cpu.DemuxedOpcode {
        @Override
        public void demuxed(Cpu cpu, int opcode) {
            final boolean w = (opcode & 0b0000_0001) == 0b01;

            final int imm;
            if (w) {
                imm = cpu.ipRead16();
            } else {
                imm = cpu.ipRead8();
            }

            final int value = imm | cpu.mrrModValue;
            cpu.flags &= (~Cpu.FLAG_OF | ~Cpu.FLAG_CF);
            Cpu.Opcode.flagsPsz(cpu, w, value);
            cpu.writeByModRegRm(w, value);
        }
    }

    public static class OrAccImm extends Cpu.Opcode {
        @Override
        public void execute(Cpu cpu, int opcode) {
            final boolean w = (opcode & 0b0000_0001) == 0b01;

            final int imm;
            if (w) {
                imm = cpu.ipRead16();
            } else {
                imm = cpu.ipRead8();
            }

            final int value = cpu.registers[Cpu.AX] | imm;
            cpu.flags &= (~Cpu.FLAG_OF | ~Cpu.FLAG_CF);
            Cpu.Opcode.flagsPsz(cpu, w, value);
            cpu.writeRegister(w, Cpu.AX, value);
        }
    }

    public static class OrRmImmSignExt extends Cpu.DemuxedOpcode {
        @Override
        public void demuxed(Cpu cpu, int opcode) {
            final boolean w = (opcode & 0b0000_0001) == 0b01;

            final int imm = cpu.ipRead8WithSign();
            final int value = imm | cpu.mrrModValue;

            cpu.flags &= (~Cpu.FLAG_OF | ~Cpu.FLAG_CF);
            Cpu.Opcode.flagsPsz(cpu, w, value);
            cpu.writeByModRegRm(w, value);
        }
    }
}
