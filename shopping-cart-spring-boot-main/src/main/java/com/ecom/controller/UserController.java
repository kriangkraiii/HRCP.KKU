package com.ecom.controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.ecom.model.Cart;
import com.ecom.model.Category;
import com.ecom.model.OrderRequest;
import com.ecom.model.ProductOrder;
import com.ecom.model.UserDtls;
import com.ecom.service.CartService;
import com.ecom.service.CategoryService;
import com.ecom.service.FileService;
import com.ecom.service.GameLibraryService;
import com.ecom.service.OrderService;
import com.ecom.service.UserService;
import com.ecom.service.WalletService;
import com.ecom.util.BucketType;
import com.ecom.util.CommonUtil;
import com.ecom.util.OrderStatus;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/user")
public class UserController {
	@Autowired
	private UserService userService;
	@Autowired
	private CategoryService categoryService;

	@Autowired
	private CartService cartService;

	@Autowired
	private OrderService orderService;

	@Autowired
	private CommonUtil commonUtil;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private FileService fileService;

	@Autowired
	private WalletService walletService;

	@Autowired
	private GameLibraryService gameLibraryService;

	@GetMapping("/")
	public String home() {
		return "user/home";
	}


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



	@GetMapping("/addCart")
	public String addToCart(@RequestParam Integer pid, @RequestParam Integer uid, HttpSession session) {
	    try {
	        // Check if user already owns this game
	        if (gameLibraryService.isGameOwned(uid, pid)) {
	            session.setAttribute("errorMsg", "You already own this game!");
	            return "redirect:/product/" + pid;
	        }
	        Cart saveCart = cartService.saveCart(pid, uid);
	        if (ObjectUtils.isEmpty(saveCart)) {
	            session.setAttribute("errorMsg", "Product add to cart failed");
	        } else {
	            session.setAttribute("succMsg", "Product added to cart");
	        }
	    } catch (Exception e) {
	        e.printStackTrace();
	        session.setAttribute("errorMsg", "Error: " + e.getMessage());
	    }
	    return "redirect:/product/" + pid;
	}


	@GetMapping("/cart")
	public String loadCartPage(Principal p, Model m) {
	    UserDtls user = getLoggedInUserDetails(p);
	    List<Cart> carts = cartService.getCartsByUser(user.getId());
	    m.addAttribute("carts", carts);
	    
	    if (carts != null && !carts.isEmpty()) {
	        Double totalOrderPrice = carts.stream()
	            .mapToDouble(cart -> cart.getProduct().getDiscountPrice())
	            .sum();
	        m.addAttribute("totalOrderPrice", totalOrderPrice);
	    } else {
	        m.addAttribute("totalOrderPrice", 0.0);
	    }
	    
	    // Add wallet balance
	    double walletBalance = walletService.getBalance(user);
	    m.addAttribute("walletBalance", walletBalance);
	    
	    return "user/cart";
	}


	@GetMapping("/cartQuantityUpdate")
	public String updateCartQuantity(@RequestParam String sy, @RequestParam Integer cid) {
		cartService.updateQuantity(sy, cid);
		return "redirect:/user/cart";
	}

	private UserDtls getLoggedInUserDetails(Principal p) {
	    if (p == null) {
	        return null;
	    }
	    String email = p.getName();
	    UserDtls userDtls = userService.getUserByEmail(email);
	    return userDtls;
	}


	@GetMapping("/orders")
	public String orderPage(Principal p, Model m) {
	    UserDtls user = getLoggedInUserDetails(p);
	    List<Cart> carts = cartService.getCartsByUser(user.getId());
	    m.addAttribute("carts", carts);
	    
	    if (carts != null && !carts.isEmpty()) {
	        Double totalOrderPrice = carts.stream()
	            .mapToDouble(cart -> cart.getProduct().getDiscountPrice())
	            .sum();
	        m.addAttribute("orderPrice", totalOrderPrice);
	        m.addAttribute("totalOrderPrice", totalOrderPrice);
	    } else {
	        m.addAttribute("orderPrice", 0.0);
	        m.addAttribute("totalOrderPrice", 0.0);
	    }
	    
	    double walletBalance = walletService.getBalance(user);
	    m.addAttribute("walletBalance", walletBalance);
	    
	    return "user/order";
	}


