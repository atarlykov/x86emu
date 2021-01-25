package at.emu.i8086.clocked.cpu;

import java.util.HashMap;
import java.util.Map;

public class Strings implements Cpu.ClockedOpcodeConfiguration {
    /*
     * String manipulations
     *  REP                 1111_001z  (REPE/REPZ,  REPNE/REPNZ)
     *
     *  MOVS (MOVSB/MOVSW)  1010_010w
     *  CMPS                1010_011w
     *  SCAS                1010_111w
     *  LODS                1010_110w
     *  STOS                1010_101w
     *
     *  SI:DI   source:destination
     *  CX      repetition counter
     *  AL/AX   scan value
     *  DF      0   - auto increment
     *          1   - auto decrement
     *  ZF      scan/compare terminator
     *
     * @return configuration of opcodes
     */

    @Override
    public void getClockedConfiguration(Cpu.Opcode[] registry)
    {
        config(registry, "1010_010*", R(18, 9, 17, Movs.class,  "MOVS", ""));
        config(registry, "1010_011*", R(22, 9, 22, Cmps.class,  "CMPS", ""));
        config(registry, "1010_111*", R(15, 9, 15, Scas.class,  "SCAS", ""));
        config(registry, "1010_110*", R(12, 9, 13, Lods.class,  "LODS", ""));
        config(registry, "1010_101*", R(11, 9, 10, Stos.class,  "STOS", ""));

        config(registry, "1111_001*", S( 2, Rep.class,  "REP", ""));
    }

    /**
     * Moves word/byte from DS:SI to ES:DI (DS could be overridden)
     */
    public static class Movs extends Cpu.StringClockOpcode {
        @Override
        public void execute(Cpu cpu, int opcode)
        {
            if (cpu.repPrefix) {
                int rep = cpu.rep;
                execute(cpu, rep, opcode);
                return;
            }

            boolean w = (opcode & 0b0000_0001) == 0b01;

            // this gives +1/-1 depending on DF
            int delta = 1 - ((cpu.flags >> (Cpu.FLAG_DF_POS - 1)) & 0b10);

            if (w) {
                int data = cpu.mread16(cpu.registers[Cpu.SI]);
                cpu.mwrite16(Cpu.ES, cpu.registers[Cpu.DI], data);

                // correct to +2/-2
                delta <<= 1;
            } else {
                int data = cpu.mread8(cpu.registers[Cpu.SI]);
                cpu.mwrite8(Cpu.ES, cpu.registers[Cpu.DI], data);
            }
            cpu.registers[Cpu.SI] += delta;
            cpu.registers[Cpu.DI] += delta;

            // no repetition, fixed clock
            cpu.clocks(clocks);
        }

        /**
         * rep movs variant of the instruction
         * @param cpu ref to cpu
         * @param rep rep prefix
         * @param opcode movs opcode
         */
        public void execute(Cpu cpu, int rep, int opcode)
        {
            boolean w = (opcode & 0b0000_0001) == 0b01;
            boolean z = (rep & 0b0000_0001) == 0b01;

            if (!z) {
                // movs opcode only used with
                // rep/repe/repz, where z == 1
                cpu.hlt = true;
                return;
            }

            // this gives +1/-1 depending on DF
            int delta = 1 - ((cpu.flags >> (Cpu.FLAG_DF_POS - 1)) & 0b10);

            // base clocks
            cpu.clocks(clocksAlt);

            if (w) {
                // correct to +2/-2
                delta <<= 1;

                while (cpu.registers[Cpu.CX] != 0) {
                    int data = cpu.mread16(cpu.registers[Cpu.SI]);
                    cpu.mwrite16(Cpu.ES, cpu.registers[Cpu.DI], data);
                    cpu.registers[Cpu.SI] += delta;
                    cpu.registers[Cpu.DI] += delta;
                    cpu.registers[Cpu.CX]--;

                    // repetition clock
                    cpu.clocks(clocksAlt2);
                }
            } else {
                while (cpu.registers[Cpu.CX] != 0) {
                    int data = cpu.mread8(cpu.registers[Cpu.SI]);
                    cpu.mwrite8(Cpu.ES, cpu.registers[Cpu.DI], data);
                    cpu.registers[Cpu.SI] += delta;
                    cpu.registers[Cpu.DI] += delta;
                    cpu.registers[Cpu.CX]--;

                    // repetition clock
                    cpu.clocks(clocksAlt2);
                }
            }
        }
    }

