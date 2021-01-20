package at.emu.i8086;

class DMA8237 extends Cpu.PortHandler
{
    /**
     * 8237 DMA Controller
     * 8086 manual - B-92 (p.641)
     * 8237A manual
     *
     * registers
     * - current address reg, 16 bit, per channel
     * - current word reg, 16 bit, per channel
     * - base address and base word count registers, 16 bit, per channel (base values)
     * - command reg, 8 bit
     *      [7 6 5 4 3 2 1 0]
     *                   | |
     *                   | +- (0):  MMT, 0 - mem to mem disable, 1 - enable
     *                   +--- (1): ADHE, 0 - channel 0 address hold disable, 1 - enable, x if bit 0 = 0
     *                    --- (2): COND, 0 - controller enable, 1 - disable
     *                    --- (3): COMP, 0 - normal timing, 1 - compressed, x - if bit 0 = 1
     *                    --- (4): PRIO, 0 - fixed priority, 1 - rotating
     *                    --- (5): EXTW, 0 - late write selection, 1- extended ..., x if bit 3 = 1
     *                    --- (6): DRQP, 0 - DREQ sense active high, 1 - low
     *                    --- (7):DACKP, 0 - DACK sense active low, 1 - high
     *
     * - mode reg, 6 bit, bit 0 and 1 determine channel
     *      [7 6 5 4 3 2 1 0]
     *                |   |
     *                |   +- (10): channel, 00->0, 01->1, 10->2, 11->3
     *                +----- (32): 00->verify transfer, 01->write transfer, 10->read transfer, 11->illegal, xx if bits 76=11
     *                        (4): 0 - auto initialization disabled, 1 - enabled (resets address and count after transfer)
     *                        (5): 0 - address increment select, 1 - decrement
     *                       (76): 00->demand mode select, 01->single .., 10->block .., 11->cascade ..
     *
     * - request reg, 4 bit
     *      [7 6 5 4 3 2 1 0]
     *                |   |
     *                |   +- (10): channel, 00->0, 01->1, 10->2, 11->3
     *                +-----  (2): 0 - reset request bit, 1 - set request bit
     *
     * - mask reg (2 types of commands), this prevents dma from sending DRQ signal to a device
     *    - per channel (single channel mask register)
     *      [7 6 5 4 3 2 1 0] separately set/clear mask bits  (masking channel 4 will mask channels 4,5,6,7 due to cascading)
     *                |   |
     *                |   +- (10): channel, 00->0, 01->1, 10->2, 11->3
     *                +-----  (2): 0 - clear mask bit, 1 - set bit
     *
     *    - all channels (multi channel mask register)
     *      [7 6 5 4 3 2 1 0] separately set/clear mask bits
     *               |   | |
     *               |   | +- (0): 0 - clear channel 0 mask bit, 1 - set
     *               |   +--- (1): 0 - clear channel 1 mask bit, 1 - set
     *                     -- (2): 0 - clear channel 2 mask bit, 1 - set
     *                     -- (3): 0 - clear channel 3 mask bit, 1 - set
     *
     * - status reg, for device (read resets TC bits)
     *      [7 6 5 4 3 2 1 0] separately set/clear mask bits
     *               |   | |
     *               |   | +- (0): channel 0 has reached TC (transfer complete)
     *               |   +--- (1): channel 1 has reached TC
     *                     -- (2): channel 2 has reached TC
     *                     -- (3): channel 3 has reached TC
     *                     -- (4): channel 0 request pending
     *                     -- (5): channel 1 request pending
     *                     -- (6): channel 2 request pending
     *                     -- (7): channel 3 request pending
     *
     * - temporary reg, data during mem-to-mem operation, holds last byte transferred, cleared by reset
     *
     * - software commands (each command ~ write to specific port)
     *    - clear first/last flip-flop
     *    - master clear, works as hardware reset;
     *                    command, status, request, temporary, internal flip/flop are cleared, mask is set,
     *                    8237 enters the idle cycle
     *    - clear mask register
     *
     *
     * Operates in 2 cycles - Idle and Active, each made up of a number of states.
     * State
     *   SI (inactive state), no requests pending, during state may be in the Program Condition, being programmed
     *    O first state of service, 8237 has requested a hold, but cpu hasn't yet returned an ack.
     *    S1-S4 working states
     *   SW (wait states), can be inserted between S2, S3 and S4
     *   S11-S14 read from memory
     *   S21-S24 write to memory
     *
     *
     * DMA first chip is wired for 8 bit transfers (dma 0-3)
     * DMA second chip is wired for 16 bit transfers (dma 4-7
     * channel 0 is unavailable, memory refresh (set up by bios)
     *
     * Channels 0-3     Channels 4-7
     * IO Port         	IO Port    Size 	Read or Write 	Function
     *   0x00          	0xC0       Word 	W (bios read!)  Start Address Register channel 0/4 (unusable)
     *   0x01          	0xC2       Word 	W 	            Count Register channel 0/4 (unusable)
     *   0x02          	0xC4       Word 	W 	            Start Address Register channel 1/5
     *   0x03          	0xC6       Word 	W 	            Count Register channel 1/5
     *   0x04          	0xC8       Word 	W 	            Start Address Register channel 2/6
     *   0x05          	0xCA       Word 	W 	            Count Register channel 2/6
     *   0x06          	0xCC       Word 	W 	            Start Address Register channel 3/7
     *   0x07          	0xCE       Word 	W 	            Count Register channel 3/7
     *   0x08          	0xD0       Byte 	R 	            Status Register
     *   0x08          	0xD0       Byte 	W 	            (cmd) Command Register
     *   0x09          	0xD2       Byte 	W 	            (cmd) Request Register
     *   0x0A          	0xD4       Byte 	W 	            (cmd) Single Channel Mask Register
     *   0x0B          	0xD6       Byte 	W 	            (cmd) Mode Register
     *   0x0C          	0xD8       Byte 	W 	            (cmd) Flip-Flop Reset Register
     *   0x0D          	0xDA       Byte 	R 	            Intermediate Register
     *   0x0D          	0xDA       Byte 	W 	            (cmd) Master Reset Register
     *   0x0E          	0xDC       Byte 	W 	            (cmd) Mask Reset Register
     *   0x0F          	0xDE       Byte 	RW 	            MultiChannel Mask Register (reading is undocumented, but it works!)
     *
     * Page Address Register (R/W, per channel), upper 8 bits of the 24 bit transfer memory address
     *   0x87 	Channel 0 Page Address Register (unusable)
     *   0x83 	Channel 1 Page Address Register
     *   0x81 	Channel 2 Page Address Register
     *   0x82 	Channel 3 Page Address Register
     *   0x8F 	Channel 4 Page Address Register (unusable)
     *   0x8B 	Channel 5 Page Address Register
     *   0x89 	Channel 6 Page Address Register
     *   0x8A 	Channel 7 Page Address Register
     *
     * programming
     * - address port, (0,2,4,6) - channels 0-3 (8bit),  (c0,c4,c8,cc) - channels 4-7 (16bit)  // R/W
     * - count port,   (1,3,5,7) - channels 0-3 (8bit),  (c2,c6,ca,ce) - channels 4-7 (16bit)  // R/W
     * - page regs,    (0x87,83,81,82) and (8f,8b,89,8a)  // write only
     * - mode regs,    0x0b  and 0xd6
     * - mask regs,    0x0a  and 0xd4
     * - flip flop,    0x0c  and 0xd8
     */

