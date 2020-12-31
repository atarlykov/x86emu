package at.emu.i8086;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

/**
 * state of the emulates i8086 cpu
 */
public class Cpu {
    /**
     * Zero, sign and carry check masks, byte mode
     */
    public static final int BYTE_MASK_Z   = 0x00FF;
    public static final int BYTE_MASK_S   = 0x0080;
    public static final int BYTE_MASK_C   = 0x0100;
    /**
     * Zero sign and curry check masks, word mode
     */
    public static final int WORD_MASK_Z   = 0x0FFFF;
    public static final int WORD_MASK_S   = 0x08000;
    public static final int WORD_MASK_C   = 0x10000;


    // registers' indices in the registers array
    public static final int AX = 0;
    public static final int CX = 1;
    public static final int DX = 2;
    public static final int BX = 3;
    public static final int SP = 4;
    public static final int BP = 5;
    public static final int SI = 6;
    public static final int DI = 7;

    // segment registers indices in the segments array
    public static final int CS = 0;
    public static final int DS = 1;
    public static final int SS = 2;
    public static final int ES = 3;

    // CF
    // For x86 ALU size of 8 bits, an 8-bit two's complement interpretation,
    // the addition operation 11111111 + 11111111 results in 111111110,
    // Carry_Flag set, Sign_Flag set, and Overflow_Flag clear.
    // +
    // If 11111111 represents two's complement signed integer −1 (ADD al,-1),
    // then the interpretation of the result is 11111110 because
    // Overflow_Flag is clear, and Carry_Flag is ignored.
    // The sign of the result is negative, because Sign_Flag is set.
    // 11111110 is the two's complement form of signed integer −2.
    // +
    // If 11111111 represents unsigned integer binary number 255 (ADD al,255),
    // then the interpretation of the result that the Carry_Flag cannot be ignored.
    // The Overflow_Flag and the Sign_Flag are ignored.
    //
    //
    // OF
    // A negative result out of positive operands (or vice versa) is an overflow.
    // The overflow flag is thus set when the most significant bit is changed by adding two numbers with the same sign
    // (or subtracting two numbers with opposite signs).
    // Overflow never occurs when the sign of two addition operands are different
    // (or the sign of two subtraction operands are the same)
    //
    // flag register masks
    public static final int FLAG_CF = 0b0000_0000_0000_0001;    // status  flag: carry, add/sub/rotate, carry out/borrow into high bit
    public static final int FLAG_PF = 0b0000_0000_0000_0100;    // status  flag: parity, set if result has even parity, even number of 1-bits
    public static final int FLAG_AF = 0b0000_0000_0001_0000;    // status  flag: auxiliary carry
    public static final int FLAG_ZF = 0b0000_0000_0100_0000;    // status  flag: zero, result of the operation is zero
    public static final int FLAG_SF = 0b0000_0000_1000_0000;    // status  flag: sign, high order bit of the result
    public static final int FLAG_TF = 0b0000_0001_0000_0000;    // control flag: trap, 1 - sets cpu into single tep debug mode, cpu will aut generate interrupt after each instruction
    public static final int FLAG_IF = 0b0000_0010_0000_0000;    // control flag: interrupt enable, 1 - enables cpu to recognize external interrupts (maskable), has no effect on non-maskable external and internally generated interrupts
    public static final int FLAG_DF = 0b0000_0100_0000_0000;    // control flag: direction, 1 - string instructions will auto-decrement, 0 - increment
    public static final int FLAG_OF = 0b0000_1000_0000_0000;    // status  flag: overflow, arithmetic overflow, exception generated if available?

    /**
     *
     * logical address sources (table 2-2)
     * -------------------------------+---------+----------+---------+
     *   type of mem ref              | def seg |  alt seg |  offset |
     * -------------------------------+---------+----------+---------+
     *   instruction fetch            |   cs    |   none   |    ip   |
     *   stack operation              |   ss    |   none   |    sp   |
     *   variable (except following)  |   ds    | cs,es,ss |    ea   |
     *   str src                      |   ds    | cs,es,ss |    si   |
     *   str dst                      |   es    |   none   |    di   |
     *   bp used as base reg          |   ss    | cs,ds,ss |    ea   |
     *
     *
     * stack:
     *  ss:sp    ->> [(ss << 8) | sp]
     *  push ax  ->>  sp -= 2; [ss:sp] = ax;
     *  pop ax   ->>  ax = [ss:sp]; sp += 2;
     *  so ss:00 is the full stack
     */


