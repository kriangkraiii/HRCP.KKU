package com.ecom.academic.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.service.DocumentProgress.Stage;

/**
 * กล่อง "ขั้นต่อไป" ในหน้าคำร้องของเจ้าหน้าที่ — เช็กลิสต์ของขั้นปัจจุบัน ทั้งสองเฟส
 *
 * <p>ช่องอัปเดตสถานะกันไม่ให้ข้ามขั้นได้ แต่ไม่บอกว่าก่อนจะไปขั้นถัดไปต้องทำอะไร หลายขั้นเปลี่ยนเองเมื่อเอกสาร
 * ของเจ้าหน้าที่ลงนามครบ ({@code autoUpdateStatusByDocument} ของแต่ละเฟส) บางขั้นเป็นมติที่ประชุมที่ต้องบันทึกเอง
 *
 * <p>แต่ละข้อติ๊กตามสถานะเอกสารจริงจาก {@link DocumentProgress} ชุดเดียวกับรายการเอกสาร ไม่มีใครต้องกดติ๊กเอง
 * ข้อแรกที่ต้องทำได้ไฮไลท์ ({@link State#CURRENT}) หน้าอัปเดตสดเมื่อคำร้องเปลี่ยน ({@code request_live.js})
 */
public final class NextStepGuide {

    /** สถานะของงานหนึ่งข้อ */
    public enum State {
        /** เสร็จแล้ว */
        DONE,
        /** ข้อที่ต้องทำตอนนี้ — ข้อแรกที่ยังไม่เสร็จและเจ้าหน้าที่ทำได้ */
        CURRENT,
        /** เจ้าหน้าที่ทำได้ แต่มีข้อก่อนหน้าที่ควรทำก่อน */
        TODO,
        /** รอคนอื่น (ผู้ลงนาม ผู้ยื่น) */
        WAITING,
        /** ยังไม่ถึง */
        UPCOMING
    }

    /** งานหนึ่งข้อ — {@code documentType} คือเอกสารที่ลิงก์ไป หรือ null เมื่อเป็นงานที่ช่องอัปเดตสถานะ */
    public record Item(String text, Integer documentType, State state) {
        public String getText() {
            return text;
        }

        public Integer getDocumentType() {
            return documentType;
        }

        public State getState() {
            return state;
        }

        Item as(State newState) {
            return new Item(text, documentType, newState);
        }
    }

    /** หัวข้อของขั้น งานที่ต้องทำ และหมายเหตุ (null เมื่อไม่มี) */
    public record Step(String title, List<Item> items, String hint) {

        /** ข้อแรกที่เจ้าหน้าที่ทำได้กลายเป็นข้อที่ต้องทำตอนนี้ */
        static Step of(String title, List<Item> items, String hint) {
            List<Item> marked = new ArrayList<>(items);
            for (int i = 0; i < marked.size(); i++) {
                if (marked.get(i).state() == State.TODO) {
                    marked.set(i, marked.get(i).as(State.CURRENT));
                    break;
                }
            }
            return new Step(title, List.copyOf(marked), hint);
        }

        public String getTitle() {
            return title;
        }

        public List<Item> getItems() {
            return items;
        }

        public String getHint() {
            return hint;
        }

        public long getDoneCount() {
            return items.stream().filter(i -> i.state() == State.DONE).count();
        }

        public int getTotal() {
            return items.size();
        }
    }

    /** วันที่ของขั้นหลังมติในเฟส 2 — มาจาก {@link CouncilTimeline} */
    public record CouncilDates(LocalDate sendToHrBy, LocalDate appealBy, String appealProblem) {
        public static final CouncilDates NONE = new CouncilDates(null, null, null);
    }

    private NextStepGuide() {
    }

    private static Item manual(String text) {
        return new Item(text, null, State.TODO);
    }

    // =====================================================================
    // เฟส 2 — ขอกำหนดตำแหน่ง
    // =====================================================================

    /** เอกสารของเจ้าหน้าที่เฟส 2 ที่เลื่อนสถานะเมื่อลงนามครบ */
    static final int P2_CHECKLIST = 7;
    static final int P2_SUMMARY = 8;

