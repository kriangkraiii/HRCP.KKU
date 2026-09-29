package com.ecom.academic.service.pdf;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;

/**
 * Appends one revision to a signed-document PDF: filling fields, or signing a
 * signature field that already exists.
 *
 * <p>Every method returns the whole file, whose first bytes are exactly the
 * input — a revision never rewrites what earlier signatures cover. The fields and
 * their /Lock dictionaries are all created in the base (see {@code BasePdfBuilder});
 * creating one later would be an annotation change, which the first signer's
 * certification (DocMDP P=2) does not allow.
 */
public final class PdfIncrementService {

    /** Resource name of the embedded TH Sarabun New in the AcroForm's /DR. */
    public static final String FONT = "F1";
    public static final float TEXT_SIZE = 16f;
    public static final float MIN_TEXT_SIZE = 12f;
    private static final int SIGNATURE_SIZE = 32 * 1024;
    private static final COSName LOCK = COSName.getPDFName("Lock");

    private final ThaiText thai;

    public PdfIncrementService(ThaiText thai) {
        this.thai = thai;
    }

    /** A value that does not fit its reserved area, even at the smallest size. */
    public static final class DoesNotFitException extends IllegalArgumentException {
        private final String field;

        public DoesNotFitException(String field, String value) {
            super("\"" + value + "\" is too long for field " + field);
            this.field = field;
        }

        public String field() {
            return field;
        }
    }

    /**
     * One signer's revision.
     *
     * @param field       the pre-created signature field to sign
     * @param ownValues   the signer's own fields (date, answer, comment), filled in the same revision
     * @param lockedFields fields this signature freezes (FieldMDP Include); null freezes everything
     * @param certify     first signature of the document: DocMDP P=2 (form filling and signing still allowed)
     * @param imagePng    the visible signature, or null for an invisible one
     */
    public record SignSpec(CmsSigner signer, String field, Map<String, String> ownValues, List<String> lockedFields,
            boolean certify, byte[] imagePng, String name, String reason, String location, Calendar signedAt) {
    }

