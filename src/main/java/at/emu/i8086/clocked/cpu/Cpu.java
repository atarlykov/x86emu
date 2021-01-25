package at.emu.i8086.clocked.cpu;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * state of the emulates i8086 cpu
 */
public class Cpu
{

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
    public static final int ES = 0;
    public static final int CS = 1;
    public static final int SS = 2;
    public static final int DS = 3;

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
     * flags positions inside the register
     */
    public static final int FLAG_CF_POS = 0;
    public static final int FLAG_PF_POS = 2;
    public static final int FLAG_ZF_POS = 6;
    public static final int FLAG_SF_POS = 7;
    public static final int FLAG_DF_POS = 10;
    public static final int FLAG_OF_POS = 11;

    /**
     * Zero, sign and carry check masks, byte mode
     */
    public static final int BYTE_MASK       = 0x00FF;
    public static final int BYTE_MASK_SIGN  = 0x0080;
    public static final int BYTE_MASK_CARRY = 0x0100;
    public static final int BYTE_POS_SIGN   = 7;
    public static final int BYTE_POS_CARRY  = 8;
    /**
     * Zero sign and curry check masks, word mode
     */
    public static final int WORD_MASK       = 0x0FFFF;
    public static final int WORD_MASK_SIGN  = 0x08000;
    public static final int WORD_MASK_CARRY = 0x10000;
    public static final int WORD_POS_SIGN   = 15;
    public static final int WORD_POS_CARRY  = 16;


    /**
     * Interrupts
     */
    public static final int INT_0_DIVIDE_ERROR      = 0;
    public static final int INT_1_SINGLE_STEP       = 1;
    public static final int INT_2_NMI               = 2;
    public static final int INT_3_BREAKPOINT        = 3;
    public static final int INT_4_OVERFLOW          = 4;
    public static final int INT_8_IRQ0_SYS_TIMER    = 8;
    public static final int INT_9_IRQ1_KBD_READY    = 9;
    public static final int INT_0B_IRQ3_COM2        = 0x0B;
    public static final int INT_0C_IRQ4_COM1        = 0x0C;
    public static final int INT_0D_IRQ5_DISK_LPT    = 0x0D;
    public static final int INT_0E_IRQ6_FLOPPY      = 0x0E;
    public static final int INT_0F_IRQ7_LPT         = 0x0F;

    public static final int INT_10_VIDEO            = 0x10;
    public static final int INT_11_BIOS_EQUIPMENT   = 0x11;
    public static final int INT_12_BIOS_MEM_SIZE    = 0x12;
    public static final int INT_13_DISK             = 0x13;
    public static final int INT_19_SYS_BOOT_LOAD    = 0x19;
    public static final int INT_1A_TIME             = 0x1A;
    public static final int INT_1C_TIME             = 0x1C;



    public void nmi(int number) {
    }

    
    /**
     * Executes interrupt of the specified number,
     * doesn't perform any checks.
     * Can be used for cpu interrupts (int x, into, div zero) directly as they are not masked,
     * can also be used for NMI and INTR(pic) after mask checks.
     *
     * Saves flags and cs:ip into the stack, resets TF and IF
     * @param number number of the interrupt
     */
    public void interrupt(int number)
    {
        push16(flags);
        flags &= (~Cpu.FLAG_TF | ~Cpu.FLAG_IF);
        push16(registers[Cpu.CS]);
        push16(ip);

        // interrupt vector
        int offset = number * 4;
        int cs = mread16Direct(0x0000, offset + 2);
        writeSegment(Cpu.CS, cs);
        ip = mread16Direct(0x0000, offset);
    }




    // common registers AX,CX,DX,BX,SP,BP,SI,DI
    int[] registers = new int[8];

    // segment registers ES,CS,SS,DS
    int[] segments = new int[4];

    // effective linear address of a segment
    // that is default for the CURRENT opcode,
    // calculated based on opcode operands and
    // possible segment override prefix
    int effOpcodeMemSegment;

    // index of the segment that must be used
    // instead of the default opcode segment
    // for the CURRENT opcode,
    // value (-1) means - there is no override,
    // value is controlled by the @Control.Segment opcode
    int overrideSegmentIndex = -1;

    // halt state of the processor
    boolean hlt;

    // lock prefix for the current opcode
    boolean lockPrefix;
    // rep prefix for the current opcode
    boolean repPrefix;
    // value of the rep prefix
    int     rep;




    // instruction pointer
    int ip;

    // flag register
    int flags;

    // various details from
    // parsed mode-ref-reg/mem byte
    int mrrMod;             // mod-reg-r/m:  mod part
    int mrrReg;             // mod-reg-r/m:  reg part
    int mrrRm;              // mod-reg-r/m:  r/m part

    int mrrModRegIndex;     // mod-reg-r/m:  mod == 11  ->> r/m parsed as register index
    int mrrModRegValue;     // mod-reg-r/m:  mod == 11  ->> register[mrrModRegIndex]  depends on w(8|16)
    int mrrModEA;           // mod-reg-r/m:  mod ==!11  ->> mod-r/m parsed and calculated as ea
    int mrrModEAValue;      // mod-reg-r/m:  mod ==!11  ->> preloaded value from mem[xx : mrrModEA] depends on w(8|16)
    int mrrModValue;        // mod-reg-r/m:  any mod    ->> mrrModRegValue | mrrModEAValue depending on mod

    int mrrRegIndex;        // mod-reg-r/m:  reg part is parsed as register index
    int mrrRegValue;        // mod-reg-r/m:  register[mrrRegIndex] depends on w(8|16)

    // current clocks updated by all opcodes
    long clocks = 0;

    void clocks(short delta) {
        clocks += delta;
    }

    /**
     *
     * writes one of common registers based on reg index and byte/word mode,
     * value is cleared with byte (0xff) or word (0xffff) mask
     * @param word word mode (1) and byte mode (0)
     * @param regIndex index of the register to write
     * @param value value to write (8 or 16 bits based on word)
     */
    public void writeRegister(boolean word, int regIndex, int value)
    {
        if (word) {
            registers[regIndex] = value & 0xFFFF;
        } else {
            int regPartIndex = regIndex & 0b11;
            int regFullValue = registers[regPartIndex];

            if ((regIndex & 0b100) == 0) {
                // lower part (AL, ..)
                regFullValue &= 0xFF00;
                regFullValue |= (value & 0xFF);
            } else {
                // upper part (AH, ..)
                regFullValue &= 0x00FF;
                regFullValue |= (value & 0xFF) << 8;
            }
            registers[regPartIndex] = regFullValue;
        }
    }