    /**
     * @param docRows        เอกสารที่แสดงในหน้าคำร้อง (รวมเอกสารที่ 9 รายงานวิจัย) → ชื่อเอกสาร
     * @param awaitingResign เอกสารที่ส่งกลับแก้แล้วยังลงนามใหม่ไม่ครบ
     * @return ขั้นต่อไป หรือ null เมื่อไม่มีอะไรให้เจ้าหน้าที่ทำ (แบบร่าง หรือสภาอนุมัติแล้ว)
     */
    public static Step position(PositionRequestStatus status, Map<Integer, String> docRows,
            Map<Integer, Stage> progress, Collection<Integer> awaitingResign, CouncilDates dates) {
        return position(status, docRows, progress, awaitingResign, java.util.List.of(), dates);
    }

    /**
     * @param resubmitted เอกสารที่ส่งกลับซึ่งผู้ยื่นแก้และลงนามใหม่แล้ว ยังรอผู้ลงนามคนอื่นลงนามซ้ำ
     *                    ({@code RevisionProgress.Stage.SUBMITTED}) — ลูกอยู่ที่เจ้าหน้าที่หรือผู้ลงนาม ไม่ใช่ผู้ยื่น
     */
    public static Step position(PositionRequestStatus status, Map<Integer, String> docRows,
            Map<Integer, Stage> progress, Collection<Integer> awaitingResign, Collection<Integer> resubmitted,
            CouncilDates dates) {
        if (status == null || status == PositionRequestStatus.DRAFT || status == PositionRequestStatus.COUNCIL_APPROVED) {
            return null;
        }
        Docs docs = new Docs(SignatureModule.POSITION, docRows, progress, PositionRequestService::docNumber);
        CouncilDates d = dates == null ? CouncilDates.NONE : dates;
        List<Item> items = new ArrayList<>();
        if (status == PositionRequestStatus.REVISION_REQUESTED) {
            items.addAll(revisionItems(docs, awaitingResign, resubmitted));
        } else {
            // ขั้นแรกหลังรับคำร้องเห็นทั้งชุด (ที่เสร็จแล้วติ๊กไว้) ขั้นหลังจากนั้นเห็นเฉพาะที่ยังค้าง
            items.addAll(docs.chores(status == PositionRequestStatus.DOCUMENT_RECEIVED));
        }
        String sendBack = "ที่ประชุมให้แก้ไข → กด “ส่งกลับแก้ไข” ในเอกสารที่ต้องแก้";

        Step step = switch (status) {
            case DOCUMENT_RECEIVED -> {
                docs.staffDocument(items, P2_CHECKLIST, "ตรวจสอบความถูกต้อง/ครบถ้วน");
                yield Step.of("ตรวจสอบเอกสารของผู้ยื่น และทำแบบตรวจสอบคุณสมบัติ", items,
                        "เอกสารของผู้ยื่นไม่ถูกต้อง ให้กด “ส่งกลับแก้ไข” ในหน้าเอกสารนั้น");
            }
            case DOCUMENT_VERIFICATION -> {
                docs.staffDocument(items, P2_SUMMARY, "เสนอวาระกลั่นกรองฯ");
                yield Step.of("สรุปรายละเอียดและรายชื่อผู้ทรงคุณวุฒิ เพื่อเสนอวาระกลั่นกรองฯ", items, null);
            }
            case REVISION_REQUESTED -> Step.of(revisionTitle(items, awaitingResign, resubmitted), items,
                    "แก้และลงนามใหม่ครบทุกช่องแล้ว สถานะกลับเป็น “ตรวจสอบความถูกต้อง/ครบถ้วน” เอง");
            case SCREENING_COMMITTEE -> {
                items.add(manual("ที่ประชุมกลั่นกรองฯ เห็นชอบ → อัปเดตสถานะเป็น “รับรองมติกลั่นกรองฯ”"));
                items.add(manual(sendBack));
                yield Step.of("นำเข้าที่ประชุมคณะกรรมการกลั่นกรองฯ แล้วบันทึกมติ", items, null);
            }
            case SCREENING_APPROVED -> {
                items.add(manual("นำเข้าวาระแล้ว → อัปเดตสถานะเป็น “เสนอวาระคณะกรรมการวิทยาลัยฯ”"));
                yield Step.of("เสนอวาระคณะกรรมการประจำวิทยาลัยฯ", items, null);
            }
            case COLLEGE_COMMITTEE -> {
                items.add(manual("ที่ประชุมเห็นชอบ → อัปเดตสถานะเป็น “รับรองมติคณะกรรมการวิทยาลัยฯ” พร้อมวันที่มีมติ"));
                items.add(manual(sendBack));
                yield Step.of("บันทึกมติคณะกรรมการประจำวิทยาลัยฯ", items, null);
            }
            case COLLEGE_APPROVED -> {
                items.add(manual("ส่งเรื่องแล้ว → อัปเดตสถานะเป็น “ส่งออกกองทรัพยากรบุคคล มข.”"));
                yield Step.of("ส่งเรื่องให้กองทรัพยากรบุคคล มข.", items,
                        d.sendToHrBy() == null ? "ส่งภายใน 3 วันทำการนับถัดจากวันที่มีมติ (หรือวันที่ได้รับเอกสารแก้ไขครบ)"
                                : "ส่งภายใน " + thai(d.sendToHrBy())
                                        + " (3 วันทำการนับถัดจากวันที่มีมติหรือวันที่ได้รับเอกสารแก้ไขครบ)");
            }
            case SENT_TO_HR, APPEAL_SUBMITTED -> {
                items.add(manual("สภามหาวิทยาลัยมีมติแล้ว → อัปเดตสถานะเป็น “สภามหาวิทยาลัยอนุมัติ” หรือ “ไม่อนุมัติ”"
                        + " พร้อมวันที่สภามีมติ"));
                yield Step.of("รอผลสภามหาวิทยาลัย", items,
                        "ไม่อนุมัติ: ต้องใส่วันที่ผู้ขอรับทราบมติด้วย ใช้นับกำหนดขอทบทวน 90 วัน");
            }
            case COUNCIL_REJECTED -> {
                if (d.appealProblem() == null) {
                    items.add(manual("ผู้ขอยื่นขอทบทวนที่ส่วนงานแล้ว → อัปเดตสถานะเป็น “ยื่นขอทบทวนผลการพิจารณา”"
                            + " พร้อมวันที่รับเรื่องและวันที่คณะกรรมการประจำส่วนงานเห็นชอบ"));
                }
                yield Step.of("รอผู้ขอตัดสินใจขอทบทวน", items,
                        d.appealProblem() != null ? d.appealProblem()
                                : d.appealBy() == null ? null : "ขอทบทวนได้ถึง " + thai(d.appealBy()));
            }
            default -> null;
        };
        // ขั้นที่ flow ไม่ให้เปลี่ยนเป็น "ส่งแก้ไข" สถานะคงเดิม แต่งานยังรอเอกสารที่ส่งกลับอยู่
        return status == PositionRequestStatus.REVISION_REQUESTED ? step
                : withRevision(step, docs, awaitingResign, resubmitted);
    }

