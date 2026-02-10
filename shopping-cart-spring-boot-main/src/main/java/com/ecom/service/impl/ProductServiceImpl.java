package com.ecom.service.impl;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.multipart.MultipartFile;

import com.ecom.model.Product;
import com.ecom.repository.ProductRepository;
import com.ecom.service.CartService;
import com.ecom.service.ProductService;
import com.ecom.util.BucketType;
import com.ecom.util.CommonUtil;

import jakarta.transaction.Transactional;

@Service
public class ProductServiceImpl implements ProductService {

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private CartService cartService;

	@Autowired
	private CommonUtil commonUtil;
	
	@Autowired
	FileServiceImpl fileServiceImpl;
	
	@Override
	public Product saveProduct(Product product) {
		return productRepository.save(product);
	}

	@Override
	public List<Product> getAllProducts() {
		return productRepository.findAll();
	}

	@Override
	public Page<Product> getAllProductsPagination(Integer pageNo, Integer pageSize) {
		Pageable pageable = PageRequest.of(pageNo, pageSize);
		return productRepository.findAll(pageable);
	}
	@Override
	public Long getCountActiveProducts() {
	    return getTotalActiveProductsCount();
	}

	@Override
	public List<Integer> getTopProductsData() {
	    // Return empty list for now - you can implement product sales statistics later
	    return new ArrayList<>();
	}

	@Override
	public List<String> getTopProductsLabels() {
	    // Return empty list for now - you can implement product labels later
	    return new ArrayList<>();
	}

	@Override
	@Transactional
	public Boolean deleteProduct(Integer id) {
	    try {
	        Optional<Product> productOpt = productRepository.findById(id);
	        if (productOpt.isPresent()) {
	            Product product = productOpt.get();
	            
	            // First, remove all cart items that reference this product
	            cartService.deleteCartItemsByProductId(id);
	            
	            // Then delete the product
	            productRepository.deleteById(id);
	            return true;
	        }
	        return false;
	    } catch (Exception e) {
	        e.printStackTrace();
	        return false;
	    }
	}

	@Override
	public Product getProductById(Integer id) {
		Product product = productRepository.findById(id).orElse(null);
		return product;
	}
	@Override
	public Product updateProduct(Product product, MultipartFile image) {

	    Product dbProduct = getProductById(product.getId());

	    if (!image.isEmpty()) {
	        String imageUrl = commonUtil.getImageUrl(image, BucketType.PRODUCT.getId());
	        dbProduct.setImage(imageUrl);
	    }

	    dbProduct.setTitle(product.getTitle());
	    dbProduct.setDescription(product.getDescription());
	    dbProduct.setCategory(product.getCategory());
	    dbProduct.setPrice(product.getPrice());
	    dbProduct.setIsActive(product.getIsActive());
	    dbProduct.setDiscount(product.getDiscount());
	    dbProduct.setDownloadLink(product.getDownloadLink());
	    dbProduct.setFileSize(product.getFileSize());
	    if (product.getGameFilePath() != null) {
	        dbProduct.setGameFilePath(product.getGameFilePath());
	    }
	    if (product.getImages() != null) {
	        dbProduct.setImages(product.getImages());
	    }

	    // Calculate discount price
	    Double discount = product.getPrice() * (product.getDiscount() / 100.0);
	    Double discountPrice = product.getPrice() - discount;
	    dbProduct.setDiscountPrice(discountPrice);

	    Product updateProduct = productRepository.save(dbProduct);

	    if (!ObjectUtils.isEmpty(updateProduct)) {
	        if (!image.isEmpty()) {
	            try {
	                // Create external upload directory
	                String uploadDir = System.getProperty("user.dir") + "/uploads/product_img/";
	                File uploadFolder = new File(uploadDir);
	                fileServiceImpl.uploadFileS3(image, BucketType.PRODUCT.getId());
	                if (!uploadFolder.exists()) {
	                    uploadFolder.mkdirs();
	                    
	                }

	                Path path = Paths.get(uploadDir + image.getOriginalFilename());
	                Files.copy(image.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);

	            } catch (Exception e) {
	                e.printStackTrace();
	            }
	        }
	        return updateProduct;
	    }
	    return null;
	}

	@Override
	public List<Product> getAllActiveProducts(String category) {
		List<Product> products = null;
		if (ObjectUtils.isEmpty(category)) {
			products = productRepository.findByIsActiveTrue();
		} else {
			products = productRepository.findByCategory(category);
		}

		return products;
	}

	@Override
	public List<Product> searchProduct(String ch) {
		return productRepository.findByTitleContainingIgnoreCaseOrCategoryContainingIgnoreCase(ch, ch);
	}

	@Override
	public Page<Product> searchProductPagination(Integer pageNo, Integer pageSize, String ch) {
		Pageable pageable = PageRequest.of(pageNo, pageSize);
		return productRepository.findByTitleContainingIgnoreCaseOrCategoryContainingIgnoreCase(ch, ch, pageable);
	}

	@Override
	public Page<Product> getAllActiveProductPagination(Integer pageNo, Integer pageSize, String category) {

		Pageable pageable = PageRequest.of(pageNo, pageSize);
		Page<Product> pageProduct = null;

		if (ObjectUtils.isEmpty(category)) {
			pageProduct = productRepository.findByIsActiveTrue(pageable);
		} else {
			pageProduct = productRepository.findByCategory(pageable, category);
		}
		return pageProduct;
	}

	@Override
	public Page<Product> searchActiveProductPagination(Integer pageNo, Integer pageSize, String category, String ch) {

		Page<Product> pageProduct = null;
		Pageable pageable = PageRequest.of(pageNo, pageSize);

		pageProduct = productRepository.findByisActiveTrueAndTitleContainingIgnoreCaseOrCategoryContainingIgnoreCase(ch,
				ch, pageable);

		return pageProduct;
	}

	@Override
	public Long getTotalActiveProductsCount() {
		return productRepository.countByIsActiveTrue();
	}

	@Override
	public Long getTotalLowStockProductsCount() {
		return 0L;
	}
}