    /**
     * writes one of common registers based on reg index
     * value is cleared with word (0xffff) mask
     * @param regIndex index of the register to write
     * @param value value to write (16 bits)
     */
    public void writeRegisterWord(int regIndex, int value)
    {
        registers[regIndex] = value & 0xFFFF;
    }

    /**
     * write upper byte of the specified register
     * @param regIndex register index
     * @param value byte value to write into upper part
     */
    public void writeRegisterUpperByte(int regIndex, int value)
    {
        int regFullValue = registers[regIndex];
        regFullValue &= 0x00FF;
        regFullValue |= (value & 0xFF) << 8;
        registers[regIndex] = regFullValue;
    }

    /**
     * write low byte of the specified register
     * @param regIndex register index
     * @param value byte value to write into upper part
     */
    public void writeRegisterLowByte(int regIndex, int value)
    {
        int regFullValue = registers[regIndex];
        regFullValue &= 0xFF00;
        regFullValue |= (value & 0xFF);
        registers[regIndex] = regFullValue;
    }

    /**
     * writes segment register indexed by regIndex,
     * value is cleared with 0xffff mask
     * @param regIndex index of segment register
     * @param value value to write
     */
    public void writeSegment(int regIndex, int value)
    {
        segments[regIndex] = value & 0xFFFF;
    }



    /**
     * Reads mod-reg-r/m byte pointed with instruction pointer and parses it
     * into internal cpu fields ready to be processed by an opcode function.
     * (some instructions break this contract - like mov SR,R)
     *
     *  table 4-10
     *  ------------------+----------+--------------+----------------+------------------+------------
     *  mod=11            |               effective address calculation                 |
     *  ------------------+----------+--------------+----------------+------------------+    def
     *  r/m    w=0    w=1 |     r/m  |  mod=00      |  mod=01        |  mod=10          |  segment
     *  ------------------+----------+--------------+----------------+------------------+------------
     *  000    al     ax  |     000  |  bx + si     |  bx + si + d8  |  bx + si + d16   |    ds
     *  001    cl     cx  |     001  |  bx + di     |  bx + di + d8  |  bx + di + d16   |    ds
     *  010    dl     dx  |     010  |  bp + si     |  bp + si + d8  |  bp + si + d16   |      ss
     *  011    bl     bx  |     011  |  bp + di     |  bp + di + d8  |  bp + di + d16   |      ss
     *  100    ah     sp  |     100  |  si          |  si + d8       |  si + d16        |    ds
     *  101    ch     bp  |     101  |  di          |  di + d8       |  di + d16        |    ds
     *  110    bh     si  |     110  |  direct addr |  bp + d8       |  bp + d16        |      ss (if bp)
     *  111    dh     di  |     111  |  bx          |  bx + d8       |  bx + d16        |    ds
     *
     *  Description                                         Clock Cycles
     *  ------------------------------------------------------------------------
     * 	Displacement                                            6
     * 	Base or Index (BX,BP,SI,DI)                             5
     * 	Displacement+(Base or Index)                            9
     * 	Base+Index (BP+DI,BX+SI)                                7
     * 	Base+Index (BP+SI,BX+DI)                                8
     * 	Base+Index+Displacement (BP+DI,BX+SI)                  11
     * 	Base+Index+Displacement (BP+SI+disp,BX+DI+disp)	       12
     *
     *  - add 4 cycles for word operands at odd addresses
     * 	- add 2 cycles for segment override
     *
     * See Cpu.mrrXXX fields for detailed description
     * @param opcode current opcode to which mod-reg-r/m belongs to
     */
    int readModRegRm(int opcode)
    {
        // read and divide mod-reg-r/m byte into local variables
        int mrr = ipRead8();
        mrrMod = mrr >> 6;
        mrrReg = (mrr & 0b00111000) >> 3;
        mrrRm  = (mrr & 0b00000111);

        // check if opcode is in word (16 bit) mode ir 8 bit mode
        boolean w = (opcode & 0b0000_0001) == 1;

        // parse -reg- part (it could be an opcode extension sometimes)
        mrrRegIndex = mrrReg;
        if (w) {
            // 16 bit mode, all 8 registers
            mrrRegValue = registers[mrrRegIndex];
        } else {
            // 8 bit mode, only 4 register - xH or xL
            // mrrRegIndex &= 0b011; <-- we need original index to distinguish xH/xL later on write
            // read full register and adjust
            mrrRegValue = registers[mrrRegIndex & 0b011];
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
                if ((mrrRm & 0b100) == 0) {
                    mrrModRegValue &= 0xFF;
                } else {
                    mrrModRegValue >>= 8;
                }
            }
            // provide unified access to mod-r/m value
            mrrModValue = mrrModRegValue;
        }
        else {
            // mod!=11, mod+r/m give effective address

            // default memory segment for the opcode
            int eff = Cpu.DS;

            if (mrrMod == 0b00)
            {
                // no displacement
                switch (mrrRm) {
                    case 0b000:               mrrModEA = registers[BX] + registers[SI]; clocks += 7; break;
                    case 0b001:               mrrModEA = registers[BX] + registers[DI]; clocks += 8; break;
                    case 0b010: eff = Cpu.SS; mrrModEA = registers[BP] + registers[SI]; clocks += 8; break;
                    case 0b011: eff = Cpu.SS; mrrModEA = registers[BP] + registers[DI]; clocks += 7; break;
                    case 0b100:               mrrModEA = registers[SI];                 clocks += 5; break;
                    case 0b101:               mrrModEA = registers[DI];                 clocks += 5; break;
                    case 0b110: eff = Cpu.DS; mrrModEA = ipRead16();                    clocks += 6; break;    /* segment is DS */
                    case 0b111:               mrrModEA = registers[BX];                 clocks += 5; break;
                }
            }
            else {
                // 8 or 16 bit displacement
                switch (mrrRm) {
                    case 0b000:               mrrModEA = registers[BX] + registers[SI]; clocks += 11; break;
                    case 0b001:               mrrModEA = registers[BX] + registers[DI]; clocks += 12; break;
                    case 0b010: eff = Cpu.SS; mrrModEA = registers[BP] + registers[SI]; clocks += 12; break;
                    case 0b011: eff = Cpu.SS; mrrModEA = registers[BP] + registers[DI]; clocks += 11; break;
                    case 0b100:               mrrModEA = registers[SI];                 clocks += 9; break;
                    case 0b101:               mrrModEA = registers[DI];                 clocks += 9; break;
                    case 0b110: eff = Cpu.SS; mrrModEA = registers[BP];                 clocks += 9; break;
                    case 0b111:               mrrModEA = registers[BX];                 clocks += 9; break;
                }

                int value;
                if (mrrMod == 0b01) {
                    value = ipRead8();
                } else {
                    // mrrMod === 0b10
                    value = ipRead16();
                }
                mrrModEA += value;
            }


            // prepare linear address of opcode mem segment,
            // will be used in mread/mwrite
            if (overrideSegmentIndex != -1) {
                effOpcodeMemSegment = segments[overrideSegmentIndex] << 4;
            } else {
                effOpcodeMemSegment = segments[eff] << 4;
            }

            // pre-read value from memory, it will be consumed
            // by the operation or will be updated (so let it be in cache)
            mrrModEAValue = mread(w, mrrModEA);

            // provide unified access to mod-r/m value
            mrrModValue = mrrModEAValue;
        }