    short[] REG_ADDRESS = new short[] {0x00, 0x02, 0x04, 0x06, 0xC0, 0xC4, 0xC8, 0xCC};
    short[] REG_COUNT   = new short[] {0x01, 0x03, 0x05, 0x07, 0xC2, 0xC6, 0xCA, 0xCE};
    short[] REG_PAGE    = new short[] {0x87, 0x83, 0x81, 0x82, 0x8F, 0x8B, 0x89, 0x8A};


    // command registers (ports), any write to them is the command,
    // all ports are 8 bit
    short[] REG_CMD_CMD     = new short[] {0x08, 0xD0};
    short[] REG_CMD_REQ     = new short[] {0x09, 0xD2};
    short[] REG_CMD_SCMR    = new short[] {0x0A, 0xD4};
    short[] REG_CMD_MODE    = new short[] {0x0B, 0xD6};
    short[] REG_CMD_FFRR    = new short[] {0x0C, 0xD8};
    short[] REG_CMD_MSTR    = new short[] {0x0D, 0xDA};
    short[] REG_CMD_MSKR    = new short[] {0x0E, 0xDC};

    // command registers (ports) in read mode
    short[] REG_STATUS          = new short[] {0x08, 0xD0};
    short[] REG_INTERMEDIATE    = new short[] {0x0D, 0xDA};
    short[] REG_MULTICHANNEL    = new short[] {0x0F, 0xDE};


    public static final int MASK_REG_CMD_COND   = 0b0000_0100;


//    short[] address     = new short[8];
//    short[] count       = new short[8];
//    short[] page        = new short[8]; // it's byte
//    short[] mode        = new short[8];

    short[] status      = new short[] {0, 0};
    boolean[] enabled   = new boolean[2];

    //  7654 3210
    //  0000_0000
    int masked;

    static class Channel {

        public static final int MASK_ENABLED    = 0b000001_00000000;
        public static final int MASK_ADDR_WRITE = 0b000100_00000000;
        public static final int MASK_ADDR_READ  = 0b001000_00000000;
        public static final int MASK_CNT_WRITE  = 0b010000_00000000;
        public static final int MASK_CNT_READ   = 0b100000_00000000;

        short address;
        short count;
        //        | address read byte state
        //        || address write byte state (0 low, 1 high)
        //        ||     |enabled          | mode (8 bytes)
        // 00_00__00___ 00_________________00000000
        //    || count write byte state
        //    | count read byte state
        short mode;
        byte page;