    // =====================================================================
    // เฟส 1 — ประเมินผลการสอน
    // =====================================================================

    /** เอกสารของเจ้าหน้าที่เฟส 1 — ลงนามครบแล้วสถานะเลื่อนเอง ยกเว้น 3 (ขอรายชื่อ) และ 6 (ข้อเสนอแนะ) */
    static final int P1_COMMITTEE_NAMES = 3;
    static final int P1_APPOINTMENT = 4;
    static final int P1_INVITATION = 5;
    static final int P1_SUGGESTIONS = 6;
    static final int P1_EVALUATION = 7;
    static final int P1_ENDORSEMENT = 8;
    static final int P1_RESULT_LETTER = 9;

    /**
     * @param docRows  เอกสารในหน้าคำร้อง → ชื่อเอกสาร (เลขเอกสารคือเลข type)
     * @param appealBy วันสุดท้ายที่ผู้ขอขอทบทวนได้ (null เมื่อยังไม่เริ่มนับ)
     * @return ขั้นต่อไป หรือ null เมื่อไม่มีอะไรให้เจ้าหน้าที่ทำ (แบบร่าง หรือเสร็จสิ้นแล้ว)
     */
    public static Step academic(RequestStatus status, Map<Integer, String> docRows, Map<Integer, Stage> progress,
            LocalDate appealBy) {
        return academic(status, docRows, progress, appealBy, List.of(), List.of());
    }

