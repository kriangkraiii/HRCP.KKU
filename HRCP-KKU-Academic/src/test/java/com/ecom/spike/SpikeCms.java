package com.ecom.spike;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;

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
 * SPIKE: CAdES-detached CMS for PAdES baseline B — signing-certificate-v2, and no
 * signing-time attribute (PAdES takes the time from the dictionary's /M).
 */
final class SpikeCms implements SignatureInterface {

    final PrivateKey key;
    final X509Certificate cert;
    final Certificate[] chain;

    SpikeCms(byte[] p12, String pin) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayInputStream(p12), pin.toCharArray());
        String alias = Collections.list(ks.aliases()).stream().filter(a -> {
            try {
                return ks.isKeyEntry(a);
            } catch (Exception e) {
                return false;
            }
        }).findFirst().orElseThrow();
        this.key = (PrivateKey) ks.getKey(alias, pin.toCharArray());
        this.chain = ks.getCertificateChain(alias);
        this.cert = (X509Certificate) chain[0];
    }

    @Override
    public byte[] sign(InputStream content) throws IOException {
        try {
            ESSCertIDv2 id = new ESSCertIDv2(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256),
                    MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()),
                    new IssuerSerial(new GeneralNames(new GeneralName(
                            X500Name.getInstance(cert.getIssuerX500Principal().getEncoded()))),
                            cert.getSerialNumber()));
            ASN1EncodableVector v = new ASN1EncodableVector();
            v.add(new Attribute(PKCSObjectIdentifiers.id_aa_signingCertificateV2,
                    new DERSet(new SigningCertificateV2(new ESSCertIDv2[] { id }))));
            DefaultSignedAttributeTableGenerator defaults = new DefaultSignedAttributeTableGenerator(new AttributeTable(v));
            CMSAttributeTableGenerator signed = params -> {
                AttributeTable t = defaults.getAttributes(params);
                return t.remove(CMSAttributes.signingTime);
            };

            String alg = key.getAlgorithm().equals("EC") ? "SHA256withECDSA" : "SHA256withRSA";
            CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
            gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().build())
                    .setSignedAttributeGenerator(signed)
                    .build(new JcaContentSignerBuilder(alg).build(key), cert));
            gen.addCertificates(new JcaCertStore(Arrays.asList(chain)));
            return gen.generate(new CMSProcessableByteArray(content.readAllBytes()), false).getEncoded();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
