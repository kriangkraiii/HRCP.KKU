package com.ecom.controller;

import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ecom.model.Category;
import com.ecom.model.Product;
import com.ecom.model.UserDtls;
import com.ecom.service.CartService;
import com.ecom.service.CategoryService;
import com.ecom.service.GameLibraryService;
import com.ecom.service.ProductService;
import com.ecom.service.UserService;
import com.ecom.util.CommonUtil;

import io.micrometer.common.util.StringUtils;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
public class HomeController {

	@Autowired
	private CategoryService categoryService;

	@Autowired
	private ProductService productService;

	@Autowired
	private UserService userService;

	@Autowired
	private CommonUtil commonUtil;

	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	@Autowired
	private CartService cartService;

	@Autowired
	private GameLibraryService gameLibraryService;

	@ModelAttribute
	public void getUserDetails(Principal p, Model m) {
		if (p != null) {
			String email = p.getName();
			UserDtls userDtls = userService.getUserByEmail(email);
			m.addAttribute("user", userDtls);
			Integer countCart = cartService.getCountCart(userDtls.getId());
			m.addAttribute("countCart", countCart);
		}

		List<Category> allActiveCategory = categoryService.getAllActiveCategory();
		m.addAttribute("categorys", allActiveCategory);
	}

	@GetMapping("/")
	public String index(Model m) {

		List<Category> allActiveCategory = categoryService.getAllActiveCategory().stream()
				.sorted((c1, c2) -> c2.getId().compareTo(c1.getId())).limit(6).toList();
		List<Product> allActiveProducts = productService.getAllActiveProducts("").stream()
				.sorted((p1, p2) -> p2.getId().compareTo(p1.getId())).limit(8).toList();
		m.addAttribute("category", allActiveCategory);
		m.addAttribute("products", allActiveProducts);
		return "guest/index";
	}

	@GetMapping("/signin")
	public String login() {
		return "guest/login";
	}

	@GetMapping("/register")
	public String register() {
		return "guest/register";
	}

	@GetMapping("/products")
	public String products(Model m, @RequestParam(value = "category", defaultValue = "") String category,
			@RequestParam(name = "pageNo", defaultValue = "0") Integer pageNo,
			@RequestParam(name = "pageSize", defaultValue = "12") Integer pageSize,
			@RequestParam(defaultValue = "") String ch) {

		List<Category> categories = categoryService.getAllActiveCategory();
		m.addAttribute("paramValue", category);
		m.addAttribute("categories", categories);


		Page<Product> page = null;
		if (StringUtils.isEmpty(ch)) {
			page = productService.getAllActiveProductPagination(pageNo, pageSize, category);
		} else {
			page = productService.searchActiveProductPagination(pageNo, pageSize, category, ch);
		}

		List<Product> products = page.getContent();
		m.addAttribute("products", products);
		m.addAttribute("productsSize", products.size());

		m.addAttribute("pageNo", page.getNumber());
		m.addAttribute("pageSize", pageSize);
		m.addAttribute("totalElements", page.getTotalElements());
		m.addAttribute("totalPages", page.getTotalPages());
		m.addAttribute("isFirst", page.isFirst());
		m.addAttribute("isLast", page.isLast());

		return "guest/product";
	}

	@GetMapping("/product/{id}")
	public String product(@PathVariable int id, Model m, Principal p) {
		Product productById = productService.getProductById(id);
		m.addAttribute("product", productById);
		// Check if logged-in user already owns this game
		if (p != null) {
			UserDtls user = userService.getUserByEmail(p.getName());
			if (user != null) {
				Boolean owned = gameLibraryService.isGameOwned(user.getId(), id);
				m.addAttribute("alreadyOwned", owned);
			}
		}
		return "guest/view_product";
	}

