/* Generated SBE (Simple Binary Encoding) message codec. */
package com.bofa.equity.sbe;

@SuppressWarnings("all")
public enum SideType
{

    /**
     * Buy
     */
    B((short)0),


    /**
     * Sell
     */
    S((short)1),

    /**
     * To be used to represent not present or null.
     */
    NULL_VAL((short)255);

    private final short value;

    SideType(final short value)
    {
        this.value = value;
    }

    /**
     * The raw encoded value in the Java type representation.
     *
     * @return the raw value encoded.
     */
    public short value()
    {
        return value;
    }

    /**
     * Lookup the enum value representing the value.
     *
     * @param value encoded to be looked up.
     * @return the enum value representing the value.
     */
    public static SideType get(final short value)
    {
        switch (value)
        {
            case 0: return B;
            case 1: return S;
            case 255: return NULL_VAL;
        }

        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
