package com.ecom.academic.service.pdf;

import java.util.List;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;

/**
 * Modification-detection dictionaries (ISO 32000-2 §12.8.2), built from raw COS
 * objects because PDFBox has no API for them.
 */
final class MdpSupport {

    private MdpSupport() {
    }

    private static final COSName ACTION = COSName.getPDFName("Action");
    private static final COSName TRANSFORM_METHOD = COSName.getPDFName("TransformMethod");
    private static final COSName TRANSFORM_PARAMS = COSName.getPDFName("TransformParams");

    /** Certification: after this signature only changes of level {@code p} are allowed (2 = forms and signing). */
    static COSDictionary docMdp(int p) {
        COSDictionary params = new COSDictionary();
        params.setItem(COSName.TYPE, COSName.getPDFName("TransformParams"));
        params.setInt(COSName.P, p);
        params.setName(COSName.V, "1.2");
        COSDictionary ref = new COSDictionary();
        ref.setItem(COSName.TYPE, COSName.getPDFName("SigRef"));
        ref.setItem(TRANSFORM_METHOD, COSName.DOCMDP);
        ref.setItem(TRANSFORM_PARAMS, params);
        return ref;
    }

    /** The fields a signature freezes: {@code Include} those listed, or {@code All}. */
    static COSDictionary fieldMdp(String action, List<String> fields, COSDictionary catalog) {
        COSDictionary params = new COSDictionary();
        params.setItem(COSName.TYPE, COSName.getPDFName("TransformParams"));
        params.setItem(ACTION, COSName.getPDFName(action));
        if (fields != null) {
            params.setItem(COSName.FIELDS, names(fields));
        }
        params.setName(COSName.V, "1.2");
        COSDictionary ref = new COSDictionary();
        ref.setItem(COSName.TYPE, COSName.getPDFName("SigRef"));
        ref.setItem(TRANSFORM_METHOD, COSName.getPDFName("FieldMDP"));
        ref.setItem(TRANSFORM_PARAMS, params);
        ref.setItem(COSName.getPDFName("Data"), catalog);
        return ref;
    }

    /**
     * The signature field's own /Lock, set in the base. {@code p} (PDF 2.0) also
     * sets the document's permissions once signed: 1 = no changes at all.
     */
    static COSDictionary lock(String action, List<String> fields, Integer p) {
        COSDictionary lock = new COSDictionary();
        lock.setItem(COSName.TYPE, COSName.getPDFName("SigFieldLock"));
        lock.setItem(ACTION, COSName.getPDFName(action));
        if (fields != null) {
            lock.setItem(COSName.FIELDS, names(fields));
        }
        if (p != null) {
            lock.setInt(COSName.P, p);
        }
        return lock;
    }

    private static COSArray names(List<String> fields) {
        COSArray arr = new COSArray();
        fields.forEach(f -> arr.add(new COSString(f)));
        return arr;
    }
}
