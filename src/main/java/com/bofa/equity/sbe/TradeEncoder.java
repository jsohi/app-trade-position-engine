/* Generated SBE (Simple Binary Encoding) message codec. */
package com.bofa.equity.sbe;

import org.agrona.MutableDirectBuffer;
import org.agrona.DirectBuffer;


/**
 * Trade message
 */
@SuppressWarnings("all")
public final class TradeEncoder
{
    public static final int BLOCK_LENGTH = 59;
    public static final int TEMPLATE_ID = 1;
    public static final int SCHEMA_ID = 100;
    public static final int SCHEMA_VERSION = 1;
    public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

    private final TradeEncoder parentMessage = this;
    private MutableDirectBuffer buffer;
    private int initialOffset;
    private int offset;
    private int limit;

    public int sbeBlockLength()
    {
        return BLOCK_LENGTH;
    }

    public int sbeTemplateId()
    {
        return TEMPLATE_ID;
    }

    public int sbeSchemaId()
    {
        return SCHEMA_ID;
    }

    public int sbeSchemaVersion()
    {
        return SCHEMA_VERSION;
    }

    public String sbeSemanticType()
    {
        return "";
    }

    public MutableDirectBuffer buffer()
    {
        return buffer;
    }

    public int initialOffset()
    {
        return initialOffset;
    }

    public int offset()
    {
        return offset;
    }

    public TradeEncoder wrap(final MutableDirectBuffer buffer, final int offset)
    {
        if (buffer != this.buffer)
        {
            this.buffer = buffer;
        }
        this.initialOffset = offset;
        this.offset = offset;
        limit(offset + BLOCK_LENGTH);

        return this;
    }

    public TradeEncoder wrapAndApplyHeader(
        final MutableDirectBuffer buffer, final int offset, final MessageHeaderEncoder headerEncoder)
    {
        headerEncoder
            .wrap(buffer, offset)
            .blockLength(BLOCK_LENGTH)
            .templateId(TEMPLATE_ID)
            .schemaId(SCHEMA_ID)
            .version(SCHEMA_VERSION);

        return wrap(buffer, offset + MessageHeaderEncoder.ENCODED_LENGTH);
    }

    public int encodedLength()
    {
        return limit - offset;
    }

    public int limit()
    {
        return limit;
    }

    public void limit(final int limit)
    {
        this.limit = limit;
    }

    public static int referenceIdId()
    {
        return 1;
    }

    public static int referenceIdSinceVersion()
    {
        return 0;
    }

    public static int referenceIdEncodingOffset()
    {
        return 0;
    }

    public static int referenceIdEncodingLength()
    {
        return 32;
    }

    public static String referenceIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static byte referenceIdNullValue()
    {
        return (byte)0;
    }

    public static byte referenceIdMinValue()
    {
        return (byte)32;
    }

    public static byte referenceIdMaxValue()
    {
        return (byte)126;
    }

    public static int referenceIdLength()
    {
        return 32;
    }


    public TradeEncoder referenceId(final int index, final byte value)
    {
        if (index < 0 || index >= 32)
        {
            throw new IndexOutOfBoundsException("index out of range: index=" + index);
        }

        final int pos = offset + 0 + (index * 1);
        buffer.putByte(pos, value);

        return this;
    }

    public static String referenceIdCharacterEncoding()
    {
        return java.nio.charset.StandardCharsets.US_ASCII.name();
    }

    public TradeEncoder putReferenceId(final byte[] src, final int srcOffset)
    {
        final int length = 32;
        if (srcOffset < 0 || srcOffset > (src.length - length))
        {
            throw new IndexOutOfBoundsException("Copy will go out of range: offset=" + srcOffset);
        }

        buffer.putBytes(offset + 0, src, srcOffset, length);

        return this;
    }

    public TradeEncoder referenceId(final String src)
    {
        final int length = 32;
        final int srcLength = null == src ? 0 : src.length();
        if (srcLength > length)
        {
            throw new IndexOutOfBoundsException("String too large for copy: byte length=" + srcLength);
        }

        buffer.putStringWithoutLengthAscii(offset + 0, src);

        for (int start = srcLength; start < length; ++start)
        {
            buffer.putByte(offset + 0 + start, (byte)0);
        }

        return this;
    }

    public TradeEncoder referenceId(final CharSequence src)
    {
        final int length = 32;
        final int srcLength = null == src ? 0 : src.length();
        if (srcLength > length)
        {
            throw new IndexOutOfBoundsException("CharSequence too large for copy: byte length=" + srcLength);
        }

        buffer.putStringWithoutLengthAscii(offset + 0, src);

        for (int start = srcLength; start < length; ++start)
        {
            buffer.putByte(offset + 0 + start, (byte)0);
        }

        return this;
    }

    public static int accountIdId()
    {
        return 2;
    }

    public static int accountIdSinceVersion()
    {
        return 0;
    }

    public static int accountIdEncodingOffset()
    {
        return 32;
    }

    public static int accountIdEncodingLength()
    {
        return 1;
    }

    public static String accountIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static short accountIdNullValue()
    {
        return (short)255;
    }

    public static short accountIdMinValue()
    {
        return (short)1;
    }

    public static short accountIdMaxValue()
    {
        return (short)10;
    }

    public TradeEncoder accountId(final short value)
    {
        buffer.putByte(offset + 32, (byte)value);
        return this;
    }


    public static int securityIdId()
    {
        return 3;
    }

    public static int securityIdSinceVersion()
    {
        return 0;
    }