    // common registers
    int[] registers = new int[8];
    // segment registers
    int[] segments = new int[4];

    // instruction pointer
    int ip;
    // flag register
    int flags;


    int opcode;             // current opcode
    boolean d, w, s, v ,z;  // bits in opcode

    int displacement;
    int data;

    int mrrMod;             // mod-reg-r/m: mod part
    int mrrReg;             // mod-reg-r/m: reg part
    int mrrRm;              // mod-reg-r/m: r/m part

    int mrrModRegIndex;     // mod-reg-r/m:  mod == 11  ->> mod parsed as register index
    int mrrModRegValue;     // mod-reg-r/m:  mod == 11  ->> register[mrrModRegIndex]  depends on w(8|16) and d(from|to)?
    int mrrModEA;           // mod-reg-r/m:  mod ==!11  ->> mod parsed and calculated as ea
    int mrrModEAValue;      // mod-reg-r/m:  mod ==!11  ->> mem[xx : mrrModEA] depends on w(8|16) and d(from|to)?
    int mrrModValue;        // mod-reg-r/m:  any mod    --> mrrModRegValue | mrrModEAValue depending on mod (not to parse later)

    int mrrRegIndex;        // mod-reg-r/m:  reg parsed as register index
    int mrrRegValue;        // mod-reg-r/m:  register[mrrRegIndex] depends on w(8|16) and d(from|to)?



    byte[] memory = new byte[1024 * 1024];
    int mem16(int offset) {
        return 0;
    }
    void mem16(int offset, int value) {
    }
    int mem8(int offset) {
        return 0;
    }
    void mem8(int offset, int value) {
    }



    /**
     * sets flag(s) specified in the mask with "1"
     * @param mask flags' mask
     */
    void setFlag(int mask) {
        flags |= mask;
    }

    /**
     * cleats flags that specified in the mask with "1"
     * @param mask mask
     */
    void resetFlag(int mask) {
        flags &= ~mask;
    }

    /**
     * reads next byte moving instruction pointer by one,
     * usually reads at CS:IP, todo: 0x2E CS override prefix??
     * @return byte read
     */
    int read8() {
        // todo read by effective addr
        return Byte.toUnsignedInt(memory[ip++]);
    }

    /**
     * reads next two bytes moving instruction pointer by two,
     * reads low byte than hi byte
     * @return word read
     */
    int read16() {
        //int value = read8();
        //value |= (read8() << 8);
        //return value;
        return read8() | (read8() << 8);
    }

    void readDisplacement() {
        displacement = read8() | (read8() << 8);
    }

    void readData(int opcode) {
        data = read8();
        if ((opcode & 0b00000001) != 0) {
            data |= read8() << 8;
        }
    }

    /**
     * reads opcode variable
     */
    void readOpcode() {
        opcode = read8();
    }

    /**
     * reads and divides mod-reg-r/m byte into local variables,
     * see Cpu.mrrXXX for details
     *
     */
    void readModRegRm()
    {
        int value = read8();
        mrrMod = value >> 6;
        mrrReg = (value & 0b00111000) >> 3;
        mrrRm  = (value & 0b00000111);
    }

