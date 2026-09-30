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
        private final String value;

        public DoesNotFitException(String field, String value) {
            super("\"" + value + "\" is too long for field " + field);
            this.field = field;
            this.value = value;
        }

        public String field() {
            return field;
        }

        public String value() {
            return value;
        }
    }

    /** How a value is drawn in its box: the size, and its lines when the box has more than one. */
    private record Fit(float size, List<String> wrapped) {
    }

    /**
     * The size (and lines) {@code text} is drawn at in a box {@code width} wide with
     * {@code lines} lines, shrinking from {@code preferred} down to {@link #MIN_TEXT_SIZE}.
     *
     * @return null when it does not fit even at the smallest size
     */
    private Fit fit(String text, float preferred, float width, int lines) {
        if (text.isEmpty()) {
            return new Fit(preferred, null);
        }
        float minimum = Math.min(MIN_TEXT_SIZE, preferred);
        if (lines > 1) {
            for (float s = preferred; s >= minimum; s -= 0.5f) {
                List<String> candidate = wrap(text, s, width - 1);
                if (candidate != null && candidate.size() <= lines) {
                    return new Fit(s, candidate);
                }
            }
            return null;
        }
        float size = thai.fittingSize(text, preferred, minimum, width - 1);
        return size < 0 ? null : new Fit(size, null);
    }

    /**
     * Whether {@code text} fits a reserved box — the same rule {@link #fill} applies, so
     * a value checked here is never refused when it is written into the PDF.
     */
    public boolean fits(String text, float preferred, float width, int lines) {
        return text == null || fit(text, preferred, width, Math.max(1, lines)) != null;
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

    /**
     * What {@link #sign} would draw, without signing: the signer's fields and their
     * picture in the signature box. For the signing page's preview only — the result
     * is shown and thrown away, never stored.
     */
    public byte[] preview(byte[] pdf, String signatureField, Map<String, String> ownValues, byte[] imagePng)
            throws IOException {
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(pdf))) {
            PDAcroForm form = requireForm(doc);
            for (var e : ownValues.entrySet()) {
                fillField(doc, form, e.getKey(), e.getValue());
            }
            if (imagePng != null && form.getField(signatureField) instanceof PDSignatureField sf) {
                PDAnnotationWidget widget = sf.getWidgets().get(0);
                widget.setAppearance(imageAppearance(doc, imagePng, widget.getRectangle()));
                widget.getCOSObject().setNeedToBeUpdated(true);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.saveIncremental(out);
            return out.toByteArray();
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
        int lines = field.getCOSObject().getInt(BasePdfBuilder.LINES, 1);
        float pitch = field.getCOSObject().getFloat(BasePdfBuilder.PITCH, 0);
        for (PDAnnotationWidget w : field.getWidgets()) {
            PDRectangle r = w.getRectangle();
            Fit fit = fit(text, preferred, r.getWidth(), lines);
            if (fit == null) {
                throw new DoesNotFitException(name, text);
            }
            float size = fit.size();
            List<String> wrapped = fit.wrapped();

            PDAppearanceStream ap = new PDAppearanceStream(doc);
            ap.setBBox(new PDRectangle(r.getWidth(), r.getHeight()));
            PDResources res = new PDResources();
            // The /DR font object itself: a later revision must never embed a new font.
            COSDictionary fonts = new COSDictionary();
            fonts.setItem(FONT, form.getDefaultResources().getCOSObject().getCOSDictionary(COSName.FONT).getItem(FONT));
            res.getCOSObject().setItem(COSName.FONT, fonts);
            ap.setResources(res);
            StringBuilder body = new StringBuilder();
            if (text.isEmpty()) {
                // nothing to draw
            } else if (TICK.equals(text)) {
                body.append(tick(r, preferred));
            } else if (wrapped != null) {
                // Line i sits where the base found the template's line i.
                float lineBox = preferred * 1.3f;
                float firstBaseline = r.getHeight() - lineBox + lineBox * BASELINE_RATIO;
                for (int i = 0; i < wrapped.size(); i++) {
                    body.append(thai.operators(FONT, size, wrapped.get(i), 0, firstBaseline - i * pitch));
                }
            } else {
                body.append(thai.operators(FONT, size, text, 0, baseline(r)));
            }
            String ops = "/Tx BMC\n" + body + "EMC\n";
            try (var os = ap.getContentStream().createOutputStream(COSName.FLATE_DECODE)) {
                os.write(ops.getBytes(StandardCharsets.US_ASCII));
            }
            PDAppearanceDictionary apd = new PDAppearanceDictionary();
            apd.setNormalAppearance(ap);
            w.setAppearance(apd);
            w.getCOSObject().setNeedToBeUpdated(true);
        }
        // A push button has no value a viewer could redraw; the text goes in /TU,
        // where search, screen readers and valueOf() find it.
        field.getCOSObject().setItem(isPushButton(field) ? COSName.TU : COSName.V, new COSString(text));
        field.getCOSObject().setNeedToBeUpdated(true);
    }

    /**
     * Breaks Thai text into lines no wider than {@code width} at {@code size}, at
     * word boundaries (the JDK's Thai dictionary). Null when a single word is wider
     * than a line.
     */
    List<String> wrap(String text, float size, float width) {
        java.text.BreakIterator words = java.text.BreakIterator.getLineInstance(Locale.forLanguageTag("th"));
        words.setText(text);
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int start = words.first();
        for (int end = words.next(); end != java.text.BreakIterator.DONE; start = end, end = words.next()) {
            String word = text.substring(start, end);
            if (thai.width(line + word.stripTrailing(), size) <= width) {
                line.append(word);
                continue;
            }
            if (line.length() > 0) {
                lines.add(line.toString().strip());
                line.setLength(0);
            }
            if (thai.width(word.stripTrailing(), size) > width) {
                return null;
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString().strip());
        }
        return lines;
    }

    static boolean isPushButton(PDField field) {
        return field instanceof org.apache.pdfbox.pdmodel.interactive.form.PDPushButton;
    }

    /** The text a server-filled field holds (push buttons keep it in /TU). */
    public static String valueOf(PDField field) {
        if (isPushButton(field)) {
            String tu = field.getCOSObject().getString(COSName.TU);
            return tu != null ? tu : "";
        }
        return field.getValueAsString();
    }

    /** The mark printed in a tick box. TH Sarabun New has no ✓, so it is drawn. */
    public static final String TICK = "✓";

    /**
     * The ✓ outline of DejaVu Sans (2048 units per em), the font LibreOffice falls
     * back to for the ticks it prints in the base, so both columns match.
     */
    private static final String TICK_GLYPH = "453 654 m 479 654 498.7 632.7 512 590 c 538.7 510 557.7 470 569 470 c "
            + "577.7 470 586.7 476.7 596 490 c 783.3 790 956.7 1032.7 1116 1218 c 1157.3 1266 1223 1290 1313 1290 c "
            + "1334.3 1290 1348.7 1288 1356 1284 c 1363.3 1280 1367 1275 1367 1269 c 1367 1259.7 1356 1241.3 1334 1214 c "
            + "1076.7 904.7 838 578 618 234 c 602.7 210 571.3 198 524 198 c 476 198 447.7 200 439 204 c "
            + "416.3 214 389.7 265 359 357 c 324.3 459 307 523 307 549 c 307 577 330.3 604 377 630 c "
            + "405.7 646 431 654 453 654 c h f";
    private static final float TICK_INK_LEFT = 307, TICK_INK_WIDTH = 1060, TICK_EM = 2048;

    /** A check mark centred in the box, sitting on the text baseline. */
    private static String tick(PDRectangle r, float size) {
        float k = size / TICK_EM;
        float x = (r.getWidth() - TICK_INK_WIDTH * k) / 2 - TICK_INK_LEFT * k;
        return String.format(Locale.ROOT,
                "/Span <</ActualText <FEFF2713>>> BDC q 0 g %.5f 0 0 %.5f %.2f %.2f cm %s Q EMC\n",
                k, k, x, baseline(r), TICK_GLYPH);
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
