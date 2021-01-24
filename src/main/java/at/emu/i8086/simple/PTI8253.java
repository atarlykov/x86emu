package at.emu.i8086.simple;

class PTI8253 extends Cpu.PortHandler
{
    /**
     * Programmable interval timer 8253/8254
     * runs at 1.193182 MHz, has 3 channels, channel 2 gate is controlled by PPI port 0x61, bit 0
     *
     * channel 0    is connected directly to IRQ0,
     *              set by bios to 18.2Hz (0 or 65535), min frequency is 18.2 (as 1.193182/65536)
     * channel 1    is unusable and may not exist,
     *              used for DMA refresh
     * channel 2    is connected to PC speaker, can be used for other purposes,
     *              the only channel where gate input could be controlled (0x61, bit 0) and
     *              the only where output can be read (port 0x61, bit 5)
     *
     * ports
     *  0x40    - channel 0 data port, r/w
     *  0x41    - channel 1 data port, r/w
     *  0x42    - channel 2 data port, r/w
     *  0x43    - mode/command register, write only
     *   [7 6 5 4 3 2 1 0]
     *                  |
     *                  +--   [0]: BCD/binary mode, 0 - 16 bit binary, 1 - for digit bcd (x86 uses binary only)
     *                   -- [321]: operating mode (has different meaning if read-back)
     *                              000 - mode 0, interrupt on terminal count (channel 0 only?)
     *                              001 - mode 1, hw re-triggerable, one shot (count start after gate input => channel 2 only)
     *                              x10 - mode 2, rate  generator
     *                              x11 - mode 3, square wave generator
     *                              100 - mode 4, software triggerable strobe
     *                              101 - mode 5, hardware triggerable strobe
     *                   --  [54]: access mode (has different meaning if read-back)
     *                               00 - latch count value command (store value to tmp reg)
     *                               01 - access mode, lo byte only
     *                               10 - access mode, hi byte only
     *                               11 - access mode, lo/hi bytes
     *                   --  [76]: select channel
     *                               00 - channel 0
     *                               01 - channel 1
     *                               10 - channel 2
     *                               11 - read-back command (8254 only, at+)
     *
     *                   read-back command (next read from specified channels will return status byte)
     *                   --   [0]:  reserved (0)
     *                   --   [1]:  read back timer channel 0 (1 = yes, 0 = no)
     *                   --   [2]:  read back timer channel 1 (1 = yes, 0 = no)
     *                   --   [3]:  read back timer channel 2 (1 = yes, 0 = no)
     *                   --   [4]:  latch status flag (0 = latch status, 1 = don't latch status)
     *                   --   [5]:  latch count flag (0 = latch count, 1 = don't latch count)
     *                   --  [76]:  11
     *
     *                   read-back status byte
     *                  +--   [0]: BCD/binary mode, 0 - 16 bit binary, 1 - for digit bcd (x86 uses binary only)
     *                   -- [321]: operating mode (see above)
     *                   --  [54]: access mode (see above)
     *                   --   [6]: null count flags
     *                   --   [7]: output pin state
     *
     */


    public static final int PORT_TIMER0     = 0x40;
    public static final int PORT_TIMER1     = 0x41;
    public static final int PORT_TIMER2     = 0x42;
    public static final int PORT_COMMAND    = 0x43;

    // channel 0 is always connected to irq0
    public static final int CHANNEL0_IRQ    = 0;

    /**
     * Holds state for each channel
     */
    static class State
    {
        // masks to check data in #state field
        public static final int MASK_MODE           = 0b0000_0011;
        public static final int MASK_ACCMODE        = 0b0000_1100;
        public static final int MASK_LOHI_WRITE     = 0b0001_0000;
        public static final int MASK_LOHI_READ      = 0b0010_0000;

        // access modes in #state field
        public static final int ACCESS_LO_BYTE      = 0b000100;
        public static final int ACCESS_HI_BYTE      = 0b001000;
        public static final int ACCESS_LOHI_BYTES   = 0b001100;

        /**
         * holds mode, access mode, and r/w state in case of LO/HI mode
         * lowest bits are:
         *                     + access mode
         *                     |          + mode
         * __00______0__0______00_________00
         *           |  + program state for lo/hi access mode (0 - lo, 1 - hi)
         *           + read state for lo/hi access mode (0 - lo, 1 - hi)
        */
        int state   = ACCESS_LO_BYTE;

        // internal counter to track periods
        int counter;

        // reload value as programmed,
        // valid values are 1 - 65535(6), 0 - not initialized
        int reload;

        // latch value after latch command,
        // -1: not initialized
        int latch = -1;

        /**
         * stores latch value to be read later
         * from channel port (only useful in LO/HI mode)
         */
        void latch()
        {
            latch = counter;
            // reset read state for lo/hi access mode
            state &= ~MASK_LOHI_READ;
        }

        /**
         * sets access mode and initial states for read / write,
         * resets read and write (program) state for lo/hi byte mode
         * see ACCESS_xxx
         * @param amode access mode to set for the channel
         */
        void setAccessMode(int amode)
        {
            state = (state & ~MASK_ACCMODE & ~MASK_LOHI_WRITE & ~MASK_LOHI_READ) | amode;
        }

        /**
         * set channel's operating mode
         * @param mode new mode
         */
        void setMode(int mode) {
            state = (state & ~MASK_MODE) | mode;
        }

