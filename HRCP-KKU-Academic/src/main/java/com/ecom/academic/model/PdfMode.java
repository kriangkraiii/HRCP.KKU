package com.ecom.academic.model;

/** How an envelope's signed PDF is produced. Fixed when the envelope is created. */
public enum PdfMode {
    /** Rendered after the last signature; digital signatures applied with saved PINs. */
    LEGACY,
    /** Each signer signs the PDF at signing time; later values are appended as form fills. */
    INCREMENTAL
}
