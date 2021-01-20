package at.emu.i8086;

class PIC8259 extends Cpu.PortHandler
{
    /*
     * Intel - The 8086 Family User's Manual,
     * A-153+ (p.462), B-22, B-106 (p.655)
     *
     * ICW1..ICW4 (initialization words) are used for initialization,
     * icw1 ->> command port, icw2..4 ->> data port.
     * The following logic is used:
     * - icw1 and icw2 must always present
     * - icw3 only when icw1[SNGL]==0 (not single)
     * - icw4 only when icw1[IC4] ==1
     * - ready to accept interrupts
     *
     * OCW (operation command word) can be sent when initialized to control various modes (see below)
     * A0 ->> address line, indicates command port (0) or data port (1)
     *
     * ICW1
     *  A0  D7  D6  D5  D4    D3   D2    D1   D0
     * [ 0  A7  A6  A5   1  LTIM  ADI  SNGL  IC4]
     *                                    |   +--- [0]: 1 ->> ICW4 needed, 0 ->> not needed
     *                                    +------- [1]: 1 ->> single, 0 ->> cascade mode
     *                                             [2]: call address interval, 1 ->> 4, 0 ->> 8
     *                                             [3]: 1 ->> level triggered mode, 0 ->> edge triggered mode
     *                                             [4]: must be 1
     *                                           [765]: A7-A5 of interrupt vector address (mcs-80/85 mode only)
     * when D4=1 ->> this is ICW1 and init sequence starts (init/re-init):
     * - imr cleared
     * - R7 input assigned priority 7
     * - slave mode address is set to 7
     * - special mask mode cleared, status read set to irr
     * - if IC4=0 ->> all functions selected in ICW4 are set to zero
     *
     * ICW2 (in 80/86 mode)
     *  A0  D7  D6  D5  D4  D3   D2  D1  D0
     * [ 1  T7  T6  T5  T4  T3  A10  A9  A8]
     *                       |
     *                       +------------------ [7-3]: T7-T3 of interrupt vector address
     *
     * ICW3 (master device)
     *  A0  D7  D6  D5  D4  D3  D2  D1  D0
     * [ 1  S7  S6  S5  S4  S3  S2  S1  S0]
     *       |                           |
     *       +---------------------------+------ [7-0]: 1 ->> IR input has slave, 0 ->> doesn't have slave
     *
     * ICW3 (slave device)
     *  A0  D7  D6  D5  D4  D3  D2  D1  D0
     * [ 1   0   0   0   0   0 ID2 ID1 ID0]
     *                           |       |
     *                           +-------+------ [2-0]: slave id 0..7
     *
     * ICW4
     *  A0  D7  D6  D5   D4  D3  D2   D1  D0
     * [ 1   0   0   0 SFNM BUF M/S AEOI uPM]
     *                                     +--- [0]: 1 ->> 8086/8088 mode, 0 ->> MCS-80/85 mode
     *                                          [1]: 1 ->> auto EOI, 0 ->> normal
     *                                         [32]: 0x ->> non buffered mode
     *                                               10 ->> buffered mode/slave
     *                                               11 ->> buffered mode/master
     *                                          [4]: 1 -> special fully nested mode, 0 ->>  not --""--
     *
     *
     * OCW1
     *  A0  D7  D6  D5  D4  D3  D2  D1  D0
     * [ 1  M7  M6  M5  M4  M3  M2  M1  M0]
     *       |                           |
     *       +---------------------------+------ [7-0]: interrupt mask in imr, 1 ->> mask set (channel disabled), 0 ->> reset (enabled)
     *
     * OCW2
     *  A0  D7  D6  D5  D4  D3  D2  D1  D0
     * [ 0   R  SL EOI   0   0  L2  L1  L0]
     *       |       |           |       |
     *       |       |           +-------+------ [2-0]: interrupt level acted upon when SEOI bit is active
     *       +-------+-------------------------- [7-5]: 001 ->> non specific EOI cmd
     *                                                  011 ->> specific EOI cmd
     *                                                  101 ->> rotate on non specific eoi cmd
     *                                                  100 ->> rotate in auto eoi mode (set)
     *                                                  000 ->> rotate in auto eoi mode (clear)
     *                                                  111 ->> rotate on specific eoi cmd
     *                                                  110 ->> set priority cmd
     *                                                  010 ->> no operation
     *
     * OCW3
     *  A0  D7   D6  D5  D4  D3  D2  D1  D0
     * [ 0   x ESMM SMM   0   1   P  RR RIS]
     *                                |  |
     *                                +--+------ [10]: 10 ->> read ir reg on next read pulse
     *                                                 11 ->> read is reg on next read pulse
     *                                                 0x ->> no action
     *                                            [2]: 1 ->> poll command, 0 ->> no poll command
     *                                           [65]: 10 ->> reset special mask
     *                                                 11 ->> set special mask
     *                                                 0x ->> no action
     *
     * Reading status
     * OCW3 ->> IRR and ISR, 8859A remembers current reg to read, on init is set to IRR and switches between them
     * OCW1 ->> IMR, just read it
     *
     * Disabling pic  ->> output 0xFF to data port (?)
     *
     */

