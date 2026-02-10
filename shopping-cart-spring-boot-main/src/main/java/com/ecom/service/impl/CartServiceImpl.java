package com.ecom.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import com.ecom.model.Cart;
import com.ecom.model.Product;
import com.ecom.model.UserDtls;
import com.ecom.repository.CartRepository;
import com.ecom.repository.GameLibraryRepository;
import com.ecom.repository.ProductRepository;
import com.ecom.repository.UserRepository;
import com.ecom.service.CartService;

import jakarta.transaction.Transactional;

@Service
public class CartServiceImpl implements CartService {

	@Autowired
	private CartRepository cartRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private GameLibraryRepository gameLibraryRepository;

	private static final Double DELIVERY_FEE = 0.0; // You can make this configurable
    private static final Double TAX_RATE = 0.07; // 7% tax rate - make this configurable
    
    public Double calculateDeliveryFee(Double totalAmount) {
        // You can add logic here - e.g., free delivery over certain amount
        return totalAmount > 1000 ? 0.0 : DELIVERY_FEE;
    }
    
    public Double calculateTax(Double subtotal) {
        return subtotal * TAX_RATE;
    }
    
    public Double calculateGrandTotal(Double subtotal) {
        Double deliveryFee = calculateDeliveryFee(subtotal);
        Double tax = calculateTax(subtotal);
        return subtotal + deliveryFee + tax;
    }
    @Override
    @Transactional
    public void deleteCartItemsByProductId(Integer productId) {
        cartRepository.deleteByProductId(productId);
    }

	@Override
	public Cart saveCart(Integer productId, Integer userId) {

		UserDtls userDtls = userRepository.findById(userId).get();
		Product product = productRepository.findById(productId).get();

		// Block: user already owns this game
		if (gameLibraryRepository.existsByUserIdAndProductId(userId, productId)) {
			return null; // Already owned - cannot add to cart
		}

		Cart cartStatus = cartRepository.findByProductIdAndUserId(productId, userId);

		Cart cart = null;

		if (ObjectUtils.isEmpty(cartStatus)) {
			cart = new Cart();
			cart.setProduct(product);
			cart.setUser(userDtls);
			cart.setQuantity(1);
			cart.setTotalPrice(1 * product.getDiscountPrice());
		} else {
			// Game already in cart - don't increase quantity (1 copy only)
			return cartStatus;
		}
		Cart saveCart = cartRepository.save(cart);

		return saveCart;
	}

	@Override
	public List<Cart> getCartsByUser(Integer userId) {
		List<Cart> carts = cartRepository.findByUserId(userId);

		Double totalOrderPrice = 0.0;
		List<Cart> updateCarts = new ArrayList<>();
		for (Cart c : carts) {
			Double totalPrice = (c.getProduct().getDiscountPrice() * c.getQuantity());
			c.setTotalPrice(totalPrice);
			totalOrderPrice = totalOrderPrice + totalPrice;
			c.setTotalOrderPrice(totalOrderPrice);
			updateCarts.add(c);
		}

		return updateCarts;
	}

	@Override
	public Integer getCountCart(Integer userId) {
		Integer countByUserId = cartRepository.countByUserId(userId);
		return countByUserId;
	}

	@Override
	public void updateQuantity(String sy, Integer cid) {

		Cart cart = cartRepository.findById(cid).get();
		int updateQuantity;

		if (sy.equalsIgnoreCase("de")) {
			updateQuantity = cart.getQuantity() - 1;

			if (updateQuantity <= 0) {
				cartRepository.delete(cart);
			} else {
				cart.setQuantity(updateQuantity);
				cartRepository.save(cart);
			}

		} else {
			updateQuantity = cart.getQuantity() + 1;
			cart.setQuantity(updateQuantity);
			cartRepository.save(cart);
		}

	}

}