    /**
     * Compares word/byte from DS:SI with ES:DI (DS could be overridden),
     * updates  (af,cf,of,pf,sf,zf) based on ([si] - [di])
     */
    public static class Cmps extends Cpu.StringClockOpcode {
        @Override
        public void execute(Cpu cpu, int opcode)
        {
            int dst, src;

            if (cpu.repPrefix) {
                int rep = cpu.rep;
                execute(cpu, rep, opcode);
                return;
            }

            boolean w = (opcode & 0b0000_0001) == 0b01;

            // this gives +1/-1 depending on DF
            int delta = 1 - ((cpu.flags >> (Cpu.FLAG_DF_POS - 1)) & 0b10);

            if (w) {
                src = cpu.mread16(cpu.registers[Cpu.SI]);
                dst = cpu.mread16(Cpu.ES, cpu.registers[Cpu.DI]);

                // correct to +2/-2
                delta <<= 1;
            } else {
                src = cpu.mread8(cpu.registers[Cpu.SI]);
                dst = cpu.mread8(Cpu.ES, cpu.registers[Cpu.DI]);
            }

            cpu.registers[Cpu.SI] += delta;
            cpu.registers[Cpu.DI] += delta;

            // this is inverse of the src-dst pair in terms of Sub,
            // here [si] - [di]  is used
            Sub.flags(cpu, w, src, dst);

            // no repetition, fixed clock
            cpu.clocks(clocks);
        }

        /**
         * rep cmps variant of the instruction
         * @param cpu ref to cpu
         * @param rep rep prefix
         * @param opcode cmps opcode
         */
        public void execute(Cpu cpu, int rep, int opcode)
        {
            boolean w = (opcode & 0b0000_0001) == 0b01;
            int     z = (rep & 0b0000_0001);

            // this gives +1/-1 depending on DF
            int delta = 1 - ((cpu.flags >> (Cpu.FLAG_DF_POS - 1)) & 0b10);

            // base clocks
            cpu.clocks(clocksAlt);

            if (w) {
                // correct to +2/-2
                delta <<= 1;

                while (cpu.registers[Cpu.CX] != 0) {
                    int src = cpu.mread16(cpu.registers[Cpu.SI]);
                    int dst = cpu.mread16(Cpu.ES, cpu.registers[Cpu.DI]);

                    cpu.registers[Cpu.SI] += delta;
                    cpu.registers[Cpu.DI] += delta;
                    cpu.registers[Cpu.CX]--;

                    // this is inverse of the src-dst pair in terms of Sub,
                    // here [si] - [di]  is used
                    Sub.flags(cpu, true, src, dst);

                    // repetition clocks
                    cpu.clocks(clocksAlt2);

                    if (z != ((cpu.flags >> Cpu.FLAG_ZF_POS) & 1)) {
                        break;
                    }
                }
            } else {
                while (cpu.registers[Cpu.CX] != 0) {
                    int src = cpu.mread8(cpu.registers[Cpu.SI]);
                    int dst = cpu.mread8(Cpu.ES, cpu.registers[Cpu.DI]);
                    cpu.registers[Cpu.SI] += delta;
                    cpu.registers[Cpu.DI] += delta;
                    cpu.registers[Cpu.CX]--;

                    // this is inverse of the src-dst pair in terms of Sub,
                    // here [si] - [di]  is used
                    Sub.flags(cpu, true, src, dst);

                    // repetition clocks
                    cpu.clocks(clocksAlt2);

                    if (z != ((cpu.flags >> Cpu.FLAG_ZF_POS) & 1)) {
                        break;
                    }
                }
            }
        }

    }

