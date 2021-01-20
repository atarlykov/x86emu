package at.emu.i8086;


/**
 * this emulates XT keyboard
 */
public class XtKeyboard {

    /**
     * Byte buffer for scan codes
     */
    static class CircularByteBuffer
    {
        // data in the buffer
        final byte[] data;
        // points to the next element to read
        int head;
        // number of elements
        int elements;

        public CircularByteBuffer(int size) {
            data = new byte[size];
            clear();
        }

        /**
         * adds another code to the buffer
         * @param value new value to add
         */
        public void add(byte value)
        {
            if (elements == data.length) {
                // keyboard replaces the last code,
                // but that doesn't really matter
                return;
            }

            int index = (head + elements) % data.length;
            data[index] = value;
            elements++;
        }

        /**
         * @return gets next value from the buffer or null if empty
         */
        public int get() {
            if (elements == 0) {
                return 0;
            } else {
                byte value = data[head];
                head = (head + 1) % data.length;
                elements--;
                return value;
            }
        }

        /**
         * @return true if buffer is empty
         */
        public boolean empty() {
            return elements == 0;
        }

        /**
         * resets buffer state
         */
        public void clear() {
            head = 0;
            elements = 0;
        }
    }

    /**
     * tracks if clock low has been issued with some previous command,
     * used to reset kbd (bios routines)
     */
    private boolean clockLowSet;

    /**
     * buffer to store scan codes
     */
    private final CircularByteBuffer buffer = new CircularByteBuffer(16);

    /**
     * called on keyboard clear flag set
     */
    public void clear() {
    }

    /**
     *  called on enable bit set (practically always)
     */
    public void enable() {
    }

    /**
     * called when read from PPI KBD port (0x61) is issued
     * @return next scan code available or 0 if now data ready
     */
    public int getNextScanCode() {
        return 0xAA;
    }

    /**
     * called with clock flag state after write to the io port
     * @param high clock is high or low
     */
    public void clock(boolean high)
    {
        if (!high) {
            clockLowSet = true;
        }
        else {
            if (clockLowSet) {
                clockLowSet = false;
                reset();
            }
        }
    }

    /**
     * resets keyboards, adds 0xAA code
     * to the buffer to indicated correctly initialized state
     */
    private void reset() {
        buffer.clear();
        buffer.add((byte)0xAA);
    }
}
