package com.cts.eservice.entity;

import java.util.Date;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;

import lombok.Data;
@Data
@Entity
public class Orders {

	@Id
	private int orderId;

	private double amount;

	private Date date;

	@OneToOne(targetEntity = Customer.class, cascade = CascadeType.ALL)
	@JoinColumn(name = "user_id", referencedColumnName = "user_id")
	private Customer customer;

	@OneToOne(targetEntity = Communication.class, cascade = CascadeType.ALL)
	@JoinColumn(name = "address_id", referencedColumnName = "address_id")
	private Communication communication;

	@OneToOne(targetEntity = Product.class, cascade = CascadeType.ALL)
	@JoinColumn(name = "product_id", referencedColumnName = "product_id")
	private Product product;

	

}
