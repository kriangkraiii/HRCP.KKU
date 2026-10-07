package com.ecom.external.service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Optional;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import com.ecom.external.model.KkuRegulationDoc;
import com.ecom.external.repository.KkuRegulationDocRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * ไฟล์ PDF ของคลังเอกสาร มข. ส่งผ่านเซิร์ฟเวอร์ของเราเอง
 *
 * <p>hr2.kku.ac.th ส่ง {@code X-Frame-Options: SAMEORIGIN} ฝังไฟล์ของเขาใน iframe ตรง ๆ ไม่ได้ หน้าเอกสารจึงเคย
 * เปิดผ่าน Google Docs Viewer ซึ่งต้องให้ Google ดึงไฟล์ไปแปลงก่อน — ช้าหลายวินาที (แค่ HTML ของ viewer ก็ ~3.5 วินาที)
 * ดึงเองจาก hr2 ใช้ ~0.1 วินาที แล้วเบราว์เซอร์แสดงด้วยตัวอ่าน PDF ของมันเอง และเก็บไว้ในหน่วยความจำให้คนถัดไป
 *
 * <p>ส่งได้เฉพาะไฟล์ที่อยู่ในคลังเอกสาร (หาจาก id) และอยู่บน hr2.kku.ac.th เท่านั้น — ไม่เปิดช่องให้ใช้เซิร์ฟเวอร์นี้
 * ดึง URL อื่น (SSRF)
 */
@Service
public class KkuDocumentFileService {

    public static final String ALLOWED_HOST = "hr2.kku.ac.th";

    /** เอกสารใหญ่สุดบนหน้า HR ตอนนี้ ~4.3 MB */
    public static final int MAX_BYTES = 30 * 1024 * 1024;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final KkuRegulationDocRepository docRepo;

    /** ทั้งคลังราว 50 ไฟล์ ~20 MB — ใส่ได้หมด ไฟล์ที่ HR แก้ใหม่จะถูกดึงใหม่ภายในหนึ่งวัน */
    private final Cache<String, byte[]> files = Caffeine.newBuilder()
            .maximumWeight(80L * 1024 * 1024)
            .weigher((String url, byte[] bytes) -> bytes.length)
            .expireAfterWrite(Duration.ofDays(1))
            .build();

    public KkuDocumentFileService(KkuRegulationDocRepository docRepo) {
        this.docRepo = docRepo;
    }

    /** เอกสารในคลังที่ส่งต่อได้ — ว่างเมื่อไม่มี id นี้ หรือไฟล์ไม่ได้อยู่บน hr2.kku.ac.th */
    public Optional<KkuRegulationDoc> find(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return docRepo.findById(id).filter(doc -> isOnHrSite(doc.getFileUrl()));
    }

    /** เนื้อไฟล์ จากหน่วยความจำ หรือดึงจาก hr2 — ดึงไม่ได้หรือใหญ่เกินจะไม่ถูกเก็บไว้ */
    public byte[] bytes(KkuRegulationDoc doc) throws IOException {
        String url = doc.getFileUrl();
        byte[] cached = files.getIfPresent(url);
        if (cached != null) {
            return cached;
        }
        byte[] fetched = download(url);
        if (fetched.length > MAX_BYTES) {
            throw new IOException("ไฟล์ใหญ่เกิน " + MAX_BYTES + " ไบต์: " + url);
        }
        files.put(url, fetched);
        return fetched;
    }

    protected byte[] download(String url) throws IOException {
        Connection.Response response = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .ignoreContentType(true)
                // เกินขนาดนี้ Jsoup ตัดทิ้งเงียบ ๆ — อ่านเกินไปหนึ่งไบต์ไว้ให้ bytes() รู้ว่าใหญ่เกิน
                .maxBodySize(MAX_BYTES + 1)
                .timeout(30000)
                .execute();
        return response.bodyAsBytes();
    }

    private static boolean isOnHrSite(String url) {
        if (url == null) {
            return false;
        }
        try {
            // ชื่อไฟล์บน hr2 เป็นภาษาไทยและอาจมีช่องว่าง — URI รับอักษรไทยได้ แต่ช่องว่างต้องเข้ารหัสก่อน
            URI parsed = new URI(url.strip().replace(" ", "%20"));
            return "https".equalsIgnoreCase(parsed.getScheme()) && ALLOWED_HOST.equalsIgnoreCase(parsed.getHost());
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