    /**
     * Scans word/byte from ES:DI
     * updates (af,cf,of,pf,sf,zf) based on (AL/AX - [di])
     */
    public static class Scas extends Cpu.StringClockOpcode {
        @Override
        public void execute(Cpu cpu, int opcode)
        {
            int dst;

            if (cpu.repPrefix) {
                int rep = cpu.rep;
                execute(cpu, rep, opcode);
                return;
            }

            boolean w = (opcode & 0b0000_0001) == 0b01;

            // this gives +1/-1 depending on DF
            int delta = 1 - ((cpu.flags >> (Cpu.FLAG_DF_POS - 1)) & 0b10);

            if (w) {
                dst = cpu.mread16(Cpu.ES, cpu.registers[Cpu.DI]);

                // correct to +2/-2
                delta <<= 1;
            } else {
                dst = cpu.mread8(Cpu.ES, cpu.registers[Cpu.DI]);
            }

            cpu.registers[Cpu.DI] += delta;

            // this is inverse of the src-dst pair in terms of Sub,
            // here ax - [di]  is used
            int ax = cpu.registers[Cpu.AX];
            Sub.flags(cpu, w, ax, dst);

            // no repetition, fixed clock
            cpu.clocks(clocks);
        }


        /**
         * rep cmps variant of the instruction
         * @param cpu ref to cpu
         * @param rep rep prefix
         * @param opcode cmps opcode
         */
        public void execute(Cpu cpu, int rep, int opcode)
        {
            boolean w = (opcode & 0b0000_0001) == 0b01;
            int     z = (rep & 0b0000_0001);

            int ax = cpu.registers[Cpu.AX];

            // this gives +1/-1 depending on DF
            int delta = 1 - ((cpu.flags >> (Cpu.FLAG_DF_POS - 1)) & 0b10);

            // base clocks
            cpu.clocks(clocksAlt);

            if (w) {
                // correct to +2/-2
                delta <<= 1;

                while (cpu.registers[Cpu.CX] != 0) {
                    int dst = cpu.mread16(Cpu.ES, cpu.registers[Cpu.DI]);

                    cpu.registers[Cpu.DI] += delta;
                    cpu.registers[Cpu.CX]--;

                    // this is inverse of the src-dst pair in terms of Sub,
                    // here ax - [di]  is used
                    Sub.flags(cpu, true, ax, dst);

                    // repetition clocks
                    cpu.clocks(clocksAlt2);

                    if (z != ((cpu.flags >> Cpu.FLAG_ZF_POS) & 1)) {
                        break;
                    }
                }
            } else {
                while (cpu.registers[Cpu.CX] != 0) {
                    int dst = cpu.mread8(Cpu.ES, cpu.registers[Cpu.DI]);
                    cpu.registers[Cpu.DI] += delta;
                    cpu.registers[Cpu.CX]--;

                    // this is inverse of the src-dst pair in terms of Sub,
                    // here ax - [di]  is used
                    Sub.flags(cpu, true, ax, dst);

                    // repetition clocks
                    cpu.clocks(clocksAlt2);

                    if (z != ((cpu.flags >> Cpu.FLAG_ZF_POS) & 1)) {
                        break;
                    }
                }
            }
        }
    }

    /**
     * Loads word/byte from DS:SI (DS could be overridden) to AL/AX
     */
    public static class Lods extends Cpu.StringClockOpcode {
        @Override
        public void execute(Cpu cpu, int opcode)
        {
            int src;

            if (cpu.repPrefix) {
                int rep = cpu.rep;
                execute(cpu, rep, opcode);
                return;
            }

            boolean w = (opcode & 0b0000_0001) == 0b01;

            // this gives +1/-1 depending on DF
            int delta = 1 - ((cpu.flags >> (Cpu.FLAG_DF_POS - 1)) & 0b10);

            if (w) {
                src = cpu.mread16(cpu.registers[Cpu.SI]);

                // correct to +2/-2
                delta <<= 1;
            } else {
                src = cpu.mread8(cpu.registers[Cpu.SI]);
            }

            cpu.writeRegisterWord(Cpu.SI, cpu.registers[Cpu.SI] + delta);
            cpu.writeRegister(w, Cpu.AX, src);

            // no repetition, fixed clock
            cpu.clocks(clocks);
        }