    // ICW1 masks
    public static final int MASK_ICW1_IC4       = 0x01;
    public static final int MASK_ICW1_SNGL      = 0x02;
    public static final int MASK_ICW1_ADI       = 0x04;
    public static final int MASK_ICW1_LTIM      = 0x08;
    public static final int MASK_ICW1_D4_INIT   = 0x10;

    // ICW4 masks
    public static final int MASK_ICW4_uPM       = 0x01;
    public static final int MASK_ICW4_AEOI      = 0x02;
    public static final int MASK_ICW4_SFNM      = 0x10;
    public static final int OCW4_BUF_SLAVE      = 0x08;
    public static final int OCW4_BUF_MASTER     = 0x0C;

    // mask to distinguish ocw2 and ocw3
    public static final int MASK_OCW23_SELECT   = 0b0001_1000;
    public static final int OCW2_SELECTOR       = 0b0000_0000;
    public static final int OCW3_SELECTOR       = 0b0000_1000;

    // IRR/ISR read command
    public static final int MASK_OCW3_IRR_ISR   = 0b0000_0011;
    public static final int OCW3_IRR            = 0b0000_0010;
    public static final int OCW3_ISR            = 0b0000_0011;

    // special mask
    public static final int MASK_OCW3_SMASK     = 0b0110_0000;
    public static final int OCW3_SMASK_SET      = 0b0110_0000;
    public static final int OCW3_SMASK_RESET    = 0b0100_0000;

    /**
     * command port for this pic
     */
    private final int cmdPort;
    /**
     * data port for this pic
     */
    private final int dataPort;
    /**
     * is this PIC a master or slave,
     * this must be specified via constructor
     * as there is no way to set this via commands
     */
    private final boolean master;

    /**
     * ICW1 & 2 received during initialization,
     * ICW1 is in the lowest byte
     */
    private int icw;
    /**
     * number of icw received, used during an initialization
     */
    private int icwReceived;
    /**
     * true if initialization process has ended,
     * can be reset on subsequent initializations
     */
    private boolean initialized;

    // number of base interrupt
    int baseInterrupt;
    // slaves mask (master mode)
    int slaves;
    // number of master pic ir (slave mode)
    int masterIr;

    /**
     * masking register, 0xFF disables the controller
     */
    int imr;
    /**
     * interrupts being services right now
     */
    int isr;
    /**
     * interrupt requests ready to be processed
     */
    int irr;

    /**
     * tracks if next read from command register must
     * return IRR (false) or ISR (true) register,
     * default state is IRR
     */
    boolean nextReadIsr;
    /**
     * special mask mode,
     * enables serving interrupts from other than the one being services at the moment
     * (from other levels)
     */
    boolean sMask;

    /**
     * internally precalculated flag to simplify checks
     * for pending interrupt requests
     */
    private boolean hasRequests = false;

    public PIC8259(int cmdPort, int dataPort, boolean master) {
        this.cmdPort = cmdPort;
        this.dataPort = dataPort;
        this.master = master;
    }

    /**
     * public api for cpu, checks if there are some
     * interrupt requests in IRR register and call cpu to process
     * the one with highest priority
     */
    public void runInterruptHandler(Cpu cpu)
    {
        if (!hasRequests) {
            return;
        }

        if ((isr != 0) && !sMask) {
            // don't send interrupts to cpu if
            // some is being processed already and
            // nester interrupts are disallowed
            return;
        }

        // check if we have ready to be served interrupts
        // excluding ones being processed (even in special mask mode)
        int ready = (irr & ~isr);
        if (ready != 0) {
            // find interrupt with the highest priority
            int zeros = Integer.numberOfTrailingZeros(ready);
            int bitMask = 1 << zeros; // Integer.lowestOneBit(ready);
            // remove from requests
            irr &= ~bitMask;
            // indicate as active
            isr |= bitMask;
            // run the handler
            cpu.interrupt(baseInterrupt + zeros);

            // clear global flag is there are no requests active
            if (irr == 0) {
                hasRequests = false;
            }
        }
    }


    /**
     * called by slave devices to indicate interrupt request
     * at the specified physical lane
     * @param line interrupt line (0..7), irq#0..7 for master pic, irq#8..15 for slave
     */
    public void interrupt(int line)
    {
        int request = 1 << line;
        if ((imr & request) == 0) {
            // not masked, save request
            irr |= request;

            // indicate we a request to global flag
            hasRequests = true;
        }
    }

    /**
     * non specific eoi command,
     * clears ISR bit with highest priority,
     * only handles standard priorities for now
     */
    private void eoi()
    {
        int bitMask = Integer.lowestOneBit(isr);
        // bit must not be cleared in super mask mode when it's masked in imr,
        // by intel 8086 manual, End If Interrupt, B-117
        if (!(sMask && ((imr & bitMask) != 0))) {
            isr &= ~bitMask;
        }
    }

