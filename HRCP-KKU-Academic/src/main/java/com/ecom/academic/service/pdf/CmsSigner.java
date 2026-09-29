package com.ecom.academic.service.pdf;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;

import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.ess.ESSCertIDv2;
import org.bouncycastle.asn1.ess.SigningCertificateV2;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.IssuerSerial;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSAttributeTableGenerator;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

/**
 * A .p12 opened for one signature: CAdES-detached CMS for PAdES baseline B.
 *
 * <p>Carries signing-certificate-v2 and no signing-time attribute — PAdES takes
 * the time from the signature dictionary's /M. The key lives only as long as this
 * object; nothing here is stored.
 */
public final class CmsSigner implements SignatureInterface {

    private final PrivateKey key;
    private final X509Certificate certificate;
    private final Certificate[] chain;

    private CmsSigner(PrivateKey key, Certificate[] chain) {
        this.key = key;
        this.chain = chain;
        this.certificate = (X509Certificate) chain[0];
    }

    /**
     * @throws GeneralSecurityException for a wrong PIN or a file without a key
     */
    public static CmsSigner open(byte[] p12, char[] pin) throws GeneralSecurityException, IOException {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayInputStream(p12), pin);
        for (String alias : Collections.list(ks.aliases())) {
            if (ks.isKeyEntry(alias)) {
                Certificate[] chain = ks.getCertificateChain(alias);
                if (chain != null && chain.length > 0 && chain[0] instanceof X509Certificate) {
                    return new CmsSigner((PrivateKey) ks.getKey(alias, pin), chain);
                }
            }
        }
        throw new GeneralSecurityException("No signing key in the .p12 file");
    }

    public X509Certificate certificate() {
        return certificate;
    }

    /** SHA-256 of the DER certificate, lowercase hex. */
    public String fingerprint() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public byte[] sign(InputStream content) throws IOException {
        try {
            ESSCertIDv2 id = new ESSCertIDv2(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256),
                    MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()),
                    new IssuerSerial(new GeneralNames(new GeneralName(
                            X500Name.getInstance(certificate.getIssuerX500Principal().getEncoded()))),
                            certificate.getSerialNumber()));
            ASN1EncodableVector v = new ASN1EncodableVector();
            v.add(new Attribute(PKCSObjectIdentifiers.id_aa_signingCertificateV2,
                    new DERSet(new SigningCertificateV2(new ESSCertIDv2[] { id }))));
            DefaultSignedAttributeTableGenerator defaults = new DefaultSignedAttributeTableGenerator(new AttributeTable(v));
            CMSAttributeTableGenerator signed = params -> defaults.getAttributes(params).remove(CMSAttributes.signingTime);

            String alg = "EC".equals(key.getAlgorithm()) ? "SHA256withECDSA" : "SHA256withRSA";
            CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
            gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().build())
                    .setSignedAttributeGenerator(signed)
                    .build(new JcaContentSignerBuilder(alg).build(key), certificate));
            gen.addCertificates(new JcaCertStore(Arrays.asList(chain)));
            return gen.generate(new CMSProcessableByteArray(content.readAllBytes()), false).getEncoded();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Could not create the CMS signature: " + e.getMessage(), e);
        }
    }
}