        /**
         * rep lods variant of the instruction,
         * that is VERY strange as it has no any meaning,
         * only last value will be stored into AX
         * (todo: simulate repetitions)
         * @param cpu ref to cpu
         * @param rep rep prefix
         * @param opcode lods opcode
         */
        public void execute(Cpu cpu, int rep, int opcode)
        {
            boolean w = (opcode & 0b0000_0001) == 0b01;
            boolean z = (rep & 0b0000_0001) == 0b01;

            if (!z) {
                // lods opcode only used with
                // rep/repe/repz, where z == 1
                cpu.hlt = true;
                return;
            }

            int ax;
            int cx = cpu.registers[Cpu.CX];

            // this gives +1/-1 depending on DF
            int delta = 1 - ((cpu.flags >> (Cpu.FLAG_DF_POS - 1)) & 0b10);

            // base clocks
            cpu.clocks(clocksAlt);

            if (w) {
                // correct to +2/-2
                delta <<= 1;
                int offset = cpu.registers[Cpu.SI] + cx * delta;
                ax = cpu.mread16(offset);

                // repetition clocks
                cpu.clocks((short)(clocksAlt2 * cx));
                cpu.writeRegisterWord(Cpu.CX, 0);

            } else {
                int offset = cpu.registers[Cpu.SI] + cx * delta;
                ax = cpu.mread8(offset);

                // repetition clocks
                cpu.clocks((short)(clocksAlt2 * cx));
                cpu.writeRegisterWord(Cpu.CX, 0);
            }

            cpu.writeRegister(w, Cpu.AX, ax);
        }
    }

    /**
     * Stores word/byte from AL/AX to ES:DI
     */
    public static class Stos extends Cpu.StringClockOpcode {
        @Override
        public void execute(Cpu cpu, int opcode)
        {
            if (cpu.repPrefix) {
                int rep = cpu.rep;
                execute(cpu, rep, opcode);
                return;
            }

            boolean w = (opcode & 0b0000_0001) == 0b01;
            int ax = cpu.registers[Cpu.AX];

            // this gives +1/-1 depending on DF
            int delta = 1 - ((cpu.flags >> (Cpu.FLAG_DF_POS - 1)) & 0b10);

            if (w) {
                cpu.mwrite16(Cpu.ES, cpu.registers[Cpu.DI], ax);
                // correct to +2/-2
                delta <<= 1;
            } else {
                cpu.mwrite8(Cpu.ES, cpu.registers[Cpu.DI], ax);
            }

            cpu.writeRegisterWord(Cpu.DI, cpu.registers[Cpu.DI] + delta);

            // no repetition, fixed clock
            cpu.clocks(clocks);
        }

        /**
         * rep stos variant of the instruction
         * @param cpu ref to cpu
         * @param rep rep prefix
         * @param opcode stos opcode
         */
        public void execute(Cpu cpu, int rep, int opcode)
        {
            boolean w = (opcode & 0b0000_0001) == 0b01;
            boolean z = (rep & 0b0000_0001) == 0b01;

            if (!z) {
                // stos opcode only used with
                // rep/repe/repz, where z == 1
                cpu.hlt = true;
                return;
            }

            int ax = cpu.registers[Cpu.AX];

            // this gives +1/-1 depending on DF
            int delta = 1 - ((cpu.flags >> (Cpu.FLAG_DF_POS - 1)) & 0b10);

            if (w) {
                // correct to +2/-2
                delta <<= 1;

                while (cpu.registers[Cpu.CX] != 0) {
                    cpu.mwrite16(Cpu.ES, cpu.registers[Cpu.DI], ax);
                    cpu.registers[Cpu.DI] += delta;
                    cpu.registers[Cpu.CX]--;
                }
            } else {
                while (cpu.registers[Cpu.CX] != 0) {
                    cpu.mwrite8(Cpu.ES, cpu.registers[Cpu.DI], ax);
                    cpu.registers[Cpu.DI] += delta;
                    cpu.registers[Cpu.CX]--;
                }
            }
        }
    }

    /**
     * Repeat prefix for string operations
     *  rep          for movs,stos, while cx not 0  (the same as repe/repz) , z == 1
     *  repe/repz    for cmps,scas, require DF to be set     before the next iteration, z == 1
     *  repne/repnz  for cmps,scas, require DF to be cleared before the next iteration, z == 0
     */
    public static class Rep extends Cpu.FixedClockOpcode {
        @Override
        public void executeClocked(Cpu cpu, int opcode)
        {
            cpu.repPrefix = true;
            cpu.rep = opcode;
            cpu.step();
            cpu.repPrefix = false;
        }
    }

}