    /**
     * specific EOI command
     * @param bit bit number to clear (0..7)
     */
    private void eoi(int bit)
    {
        isr &= ~(1 << bit);
    }

    /**
     * handles initialization step when icw1 command is received
     * @param port port
     * @param value value received
     */
    private void initialize(int port, int value)
    {
        if ((port == cmdPort) && ((value & MASK_ICW1_D4_INIT) != 0))
        {
            // initialization procedure start
            initialized = false;

            /*
             * - imr cleared
             * - R7 input assigned priority 7
             * - slave mode address is set to 7
             * - special mask mode cleared, status read set to irr
             * - if IC4=0 ->> all functions selected in ICW4 are set to zero
             */
            isr = imr = 0;
            // irr is selected for the next read register cmd
            nextReadIsr = false;
            //MASK_ICW1_IC4

            // store for future use and be ready for
            // subsequent init words
            icw = value;
            icwReceived = 1;
        }
        else {
            // data port write
            if (!initialized) {
                // we are in the middle of initialization,
                // maintain stage
                //noinspection EnhancedSwitchMigration
                switch (icwReceived)
                {
                    case 1:
                        // this is ICW2
                        icwReceived++;
                        // base interrupt number, 3 low bits are zeros
                        // as pic will use base..base+7 interrupts
                        baseInterrupt = (value & 0xF8);

                        // check stored icw1 for single/cascade mode
                        if ((icw & MASK_ICW1_SNGL) == 0) {
                            // cascade mode, go wait for icw3
                            break;
                        }

                        // master mode, there will be no icw3,
                        // check if icw4 will be sent
                        if ((icw & MASK_ICW1_IC4) == 0) {
                            // no icw4
                            assert true : "that must not happen on x86 system (mcs-80/85 mode)";
                            initialized = true;
                        } else {
                            // switch directly to icw4 receive skipping icw3
                            icwReceived++;
                        }
                        break;

                    case 2:
                        // we can only get here if icw3 is a must
                        if (master) {
                            slaves = (value & 0xFF);
                        } else {
                            masterIr = (value & 0b0111);
                        }
                        // check if icw4 must be received
                        if ((icw & MASK_ICW1_IC4) == 0) {
                            assert true : "that must not happen on x86 system (80/85 mode)";
                            initialized = true;
                        } else {
                            // wait for icw4
                            icwReceived++;
                        }
                        break;

                    case 3:
                        icw |= (value & 0xFF) << 8;
                        initialized = true;
                        break;

                    default:
                        throw new RuntimeException("PIC8259 initialization failed");
                }
            }

        }
    }

    @Override
    void pout(boolean word, int port, int value)
    {
        if (port == cmdPort)
        {
            if ((value & MASK_ICW1_D4_INIT) != 0) {
                // init/re-init requested
                initialize(port, value);
                return;
            }

            int ocw23Selector = value & MASK_OCW23_SELECT;
            if (ocw23Selector == OCW2_SELECTOR)
            {
                int rSlEoi = value & 0xE0;
                if (rSlEoi == 0b0010_0000) {
                    // non specific EOI
                    eoi();
                } else if (rSlEoi == 0b0110_0000) {
                    // specific EOI
                    int bit = value & 0x07;
                    eoi(bit);
                }
                else if (rSlEoi == 0b1100_0000) {
                    // set priory command
                    System.out.println("PIC8259: set priority command");
                }
                else {
                    System.out.println("PIC8259: unhandled OCW write " + Integer.toBinaryString(value));
                }
            }
            else if (ocw23Selector == OCW3_SELECTOR)
            {
                int irrIsr = value & MASK_OCW3_IRR_ISR;
                if (irrIsr == OCW3_ISR) {
                    nextReadIsr = true;
                } else if (irrIsr == OCW3_IRR) {
                    nextReadIsr = false;
                }

                int smask = value & MASK_OCW3_SMASK;
                if (smask == OCW3_SMASK_SET) {
                    sMask = true;
                    // reset global flag to allow
                    // detailed check for ready interrupts
                    hasRequests = true;
                } else if (smask == OCW3_SMASK_RESET) {
                    sMask = false;
                }

                // POLL ?
            }
        }
        else {
            if (!initialized) {
                // we are in the middle of initialization, maintain stage
                initialize(port, value);
                return;
            }

            // only allowed write to data register is ocw1
            // which overrides IMR register,
            // 0xFF is used to mask all channels (disable pic)
            imr = value & 0xFF;
        }
    }

    @Override
    int pin(boolean word, int port)
    {
        //        if (!initialized) {
        //            return 0;
        //        }

        if (port == dataPort) {
            // only allowed read from data register is IMR register
            return imr;
        }
        else {
            // command register - IRR or ISR read
            nextReadIsr = !nextReadIsr;
            if (nextReadIsr) {
                // we have inverted flag
                return irr;
            } else {
                // we have inverted flag
                return isr;
            }
        }
    }

}