    /**
     * Reads mod-reg-r/m byte pointed with instruction pointer and parses it
     * into internal cpu fields ready to be processed by an opcode function.
     * See Cpu.mrrXXX fields for detailed description
     * @param opcode current opcode to which mod-reg-r/m belongs to
     */
    void readAndProcessModRegRm(int opcode)
    {
        // read byte and parse to mod-reg-r/m
        readModRegRm();

        // check if opcode is in word (16 bit) mode ir 8 bit mode
        boolean w = (opcode & 0b0000_0001) == 1;

        // parse -reg- part (not necessary when reg is opcode ext)
        mrrRegIndex = mrrReg;
        if (w) {
            // 16 bit mode, all 8 registers
            mrrRegValue = registers[mrrRegIndex];
        } else {
            // 8 bit mode, only 4 register - xH or xL
            mrrRegIndex &= 0b011;
            // read full register and adjust
            mrrRegValue = registers[mrrRegIndex];
            if ((mrrReg & 0b100) == 0) {
                mrrRegValue &= 0xFF;
            } else {
                mrrRegValue >>= 8;
            }
        }

        // process mod- and -rm parts together
        if ((mrrMod ^ 0b11) == 0)
        {
            // mod==11, r/m points to a register
            mrrModRegIndex = mrrRm;
            if (w) {
                // 16 bit mode, all 8 registers
                mrrModRegValue = registers[mrrModRegIndex];
            } else {
                // 8 bit mode, only 4 register - xH or xL
                mrrModRegIndex &= 0b011;
                // read full register and adjust
                mrrModRegValue = registers[mrrModRegIndex];
                if ((mrrMod & 0b100) == 0) {
                    mrrModRegValue &= 0xFF;
                } else {
                    mrrModRegValue >>= 8;
                }
            }
            // provide unified access to mod-r/m value
            mrrModValue = mrrModRegValue;
        }
        else {
            // mod!=11, mod + r/m give effective address

            // base template for all variants for mod: 00|01|10
            mrrModEA = switch (mrrRm) {
                case 0b000 -> registers[BX] + registers[SI];
                case 0b001 -> registers[BX] + registers[DI];
                case 0b010 -> registers[BP] + registers[SI];
                case 0b011 -> registers[BP] + registers[DI];
                case 0b100 -> registers[SI];
                case 0b101 -> registers[DI];
                case 0b110 -> registers[BP];
                case 0b111 -> registers[BX];
                default -> throw new RuntimeException("impossible as r/m contains only 3 bits");
            };

            if ((mrrMod == 0b00) && (mrrRm == 0b110))
            {
                // mod=00 is the template above except for r/m=110,
                // where it's a direct address as the displacement
                readDisplacement();
                mrrModEA = displacement;
            }
            else if (mrrMod == 0b01)
            {
                // mod=01 is the template + data8
                int value = read8();
                mrrModEA += value;
            }
            else if (mrrMod == 0b10)
            {
                // mod=10 is the template + data16
                int value = read16();
                mrrModEA += value;
            }

            // pre-read value from memory, it will be consumed
            // by the operation or will be updated (so let it be in cache)
            if (w) {
                mrrModEAValue = mem16(mrrModEA);
            } else {
                mrrModEAValue = mem8(mrrModEA);
            }

            // provide unified access to mod-r/m value
            mrrModValue = mrrModEAValue;

            // todo: check for overflow, flags?
        }
    }



    //    Reg/mem and reg       001110dw mrr    disp disp
    //    Imm with reg/mem      100000sw m111r  disp disp data data(s:w=01)
    //    Imm wth accumulator   0011110w data data(w=1) // error in intel manual @p166 table 4-12


    Opcode[] opcodes = new Opcode[256];
    {
        opcodes[0b0011_10_00] = new Cmp.CmpRmR();
        opcodes[0b0011_10_01] = new Cmp.CmpRmR();
        opcodes[0b0011_10_10] = new Cmp.CmpRmR();
        opcodes[0b0011_10_11] = new Cmp.CmpRmR();

        opcodes[0b0011_11_00] = new Cmp.CmpAccImm();
        opcodes[0b0011_11_01] = new Cmp.CmpAccImm();


        opcodes[0b0111_01_00] = new JeJz();


        // m111r
        opcodes[0b1000_00_00] = new Cmp.CmpRmImm();
        opcodes[0b1000_00_01] = new Cmp.CmpRmImm();
        opcodes[0b1000_00_10] = new Cmp.CmpRmImm();
        opcodes[0b1000_00_11] = new Cmp.CmpRmImm();
    }

    void reset() {
        Arrays.fill(registers, 0x0000);
        Arrays.fill(segments, 0x0000);
        //seg[CS] = 0xFFFF;
        flags = 0;
        ip = 0;
    }


