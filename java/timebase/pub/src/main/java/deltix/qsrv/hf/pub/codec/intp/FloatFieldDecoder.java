package deltix.qsrv.hf.pub.codec.intp;

import deltix.qsrv.hf.pub.codec.NonStaticFieldLayout;
import deltix.qsrv.hf.codec.CodecUtils;
import deltix.qsrv.hf.pub.codec.validerrors.*;
import deltix.qsrv.hf.pub.md.*;
import deltix.util.lang.MathUtil;
import deltix.util.lang.StringUtils;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;

/**
 *
 */
final class FloatFieldDecoder extends FieldDecoder {
    private final int       relativeIdx;
    private final int       baseIdx;
    private final float     min;
    private final float     max;    
    
    FloatFieldDecoder (NonStaticFieldLayout f) {
        super (f);
        relativeIdx = f.relativeTo == null ? -1 : f.relativeTo.ownBaseIndex;
        baseIdx = f.ownBaseIndex;
        
        FloatDataType fdt = (FloatDataType) f.getType ();

        min = fdt.getMinNotNull ().floatValue ();
        max = fdt.getMaxNotNull ().floatValue ();        
    }

    @Override
    public int      compare (DecodingContext ctxt1, DecodingContext ctxt2) {
        final float v1 = getFloat(ctxt1);
        assert isNullable || !Float.isNaN(v1) : getNotNullableMsg();
        final float v2 = getFloat(ctxt2);
        assert isNullable || !Float.isNaN(v2) : getNotNullableMsg();
        final int result = CodecUtils.compareNulls(Float.isNaN(v1), Float.isNaN(v2));
        if (result < 2)
            return result;
        else
            return (MathUtil.compare(v1, v2));
    }

    @Override
    final protected void copy (DecodingContext ctxt, Object obj)
            throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        final float v = getFloat(ctxt);
        assert isNullable || !Float.isNaN(v) : getNotNullableMsg();
        setter.setFloat(obj, v);
    }

    @Override
    protected void setNull(Object obj) throws IllegalAccessException, InvocationTargetException {
        setter.setFloat(obj, Float.NaN);
    }

    @Override
    protected void setNull(Object obj, int idx) {
        Array.setFloat(obj, idx, Float.NaN);
    }

    protected float readFloat (DecodingContext ctxt) {
        return (ctxt.in.readFloat ());
    }

    @Override
    public float    getFloat (DecodingContext ctxt) {
        float               d = readFloat (ctxt);
        
        if (relativeIdx >= 0) {            
            float           base = ctxt.floatBaseValues [relativeIdx];

            if (!Float.isNaN (base))
                d += base;
        }
        
        if (baseIdx >= 0)
            ctxt.floatBaseValues [baseIdx] = d;
        
        return (d);
    }

    @Override
    public double   getDouble (DecodingContext ctxt) {
        return (getFloat (ctxt));
    }

    @Override
    public String   getString (DecodingContext ctxt) {
        final float v = getFloat(ctxt);
        return Float.isNaN(v) ? null : StringUtils.toDecimalString(v);
    }

    @Override
    public void     skip (DecodingContext ctxt) {
        if (baseIdx >= 0)
            ctxt.floatBaseValues[baseIdx] = readFloat(ctxt);
        else
            ctxt.in.skipBytes(4);
    }

    @Override
    public boolean isNull(DecodingContext ctxt) {
        return isNull(getFloat(ctxt));
    }

    @Override
    public boolean isNull(double value) {
        //return Float.isNaN(value);
        return Double.isNaN(value);
    }
    
    @Override
    public ValidationError      validate (DecodingContext ctxt) {
        final int           offset = ctxt.in.getCurrentOffset ();        
        final float         v = getFloat (ctxt);
        
        if (isNull (v)) {
            if (!isNullable)
                return (new IllegalNullValue (offset, fieldInfo));        
        }
        else if (v < min || v > max)
            return (new FloatFieldOutOfRange (offset, fieldInfo, v, min, max));
        
        return (null);
    }
}