	@PostMapping("/saveUser")
	public String saveUser(@ModelAttribute UserDtls user, HttpSession session) {

	    Boolean existsEmail = userService.existsEmail(user.getEmail());
	    
	    
	    if (existsEmail) {
	        session.setAttribute("errorMsg", "Email already exist");
	    } else {
	        try {
	            // Set default profile image
	            user.setProfileImage("default.png");
	            UserDtls saveUser = userService.saveUser(user);

	            if (!ObjectUtils.isEmpty(saveUser)) {
	                session.setAttribute("succMsg", "Register successfully");
	            } else {
	                session.setAttribute("errorMsg", "Something wrong on server");
	            }
	        } catch (Exception e) {
	            e.printStackTrace();
	            session.setAttribute("errorMsg", "Registration failed: " + e.getMessage());
	        }
	    }

	    return "redirect:/register";
	}



//	Forgot Password Code 

	@GetMapping("/forgot-password")
	public String showForgotPassword() {
		return "guest/forgot_password";
	}

	@PostMapping("/forgot-password")
	public String processForgotPassword(@RequestParam String email, HttpSession session, HttpServletRequest request)
			throws UnsupportedEncodingException, MessagingException {

		UserDtls userByEmail = userService.getUserByEmail(email);

		if (ObjectUtils.isEmpty(userByEmail)) {
			session.setAttribute("errorMsg", "Invalid email");
		} else {

			String resetToken = UUID.randomUUID().toString();
			userService.updateUserResetToken(email, resetToken);

			// Generate URL :
			// http://localhost:8080/reset-password?token=sfgdbgfswegfbdgfewgvsrg

			String url = CommonUtil.generateUrl(request) + "/reset-password?token=" + resetToken;

			Boolean sendMail = commonUtil.sendMail(url, email);

			if (sendMail) {
				session.setAttribute("succMsg", "Please check your email..Password Reset link sent");
			} else {
				session.setAttribute("errorMsg", "Somethong wrong on server ! Email not send");
			}
		}

		return "redirect:/forgot-password";
	}

	@GetMapping("/reset-password")
	public String showResetPassword(@RequestParam String token, HttpSession session, Model m) {

		UserDtls userByToken = userService.getUserByToken(token);

		if (userByToken == null) {
			m.addAttribute("msg", "Your link is invalid or expired !!");
			return "guest/message";
		}
		m.addAttribute("token", token);
		return "guest/reset_password";
	}

	@PostMapping("/reset-password")
	public String resetPassword(@RequestParam String token, 
	                          @RequestParam String password, 
	                          @RequestParam String confirmPassword,
	                          HttpSession session, Model m) {
	    
	    System.out.println("Reset password attempt with token: " + token);
	    
	    UserDtls userByToken = userService.getUserByToken(token);
	    if (userByToken == null) {
	        System.out.println("Invalid token: " + token);
	        m.addAttribute("msg", "Your link is invalid or expired !!");
	        return "guest/message";
	    }
	    
	    System.out.println("Valid token for user: " + userByToken.getEmail());
	    
	    
	    // Server-side password confirmation validation
	    if (!password.equals(confirmPassword)) {
	        session.setAttribute("errorMsg", "Passwords do not match!");
	        m.addAttribute("token", token);
	        return "guest/reset_password";
	    }
	    
	    // Optional: Add password strength validation
	    if (password.length() < 6) {
	        session.setAttribute("errorMsg", "Password must be at least 6 characters long!");
	        m.addAttribute("token", token);
	        return "guest/reset_password";
	    }
	    
	    userByToken.setPassword(passwordEncoder.encode(password));
	    userByToken.setResetToken(null);
	    userService.updateUser(userByToken);
	    
	    session.setAttribute("succMsg", "Password reset successfully. Please login with your new password.");
	    return "redirect:/signin";
	}



	@GetMapping("/search")
	public String searchProduct(@RequestParam String ch, Model m) {
		List<Product> searchProducts = productService.searchProduct(ch);
		m.addAttribute("products", searchProducts);
		List<Category> categories = categoryService.getAllActiveCategory();
		m.addAttribute("categories", categories);
		return "guest/product";

	}

}