	@PostMapping("/save-order")
	public String saveOrder(@ModelAttribute OrderRequest request, Principal p, HttpSession session) {
		try {
			UserDtls user = getLoggedInUserDetails(p);
			orderService.saveOrder(user.getId(), request);
			return "redirect:/user/success";
		} catch (Exception e) {
			session.setAttribute("errorMsg", e.getMessage());
			return "redirect:/user/orders";
		}
	}

	@GetMapping("/success")
	public String loadSuccess() {
		return "user/success";
	}

	@GetMapping("/user-orders")
	public String myOrder(Model m, Principal p) {
		UserDtls loginUser = getLoggedInUserDetails(p);
		List<ProductOrder> orders = orderService.getOrdersByUser(loginUser.getId());
		m.addAttribute("orders", orders);
		return "user/my_orders";
	}


	@GetMapping("/update-status")
	public String updateOrderStatus(@RequestParam Integer id, @RequestParam Integer st, HttpSession session) {

		OrderStatus[] values = OrderStatus.values();
		String status = null;

		for (OrderStatus orderSt : values) {
			if (orderSt.getId().equals(st)) {
				status = orderSt.getName();
			}
		}

		ProductOrder updateOrder = orderService.updateOrderStatus(id, status);
		
		try {
			commonUtil.sendMailForProductOrder(updateOrder, status);
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (!ObjectUtils.isEmpty(updateOrder)) {
			session.setAttribute("succMsg", "Status Updated");
		} else {
			session.setAttribute("errorMsg", "status not updated");
		}
		return "redirect:/user/user-orders";
	}

	@GetMapping("/profile")
	public String profile() {
		return "user/profile";
	}
//
//	@GetMapping("/profile")
//	public String profile(Principal p, Model m) {
//	    UserDtls user = getLoggedInUserDetails(p);
//	    m.addAttribute("user", user);
//	    return "/user/profile";
//	}


	@PostMapping("/update-profile")
	public String updateProfile(@ModelAttribute UserDtls user, @RequestParam MultipartFile img, HttpSession session) {
	    if (img != null && !img.isEmpty()) {
	        String imageUrl = commonUtil.getImageUrl(img, BucketType.PROFILE.getId());
	        user.setProfileImage(imageUrl);
	    }
	    
	    UserDtls updateUserProfile = userService.updateUserProfile(user, img);
	    
	    if (ObjectUtils.isEmpty(updateUserProfile)) {
	        session.setAttribute("errorMsg", "Profile not updated");
	    } else {
	        session.setAttribute("succMsg", "Profile Updated");
	        if (img != null && !img.isEmpty()) {
	            fileService.uploadFileS3(img, 3);
	        }
	    }
	    return "redirect:/user/profile";
	}


	@PostMapping("/change-password")
	public String changePassword(@RequestParam String newPassword, @RequestParam String currentPassword, Principal p,
			HttpSession session) {
		UserDtls loggedInUserDetails = getLoggedInUserDetails(p);

		boolean matches = passwordEncoder.matches(currentPassword, loggedInUserDetails.getPassword());

		if (matches) {
			String encodePassword = passwordEncoder.encode(newPassword);
			loggedInUserDetails.setPassword(encodePassword);
			UserDtls updateUser = userService.updateUser(loggedInUserDetails);
			if (ObjectUtils.isEmpty(updateUser)) {
				session.setAttribute("errorMsg", "Password not updated !! Error in server");
			} else {
				session.setAttribute("succMsg", "Password Updated sucessfully");
			}
		} else {
			session.setAttribute("errorMsg", "Current Password incorrect");
		}

		return "redirect:/user/profile";
	}

}