    /**
     *
     *
     * [opcode]  [mod.reg.r/m]  [low.disp/data]  [high.disp/data]  [low.data]  [high.data]
     *
     * 	cmp sp, 100h
     * 	jz cont
     *
     * hlt:
     * 	hlt
     *
     * cont:
     * 	mov sp, 1000h
     * 	mov al, '.'
     */
    void debug() {


        registers[Cpu.SP] = 0x100;

        int opcode = read8();
        opcodes[opcode].execute(this, opcode);

        opcode = read8();
        opcodes[opcode].execute(this, opcode);

        System.out.println("x");
    }


    public static void main(String[] args) {
        Cpu cpu = new Cpu();
        cpu.reset();
        cpu.debug();
    }


    /**
     * base class for all opcodes
     */
    public static abstract class Opcode {
        /**
         * method called by the pipeline after reading an instruction
         * @param cpu ref to cpu instance
         * @param opcode current opcode read from memory
         */
        public abstract void execute(Cpu cpu, int opcode);

        /**
         * writes one of common registers based on reg index and byte/word mode
         * @param cpu ref to cpu instance
         * @param w word mode (1) and byte mode (0)
         * @param regIndex index of the register to write
         * @param value new value (8 or 16 bits based on w)
         */
        public static void writeRegister(Cpu cpu, boolean w, int regIndex, int value)
        {
            if (w) {
                cpu.registers[regIndex] = value & 0xFFFF;
            } else {
                int regFullValue = cpu.registers[regIndex];
                if ((cpu.mrrRm & 0b100) == 0) {
                    // upper part (AH, ..)
                    regFullValue &= 0x00FF;
                    regFullValue |= (value & 0xFF) << 8;
                } else {
                    // lower part (AL, ..)
                    regFullValue &= 0xFF00;
                    regFullValue |= (value & 0xFF);
                }
                cpu.registers[regIndex] = regFullValue;
            }
        }

    }

    /**
     * Some cpu commands share the same opcode and distinguished with 'reg' part of mod-reg-r/m byte
     * that follows the opcode, this provides a way to create child opcodes that MUST NOT read
     * mod-reg-r/m byte as it has been already read by @{@link RegBasedDemux}
     */
    public static abstract class DemuxedOpcode extends Opcode {
        /**
         * child classes can't inherit this method just as
         * a reminder to NOT read mod-reg-r/m
         */
        @Override
        public final void execute(Cpu cpu, int opcode) {
        }

        /**
         * method to be used in child classes instead of {@link #execute(Cpu, int)}
         * @param cpu ref to cpu instance
         * @param opcode current opcode
         */
        abstract void demuxed(Cpu cpu, int opcode);
    }

    /**
     * Some cpu commands share the same opcode and distinguished with 'reg' part of mod-reg-r/m byte
     * that follows the opcode, this provides a way to register several @{@link DemuxedOpcode}
     * under the same opcode and call them dynamically based on mod-reg-r/m byte
     */
    public static abstract class RegBasedDemux extends Opcode {
        /**
         * demuxed opcodes to be called
         */
        final DemuxedOpcode[] exits;

        /**
         * will demux opcodes as:
         *   reg     opcode
         *   000       e0
         *   001       e1
         *   010       e2
         *   011       e3
         *        ...
         *   111       e7
         */
        public RegBasedDemux(
                DemuxedOpcode e0, DemuxedOpcode e1, DemuxedOpcode e2, DemuxedOpcode e3,
                DemuxedOpcode e4, DemuxedOpcode e5, DemuxedOpcode e6, DemuxedOpcode e7)
        {
            exits = new DemuxedOpcode[] {e0, e1, e2, e3, e4, e5, e6, e7};
        }

        /**
         * will demux opcodes as:
         *   reg     opcode
         *   000       _exits[0]
         *   001       _exits[1]
         *   010       _exits[2]
         *   011       _exits[3]
         *        ...
         *   111       _exits[7]
         */
        public RegBasedDemux(DemuxedOpcode[] _exits)
        {
            exits = _exits;
            if (exits.length != 8) {
                throw new IllegalArgumentException("reg part of mod-reg-r/m byte is 3 bits in size, must have 8 exists");
            }
        }

        /**
         * will demux opcodes as opcode[key]
         */
        public RegBasedDemux(Map<Integer, DemuxedOpcode> _exits)
        {
            exits = new DemuxedOpcode[8];
            try {
                _exits.forEach((reg, op) -> exits[reg] = op);
            } catch (Exception e) {
                throw new IllegalArgumentException("reg part of mod-reg-r/m byte is 3 bits in size, must have 000..111 value", e);
            }
        }

