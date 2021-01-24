package at.emu.i8086.simple;

class PPI8255 extends Cpu.PortHandler
{
    /**
     * PPI 8255 (8255A/8255A-5/82C55)
     *
     * Ports  (0x60 - 0x63)  (386SX: 0xC0, C2, C4, C6)
     *  A1  A0   Function
     *   0   0   Port A
     *   0   1   Port B
     *   1   0   Port C
     *   1   1   Command register
     * Usage: keyboard (pc/xt), speaker, timer, NMI source identification
     *
     * Programmed with 2 commands:
     * - command byte A
     *   [7 6 5 4 3 2 1 0]
     *                  |   B group
     *                  +-- [0]: port C (PC3-PC0), 1 - input, 0 - output
     *                   -- [1]: port B, 1 - input, 0 - output
     *                   -- [2]: mode, 1 - mode 1, 0 - mode 0
     *                      A group
     *                   -- [3]: port C (PC7-PC4), 1 - input, 0 - output
     *                   -- [4]: port A, 1 - input, 0 - output
     *                   --[65]: mode, 00 - mode 0, 01 - mode 1, 1x - mode 2
     *                   -- [7]: 1 (indicates command byte A)
     * command byte B
     *   [7 x x x 3 2 1 0]
     *                  |
     *                  +-- [0]: set/reset bit, 1 - set, 0 - reset
     *                   -[321]: bit selection
     *                   -- [7]: 0 (indicates command byte B)
     *
     *
     * IBM_5155_5160_Technical_Reference_6280089_MAR86.pdf, 1-27 8255A I/O Bit Map
     *
     * 0x60  input: keyboard scan code,  output: diagnostic
     *
     * 0x61  output: 0: 1 ->> timer 2 gate speaker enable // gates input signal from timer to the channel (@see pit)
     *               1: 1 ->> speaker data enable
     *               2: spare (turbo switch)
     *               3: 0 ->> read low switches or 1 ->> read high switches
     *               4: 0 ->> enable ram parity check
     *               5: 0 ->> enable io channel check
     *               6: 0 ->> low hold keyboard clock low, 1 ->> high
     *               7: 0 ->> enable keyboard or 1 ->> clear keyboard
     *
     * 0x62  input:  0 + loop on POST                sw1 | sw5
     *               1 + coprocessor installed       sw2 | sw6
     *               2 + planar ram size 0           sw3 | sw7
     *               3 + planar ram size 1           sw4 | sw8
     *               4 spare
     *               5 + timer channel 2 out
     *               6 + i/o channel check
     *               7 + ram parity check
     *
     *               or could reflect switches on mb - ram/display/#diskettes [0x61, bit3]
     *               sw4  sw3      mb 64/256K    mb  256/640K
     *                0    0           64K           256K
     *                0    1          128K           512K
     *                1    0          192K           576K
     *                1    1          256K           640K
     *
     *               sw6  sw5         display at power up
     *                0    0          reserved
     *                0    1          color 40x25 bw mode
     *                1    0          color 80x25 bw mode
     *                1    1          ibm monochrome 80x25
     *
     *               sw8  sw7         # of diskettes
     *                0    0          1
     *                0    1          2
     *                1    0          3
     *                1    1          4
     *
     *
     * 0x63  command/mode register
     *       mode register value 10011001 (0x99)
     */


    public static final int PORT_A      = 0x60;
    public static final int PORT_B      = 0x61;
    public static final int PORT_C      = 0x62;
    public static final int PORT_CMD    = 0x63;


    public static final int MASK_PORT_B_TIMER       = 0x01;
    public static final int MASK_PORT_B_SPEAKER     = 0x02;
    public static final int MASK_PORT_B_SWITCHES    = 0x08;
    public static final int MASK_PORT_B_KBD_CLOCK   = 0x40;
    public static final int MASK_PORT_B_KBD_CLEAR   = 0x80;


    /**
     * controls hardware switches read mode,
     * high switches (5-8) or low ones (1-4)
     */
    private boolean readHighSwitches;

    /**
     * reference to keyboard for passing commands too
     * and reading codes from
     */
    private final XtKeyboard keyboard;

    
    public PPI8255(XtKeyboard keyboard) {
        this.keyboard = keyboard;
    }

    /**
     *
     * @param word
     * @param port
     * @param value
     */
    @Override
    void pout(boolean word, int port, int value)
    {
        if (port == PORT_CMD)
        {
            if ((value & 0b1000_0000) != 0) {
                // command byte A
                System.out.printf("PPI8255A: command byte A: %02X%n", value);
            } else {
                System.out.printf("PPI8255A: command byte B: %02X%n", value);
            }
        }

        else if (port == PORT_A) {
            // seems to be diagnostic output, allow always for now
            diagnostic(value);
        }

        else if ((port == PORT_B))
        {
            if ((value & MASK_PORT_B_SWITCHES) == 0) {
                readHighSwitches = false;
            } else {
                readHighSwitches = true;
            }

            if ((value & MASK_PORT_B_TIMER) != 0) {
                // pti.gateChannel2();
            }

            // first handle clear part as it could
            // drop internal buffer
            if ((value & MASK_PORT_B_KBD_CLEAR) != 0) {
                keyboard.clear();
            } else {
                keyboard.enable();
            }

            // this is better to run after clear
            // as initialization could generate status code into buffer
            if ((value & MASK_PORT_B_KBD_CLOCK) != 0) {
                keyboard.clock(true);
            } else {
                keyboard.clock(false);
            }

        }
    }

    @Override
    int pin(boolean word, int port)
    {
        if (port == PORT_A) {
            return keyboard.getNextScanCode();
        }
        else if (port == PORT_C)
        {
            if (readHighSwitches) {
                // 1 diskette, 80x25 display
                return 0b0000_0010;
            } else {
                // 640K, no coprocessor, no loop
                return 0b0000_1100;
            }
        }
        else {
            System.out.printf("PPI8255A: unhandled read from port %02X%n", port);
            return 0;
        }
    }

    /**
     * handles diagnostic output to port A
     * @param value byte value
     */
    private void diagnostic(int value) {
        System.out.printf("diagnostic: %02X%n", value & 0xFF);
    }
}