        /**
         * write divisor value to reload field based on LO,HI or LO/HI access mode,
         * tracks state for LO/HI mode
         * @param value new byte value to write
         */
        void write(int value)
        {
            // assume we must handle lo byte now
            boolean loByte = true;

            // current access mode
            int amode = state & MASK_ACCMODE;
            if (amode == ACCESS_LOHI_BYTES)
            {
                // lo/hi mode, check current state - lo or hi
                if ((state & MASK_LOHI_WRITE) == 0) {
                    // first(lo) byte, stay in loByte and prepare for hi byte
                    state |= MASK_LOHI_WRITE;
                } else {
                    // switch back to be ready for lo byte on the next turn
                    state &= ~MASK_LOHI_WRITE;
                    loByte = false;
                }
            }
            else if (amode == ACCESS_HI_BYTE) {
                loByte = false;
            }

            // todo: investigate if we need to reset upper/low part on write?

            if (loByte) {
                reload &= 0xFF00;
                reload |= (value);
            }
            else {
                reload &= 0x00FF;
                reload |= (value << 8);
            }

            // todo: review 00 ->> FFFF or 10000 (bios checks 00-->FF)
            reload = reload == 0 ? 65536 : reload;
        }

        /**
         * reads current counter or latch if it was requested be port write
         * @return LO or HI byte
         */
        int read()
        {
            // assume we must handle lo byte now
            boolean loByte = true;
            boolean resetLatch = true;

            // current access mode
            int amode = state & MASK_ACCMODE;
            if (amode == ACCESS_LOHI_BYTES)
            {
                // lo/hi mode, check current state - lo or hi
                if ((state & MASK_LOHI_READ) == 0) {
                    // first(lo) byte, stay in loByte and prepare for hi byte
                    state |= MASK_LOHI_READ;
                    resetLatch = false;
                } else {
                    // switch back to be ready for lo byte on the next turn
                    state &= ~MASK_LOHI_READ;
                    loByte = false;
                }
            }
            else if (amode == ACCESS_HI_BYTE) {
                loByte = false;
            }


            int value = latch;
            if (value == -1) {
                value = counter;
            }
            else if (resetLatch) {
                // latch is active and must be reset
                latch = -1;
            }

            if (loByte) {
                return value & 0xFF;
            }
            else {
                return (value >> 8) & 0xFF;
            }
        }
    }

    /**
     * states of all channels
     */
    private State[] states = new State[] {
            new State(), new State(), new State()
    };


    long ns;
    PIC8259 pic;

    public PTI8253(PIC8259 pic) {
        this.pic = pic;
    }

    /**
     * must be called periodically to update timer counters
     * timer doesn't count during programming period (between setup cmd and channel write(s)),
     * could be supported via additional bits in #State.state field
     */
    public void update()
    {
        //long now = System.nanoTime();

        State state = states[0];
        //boolean programming = (state.state & State.MASK_PRG) != 0;
        if ((state.reload != 0)) {
            state.counter -= 1;
            if (state.counter < 0) {
                state.counter = state.reload + state.counter;
                // switch case mode
                pic.interrupt(CHANNEL0_IRQ);
            }
        }

        state = states[1];
        //programming = (state.state & State.MASK_PRG) != 0;
        if ((state.reload != 0)) {
            state.counter -= 1;
            if (state.counter < 0) {
                state.counter = state.reload + state.counter;
                // switch case mode
            }
        }

        state = states[2];
        //programming = (state.state & State.MASK_PRG) != 0;
        if ((state.reload != 0)) {
            state.counter -= 1;
            if (state.counter < 0) {
                state.counter = state.reload + state.counter;
                // switch case mode
            }
        }

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
        value &= 0xFF;

        if (port == PORT_COMMAND)
        {

            if ((value & 0b0000_0001) != 0) {
                throw new RuntimeException("PIT8253, BCD mode is not supposed for XT");
            }
            int channel = value >> 6;
            if (channel == 3) {
                // read back command
                throw new RuntimeException("PIT8253, read-back command not supported on XT");
            }
            else {
                State state = states[channel];

                int aMode = (value >> 4) & 0b0011;
                if (aMode == 0)
                {
                    // latch command, no mode change
                    state.latch();
                }
                else {
                    // channel setup command
                    switch (aMode) {
                        case 1: state.setAccessMode(State.ACCESS_LO_BYTE); break;
                        case 2: state.setAccessMode(State.ACCESS_HI_BYTE); break;
                        case 3: state.setAccessMode(State.ACCESS_LOHI_BYTES); break;
                    }

                    int mode = (value >> 1) & 0b0111;
                    if (5 < mode) {
                        mode &= 0b011;
                    }
                    state.setMode(mode);
                }
            }
        }
        else if (port == PORT_TIMER2) {
            states[2].write(value);
        }
        else if (port == PORT_TIMER0) {
            states[0].write(value);
        }
        else if (port == PORT_TIMER1) {
            states[1].write(value);
        }
    }

    @Override
    int pin(boolean word, int port)
    {
        if (port == PORT_TIMER2) {
            return states[2].read();
        }
        else if (port == PORT_TIMER0) {
            return states[0].read();
        }
        else if (port == PORT_TIMER1) {
            return states[1].read();
        }
        else {
            return 0;
            /*
            if (port == PORT_COMMAND)
            {
                throw new RuntimeException("PIT8253, read of command register is not supported");
            }
            */
        }
    }
}
