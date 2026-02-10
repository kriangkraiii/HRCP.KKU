package com.ecom.util;

public enum OrderStatus {

	PENDING_PAYMENT(1, "Pending Payment"), PAID(2, "Paid"), COMPLETED(3, "Completed"),
	CANCELLED(4, "Cancelled"), REFUNDED(5, "Refunded");

	private Integer id;

	private String name;

	private OrderStatus(Integer id, String name) {
		this.id = id;
		this.name = name;
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

}
