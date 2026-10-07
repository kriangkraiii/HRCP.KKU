package com.ecom.external.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ecom.external.model.KkuRegulationDoc;
import com.ecom.external.repository.KkuRegulationDocRepository;

/** เปิดเอกสาร มข. ผ่านเซิร์ฟเวอร์ของเราแทน Google Docs Viewer — hr2 ห้ามฝังใน iframe ข้ามโดเมน (X-Frame-Options) */
class KkuDocumentFileServiceTest {

    private KkuRegulationDocRepository docRepo;
    private final AtomicInteger downloads = new AtomicInteger();
    private byte[] served = "%PDF-1.7 test".getBytes();
    private KkuDocumentFileService service;

    @BeforeEach
    void setUp() {
        docRepo = mock(KkuRegulationDocRepository.class);
        service = new KkuDocumentFileService(docRepo) {
            @Override
            protected byte[] download(String url) throws IOException {
                downloads.incrementAndGet();
                if (served.length > MAX_BYTES) {
                    throw new IOException("too large");
                }
                return served;
            }
        };
    }

    private KkuRegulationDoc doc(long id, String url) {
        KkuRegulationDoc d = new KkuRegulationDoc("ข้อบังคับ", "ข้อบังคับ 2569", url, "a.pdf", 1);
        when(docRepo.findById(id)).thenReturn(Optional.of(d));
        return d;
    }

    @Test
    void servesTheFileAndKeepsItForTheNextReader() throws IOException {
        KkuRegulationDoc d = doc(1L, "https://hr2.kku.ac.th/wp-content/uploads/a.pdf");

        assertThat(service.find(1L)).contains(d);
        assertThat(service.bytes(d)).isEqualTo(served);
        assertThat(service.bytes(d)).isEqualTo(served);
        assertThat(downloads).hasValue(1);
    }

    @Test
    void onlyServesFilesFromTheHrSite() {
        doc(2L, "https://evil.example.com/a.pdf");
        doc(3L, "http://169.254.169.254/latest/meta-data");

        assertThat(service.find(2L)).isEmpty();
        assertThat(service.find(3L)).isEmpty();
        assertThat(service.find(99L)).isEmpty();
        assertThat(downloads).hasValue(0);
    }

    @Test
    void anOversizedFileIsRefusedAndNotCached() {
        KkuRegulationDoc d = doc(4L, "https://hr2.kku.ac.th/wp-content/uploads/big.pdf");
        served = new byte[KkuDocumentFileService.MAX_BYTES + 1];

        assertThatThrownBy(() -> service.bytes(d)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> service.bytes(d)).isInstanceOf(IOException.class);
        assertThat(downloads).hasValue(2);
    }
}
