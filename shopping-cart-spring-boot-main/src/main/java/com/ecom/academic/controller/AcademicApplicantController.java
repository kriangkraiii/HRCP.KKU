package com.ecom.academic.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.ecom.academic.model.AcademicDocument;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.StaffMemberService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@Controller
@RequestMapping("/user/academic")
public class AcademicApplicantController {

    @Autowired
    private AcademicRequestService requestService;

    @Autowired
    private DocumentGenerationService documentService;

    @Autowired
    private StaffMemberService staffMemberService;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/dashboard")
    public String dashboard(Principal principal, Model model) {
        UserDtls user = getUser(principal);
        List<AcademicRequest> requests = requestService.findByApplicant(user.getId());
        model.addAttribute("requests", requests);
        model.addAttribute("statuses", RequestStatus.values());
        return "academic/applicant/dashboard";
    }

    @GetMapping("/new-request")
    public String newRequestForm(Model model) {
        model.addAttribute("staffMembers", staffMemberService.findAll());
        return "academic/applicant/new_request";
    }

    @PostMapping("/new-request")
    public String submitNewRequest(@RequestParam Map<String, String> formData,
            Principal principal,
            Model model) throws IOException {
        UserDtls user = getUser(principal);
        AcademicRequest request = requestService.createRequest(user);

        String jsonData = objectMapper.writeValueAsString(formData);
        String filePath = documentService.generateDocument(request.getId(), 1, jsonData, null);

        requestService.saveDocument(request, 1, jsonData, filePath,
                "แบบตรวจสอบเบื้องต้นเอกสารประกอบประเมินผลการสอน", null);

        return "redirect:/user/academic/request/" + request.getId();
    }

    @GetMapping("/request/{id}")
    public String viewRequest(@PathVariable Long id, Principal principal, Model model) {
        AcademicRequest request = requestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        UserDtls user = getUser(principal);
        if (!request.getApplicant().getId().equals(user.getId())) {
            return "redirect:/user/academic/dashboard";
        }

        List<AcademicDocument> documents = requestService.getDocuments(id);
        model.addAttribute("request", request);
        model.addAttribute("documents", documents);
        model.addAttribute("statuses", RequestStatus.values());
        model.addAttribute("statusHistory", requestService.getStatusHistory(id));
        return "academic/applicant/request_detail";
    }

    @PostMapping("/upload-revision/{id}")
    public String uploadRevision(@PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            Principal principal) throws IOException {
        AcademicRequest request = requestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        UserDtls user = getUser(principal);
        if (!request.getApplicant().getId().equals(user.getId())) {
            return "redirect:/user/academic/dashboard";
        }

        String uploadDir = "uploads/academic/" + id + "/revisions/";
        Files.createDirectories(Paths.get(uploadDir));
        String filePath = uploadDir + file.getOriginalFilename();
        file.transferTo(Paths.get(filePath));

        requestService.setRevisionFile(id, filePath);
        return "redirect:/user/academic/request/" + id + "?success=uploaded";
    }

    @GetMapping("/download/{id}/{docId}")
    public ResponseEntity<ByteArrayResource> downloadDocument(@PathVariable Long id,
            @PathVariable Long docId,
            Principal principal) throws IOException {
        AcademicRequest request = requestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        UserDtls user = getUser(principal);
        if (!request.getApplicant().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        List<AcademicDocument> docs = requestService.getDocuments(id);
        AcademicDocument doc = docs.stream()
                .filter(d -> d.getId().equals(docId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Document not found"));

        byte[] data = documentService.getDocumentBytes(doc.getGeneratedFilePath());
        ByteArrayResource resource = new ByteArrayResource(data);
        String filename = Paths.get(doc.getGeneratedFilePath()).getFileName().toString();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(data.length)
                .body(resource);
    }

    @GetMapping("/download-result/{id}")
    public ResponseEntity<ByteArrayResource> downloadResult(@PathVariable Long id, Principal principal)
            throws IOException {
        AcademicRequest request = requestService.findById(id)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        UserDtls user = getUser(principal);
        if (!request.getApplicant().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        if (request.getResultFilePath() == null) {
            return ResponseEntity.notFound().build();
        }

        Path path = Paths.get(request.getResultFilePath());
        byte[] data = Files.readAllBytes(path);
        ByteArrayResource resource = new ByteArrayResource(data);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + path.getFileName().toString() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(data.length)
                .body(resource);
    }

    private UserDtls getUser(Principal principal) {
        return userRepository.findByEmail(principal.getName());
    }
}
