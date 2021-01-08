package at.emu.i8086;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Runs codegolf test program from
 * https://codegolf.stackexchange.com/questions/4732/emulate-an-intel-8086-cpu
 */
public class CodegolfTest {

    /**
     * Expected output of the test program,
     * located at 0x8000 as 80*25 array
     */
    public final static String EXPECTED_OUTPUT =
                    ".........                                                                       " +
                    "Hello, world!                                                                   " +
                    "0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~ " +
                    "                                                                                " +
                    "                                                                                " +
                    "################################################################################" +
                    "##                                                                            ##" +
                    "##  0 1 1 2 3 5 8 13 21 34 55 89 144 233 377 610 987                          ##" +
                    "##                                                                            ##" +
                    "##  0 1 4 9 16 25 36 49 64 81 100 121 144 169 196 225 256 289 324 361 400     ##" +
                    "##                                                                            ##" +
                    "##  2 3 5 7 11 13 17 19 23 29 31 37 41 43 47 53 59 61 67 71 73 79 83 89 97    ##" +
                    "##                                                                            ##" +
                    "##                                                                            ##" +
                    "##                                                                            ##" +
                    "##                                                                            ##" +
                    "##                                                                            ##" +
                    "##                                                                            ##" +
                    "##                                                                            ##" +
                    "##                                                                            ##" +
                    "##                                                                            ##" +
                    "##                                                                            ##" +
                    "##                                                                            ##" +
                    "##                                                                            ##" +
                    "################################################################################";

    /**
     * All segment registers must be 0x0000,
     * registers must be 0x0000, SP must be 0x0100.
     * code must be loaded at 0x0000:0000
     */
    @Test
    public void runSinglePassTest() throws Exception
    {
        Cpu cpu = new Cpu();
        cpu.init();
        cpu.reset();

        Path code = Paths.get(CodegolfTest.class.getResource("/codegolf").toURI());
        byte[] bytes = Files.readAllBytes(code);
        System.arraycopy(bytes, 0, cpu.memory, 0, bytes.length);


        cpu.registers[Cpu.SP] = 0x100;

        while (true)
        {
            int opcode = cpu.ipRead8();
            if (cpu.opcodes[opcode] == null) {
                break;
            }
            cpu.opcodes[opcode].execute(cpu, opcode);
            if (cpu.hlt) {
                break;
            }
        }

        StringBuilder result = new StringBuilder(80*25);
        for (int i = 0; i < 80*25; i++) {
            int c = Byte.toUnsignedInt(cpu.memory[0x8000 + i]);
            if (c == 0) {
                result.append(' ');
            } else {
                result.append((char) c);
            }
        }

        Assertions.assertEquals(EXPECTED_OUTPUT, result.toString());
    }

    /**
     * runs some kind of performance test
     * @throws Exception if any
     */
    public static void performance() throws Exception
    {
        Path code = Paths.get(CodegolfTest.class.getResource("/codegolf").toURI());
        byte[] bytes = Files.readAllBytes(code);

        Cpu cpu = new Cpu();
        cpu.init();

        // warmup
        for (int run = 0; run < 10_000; run++) {
            // reset test state
            System.arraycopy(bytes, 0, cpu.memory, 0, bytes.length);
            perfCycle(cpu);
        }

        // reset test state
        System.arraycopy(bytes, 0, cpu.memory, 0, bytes.length);

        long tStart = System.nanoTime();
        perfCycle(cpu);
        long time = System.nanoTime() - tStart;

        scr(cpu);
        System.out.println("time: " + time);
    }

    /**
     * runs one cycle of test
     * @param cpu ref to cpu
     */
    public static void perfCycle(Cpu cpu)
    {
        cpu.reset();
        cpu.registers[Cpu.SP] = 0x100;

        while (true) {
            int opcode = cpu.ipRead8();
            cpu.opcodes[opcode].execute(cpu, opcode);
            if (cpu.hlt) {
                break;
            }
        }
    }

    /**
     * Dumps test result rendered in memory to screen
     * @param cpu ret to cpu
     */
    public static void scr(Cpu cpu)
    {
        for (int i = 0; i < 25; i++) {
            for (int e = 0; e < 80; e++) {
                int c = Byte.toUnsignedInt(cpu.memory[0x8000 + i * 80 + e]);
                if (c == 0) {
                    System.out.print(" ");
                } else {
                    System.out.print((char) c);
                }
            }
            System.out.println();
        }
    }

    
    public static void main(String[] args) throws Exception {
        //new CodegolfTest().runSinglePassTest();
        performance();
    }
}