    /**
     * @param awaitingResign เอกสารที่ส่งกลับแก้แล้วยังลงนามใหม่ไม่ครบ — การส่งกลับไม่เปลี่ยนสถานะคำร้องเฟส 1
     *                       งานจึงรอผู้ยื่นทั้งที่สถานะยังเป็นขั้นเดิม
     * @param resubmitted    เอกสารที่ส่งกลับซึ่งผู้ยื่นแก้และลงนามใหม่แล้ว ยังรอผู้ลงนามคนอื่น
     */
    public static Step academic(RequestStatus status, Map<Integer, String> docRows, Map<Integer, Stage> progress,
            LocalDate appealBy, Collection<Integer> awaitingResign, Collection<Integer> resubmitted) {
        if (status == null || status == RequestStatus.DRAFT || status == RequestStatus.COMPLETED) {
            return null;
        }
        Docs docs = new Docs(SignatureModule.ACADEMIC, docRows, progress, type -> type);
        List<Item> items = new ArrayList<>(docs.chores(status == RequestStatus.RECEIVED));

        Step step = switch (status) {
            case RECEIVED -> {
                docs.staffDocument(items, P1_COMMITTEE_NAMES, null);
                docs.staffDocument(items, P1_APPOINTMENT, "แต่งตั้งอนุกรรมการ");
                yield Step.of("ขอรายชื่อและแต่งตั้งคณะอนุกรรมการประเมินผลการสอน", items,
                        "คำสั่งแต่งตั้งต้องระบุอนุกรรมการครบ 3 คน — คณบดีไม่เห็นชอบ: อัปเดตสถานะเป็น “แบบร่าง” เพื่อคืนให้ผู้ยื่นแก้");
            }
            case SUB_COMMITTEE_APPOINTED -> {
                docs.staffDocument(items, P1_INVITATION, "นัดหมายคณะอนุกรรมการ");
                yield Step.of("เชิญกรรมการผู้ทรงคุณวุฒิและนัดประชุม", items,
                        "หนังสือเชิญลงนามครบแล้ว ระบบส่งถึงกรรมการเอง");
            }
            case MEETING_SCHEDULED -> {
                docs.staffDocument(items, P1_EVALUATION, "ผ่าน / ไม่ผ่าน ตามผลประเมิน");
                items.add(new Item("มติให้แก้ไข → ส่งข้อเสนอแนะจากคณะอนุกรรมการ (กด “ส่งข้อเสนอแนะ”) — "
                        + docs.name(P1_SUGGESTIONS), P1_SUGGESTIONS, State.TODO));
                yield Step.of("ประชุมคณะอนุกรรมการและบันทึกผลประเมิน", items, null);
            }
            case COMPLETED_REVISE -> {
                items.add(new Item("รอผู้ยื่นส่งเอกสารแก้ไขตามข้อเสนอแนะ", null, State.WAITING));
                yield Step.of("รอผู้ยื่นส่งเอกสารแก้ไข", items,
                        "ผู้ยื่นส่งฉบับแก้ครบแล้ว สถานะเปลี่ยนเป็น “ส่งเอกสารแก้ไขแล้ว” เอง");
            }
            case REVISION_SUBMITTED -> {
                items.add(manual("นัดประชุมพิจารณาฉบับแก้ → อัปเดตสถานะเป็น “นัดหมายคณะอนุกรรมการ”"
                        + " พร้อมวัน เวลา และสถานที่ประชุมรอบใหม่"));
                yield Step.of("นัดประชุมคณะอนุกรรมการพิจารณาฉบับแก้ไข", items, null);
            }
            case COMPLETED_PASS, SUBCOMMITTEE_FAIL -> {
                docs.staffDocument(items, P1_ENDORSEMENT, status == RequestStatus.COMPLETED_PASS
                        ? "รับรองผลโดยกรรมการประจำวิทยาลัยฯ" : "รับรองผล (ไม่ผ่าน) โดยกรรมการประจำวิทยาลัยฯ");
                yield Step.of("เสนอคณะกรรมการประจำวิทยาลัยฯ รับรองผลการประเมิน", items, null);
            }
            case COLLEGE_ENDORSED, COLLEGE_ENDORSED_FAIL -> {
                boolean fail = status == RequestStatus.COLLEGE_ENDORSED_FAIL;
                docs.staffDocument(items, P1_RESULT_LETTER, fail ? "แจ้งผล - ไม่ผ่าน" : "เสร็จสิ้น");
                yield Step.of("แจ้งผลการประเมินให้ผู้ขอทราบ", items, fail ? "แจ้งผลไม่ผ่านภายใน 7 วันทำการ" : null);
            }
            case COMPLETED_FAIL -> {
                items.add(new Item("รอผู้ขอตัดสินใจขอทบทวน (ผู้ขอยื่นในระบบเอง)", null, State.WAITING));
                yield Step.of("รอผู้ขอตัดสินใจขอทบทวน", items,
                        appealBy == null ? "ขอทบทวนได้ภายใน 30 วันทำการนับจากวันที่ทราบผล"
                                : "ขอทบทวนได้ถึง " + thai(appealBy));
            }
            case APPEAL_SUBMITTED -> {
                items.add(manual("หัวหน้าส่วนงานรับทบทวน → อัปเดตสถานะเป็น “นัดหมายคณะอนุกรรมการ” พร้อมวันประชุมรอบใหม่"));
                items.add(manual("ยืนผลเดิม → อัปเดตสถานะเป็น “แจ้งผล - ไม่ผ่าน” พร้อมเหตุผล"));
                yield Step.of("หัวหน้าส่วนงานพิจารณาคำขอทบทวน", items, null);
            }
            default -> null;
        };
        return withRevision(step, docs, awaitingResign, resubmitted);
    }