    public byte[] fill(byte[] pdf, Map<String, String> values) throws IOException {
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(pdf))) {
            PDAcroForm form = requireForm(doc);
            for (var e : values.entrySet()) {
                fillField(doc, form, e.getKey(), e.getValue());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.saveIncremental(out);
            return requirePrefix(pdf, out.toByteArray());
        }
    }

    public byte[] sign(byte[] pdf, SignSpec spec) throws IOException {
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(pdf))) {
            PDAcroForm form = requireForm(doc);
            for (var e : spec.ownValues().entrySet()) {
                fillField(doc, form, e.getKey(), e.getValue());
            }
            if (!(form.getField(spec.field()) instanceof PDSignatureField sf)) {
                throw new IllegalArgumentException("No signature field " + spec.field());
            }
            if (sf.getValue() != null) {
                throw new IllegalStateException("Signature field " + spec.field() + " is already signed");
            }
            PDAnnotationWidget widget = sf.getWidgets().get(0);
            PDRectangle rect = widget.getRectangle();

            PDSignature sig = new PDSignature();
            sig.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            sig.setSubFilter(COSName.getPDFName("ETSI.CAdES.detached"));
            sig.setName(spec.name());
            sig.setReason(spec.reason());
            sig.setLocation(spec.location());
            sig.setSignDate(spec.signedAt());

            COSDictionary catalog = doc.getDocumentCatalog().getCOSObject();
            COSArray refs = new COSArray();
            if (spec.certify()) {
                refs.add(MdpSupport.docMdp(2));
                COSDictionary perms = new COSDictionary();
                perms.setItem(COSName.DOCMDP, sig);
                catalog.setItem(COSName.PERMS, perms);
                catalog.setNeedToBeUpdated(true);
            }
            refs.add(spec.lockedFields() == null
                    ? MdpSupport.fieldMdp("All", null, catalog)
                    : MdpSupport.fieldMdp("Include", spec.lockedFields(), catalog));
            sig.getCOSObject().setItem(COSName.getPDFName("Reference"), refs);
            sf.getCOSObject().setItem(COSName.V, sig);

            try (SignatureOptions options = new SignatureOptions()) {
                options.setPreferredSignatureSize(SIGNATURE_SIZE);
                doc.addSignature(sig, spec.signer(), options);
                // addSignature() blanks the widget of a field it finds without a visual
                // template: put back the pre-created rectangle and draw our own picture.
                if (spec.imagePng() != null && rect != null && rect.getWidth() > 0) {
                    widget.setRectangle(rect);
                    widget.setAppearance(imageAppearance(doc, spec.imagePng(), rect));
                }
                widget.getCOSObject().setNeedToBeUpdated(true);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                doc.saveIncremental(out);
                return requirePrefix(pdf, out.toByteArray());
            }
        }
    }

    // ------------------------------------------------------------------ fields

    private void fillField(PDDocument doc, PDAcroForm form, String name, String value) throws IOException {
        PDField field = form.getField(name);
        if (field == null) {
            throw new IllegalArgumentException("No field " + name + " in this document");
        }
        if (field instanceof PDCheckBox box) {
            boolean on = value != null && !value.isBlank();
            COSName state = on ? COSName.getPDFName(box.getOnValue()) : COSName.Off;
            box.getCOSObject().setItem(COSName.V, state);
            for (PDAnnotationWidget w : box.getWidgets()) {
                w.getCOSObject().setItem(COSName.AS, state);
                w.getCOSObject().setNeedToBeUpdated(true);
            }
            box.getCOSObject().setNeedToBeUpdated(true);
            return;
        }
        String text = value == null ? "" : value;
        float preferred = daSize(field);
        for (PDAnnotationWidget w : field.getWidgets()) {
            PDRectangle r = w.getRectangle();
            float size = text.isEmpty() ? preferred
                    : thai.fittingSize(text, preferred, Math.min(MIN_TEXT_SIZE, preferred), r.getWidth() - 1);
            if (size < 0) {
                throw new DoesNotFitException(name, text);
            }

            PDAppearanceStream ap = new PDAppearanceStream(doc);
            ap.setBBox(new PDRectangle(r.getWidth(), r.getHeight()));
            PDResources res = new PDResources();
            // The /DR font object itself: a later revision must never embed a new font.
            COSDictionary fonts = new COSDictionary();
            fonts.setItem(FONT, form.getDefaultResources().getCOSObject().getCOSDictionary(COSName.FONT).getItem(FONT));
            res.getCOSObject().setItem(COSName.FONT, fonts);
            ap.setResources(res);
            String ops = "/Tx BMC\n" + (text.isEmpty() ? "" : thai.operators(FONT, size, text, 0, baseline(r))) + "EMC\n";
            try (var os = ap.getContentStream().createOutputStream(COSName.FLATE_DECODE)) {
                os.write(ops.getBytes(StandardCharsets.US_ASCII));
            }
            PDAppearanceDictionary apd = new PDAppearanceDictionary();
            apd.setNormalAppearance(ap);
            w.setAppearance(apd);
            w.getCOSObject().setNeedToBeUpdated(true);
        }
        field.getCOSObject().setItem(COSName.V, new COSString(text));
        field.getCOSObject().setNeedToBeUpdated(true);
    }

    private static final java.util.regex.Pattern DA_SIZE = java.util.regex.Pattern.compile("([0-9.]+)\\s+Tf");

    /** The size the base gave the field (the template's own), from its /DA. */
    private static float daSize(PDField field) {
        String da = field.getCOSObject().getString(COSName.DA);
        if (da != null) {
            var m = DA_SIZE.matcher(da);
            if (m.find()) {
                return Float.parseFloat(m.group(1));
            }
        }
        return TEXT_SIZE;
    }

    /**
     * Where the text baseline sits inside a field's box. {@code BasePdfBuilder}
     * places each box so that this is the template's own baseline.
     */
    public static float baseline(PDRectangle box) {
        return box.getHeight() * BASELINE_RATIO;
    }

    /** Fraction of a text box's height below the baseline. */
    public static final float BASELINE_RATIO = 0.3f;

    private static PDAppearanceDictionary imageAppearance(PDDocument doc, byte[] png, PDRectangle r) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        if (img == null) {
            throw new IOException("Signature image is not a readable PNG");
        }
        PDImageXObject image = LosslessFactory.createFromImage(doc, img);
        // Fit inside the box, keep the aspect ratio, sit on the box's bottom edge, centred.
        float scale = Math.min(r.getWidth() / img.getWidth(), r.getHeight() / img.getHeight());
        float w = img.getWidth() * scale, h = img.getHeight() * scale;
        float x = (r.getWidth() - w) / 2;

        PDAppearanceStream ap = new PDAppearanceStream(doc);
        ap.setBBox(new PDRectangle(r.getWidth(), r.getHeight()));
        PDResources res = new PDResources();
        COSName im = res.add(image);
        ap.setResources(res);
        String ops = String.format(Locale.ROOT, "q %.3f 0 0 %.3f %.3f 0 cm /%s Do Q\n", w, h, x, im.getName());
        try (var os = ap.getContentStream().createOutputStream(COSName.FLATE_DECODE)) {
            os.write(ops.getBytes(StandardCharsets.US_ASCII));
        }
        PDAppearanceDictionary apd = new PDAppearanceDictionary();
        apd.setNormalAppearance(ap);
        return apd;
    }

    private static PDAcroForm requireForm(PDDocument doc) {
        PDAcroForm form = doc.getDocumentCatalog().getAcroForm(null);
        if (form == null) {
            throw new IllegalArgumentException("Not a signing-document PDF: it has no form");
        }
        return form;
    }

    /** The new revision must start with every byte of the previous one. */
    static byte[] requirePrefix(byte[] before, byte[] after) {
        if (after.length <= before.length || !Arrays.equals(before, 0, before.length, after, 0, before.length)) {
            throw new IllegalStateException("The new revision rewrote earlier bytes");
        }
        return after;
    }

    // ------------------------------------------------------------------ checks

    /** A signature found in the file, and whether its bytes still match. */
    public record SignatureCheck(String field, String signerDn, boolean coversWholeFile, boolean valid) {
    }

    /**
     * Cryptographic check of every signature: the CMS verifies over the ByteRange.
     * Says nothing about what later revisions changed — that is the revision
     * chain's job (hashes stored per revision).
     */
    public static List<SignatureCheck> verify(byte[] pdf) throws IOException {
        List<SignatureCheck> out = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(pdf))) {
            for (PDSignatureField f : signatureFields(doc)) {
                PDSignature s = f.getSignature();
                int[] br = s.getByteRange();
                byte[] signedContent = s.getSignedContent(pdf);
                byte[] contents = s.getContents();
                boolean valid = false;
                String dn = null;
                try (ASN1InputStream asn1 = new ASN1InputStream(contents)) {
                    // /Contents is zero-padded; parse exactly one DER object.
                    byte[] der = asn1.readObject().getEncoded();
                    CMSSignedData cms = new CMSSignedData(new CMSProcessableByteArray(signedContent), der);
                    SignerInformation si = cms.getSignerInfos().getSigners().iterator().next();
                    @SuppressWarnings("unchecked")
                    X509CertificateHolder cert = (X509CertificateHolder) cms.getCertificates()
                            .getMatches(si.getSID()).iterator().next();
                    dn = cert.getSubject().toString();
                    valid = si.verify(new JcaSimpleSignerInfoVerifierBuilder().build(cert));
                } catch (Exception e) {
                    valid = false;
                }
                boolean whole = br != null && br.length == 4 && (long) br[2] + br[3] == pdf.length;
                out.add(new SignatureCheck(f.getFullyQualifiedName(), dn, whole, valid));
            }
        }
        return out;
    }

    private static List<PDSignatureField> signatureFields(PDDocument doc) {
        List<PDSignatureField> fields = new ArrayList<>();
        PDAcroForm form = doc.getDocumentCatalog().getAcroForm(null);
        if (form != null) {
            for (PDField f : form.getFieldTree()) {
                if (f instanceof PDSignatureField sf && sf.getSignature() != null) {
                    fields.add(sf);
                }
            }
        }
        return fields;
    }

    /** Whether the document has a signature in {@code field}. */
    public static boolean isSigned(byte[] pdf, String field) throws IOException {
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(pdf))) {
            PDAcroForm form = doc.getDocumentCatalog().getAcroForm(null);
            return form != null && form.getField(field) instanceof PDSignatureField sf && sf.getSignature() != null;
        }
    }

    static COSName lockKey() {
        return LOCK;
    }
}