        return mrr;
    }

    /**
     * writes value to a register or it's part as pointer by mod-reg-r/m,
     * with mod=11 and (r/m, word) pointing to a destination
     * @param word word or byte mode
     * @param regIndex index of the register to write, MUST follow the word state,
     *                 true -> 0..7 and false -> 0..3
     * @param value value to write
     */
    public void writeModRegRmRegister(boolean word, int regIndex, int value)
    {
        if (word) {
            registers[regIndex] = value & 0xFFFF;
        } else {
            /*
                seems filtered index is not necessary here as index is 0..3
                and upper/lower part is encoded with mrr
             */
            int regPartIndex = regIndex & 0b11; 
            int regFullValue = registers[regPartIndex];
            if ((mrrRm & 0b100) == 0) {
                // lower part (AL, ..)
                regFullValue &= 0xFF00;
                regFullValue |= (value & 0xFF);
            } else {
                // upper part (AH, ..)
                regFullValue &= 0x00FF;
                regFullValue |= (value & 0xFF) << 8;
            }
            registers[regPartIndex] = regFullValue;
        }
    }

    /**
     * writes value to register/mem pointed by mod-r/m parts of mrr
     *
     * @param w word or byte
     * @param value value to wrute
     */
    public void writeByModRegRm(boolean w, int value)
    {
        if ((mrrMod ^ 0b11) == 0) {
            // mod r/m is a register
            writeModRegRmRegister(w, mrrModRegIndex, value);
        } else {
            // mod r/m is memory
            mwrite(w, mrrModEA, value);
        }
    }


    /**
     *   logical address sources (table 2-2)
     * -------------------------------+---------+----------+---------+
     *   type of mem ref              | def seg |  alt seg |  offset |
     * -------------------------------+---------+----------+---------+
     *   instruction fetch            |   cs    |   none   |    ip   |
     *   stack operation              |   ss    |   none   |    sp   |
     *   variable (except following)  |   ds    | cs,es,ss |    ea   |
     *   str src                      |   ds    | cs,es,ss |    si   |
     *   str dst                      |   es    |   none   |    di   |
     *   bp used as base reg          |   ss    | cs,ds,ss |    ea   |
     */

    byte[] memory = new byte[1024 * 1024];

    /**
     * reads from default opcode segment as S:offset, uses current value of S or
     * some other segment if override opcode prefix is used (cs,es,ss)
     * @param word reads word if true and byte if false
     * @param offset offset in data segment
     * @return data read
     */
    int mread(boolean word, int offset)
    {
        int la = effOpcodeMemSegment + offset;
        if (word) {
            return (memory[la] & 0xFF) | ((memory[la + 1] & 0xFF) << 8);
        } else {
            return memory[la] & 0x00FF;
        }
    }

    /**
     * reads from default opcode segment as S:offset, uses current value of S or
     * some other segment if override opcode prefix is used (cs,es,ss)
     * @param word reads word if true and byte if false
     * @param rSeg index of segment register
     * @param offset offset in data segment
     * @return data read
     */
    int mread(boolean word, int rSeg, int offset)
    {
        int la = (segments[rSeg] << 4) + offset;
        if (word) {
            return ((memory[la] & 0xFF)) | ((memory[la + 1] & 0xFF) << 8);
        } else {
            return ((int)memory[la]) & 0x00FF;
        }
    }

    /**
     * reads from default opcode segment as S:offset, uses current value of S or
     * some other segment if override opcode prefix is used (cs,es,ss)
     * @param offset offset in data segment
     * @return data read
     */
    int mread8(int offset)
    {
        int la = effOpcodeMemSegment + offset;
        return memory[la] & 0x00FF;
    }

    /**
     * reads from the specified segment as S:offset
     * @param rSeg index of segment register
     * @param offset offset in data segment
     * @return data read
     */
    int mread8(int rSeg, int offset)
    {
        int la = (segments[rSeg] << 4) + offset;
        return (memory[la] & 0xFF);
    }

    /**
     * reads from default opcode segment as S:offset, uses current value of S or
     * some other segment if override opcode prefix is used (cs,es,ss)
     * @param offset offset in data segment
     * @return data read
     */
    int mread16(int offset)
    {
        int la = effOpcodeMemSegment + offset;
        return (memory[la] & 0xFF) | ((memory[la + 1] & 0xFF) << 8);
    }

    /**
     * reads from the specified segment as S:offset
     * @param rSeg index of segment register
     * @param offset offset in data segment
     * @return data read
     */
    int mread16(int rSeg, int offset)
    {
        int la = (segments[rSeg] << 4) + offset;
        return (memory[la] & 0xFF) | ((memory[la + 1] & 0xFF) << 8);
    }

    /**
     * reads from the specified segment and offset
     * @param seg value of a segment
     * @param offset offset in segment
     * @return data read
     */
    int mread16Direct(int seg, int offset)
    {
        int la = (seg << 4) + offset;
        return (memory[la] & 0xFF) | ((memory[la + 1] & 0xFF) << 8);
    }

    /**
     * reads by the absolute memory address (20 bit)
     * @param offset offset from memory base
     * @return data read
     */
    int mread16Direct(int offset)
    {
        int la = offset;
        return (memory[la] & 0xFF) | ((memory[la + 1] & 0xFF) << 8);
    }




    /**
     * writes to default opcode segment as S:offset, uses current value of S or
     * some other segment if override opcode prefix is used (cs,es,ss)
     * @param word writes word if true and byte if false
     * @param offset offset in data segment
     * @param value value to write
     */
    void mwrite(boolean word, int offset, int value)
    {
        int la = effOpcodeMemSegment + offset;
        memory[la] = (byte)value;
        if (word) {
            memory[la + 1] = (byte)(value >> 8);
        }
    }

    void mwrite16(int offset, int value)
    {
        int la = effOpcodeMemSegment + offset;
        memory[la] = (byte)value;
        memory[la + 1] = (byte)(value >> 8);
    }

    void mwrite16(int rSeg, int offset, int value)
    {
        int la = (segments[rSeg] << 4) + offset;
        memory[la] = (byte)value;
        memory[la + 1] = (byte)(value >> 8);
    }

    void mwrite8(int rSeg, int offset, int value)
    {
        int la = (segments[rSeg] << 4) + offset;
        memory[la] = (byte)value;
    }

    /**
     * stack support routines
     *  ss:sp    ->> [(ss << 4) | sp]
     *  push ax  ->>  sp -= 2; [ss:sp] = ax;
     *  pop ax   ->>  ax = [ss:sp]; sp += 2;
     *  ss:00 is the full stack
     */

    /**
     * generic stack push method, uses SS:SP as the position
     * to place the data (SP-1 for byte, and SP-2 fow word)
     * @param word word of byte mode
     * @param value value to place
     */
    void push(boolean word, int value)
    {
        registers[SP] -= 1;
        int la = (segments[Cpu.SS] << 4) + registers[SP];
        memory[la] = (byte) value;
        if (word) {
            registers[SP] -= 1;
            memory[la + 1] = (byte)(value >> 8);
        }
    }

    /**
     * stack push method, uses SS:SP-2 as the position
     * @param value value to place
     */
    void push16(int value)
    {
        writeRegisterWord(SP, registers[SP] - 2);
        int la = (segments[Cpu.SS] << 4) + registers[SP];
        memory[la] = (byte) value;
        memory[la + 1] = (byte)(value >> 8);
    }

    /**
     * stack pop method, uses SS:SP as the position
     * @return word read from stack
     */
    int pop16()
    {
        int la = (segments[Cpu.SS] << 4) + registers[SP];
        writeRegisterWord(SP, registers[SP] + 2);
        return (memory[la] & 0xFF) | ((memory[la + 1] & 0xFF) << 8);
    }

    /**
     * stack push method, uses SS:SP-1 as the position
     * @param value value to place
     */
    void push8(int value)
    {
        writeRegisterWord(SP, registers[SP] - 1);
        int la = (segments[Cpu.SS] << 4) + registers[SP];
        memory[la] = (byte) value;
    }

    /**
     * stack pop method, uses SS:SP as the position
     * @return byte read from stack
     */
    int pop8()
    {
        int la = (segments[Cpu.SS] << 4) + registers[SP];
        writeRegisterWord(SP, registers[SP] + 1);
        return memory[la] & 0xFF;
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
     * reads next byte moving instruction pointer by one
     * @return byte read
     */
    int ipRead8()
    {
        // todo could be optimized with cached CS and short ip
        int tmp = memory[(segments[Cpu.CS] << 4) + (ip & 0xFFFF)] & 0xFF;
        ip++;
        return tmp;
    }

    int ipRead8WithSign()
    {
        int tmp = memory[(segments[Cpu.CS] << 4) + (ip & 0xFFFF)];
        ip++;
        return tmp;
    }

    /**
     * reads next two bytes moving instruction pointer by two,
     * reads low byte than hi byte
     * @return word read
     */
    int ipRead16()
    {
        // todo that could overflow ip
        int la = (segments[Cpu.CS] << 4) + (ip & 0xFFFF);
        int tmp = (memory[la] & 0xFF) | ((memory[la + 1] & 0xFF) << 8);
        ip += 2;
        return tmp;
    }


    void pout(boolean word, int port, int value) {
        PortHandler handler = ports[port];
        if (handler != null) {
            handler.pout(word, port, value);
        } else {
            System.out.printf("-XX OUT[%04X] no handler%n", port);
        }
    }

    int pin(boolean word, int port) {
        PortHandler handler = ports[port];
        if (handler != null) {
            int value = ports[port].pin(word, port);
            return value;
        } else {
            System.out.printf("-XX  IN[%04X] no handler%n", port);
            return 0;
        }
    }

    PortHandler[] ports = new PortHandler[65536];

    static class PortHandler {

        void pout(boolean word, int port, int value) {
        }

        int pin(boolean word, int port) {
            return 0;
        }
    }

    /**
     * initializes internal state, opcodes, etc.
     */
    public void init()
    {
        Arrays.fill(opcodes, null);
        for (int i = 0; i < configurations.length; i++) {
            ClockedOpcodeConfiguration cfgProvider = configurations[i];
            cfgProvider.getClockedConfiguration(opcodes);
        }
    }

    /**
     * resets cpu to the initial state
     */
    void reset()
    {
        Arrays.fill(registers, 0x0000);
        Arrays.fill(segments, 0x0000);
        segments[CS] = 0xF000;
        ip = 0xFFF0;
        flags = 0;
        hlt = false;
        overrideSegmentIndex = -1;
    }

    /**
     * Runs one step, executing the next opcode
     * pointed by cs:ip.
     * Not intended to be run inside a main cycle,
     * byt could be run from other opcodes (prefixes), etc.
     */
    void step()
    {
        // reset default segment as some opcodes could
        // override it (EA in mod-reg-rm)
        effOpcodeMemSegment = segments[Cpu.DS] << 4;

        // read next byte from cs:ip,
        // that could be an opcode
        // or some prefix
        int code = ipRead8();

        Opcode opcode = opcodes[code];
        if (opcode == null) {
            hlt = true;
            return;
        }

        // call opcode implementation
        opcode.execute(this, code);
    }


    /**
     * registered opcodes
     */
    Opcode[] opcodes = new Opcode[256];

    /**
     * configuration of opcodes, parsed on init
     * and populated into opcodes array
     */
    ClockedOpcodeConfiguration[] configurations = new ClockedOpcodeConfiguration[] {
            new Transfer(),
            new Cmp(),
            new Add(),
            new Sub(),
            new Multiplication(),
            new Division(),
            new Jmp(),
            new Stack(),
            new Logic(),
            new Control(),
            new Ports(),
            new Strings(),
            new Interrupt()
    };


    /**
     * base class for all opcodes,
     * NOTE: it's opcode responsibility to to provide used clocks information to the cpu,
     * this is done to simplify checks and minimize configuration usage in runtime
     */
    public static abstract class Opcode
    {
        /**
         * Typical cpu clocks to execute an instruction assuming it has been already
         * prefetched to instruction queue, doesn't include mod/reg/mr EA calculation.
         * Number of clocks for conditional instructions when base condition is true.
         */
        short clocks;
        /**
         * Alternate clocks:
         * - for conditional instructions: clocks when base condition is false.
         * - for bit shifts: clocks multiplier for CL
         * - for rep [string]: base clocks
         */
        short clocksAlt;
        /**
         * - for rep [string]: per iteration multiplier
         */
        short clocksAlt2;

        /**
         * method called by the pipeline after reading an instruction
         * @param cpu ref to cpu instance
         * @param opcode current opcode read from memory
         */
        public abstract void execute(Cpu cpu, int opcode);

        /**
         * sets PF, SF, ZF cpu flags based on the value provided
         * @param cpu ref to cpu
         * @param w word (true) or byte(false) mode
         * @param value operand
         */
        public static void flagsPsz(Cpu cpu, boolean w, int value)
        {
            if (w) {
                flagsPsz16(cpu, value);
            } else {
                flagsPsz8(cpu, value);
            }
        }

        /**
         * sets PF, SF and ZF flags based on the value provided (8 bit)
         * @param cpu ref to cpu
         * @param value value to test
         */
        public static void flagsPsz8(Cpu cpu, int value)
        {
            // value without carry and upper part
            value &= Cpu.BYTE_MASK;

            int bitCount = Integer.bitCount(value);

            //          ( clear three flags at once)
            cpu.flags = (cpu.flags & ~(Cpu.FLAG_PF | Cpu.FLAG_SF | Cpu.FLAG_ZF))
                    // parity at PF position
                    | ((bitCount & 0x1) << Cpu.FLAG_PF_POS)
                    // sign at SF position
                    | ((value >> (Cpu.BYTE_POS_SIGN - Cpu.FLAG_SF_POS)) & Cpu.FLAG_SF);

            // set pf af necessary
            if (value == 0) cpu.setFlag(Cpu.FLAG_ZF);
        }

        /**
         * sets PF, SF and ZF flags based on the value provided (16 bit)
         * @param cpu ref to cpu
         * @param value value to test
         */
        public static void flagsPsz16(Cpu cpu, int value)
        {
            // value without carry and upper part
            value &= Cpu.WORD_MASK;

            int bitCount = Integer.bitCount(value);

            // quickest path
            //          ( clear three flags at once)
            cpu.flags = (cpu.flags & ~(Cpu.FLAG_PF | Cpu.FLAG_SF | Cpu.FLAG_ZF))
                    // parity at PF position
                    | ((bitCount & 0x1) << Cpu.FLAG_PF_POS)
                    // sign at SF position
                    | ((value >> (Cpu.WORD_POS_SIGN - Cpu.FLAG_SF_POS)) & Cpu.FLAG_SF);

            // set pf af necessary
            if (value == 0) cpu.setFlag(Cpu.FLAG_ZF);
        }

        /**
         * sets PF, SF, ZF and CF flags based on the value provided (8 bit)
         * @param cpu ref to cpu
         * @param value value to test
         */
        public static void flagsPszc8(Cpu cpu, int value)
        {
            // value without carry and upper part
            int bValue = value & Cpu.BYTE_MASK;
            int bitCount = Integer.bitCount(bValue);

            //          ( clear flags at once)
            cpu.flags = (cpu.flags & ~(Cpu.FLAG_PF | Cpu.FLAG_SF | Cpu.FLAG_ZF | Cpu.FLAG_CF))
                    // parity at PF position
                    | ((bitCount & 0x1) << Cpu.FLAG_PF_POS)
                    // sign at SF position
                    | ((bValue >> (Cpu.BYTE_POS_SIGN - Cpu.FLAG_SF_POS)) & Cpu.FLAG_SF)
                    // carry bit (ORIGINAL, not masked value here)
                    | ((value >> Cpu.BYTE_POS_CARRY) & Cpu.FLAG_CF);


            // set pf af necessary (MASKED value)
            if (bValue == 0) cpu.setFlag(Cpu.FLAG_ZF);
        }

        /**
         * sets PF, SF, ZF and CF flags based on the value provided (16 bit)
         * @param cpu ref to cpu
         * @param value value to test
         */
        public static void flagsPszc16(Cpu cpu, int value)
        {
            // value without carry and upper part
            int wValue = value & Cpu.WORD_MASK;
            int bitCount = Integer.bitCount(wValue);

            //          ( clear flags at once)
            cpu.flags = (cpu.flags & ~(Cpu.FLAG_PF | Cpu.FLAG_SF | Cpu.FLAG_ZF | Cpu.FLAG_CF))
                    // parity at PF position
                    | ((bitCount & 0x1) << Cpu.FLAG_PF_POS)
                    // sign at SF position
                    | ((wValue >> (Cpu.WORD_POS_SIGN - Cpu.FLAG_SF_POS)) & Cpu.FLAG_SF)
                    // carry bit (ORIGINAL, not masked value here)
                    | ((value >> Cpu.WORD_POS_CARRY) & Cpu.FLAG_CF);


            // set pf af necessary (MASKED value)
            if (wValue == 0) cpu.setFlag(Cpu.FLAG_ZF);
        }
    }

    /**
     * Base class for all opcodes that take some fixed clock cycles to execute,
     * updates cpu clocks after opcode execution
     */
    public static abstract class FixedClockOpcode extends Opcode {
        @Override
        public void execute(Cpu cpu, int opcode)
        {
            executeClocked(cpu, opcode);
            // update global clocks
            cpu.clocks(this.clocks);
        }

        /**
         * method to be implemented by child classes
         * @param cpu ref to cpu
         * @param opcode opcode being executed
         */
        abstract void executeClocked(Cpu cpu, int opcode);
    }

    /**
     * Base class for opcodes that spend different number of cycle to execute,
     * mostly all conditional jumps,
     * NOTE: this is dummy class mostly to distinguish/verify configuration
     * and future use
     */
    public static abstract class ConditionalClockOpcode extends Opcode {
    }

    /**
     * Base class for string type opcodes that spend different number of cycle to execute,
     * NOTE: this is dummy class mostly to distinguish/verify configuration
     * and future use
     */
    public static abstract class StringClockOpcode extends Opcode {
    }


    /**
     * Some cpu commands share the same opcode and distinguished with 'reg' part of mod-reg-r/m byte
     * that follows the opcode, this provides a way to register several @{@link Opcode}
     * under the same opcode and call them dynamically based on mod-reg-r/m byte
     */
    public final /*todo remove final*/ static class MrrBasedDemux extends Opcode {
        /**
         * demuxed opcodes to be called based on mrr byte
         */
        final Opcode[] exits = new Opcode[256];

        @Override
        public void execute(Cpu cpu, int opcode)
        {
            int mrr = cpu.readModRegRm(opcode);
            exits[mrr].execute(cpu, opcode);
        }
    }

    /**
     * interface to provide configuration of opcodes with machine clock timings,
     * partly used to save configuration in some independent way to allow
     * extraction it from the code to external configuration
     */
    public interface ClockedOpcodeConfiguration {
        /**
         * base marker interface for configurations
         */
        interface Configuration {}

        /**
         * main method to be implemented by opcode classes and provide information
         * about contained opcodes
         * @param registry to bi populates with opcodes via config() methods
         */
        void getClockedConfiguration(Opcode[] registry);

        /**
         * implements configuration for any target opcode,
         * one byte or with mrr part
         */
        class SimpleConfiguration implements Configuration
        {
            /**
             * defines types to support different
             * schemes for clocks calculation
             */
            enum Type {
                /**
                 * just one fixed clock
                 */
                SIMPLE,
                /**
                 * conditional type, 2 clocks - true and false cases
                 */
                COND,
                /**
                 * string operations with REP prefix, 3 clocks
                 */
                REP,
                /**
                 * cl based operations (shifts), 2 clocks
                 */
                CL
            }

            /**
             * type of the configuration
             */
            Type type = Type.SIMPLE;

            /**
             * Typical cpu clocks to execute an instruction assuming it has been already
             * prefetched to instruction queue, doesn't include mod/reg/mr EA calculation.
             * Number of clocks for conditional instructions when base condition is true.
             */
            int     clocks;
            /**
             * Alternate clocks:
             * - for conditional instructions: clocks when base condition is false.
             * - for bit shifts: clocks multiplier for CL
             * - for rep [string]: base clocks
             */
            int     clocksAlt;
            /**
             * - for rep [string]: per iteration multiplier
             */
            int     clocksAlt2;
            Class<? extends Opcode> opClass;
            String  mnemonic;
        }

        /**
         * container to opcodes that depend on mrr byte
         */
        class ModRegRmConfiguration implements Configuration {
            Map<String, SimpleConfiguration> mrr = new HashMap<>();
        }

        /**
         * service DSL method to simplify configurations
         * @param clocks number of clock
         * @param clocksAlt number of clock for conditional instructions with failed condition
         * @param opClass class that implements the opcode
         * @param mnemonic mnemonic
         * @param comment comment, could be used later
         * @return descriptor
         */
        default SimpleConfiguration S(int clocks, int clocksAlt, Class<? extends Opcode> opClass, String mnemonic, String comment)
        {
            SimpleConfiguration config = R(clocks, clocksAlt, 0, opClass, mnemonic, comment);
            config.type = SimpleConfiguration.Type.SIMPLE;
            return config;
        }

        /**
         * service DSL method to simplify configurations of conditional clocks
         * @param clocks number of clock in case of success check
         * @param clocksAlt number of clock for conditional instructions with failed condition
         * @param opClass class that implements the opcode
         * @param mnemonic mnemonic
         * @param comment comment, could be used later
         * @return descriptor
         */
        default SimpleConfiguration J(int clocks, int clocksAlt, Class<? extends Opcode> opClass, String mnemonic, String comment)
        {
            SimpleConfiguration config = R(clocks, clocksAlt, 0, opClass, mnemonic, comment);
            config.type = SimpleConfiguration.Type.COND;
            return config;
        }

        /**
         * service DSL method to simplify CL based configurations
         * @param clocks base number of clocks
         * @param clocksAlt number of clock per bit
         * @param opClass class that implements the opcode
         * @param mnemonic mnemonic
         * @param comment comment, could be used later
         * @return descriptor
         */
        default SimpleConfiguration C(int clocks, int clocksAlt, Class<? extends Opcode> opClass, String mnemonic, String comment)
        {
            SimpleConfiguration config = R(clocks, clocksAlt, 0, opClass, mnemonic, comment);
            config.type = SimpleConfiguration.Type.CL;
            return config;
        }


        /**
         * service DSL method to simplify configurations or REP type
         * @param clocks number of clocks
         * @param clocksAlt alternate clocks
         * @param clocksAlt2 alternate2 clocks
         * @param opClass class that implements the opcode
         * @param mnemonic mnemonic
         * @param comment comment, could be used later
         * @return descriptor
         */
        default SimpleConfiguration R(int clocks, int clocksAlt, int clocksAlt2, Class<? extends Opcode> opClass, String mnemonic, String comment)
        {
            SimpleConfiguration c = new SimpleConfiguration();
            c.type = SimpleConfiguration.Type.REP;
            c.clocks = clocks;
            c.clocksAlt = clocksAlt;
            c.clocksAlt2 = clocksAlt2;
            c.opClass = opClass;
            c.mnemonic = mnemonic;
            return c;
        }

        /**
         * creates configured instance of the opcode based on the configuration provided
         * @param opConfig configuration
         * @return configured instance
         */
        default Opcode op(SimpleConfiguration opConfig)
        {
            try {
                Opcode opcode = opConfig.opClass.getDeclaredConstructor().newInstance();
                opcode.clocks = (short) opConfig.clocks;
                opcode.clocksAlt = (short) opConfig.clocksAlt;
                opcode.clocksAlt2 = (short) opConfig.clocksAlt2;

                // todo validate impl class by opConfig.type

                return opcode;
            } catch (Exception e) {
                throw new RuntimeException("error creating opcode instance", e);
            }
        }


        /**
         * service DSL method to simplify configurations
         * @param clocks number of clock
         * @param opClass class that implements the opcode
         * @param mnemonic mnemonic
         * @param comment comment, could be used later
         * @return descriptor
         */
        default SimpleConfiguration S(int clocks, Class<? extends Opcode> opClass, String mnemonic, String comment)
        {
            return S(clocks, 0, opClass, mnemonic, comment);
        }

        /**
         * DSL method to populate configuration map with another one byte opcode (no mrr byte)
         * opMask can contain '01*_' characters where:
         * 0|1 - zero or one in the appropriate position of key
         *  '_' - divider, will be ignored
         *  '*' - template, will be substituted with 0 and 1
         * @param config configuration to populate
         * @param opMask opcode mask to be parsed
         * @param opConfig opcode configuration
         */
        default void config(Opcode[] config, String opMask, SimpleConfiguration opConfig)
        {
            config(config, opMask, opConfig, false);
        }

        /**
         * DSL method to populate configuration with another one byte opcode (no mrr byte)
         * opMask can contain '01*_' characters where:
         * 0|1 - zero or one in the appropriate position of key
         *  '_' - divider, will be ignored
         *  '*' - template, will be substituted with 0 and 1
         * @param config configuration to populate
         * @param opMask opcode mask to be parsed
         * @param opConfig opcode configuration
         * @param override allows to override opcode (DANGEROUS)
         */
        default void config(Opcode[] config, String opMask, SimpleConfiguration opConfig, boolean override)
        {
            // we can use only 1 instance as parameters are the same,
            // this must decrease cache usage
            Opcode opImpl = op(opConfig);

            String[] opVariants = ClockedOpcodeConfiguration.extend(opMask);
            for (String opVariant: opVariants) {
                int opcode = Integer.parseInt(opVariant, 2);
                if (!override && (config[opcode] != null)) {
                    throw new RuntimeException("opcode configuration " + opMask + " already defined ("+ opVariant +")");
                }
                config[opcode] = opImpl;
            }
        }

        /**
         * DSL method to populate configuration map with opcode depending on mrr byte,
         * opMask and mrrMask can contain '01*_' characters where:
         * 0|1 - zero or one in the appropriate position of key
         *  '_' - divider, will be ignored
         *  '*' - template, will be substituted with 0 and 1
         *
         * @param config configuration to populate
         * @param opMask opcode mask to be parsed
         * @param mrrMask mask for -reg- part of mrr byte
         * @param opConfig opcode configuration
         */
        default void config(Opcode[] config, String opMask, String mrrMask, SimpleConfiguration opConfig)
        {
            config(config, opMask, mrrMask, opConfig, false);
        }

        /**
         * DSL method to populate configuration map with opcode depending on mrr byte,
         * opMask and mrrMask can contain '01*_' characters where:
         * 0|1 - zero or one in the appropriate position of key
         *  '_' - divider, will be ignored
         *  '*' - template, will be substituted with 0 and 1
         *
         * override flag allows to have configurations like:
         * 1000_1110   **_0**xxx  {no override}     ; MOV   SR <- M  12+EA
         * 1000_1110   11_0**xxx  {override = true} ; MOV   SR <- R   2
         *
         * @param config configuration to populate
         * @param opMask opcode mask to be parsed
         * @param mrrMask mask for -reg- part of mrr byte
         * @param opConfig opcode configuration
         * @param override allows to override mrr based part of configuration
         */
        default void config(Opcode[] config, String opMask, String mrrMask, SimpleConfiguration opConfig, boolean override)
        {
            String[] opVariants = ClockedOpcodeConfiguration.extend(opMask);
            String[] mrrVariants = ClockedOpcodeConfiguration.extend(mrrMask);

            Opcode op = op(opConfig);

            for (String opVariant: opVariants)
            {
                int opcode = Integer.parseInt(opVariant, 2);

                // prevent base opcode overrides
                if ((config[opcode] != null) && !(config[opcode] instanceof MrrBasedDemux)) {
                    throw new RuntimeException("incorrect opcode configuration for " + opMask + "  " + mrrMask +
                            ", already defined (" + config[opcode].getClass().getSimpleName() + ")");
                }

                // preallocate mrr demux
                if (config[opcode] == null) {
                    config[opcode] = new MrrBasedDemux();
                }

                MrrBasedDemux demux = (MrrBasedDemux) config[opcode];
                for (String mrrVariant: mrrVariants)
                {
                    int mrr = Integer.parseInt(mrrVariant, 2);
                    if (!override && (demux.exits[mrr] != null)) {
                        throw new RuntimeException("opcode configuration [" + opMask + ", " + mrrMask  + ", " + mrrVariant + "] already defined ");
                    }
                    demux.exits[mrr] = op;
                }
            }
        }

        /**
         * merges to configurations together validating intersections
         * @param dst destination configuration
         * @param src source configuration
         */
        static void merge(Map<String, Configuration> dst, Map<String, Configuration> src)
        {
            src.forEach( (srcOpcode, srcConfig) -> {
                Configuration dstConfig = dst.get(srcOpcode);
                if (dstConfig == null) {
                    dst.put(srcOpcode, srcConfig);
                }
                else if (dstConfig instanceof SimpleConfiguration) {
                    throw new RuntimeException("error merging configurations for " + srcOpcode + " opcode");
                } else {
                    ModRegRmConfiguration dstMrrConfig = (ModRegRmConfiguration) dstConfig;
                    if ((srcConfig instanceof SimpleConfiguration)) {
                        SimpleConfiguration srcSimpleConfig = (SimpleConfiguration) srcConfig;
                        throw new RuntimeException("can't merge simple config into modregmod one for " + srcOpcode + " opcode ("
                                + srcSimpleConfig.clocks + ", " + srcSimpleConfig.opClass.getSimpleName() + ")");
                    }
                    if (!(srcConfig instanceof ModRegRmConfiguration)) {
                        throw new RuntimeException("can't merge simple config into modregmod one for " + srcOpcode + " opcode");
                    }
                    ((ModRegRmConfiguration) srcConfig).mrr.forEach((srcMrr, srcMrrConfig) -> {
                        if (dstMrrConfig.mrr.containsKey(srcMrr)) {
                            SimpleConfiguration dstMrrRegistered = dstMrrConfig.mrr.get(srcMrr);
                            throw new RuntimeException("can't merge modregmod configuration for [" + srcOpcode + ", " + srcMrr + "] opcode,\n" +
                                    "registration (" + srcMrrConfig.clocks + ", "+ srcMrrConfig.opClass.getSimpleName() + ") clashes with registered " +
                                    "(" + dstMrrRegistered.clocks + ", " + dstMrrRegistered.opClass.getSimpleName() + ")");
                        }
                        dstMrrConfig.mrr.put(srcMrr, srcMrrConfig);
                    });
                }
            });
        }

        /**
         * extends template into array of possible variants, replacing
         * "*" symbols with 0 and 1, removing service symbol "_"
         * @param template template with "01*_" symbols
         * @return array of possible variants
         */
        static String[] extend(String template)
        {
            int positions = 0;
            int asterisks = 0;
            for (int i = 0; i < template.length(); i++)
            {
                int c = template.charAt(i);
                if ("01*_".indexOf(c) == -1) {
                    throw new IllegalArgumentException("incorrect configuration \"" + template + "\", must contain only \"01*_\" symbols");
                }
                if ("01*".indexOf(c) != -1) {
                    positions++;
                }
                if (c == '*') {
                    asterisks++;
                }
            }
            if ((positions != 3) && (positions != 8)) {
                throw new IllegalArgumentException("incorrect configuration \"" + template + "\", must contain 3 or 8 positions");
            }

            // current state of the template
            char[] state = new char[positions];
            // positions of asterisk symbols in the compact template
            int[] aPositions = new int[asterisks];
            int aIndex = 0;
            int sIndex = 0;
            for (int i = 0; i < template.length(); i++)
            {
                char c = template.charAt(i);
                if ("01*".indexOf(c) == -1) {
                    continue;
                }
                if (c == '*') {
                    aPositions[aIndex++] = sIndex;
                }
                state[sIndex++] = c;
            }

            String[] result = new String[1 << asterisks];
            if (asterisks == 0) {
                result[0] = new String(state);
            } else {
                extend(result, 0, state, aPositions, 0);
            }

            return result;
        }

        /**
         * recursively extends template into array of variants
         * @param result result array to populate
         * @param resultIdx index of the next position in result to populate
         * @param state current state of the template, updated in process
         * @param aPositions positions af asterisks in the state
         * @param aIdx index of asterisk to process
         * @return number of generated elements in the result array
         */
        private static int extend(String[] result, int resultIdx, char[] state, int[] aPositions, int aIdx)
        {
            if (aIdx < aPositions.length - 1)
            {
                // intermediate asterisk, use recursion
                state[aPositions[aIdx]] = '0';
                int updates = extend(result, resultIdx, state, aPositions, aIdx + 1);

                state[aPositions[aIdx]] = '1';
                updates = extend(result, resultIdx + updates, state, aPositions, aIdx + 1);

                return updates * 2;
            } else {
                // last asterisk, add all variants
                state[aPositions[aIdx]] = '0';
                result[resultIdx] = new String(state);
                state[aPositions[aIdx]] = '1';
                result[resultIdx + 1] = new String(state);
                return 2;
            }
        }

    }

    /**
     * @return string representation of registers
     */
    public String dump() {
        return String.format("@%04X  R: %04X %04X %04X %04X - %04X %04X %04X %04X  %s  %04X %04X %04X %04X",
                ip,
                registers[0], registers[1], registers[2], registers[3],
                registers[4], registers[5], registers[6], registers[7],
                dumpFlag(),
                segments[0], segments[1], segments[2], segments[3]);
    }

    /**
     * @return string state of flag register
     */
    public String dumpFlag()
    {
        char[] tmp = new char[] {
                ((flags & FLAG_OF) == 0) ? '.' : 'O',
                ((flags & FLAG_DF) == 0) ? '.' : 'D',
                ((flags & FLAG_IF) == 0) ? '.' : 'I',
                ((flags & FLAG_TF) == 0) ? '.' : 'T',
                '-',
                ((flags & FLAG_SF) == 0) ? '.' : 'S',
                ((flags & FLAG_ZF) == 0) ? '.' : 'Z',
                ((flags & FLAG_AF) == 0) ? '.' : 'A',
                ((flags & FLAG_PF) == 0) ? '.' : 'P',
                ((flags & FLAG_CF) == 0) ? '.' : 'C'};
        return new String(tmp);
    }

    /**
     * dumps registered opcodes to stdout for visual check and debug
     */
    public void dumpRegistry()
    {
        char[] binOpcode = new char[8];
        Arrays.fill(binOpcode, '0');

        for (int i = 0; i < opcodes.length; i++) {
            Opcode opcode = opcodes[i];

            StringBuilder text = new StringBuilder();
            text.append(binOpcode[0]).append(binOpcode[1]).append(binOpcode[2]).append(binOpcode[3]).append('_').
                    append(binOpcode[4]).append(binOpcode[5]).append(binOpcode[6]).append(binOpcode[7]);

            if (opcode == null) {
                text.append("    ----\n");
            }
            else if (opcode instanceof MrrBasedDemux)
            {
                MrrBasedDemux dmx = (MrrBasedDemux) opcode;
                text.append("    ").append("REG DEMUX\n");
                for (int e = 0; e < dmx.exits.length; e++) {
                    text.append("         ");
                    if (dmx.exits[e] == null) {
                        text.append("    ---");
                    } else {
                        String value = Integer.toUnsignedString(e, 2);
                        text.append("    ");
                        for (int k = 0; k < 8 - value.length(); k++) {
                            text.append('0');
                        }
                        text.append(value).append("    ").append(dmx.exits[e].getClass().getSimpleName());
                    }
                    text.append("\n");
                }
            }
            else {
                text.append("    ").append(opcode.getClass().getSimpleName()).append("\n");
            }
            System.out.print(text.toString());

            // inc binary representation
            int idx = 7;
            while (0 <= idx) {
                if (binOpcode[idx] == '1') {
                    binOpcode[idx] = '0';
                    idx--;
                } else {
                    binOpcode[idx] = '1';
                    break;
                }
            }
        }
    }

}
