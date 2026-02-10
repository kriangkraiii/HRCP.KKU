package com.ecom.model;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class GameLibrary {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne
	private UserDtls user;

	@ManyToOne
	private Product product;

	private String orderId;

	private LocalDateTime purchaseDate;

	private Boolean isDownloaded;

	private String gameKey;

	public GameLibrary() {
	}

	public GameLibrary(Integer id, UserDtls user, Product product, String orderId, LocalDateTime purchaseDate,
			Boolean isDownloaded) {
		this.id = id;
		this.user = user;
		this.product = product;
		this.orderId = orderId;
		this.purchaseDate = purchaseDate;
		this.isDownloaded = isDownloaded;
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public UserDtls getUser() {
		return user;
	}

	public void setUser(UserDtls user) {
		this.user = user;
	}

	public Product getProduct() {
		return product;
	}

	public void setProduct(Product product) {
		this.product = product;
	}

	public String getOrderId() {
		return orderId;
	}

	public void setOrderId(String orderId) {
		this.orderId = orderId;
	}

	public LocalDateTime getPurchaseDate() {
		return purchaseDate;
	}

	public void setPurchaseDate(LocalDateTime purchaseDate) {
		this.purchaseDate = purchaseDate;
	}

	public Boolean getIsDownloaded() {
		return isDownloaded;
	}

	public void setIsDownloaded(Boolean isDownloaded) {
		this.isDownloaded = isDownloaded;
	}

	public String getGameKey() {
		return gameKey;
	}

	public void setGameKey(String gameKey) {
		this.gameKey = gameKey;
	}
}