        void address(int value)
        {
            if ((mode & MASK_ADDR_WRITE) == 0) {
                mode |= MASK_ADDR_WRITE;
                address = (short)((address & 0xFF00) | (value & 0xFF));
            } else {
                mode &= ~MASK_ADDR_WRITE;
                address = (short)((address & 0x00FF) | ((value & 0xFF) << 8));
            }
        }

        int address()
        {
            if ((mode & MASK_ADDR_READ) == 0) {
                mode |= MASK_ADDR_READ;
                return address & 0xFF;
            } else {
                mode &= ~MASK_ADDR_READ;
                return (address >> 8) & 0xFF;
            }
        }

        void count(int value)
        {
            if ((mode & MASK_CNT_WRITE) == 0) {
                mode |= MASK_CNT_WRITE;
                count = (short)((count & 0xFF00) | (value & 0xFF));
            } else {
                mode &= ~MASK_CNT_WRITE;
                count = (short)((count & 0x00FF) | ((value & 0xFF) << 8));
            }
        }

        int count()
        {
            if ((mode & MASK_CNT_READ) == 0) {
                mode |= MASK_CNT_READ;
                return count & 0xFF;
            } else {
                mode &= ~MASK_CNT_READ;
                return (count >> 8) & 0xFF;
            }
        }

        void page(int value) {
            page = (byte) value;
        }

        int page() {
            return page;
        }
    }

    Channel[] channels = new Channel[] {
            new Channel(), new Channel(), new Channel(), new Channel(),
            new Channel(), new Channel(), new Channel(), new Channel()
    };

    @Override
    void pout(boolean word, int port, int value)
    {
        System.out.printf("->> DMA OUT[%04X] (%X)%n", port, value);
        int index;
        if ((index = contains(REG_ADDRESS, (short) port)) != -1) {
            channels[index].address(value);
        }
        else if ((index = contains(REG_COUNT, (short) port)) != -1) {
            channels[index].count(value);
        }
        else if ((index = contains(REG_PAGE, (short) port)) != -1) {
            channels[index].page(value);
        }

        else if ((index = contains(REG_CMD_CMD, (short) port)) != -1) {
            // due to osdev.wiki the only working/useful bit is COND
            enabled[index] = (value & MASK_REG_CMD_COND) == 0;
        }

        else if ((index = contains(REG_CMD_REQ, (short) port)) != -1) {
            // mem-to-mem operations, not used/useless ???
            throw new RuntimeException("DMA8237 request register access, review [" + port + "," + value + "]");
        }

        else if ((index = contains(REG_CMD_SCMR, (short) port)) != -1)
        {   // single channel mask
            int channel = (value & 0b0011) + index * 4;
            if ((value & 0b0100) != 0) {
                masked |= 1 << channel;
            } else {
                masked &= ~(1 << channel);
            }
        }

        else if ((index = contains(REG_MULTICHANNEL, (short) port)) != -1)
        {   // multi channel mask
            if (index == 0) {
                masked &= 0x0F;
                masked |= (value & 0x0F);
            } else {
                masked &= 0xF0;
                masked |= (value & 0x0F) << 4;
            }
        }

        /*
         *   0x0B          	0xD6       Byte 	W 	            (cmd) Mode Register
         *   0x0C          	0xD8       Byte 	W 	            (cmd) Flip-Flop Reset Register
         *   0x0D          	0xDA       Byte 	W 	            (cmd) Master Reset Register
         *   0x0E          	0xDC       Byte 	W 	            (cmd) Mask Reset Register

         */

        else if ((index = contains(REG_CMD_MODE, (short) port)) != -1) {
            // [0..3] + [0|4]
            int channel = (value & 0b0011) + index * 4;
            channels[channel].mode = (short) value;
        }

        else if ((index = contains(REG_CMD_FFRR, (short) port)) != -1) {
            // flip flop are not used for now
        }

        else if ((index = contains(REG_CMD_MSTR, (short) port)) != -1) {
            // command, status, request, temporary, internal flip/flop are cleared, mask is set,
            enabled[index] = false; // command
            status[index] = 0;
            masked = 0xFF;
        }

        else if ((index = contains(REG_CMD_MSKR, (short) port)) != -1) {
            masked = 0;
        }
    }

    @Override
    int pin(boolean word, int port)
    {
        int index;

        if ((index = contains(REG_ADDRESS, (short) port)) != -1) {
            //return state[index].address;
            return channels[index].address();
        }
        else if ((index = contains(REG_COUNT, (short) port)) != -1) {
            return channels[index].count();
        }
        else if ((index = contains(REG_PAGE, (short) port)) != -1) {
            return channels[index].page();
        }



        if ((index = contains(REG_STATUS, (short) port)) != -1) {
            return status[index];
        }
        else if ((index = contains(REG_INTERMEDIATE, (short) port)) != -1) {
            //return temp[index];
            return 0;
        }
        else if ((index = contains(REG_MULTICHANNEL, (short) port)) != -1) {
            return masked;
        }

        //System.out.printf("->>  IN[%04X] (%X)%n", port);
        return 0;
    }

    public static int contains(short[] array, short value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == value) {
                return i;
            }
        }
        return -1;
    }



}