    public static int securityIdEncodingOffset()
    {
        return 33;
    }

    public static int securityIdEncodingLength()
    {
        return 1;
    }

    public static String securityIdMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static short securityIdNullValue()
    {
        return (short)255;
    }

    public static short securityIdMinValue()
    {
        return (short)1;
    }

    public static short securityIdMaxValue()
    {
        return (short)2000;
    }

    public TradeEncoder securityId(final short value)
    {
        buffer.putByte(offset + 33, (byte)value);
        return this;
    }


    public static int sideId()
    {
        return 4;
    }

    public static int sideSinceVersion()
    {
        return 0;
    }

    public static int sideEncodingOffset()
    {
        return 34;
    }

    public static int sideEncodingLength()
    {
        return 1;
    }

    public static String sideMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public TradeEncoder side(final SideType value)
    {
        buffer.putByte(offset + 34, (byte)value.value());
        return this;
    }

    public static int quantityId()
    {
        return 5;
    }

    public static int quantitySinceVersion()
    {
        return 0;
    }

    public static int quantityEncodingOffset()
    {
        return 35;
    }

    public static int quantityEncodingLength()
    {
        return 8;
    }

    public static String quantityMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long quantityNullValue()
    {
        return 0xffffffffffffffffL;
    }

    public static long quantityMinValue()
    {
        return 0x0L;
    }

    public static long quantityMaxValue()
    {
        return 0xfffffffffffffffeL;
    }

    public TradeEncoder quantity(final long value)
    {
        buffer.putLong(offset + 35, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
    }


    public static int priceId()
    {
        return 6;
    }

    public static int priceSinceVersion()
    {
        return 0;
    }

    public static int priceEncodingOffset()
    {
        return 43;
    }

    public static int priceEncodingLength()
    {
        return 8;
    }

    public static String priceMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static double priceNullValue()
    {
        return Double.NaN;
    }

    public static double priceMinValue()
    {
        return 4.9E-324d;
    }

    public static double priceMaxValue()
    {
        return 1.7976931348623157E308d;
    }

    public TradeEncoder price(final double value)
    {
        buffer.putDouble(offset + 43, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
    }


    public static int timestampMillisId()
    {
        return 7;
    }

    public static int timestampMillisSinceVersion()
    {
        return 0;
    }

    public static int timestampMillisEncodingOffset()
    {
        return 51;
    }

    public static int timestampMillisEncodingLength()
    {
        return 8;
    }

    public static String timestampMillisMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static long timestampMillisNullValue()
    {
        return 0xffffffffffffffffL;
    }

    public static long timestampMillisMinValue()
    {
        return 0x0L;
    }

    public static long timestampMillisMaxValue()
    {
        return 0xfffffffffffffffeL;
    }

    public TradeEncoder timestampMillis(final long value)
    {
        buffer.putLong(offset + 51, value, java.nio.ByteOrder.LITTLE_ENDIAN);
        return this;
    }


    public static int descriptionId()
    {
        return 8;
    }

    public static String descriptionCharacterEncoding()
    {
        return java.nio.charset.StandardCharsets.UTF_8.name();
    }

    public static String descriptionMetaAttribute(final MetaAttribute metaAttribute)
    {
        if (MetaAttribute.PRESENCE == metaAttribute)
        {
            return "required";
        }

        return "";
    }

    public static int descriptionHeaderLength()
    {
        return 2;
    }

    public TradeEncoder putDescription(final DirectBuffer src, final int srcOffset, final int length)
    {
        if (length > 2048)
        {
            throw new IllegalStateException("length > maxValue for type: " + length);
        }

        final int headerLength = 2;
        final int limit = parentMessage.limit();
        parentMessage.limit(limit + headerLength + length);
        buffer.putShort(limit, (short)length, java.nio.ByteOrder.LITTLE_ENDIAN);
        buffer.putBytes(limit + headerLength, src, srcOffset, length);

        return this;
    }

    public TradeEncoder putDescription(final byte[] src, final int srcOffset, final int length)
    {
        if (length > 2048)
        {
            throw new IllegalStateException("length > maxValue for type: " + length);
        }

        final int headerLength = 2;
        final int limit = parentMessage.limit();
        parentMessage.limit(limit + headerLength + length);
        buffer.putShort(limit, (short)length, java.nio.ByteOrder.LITTLE_ENDIAN);
        buffer.putBytes(limit + headerLength, src, srcOffset, length);

        return this;
    }

    public TradeEncoder description(final String value)
    {
        final byte[] bytes = (null == value || value.isEmpty()) ? org.agrona.collections.ArrayUtil.EMPTY_BYTE_ARRAY : value.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        final int length = bytes.length;
        if (length > 2048)
        {
            throw new IllegalStateException("length > maxValue for type: " + length);
        }

        final int headerLength = 2;
        final int limit = parentMessage.limit();
        parentMessage.limit(limit + headerLength + length);
        buffer.putShort(limit, (short)length, java.nio.ByteOrder.LITTLE_ENDIAN);
        buffer.putBytes(limit + headerLength, bytes, 0, length);

        return this;
    }

    public String toString()
    {
        if (null == buffer)
        {
            return "";
        }

        return appendTo(new StringBuilder()).toString();
    }

    public StringBuilder appendTo(final StringBuilder builder)
    {
        if (null == buffer)
        {
            return builder;
        }

        final TradeDecoder decoder = new TradeDecoder();
        decoder.wrap(buffer, initialOffset, BLOCK_LENGTH, SCHEMA_VERSION);

        return decoder.appendTo(builder);
    }
}