    // =====================================================================
    // เอกสารที่ส่งกลับให้แก้ — ทั้งสองเฟส
    // =====================================================================

    /** เอกสารที่ส่งกลับแล้วยังลงนามใหม่ไม่ครบ แยกว่าใครต้องทำอะไรต่อ */
    private static List<Item> revisionItems(Docs docs, Collection<Integer> awaitingResign,
            Collection<Integer> resubmitted) {
        List<Item> items = new ArrayList<>();
        for (int t : awaitingResign) {
            if (!resubmitted.contains(t)) {
                items.add(new Item("รอผู้ยื่นแก้ไขและลงนามใหม่ — " + docs.name(t), t, State.WAITING));
            } else if (docs.stage(t) == Stage.AWAITING_FORWARD) {
                // ผู้ยื่นลงนามแล้ว ผู้ลงนามที่เหลือรอเจ้าหน้าที่ตรวจแล้วส่งเวียน — ไม่ส่ง สถานะไม่มีวันกลับมาเอง
                items.add(new Item("ตรวจแล้วส่งเวียนให้ลงนามใหม่ — " + docs.name(t), t, State.TODO));
            } else {
                items.add(new Item("รอผู้ลงนามลงนามใหม่ — " + docs.name(t), t, State.WAITING));
            }
        }
        return items;
    }

    private static String revisionTitle(List<Item> items, Collection<Integer> awaitingResign,
            Collection<Integer> resubmitted) {
        return items.stream().anyMatch(i -> i.state() == State.TODO) ? "ส่งเวียนเอกสารที่แก้แล้วให้ลงนามใหม่"
                : resubmitted.containsAll(awaitingResign) && !awaitingResign.isEmpty()
                        ? "รอผู้ลงนามลงนามเอกสารที่แก้แล้ว"
                        : "รอผู้ยื่นแก้ไขเอกสารที่ส่งกลับ";
    }

    /**
     * มีเอกสารส่งกลับค้างอยู่ในขั้นที่สถานะไม่เปลี่ยนตาม — งานแก้ไขขึ้นก่อนงานของขั้น
     *
     * <p>ระหว่างนี้อัปเดตสถานะไม่ได้ ({@code resignBlocker}) ข้อที่เป็นการอัปเดตสถานะจึงยังไม่ถึง
     * ส่วนงานเอกสารของขั้นยังทำต่อได้ ข้อของเอกสารที่ส่งกลับเองใช้ข้อแก้ไขแทน
     */
    private static Step withRevision(Step step, Docs docs, Collection<Integer> awaitingResign,
            Collection<Integer> resubmitted) {
        if (step == null || awaitingResign.isEmpty()) {
            return step;
        }
        List<Item> items = revisionItems(docs, awaitingResign, resubmitted);
        String title = revisionTitle(items, awaitingResign, resubmitted);
        for (Item item : step.items()) {
            if (item.documentType() != null && awaitingResign.contains(item.documentType())) {
                continue;
            }
            boolean actionable = item.state() == State.CURRENT || item.state() == State.TODO;
            items.add(!actionable ? item : item.as(item.documentType() == null ? State.UPCOMING : State.TODO));
        }
        return Step.of(title, items, "เอกสารที่ส่งกลับต้องแก้และลงนามใหม่ครบทุกช่องก่อน จึงอัปเดตสถานะไปขั้นต่อไปได้");
    }

    // =====================================================================

