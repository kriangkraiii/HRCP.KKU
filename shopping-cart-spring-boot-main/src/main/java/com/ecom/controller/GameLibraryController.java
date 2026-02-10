package com.ecom.controller;

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;

import com.ecom.model.Category;
import com.ecom.model.GameLibrary;
import com.ecom.model.UserDtls;
import com.ecom.service.CartService;
import com.ecom.service.CategoryService;
import com.ecom.service.GameLibraryService;
import com.ecom.service.SecureDeliveryService;
import com.ecom.service.UserService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Controller
public class GameLibraryController {

	@Autowired
	private GameLibraryService gameLibraryService;

	@Autowired
	private UserService userService;

	@Autowired
	private CartService cartService;

	@Autowired
	private CategoryService categoryService;

	@Autowired
	private SecureDeliveryService secureDeliveryService;

	@ModelAttribute
	public void getUserDetails(Principal p, Model m) {
		if (p != null) {
			try {
				String email = p.getName();
				UserDtls userDtls = userService.getUserByEmail(email);
				if (userDtls != null) {
					m.addAttribute("user", userDtls);
					Integer countCart = cartService.getCountCart(userDtls.getId());
					m.addAttribute("countCart", countCart != null ? countCart : 0);
				}
			} catch (Exception e) {
				e.printStackTrace();
				m.addAttribute("countCart", 0);
			}
		}

		try {
			List<Category> allActiveCategory = categoryService.getAllActiveCategory();
			m.addAttribute("categorys", allActiveCategory != null ? allActiveCategory : new ArrayList<>());
		} catch (Exception e) {
			e.printStackTrace();
			m.addAttribute("categorys", new ArrayList<>());
		}
	}

	@GetMapping("/user/game-library")
	public String gameLibrary(Principal p, Model m) {
		if (p == null) {
			return "redirect:/signin";
		}

		String email = p.getName();
		UserDtls user = userService.getUserByEmail(email);

		List<GameLibrary> games = gameLibraryService.getGamesByUser(user.getId());

		// เช็คว่าแต่ละเกมมีไฟล์ให้ดาวน์โหลดหรือไม่
		for (GameLibrary game : games) {
			if (game.getProduct() != null && game.getProduct().getGameFilePath() != null) {
				boolean fileExists = secureDeliveryService.gameFileExists(game.getProduct().getGameFilePath());
				// ใช้ downloadLink field เดิมเพื่อเก็บสถานะ
				if (fileExists) {
					game.getProduct().setDownloadLink("SECURE_DOWNLOAD_AVAILABLE");
				}
			}
		}

		m.addAttribute("games", games);
		m.addAttribute("gamesCount", games.size());

		return "user/game_library";
	}

	/**
	 * 🔐 Secure Digital Delivery Endpoint
	 * 
	 * สร้าง Encrypted ZIP (AES-256) แบบ on-the-fly สำหรับแต่ละการดาวน์โหลด
	 * โดยใช้ License Key ของผู้ซื้อเป็นรหัสผ่าน
	 * 
	 * Flow:
	 * 1. ตรวจสอบสิทธิ์ผู้ใช้ (ต้อง login + เป็นเจ้าของเกม)
	 * 2. ดึง License Key จาก GameLibrary
	 * 3. สร้าง Encrypted ZIP ด้วย AES-256 + License Key เป็น password
	 * 4. Stream ไฟล์ ZIP ไปยังผู้ใช้โดยตรง (ไม่เก็บไฟล์ถาวร)
	 */
	@GetMapping("/user/game-library/secure-download/{id}")
	public void secureDownload(@PathVariable Integer id, Principal p, 
			HttpServletResponse response, HttpSession session) throws IOException {
		
		if (p == null) {
			response.sendRedirect("/signin");
			return;
		}

		String email = p.getName();
		UserDtls user = userService.getUserByEmail(email);

		// ตรวจสอบว่าเกมเป็นของผู้ใช้คนนี้จริง
		GameLibrary gameLibrary = gameLibraryService.getGameLibraryById(id);
		
		if (gameLibrary == null || !gameLibrary.getUser().getId().equals(user.getId())) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, "คุณไม่มีสิทธิ์ดาวน์โหลดเกมนี้");
			return;
		}

		String gameFilePath = gameLibrary.getProduct().getGameFilePath();
		String licenseKey = gameLibrary.getGameKey();

		// ตรวจสอบว่ามีไฟล์เกมและ License Key
		if (gameFilePath == null || gameFilePath.isEmpty()) {
			session.setAttribute("errorMsg", "ไฟล์เกมยังไม่พร้อมให้ดาวน์โหลด");
			response.sendRedirect("/user/game-library");
			return;
		}

		if (licenseKey == null || licenseKey.isEmpty()) {
			session.setAttribute("errorMsg", "ไม่พบ License Key สำหรับเกมนี้ กรุณาติดต่อฝ่ายสนับสนุน");
			response.sendRedirect("/user/game-library");
			return;
		}

		if (!secureDeliveryService.gameFileExists(gameFilePath)) {
			session.setAttribute("errorMsg", "ไม่พบไฟล์เกมในระบบ กรุณาติดต่อฝ่ายสนับสนุน");
			response.sendRedirect("/user/game-library");
			return;
		}

		// สร้างชื่อไฟล์ ZIP 
		String zipFileName = secureDeliveryService.getLockedZipFileName(gameLibrary.getProduct().getTitle());

		// ตั้งค่า Response Header สำหรับ download
		response.setContentType("application/zip");
		response.setHeader("Content-Disposition", "attachment; filename=\"" + zipFileName + "\"");
		response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
		response.setHeader("Pragma", "no-cache");
		response.setHeader("Expires", "0");

		try {
			// 🔐 สร้าง Encrypted ZIP (AES-256) แล้ว stream ตรงไปยังผู้ใช้
			secureDeliveryService.createEncryptedZip(gameFilePath, licenseKey, response.getOutputStream());
			response.getOutputStream().flush();

			// อัพเดตสถานะการดาวน์โหลด
			gameLibraryService.markAsDownloaded(id);

		} catch (IOException e) {
			e.printStackTrace();
			// ถ้ายังไม่ได้ commit response ให้ redirect กลับ
			if (!response.isCommitted()) {
				session.setAttribute("errorMsg", "เกิดข้อผิดพลาดในการสร้างไฟล์ดาวน์โหลด กรุณาลองใหม่");
				response.sendRedirect("/user/game-library");
			}
		}
	}

	/**
	 * Endpoint เดิมสำหรับ backward compatibility
	 */
	@GetMapping("/user/game-library/download/{id}")
	public String downloadGame(@PathVariable Integer id, Principal p, HttpSession session) {
		if (p == null) {
			return "redirect:/signin";
		}

		String email = p.getName();
		UserDtls user = userService.getUserByEmail(email);

		// Mark as downloaded
		gameLibraryService.markAsDownloaded(id);

		session.setAttribute("succMsg", "เริ่มดาวน์โหลดเกมแล้ว!");

		return "redirect:/user/game-library";
	}
}
