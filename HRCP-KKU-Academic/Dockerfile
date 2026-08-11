FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B dependency:go-offline
COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

# LibreOffice ใช้แปลง DOCX -> PDF ทั้งสำหรับ preview และปุ่มดาวน์โหลด PDF
RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
        libreoffice-writer \
        fonts-thai-tlwg \
        fonts-liberation \
        fontconfig; \
    rm -rf /var/lib/apt/lists/*

# ฟอนต์ต้องชื่อ "TH Sarabun New" ตรงกับที่เทมเพลต DOCX ใช้ — Debian ไม่มีแพ็กเกจนี้
# จึงต้องใส่ไฟล์มาเอง ถ้าไม่มี LibreOffice จะ substitute ฟอนต์อื่น
# แล้วความกว้างตัวอักษรเปลี่ยน → เลย์เอาต์เพี้ยนจากไฟล์ Word จริง
COPY docker/fonts/ /usr/share/fonts/truetype/th-sarabun/
RUN set -eux; \
    fc-cache -f -v > /dev/null; \
    fc-list | grep -qi "TH Sarabun New" || { echo "ERROR: TH Sarabun New font missing"; exit 1; }

# soffice ต้องเขียน user profile ได้ — เก็บไว้ใน /tmp
ENV HOME=/tmp

COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
