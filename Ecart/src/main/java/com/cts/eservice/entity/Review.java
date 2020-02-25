package com.cts.eservice.entity;

import java.util.Date;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;

import lombok.Data;
@Data
@Entity
public class Review {

	@Id
	@Column(name = "review_id")
	private int reviewId;

	@OneToOne(targetEntity = Customer.class, cascade = CascadeType.ALL)
	@JoinColumn(name = "user_id", referencedColumnName = "user_id")
	private Customer customer;

	private String body;

	private int stars;

	@OneToOne(targetEntity = Product.class, cascade = CascadeType.ALL)
	@JoinColumn(name = "product_id", referencedColumnName = "product_id")
	private Product product;
	
	private Date date;

	

}