    /** เอกสารของคำร้องหนึ่ง พร้อมเลขที่แสดงของแต่ละเฟส */
    private record Docs(SignatureModule module, Map<Integer, String> rows, Map<Integer, Stage> progress,
            IntUnaryOperator number) {

        Stage stage(int type) {
            return progress.getOrDefault(type, Stage.NOT_STARTED);
        }

        /**
         * งานของเจ้าหน้าที่ในเอกสารของผู้ยื่น — ตรวจแล้วส่งต่อให้ผู้ลงนาม และออกเลขที่หนังสือ
         *
         * @param includeDone true: ทั้งชุด ที่เสร็จแล้วติ๊กไว้ / false: เฉพาะที่ยังค้าง
         */
        List<Item> chores(boolean includeDone) {
            List<Item> forward = new ArrayList<>();
            List<Item> office = new ArrayList<>();
            rows.keySet().forEach(type -> {
                boolean applicantDoc = DocumentFieldOwnership.isApplicantDocument(module, type);
                if (applicantDoc && forwardedByStaff(type)) {
                    State s = switch (stage(type)) {
                        case AWAITING_FORWARD -> State.TODO;
                        case AWAITING_SIGNATURE -> State.WAITING;
                        case AWAITING_OFFICE, DONE -> State.DONE;
                        default -> State.UPCOMING;
                    };
                    forward.add(new Item("ตรวจแล้วส่งต่อลงนาม — " + name(type), type, s));
                }
                if (!DocumentFieldOwnership.officeFields(module, type).isEmpty()) {
                    State s = switch (stage(type)) {
                        case AWAITING_OFFICE -> State.TODO;
                        case DONE -> State.DONE;
                        default -> State.UPCOMING;
                    };
                    // เอกสารของเจ้าหน้าที่ยังไม่เริ่ม — ออกเลขอยู่ในขั้นของเอกสารนั้น ไม่ใช่งานของขั้นนี้
                    if (applicantDoc || s != State.UPCOMING) {
                        office.add(new Item("ออกเลขที่หนังสือและวันที่ — " + name(type), type, s));
                    }
                }
            });
            forward.addAll(office);
            return includeDone ? forward
                    : forward.stream().filter(i -> i.state() == State.TODO || i.state() == State.WAITING).toList();
        }

        /** เอกสารนี้มีผู้ลงนามที่เจ้าหน้าที่ต้องส่งต่อให้ (ไม่ใช่ช่องผู้ยื่นหรือช่องที่ผู้ยื่นเลือกเอง) */
        boolean forwardedByStaff(int type) {
            return SignatureAnchorRegistry.slotsOf(module, type).stream()
                    .anyMatch(slot -> !SignatureAnchorRegistry.isApplicantSide(module, type, slot.slotKey()));
        }

        /**
         * เอกสารของเจ้าหน้าที่ที่ขั้นนี้ต้องทำ — กรอกแล้วส่งลงนาม แล้วรอลงนามครบ
         *
         * @param movesTo สถานะที่เปลี่ยนเองเมื่อลงนามครบ หรือ null เมื่อไม่เลื่อนสถานะ
         */
        void staffDocument(List<Item> items, int type, String movesTo) {
            Stage stage = stage(type);
            boolean notSent = stage == Stage.NOT_STARTED || stage == Stage.DRAFT;
            items.add(new Item("กรอกแล้วส่งลงนาม — " + name(type), type, notSent ? State.TODO : State.DONE));
            State signed = switch (stage) {
                case AWAITING_FORWARD -> State.TODO;
                case AWAITING_SIGNATURE -> State.WAITING;
                case AWAITING_OFFICE, DONE -> State.DONE;
                default -> State.UPCOMING;
            };
            String text = stage == Stage.AWAITING_FORWARD ? "ส่งต่อลงนาม — " + name(type)
                    : "ลงนามครบ" + (movesTo == null ? "" : " → สถานะเปลี่ยนเป็น “" + movesTo + "” เอง");
            items.add(new Item(text, stage == Stage.AWAITING_FORWARD ? type : null, signed));
        }

        String name(int type) {
            String label = rows.get(type);
            return "เอกสารที่ " + number.applyAsInt(type) + (label == null ? "" : " " + label);
        }
    }

    private static String thai(LocalDate date) {
        return AcademicRequestService.formatThaiDate(date.atStartOfDay());
    }
}
