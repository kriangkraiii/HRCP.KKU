package com.ecom.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.ecom.model.StoredBlob;
import com.ecom.repository.StoredBlobRepository;

/**
 * Keeps a database copy of files stored on local disk.
 *
 * <p>Signature images and .p12 certificates used to live only on the disk of the
 * machine that received them, while the rows pointing at them live in a database
 * every machine shares. Two servers on one database — the test setup — meant a
 * certificate uploaded on one was "missing" on the other, and each re-upload
 * deactivated the certificate the other machine had. Mirroring the bytes into
 * the database makes the file readable from anywhere the row is.
 *
 * <p>Each call runs in its own transaction and never throws: the disk write has
 * already happened, and a failed mirror must neither fail nor roll back the
 * signature or certificate the caller is saving. The transaction is programmatic
 * so that a failure at commit is caught here too, not only one inside the call.
 */
@Component
public class BlobMirror {

    public static final String SIGNATURE = "signature";
    public static final String CERTIFICATE = "certificate";

    private static final Logger log = LoggerFactory.getLogger(BlobMirror.class);

    private final StoredBlobRepository repository;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public BlobMirror(StoredBlobRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTx.setReadOnly(true);
    }

    public void save(String kind, String filename, byte[] content) {
        if (filename == null || content == null || content.length == 0) {
            return;
        }
        try {
            tx.executeWithoutResult(status -> {
                if (!repository.existsById(StoredBlob.keyOf(kind, filename))) {
                    repository.save(new StoredBlob(kind, filename, content));
                }
            });
        } catch (RuntimeException e) {
            log.error("Could not copy {} {} into the database: {}", kind, filename, e.toString());
        }
    }

    /** The stored bytes, or null when there is no copy. */
    public byte[] load(String kind, String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        try {
            return readTx.execute(status -> repository.findById(StoredBlob.keyOf(kind, filename))
                    .map(StoredBlob::getContent)
                    .orElse(null));
        } catch (RuntimeException e) {
            log.error("Could not read {} {} from the database: {}", kind, filename, e.toString());
            return null;
        }
    }

    public void delete(String kind, String filename) {
        if (filename == null || filename.isBlank()) {
            return;
        }
        try {
            tx.executeWithoutResult(status -> repository.deleteById(StoredBlob.keyOf(kind, filename)));
        } catch (RuntimeException e) {
            log.error("Could not delete {} {} from the database: {}", kind, filename, e.toString());
        }
    }
}