        @Override
        public void execute(Cpu cpu, int opcode)
        {
            cpu.readAndProcessModRegRm(opcode);
            exits[cpu.mrrReg].demuxed(cpu, opcode);
        }
    }

    // Additions: ADD ADC INC AAA DAA
    // opcodes: [p165],  flags: [p50]
    // ADD (af,cf,of,pf,sf,zf)
    //    Reg/mem with reg to either  000000dw mrr   disp     disp
    //    imm to reg/mem              100000sw m000r disp     disp    data   data(if s:w=01)
    //    imm to accum                0000010w data  data(w=1)
    // ADS (the same as ADD, but codes)
    //                                000100dw --"--
    //      --"--                     100000sw m010r --"--
    //                                0001010w --"--
    // INC (af,of,pf,sf,zf   not cf)
    //    reg/mem                     1111111w m000r disp     disp
    //    reg                         01000reg
    //
    // AAA (af, cf) others=undef      00110111
    // DAA (af,cf,pf,sf,zf. of=undef) 00100111
    public static class AddRmR extends Opcode {

        public static void flags(Cpu cpu, boolean w, int value1, int value2)
        {
            int zmask, cmask, smask, wValue, value;

            if (w) {
                zmask = Cpu.WORD_MASK_Z;
                cmask = Cpu.WORD_MASK_C;
                smask = Cpu.WORD_MASK_S;
            } else {
                zmask = Cpu.BYTE_MASK_Z;
                cmask = Cpu.BYTE_MASK_C;
                smask = Cpu.BYTE_MASK_S;
            }

            value = value1 + value2;
            // value without carry
            wValue = value & zmask;

            // (af,cf,of,pf,sf,zf)

            int bitCount = Integer.bitCount(wValue & Cpu.BYTE_MASK_Z);
            if ((bitCount & 0x1) == 0) cpu.setFlag(Cpu.FLAG_PF); else cpu.resetFlag(Cpu.FLAG_PF);
            if ((value & cmask) != 0)  cpu.setFlag(Cpu.FLAG_CF); else cpu.resetFlag(Cpu.FLAG_CF);
            if ((wValue & smask) != 0) cpu.setFlag(Cpu.FLAG_SF); else cpu.resetFlag(Cpu.FLAG_SF);
            if ((wValue & zmask) == 0) cpu.setFlag(Cpu.FLAG_ZF); else cpu.resetFlag(Cpu.FLAG_ZF);

            if ( ((value1 & smask) == (value2 & smask)) &&
                    ((value1 & smask) != (value & smask)) )
                cpu.setFlag(Cpu.FLAG_OF);
            else
                cpu.resetFlag(Cpu.FLAG_OF);

            // AF only base on lower nipple of low byte
            if (0x0F < ((value1 & 0x0F) + (value2 & 0x0F)))
                cpu.setFlag(Cpu.FLAG_AF);
            else
                cpu.resetFlag(Cpu.FLAG_AF);
        }

        @Override
        public void execute(Cpu cpu, int opcode)
        {
            cpu.readAndProcessModRegRm(opcode);
            boolean w = (opcode & 0b0000_0001) == 0b01;
            boolean d = (opcode & 0b0000_0010) == 0b10;

            int sum = cpu.mrrRegValue + cpu.mrrModValue;
            flags(cpu, w, cpu.mrrRegValue, cpu.mrrModValue);
            
            if (d) {
                // reg <<- mor r/m
                Opcode.writeRegister(cpu, w, cpu.mrrRegIndex, sum);
            }
            else {
                // mod r/m <<- reg
                if ((cpu.mrrMod ^ 0b11) == 0) {
                    // mod r/m is a register
                    Opcode.writeRegister(cpu, w, cpu.mrrModRegIndex, sum);
                } else {
                    // mod r/m is memory
                    if (w) {
                        cpu.mem16(cpu.mrrModEA, sum & 0xFFFF);
                    } else {
                        cpu.mem8( cpu.mrrModEA, sum & 0x00FF);
                    }
                }
            }
        }
    }


