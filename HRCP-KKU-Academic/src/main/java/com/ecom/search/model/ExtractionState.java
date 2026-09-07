package com.ecom.search.model;

/**
 * Where a document is in the queue for pulling text out of its file.
 *
 * <p>Most rows never leave {@link #NONE}: only documents backed by an uploaded
 * DOCX or PDF have anything to extract. Generated documents are deliberately
 * left alone — their text comes from {@code json_data}, which is indexed
 * directly and for free.
 */
public enum ExtractionState {

    /** No file behind this document. The normal case. */
    NONE,

    /** A file is waiting to be read. */
    PENDING,

    /** Claimed by the worker. */
    RUNNING,

    /** Text extracted and written to {@code body}. */
    DONE,

    /** Extraction threw; the reason is in {@code extraction_error}. */
    FAILED,

    /** Deliberately not extracted — too large, encrypted, or an unsupported type. */
    SKIPPED
}