    // SUB,
    // SUB (af,cf,of,pf,sf,zf)
    //    Reg/mem and reg to either   001010dw mrr   disp     disp
    //    imm from reg/mem            100000sw m101r disp     disp    data   data(if s:w=01)
    //    imm from accum              0010110w data  data(w=1)
    // SBB (af,cf,of,pf,sf,zf)  dst = dst - src - cf, ds
    //    Reg/mem and reg to either   000110dw mrr   disp     disp
    //    imm from reg/mem            100000sw m011r disp     disp    data   data(if s:w=01)
    //    imm from accum              0001110w data  data(w=1)
    // DEC (af,of,pf,sf,zf  not cf)
    //    reg/mem                     1111111w m001r disp     disp
    //    reg                         01001reg
    // NEG (af,cf,of,pf,sf,zf) dst <- 0-dst,   0->0,  -128->-128 OF, -32768->-32768 OF, CF always except dst==0 - reset CF
    //    change sign                 1111011w m011r disp     disp
    public static class Sub
    {
        public static void flags(Cpu cpu, boolean w, int dst, int src)
        {
            int zmask, cmask, smask, wValue, value;

            if (w) {
                zmask = Cpu.WORD_MASK_Z;
                cmask = Cpu.WORD_MASK_C;
                smask = Cpu.WORD_MASK_S;
            } else {
                zmask = Cpu.BYTE_MASK_Z;
                cmask = Cpu.BYTE_MASK_C;
                smask = Cpu.BYTE_MASK_S;
            }

            value = dst - src;

            // value without carry
            wValue = value & zmask;

            // (af,cf,of,pf,sf,zf)
            int bitCount = Integer.bitCount(wValue & Cpu.BYTE_MASK_Z);
            if ((bitCount & 0x1) == 0) cpu.setFlag(Cpu.FLAG_PF); else cpu.resetFlag(Cpu.FLAG_PF);
            if ((value & cmask) != 0)  cpu.setFlag(Cpu.FLAG_CF); else cpu.resetFlag(Cpu.FLAG_CF);
            if ((wValue & smask) != 0) cpu.setFlag(Cpu.FLAG_SF); else cpu.resetFlag(Cpu.FLAG_SF);
            if ((wValue & zmask) == 0) cpu.setFlag(Cpu.FLAG_ZF); else cpu.resetFlag(Cpu.FLAG_ZF);

            // this is sub specific
            if ( ((dst & smask) != (src & smask)) &&
                    ((dst & smask) != (value & smask)) )
                cpu.setFlag(Cpu.FLAG_OF);
            else
                cpu.resetFlag(Cpu.FLAG_OF);


            // AF only base on lower nipple of low byte,
            // check for borrow from the high nibble
            // todo: check for 8|4 bit nibble (here=8 and add=4)
            if ((dst & 0xFF) < (src & 0xFF))
                cpu.setFlag(Cpu.FLAG_AF);
            else
                cpu.resetFlag(Cpu.FLAG_AF);
        }

        public static class SubRmR extends Opcode
        {
            @Override
            public void execute(Cpu cpu, int opcode)
            {
                cpu.readAndProcessModRegRm(opcode);
                boolean w = (opcode & 0b0000_0001) == 0b01;
                boolean d = (opcode & 0b0000_0010) == 0b10;

                if (d) {
                    // reg <<- mor r/m
                    int delta = cpu.mrrRegValue + cpu.mrrModValue;
                    Sub.flags(cpu, w, cpu.mrrRegValue, cpu.mrrModValue);
                    Opcode.writeRegister(cpu, w, cpu.mrrRegIndex, delta);
                }
                else {
                    // mod r/m <<- reg
                    int delta = cpu.mrrModValue - cpu.mrrRegValue;
                    Sub.flags(cpu, w, cpu.mrrModValue, cpu.mrrRegValue);
                    if ((cpu.mrrMod ^ 0b11) == 0) {
                        // mod r/m is a register
                        Opcode.writeRegister(cpu, w, cpu.mrrModRegIndex, delta);
                    } else {
                        // mod r/m is memory
                        if (w) {
                            cpu.mem16(cpu.mrrModEA, delta & 0xFFFF);
                        } else {
                            cpu.mem8( cpu.mrrModEA, delta & 0x00FF);
                        }
                    }
                }
            }
        }

        //    imm from reg/mem            100000sw m101r disp     disp    data   data(if s:w=01)
        public static class SubRmImm extends Opcode {
            @Override
            public void execute(Cpu cpu, int opcode)
            {
                cpu.readAndProcessModRegRm(opcode);
                boolean w       = (opcode & 0b0000_0001) == 0b01;
                boolean imm16   = (opcode & 0b0000_0011) == 0b01;

                // extract immediate value,
                // size depends on s:w==0:1 bits
                int imm;
                if (imm16) {
                    imm = cpu.read16();
                } else {
                    imm = cpu.read8();
                }

                // mod r/m <<- imm
                Sub.flags(cpu, w, cpu.mrrModValue, imm);
                int delta = cpu.mrrModValue - imm;

                if ((cpu.mrrMod ^ 0b11) == 0) {
                    // mod r/m is a register
                    Opcode.writeRegister(cpu, w, cpu.mrrModRegIndex, delta);
                } else {
                    // mod r/m is memory
                    if (w) {
                        cpu.mem16(cpu.mrrModEA, delta & 0xFFFF);
                    } else {
                        cpu.mem8( cpu.mrrModEA, delta & 0x00FF);
                    }
                }
            }
        }

        //    imm from accum              0010110w data  data(w=1)   [(ax|al) - imm]
        public static class SubAccImm extends Opcode
        {
            @Override
            public void execute(Cpu cpu, int opcode) {
                boolean w       = (opcode & 0b0000_0001) == 0b01;

                // accumulator
                int ax = cpu.registers[Cpu.AX];

                // extract immediate value,
                // size depends on w bit
                int imm;
                if (w) {
                    imm = cpu.read16();
                } else {
                    imm = cpu.read8();
                    // use only AL part
                    ax &= 0x000F;
                }

                Sub.flags(cpu, w, ax, imm);

                int delta = ax - imm;
                Opcode.writeRegister(cpu, w, Cpu.AX, delta);
            }
        }
    }

    // CMP
    //    Reg/mem and reg       001110dw mrr    disp disp
    //    Imm with reg/mem      100000sw m111r  disp disp data data(s:w=01)
    //    Imm wth accumulator   0011110w data data(w=1) // error in intel manual @p166 table 4-12
    //
    public static class Cmp {

        public static class CmpRmR extends Opcode
        {
            public void execute(Cpu cpu, int opcode)
            {
                cpu.readAndProcessModRegRm(opcode);
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


        //    Imm with reg/mem      100000sw m111r  disp disp data data(s:w=01)
        public static class CmpRmImm extends Opcode
        {
            public void execute(Cpu cpu, int opcode)
            {
                cpu.readAndProcessModRegRm(opcode);
                boolean w       = (opcode & 0b0000_0001) == 1;
                boolean imm16   = (opcode & 0b0000_0011) == 0b01;

                // extract immediate value,
                // size depends on s:w==0:1 bits
                int imm;
                if (imm16) {
                    imm = cpu.read16();
                } else {
                    imm = cpu.read8();
                }

                Sub.flags(cpu, w, cpu.mrrModValue, imm);
            }
        }


        //    Imm wth accumulator   0011110w data data(w=1) // error in intel manual @p166 table 4-12
        public static class CmpAccImm extends Opcode
        {
            @Override
            public void execute(Cpu cpu, int opcode)
            {
                boolean w = (opcode & 0b0000_0001) == 1;

                // accumulator
                int ax = cpu.registers[Cpu.AX];

                // extract immediate value,
                // size depends on w bit
                int imm;
                if (w) {
                    imm = cpu.read16();
                } else {
                    imm = cpu.read8();
                    // use only AL part
                    ax &= 0x000F;
                }

                Sub.flags(cpu, w, ax, imm);
            }
        }
    }


    public static class JeJz extends Opcode {
        @Override
        public void execute(Cpu cpu, int opcode)
        {
            int unsigned = cpu.read8();
            if ((cpu.flags & FLAG_ZF) != 0) {
                // use as signed
                cpu.ip += (byte)unsigned;
            }
        }
    }

}
