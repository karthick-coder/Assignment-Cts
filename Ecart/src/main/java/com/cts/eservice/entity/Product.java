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
public class Product {

	@Id
	@Column(name="product_id")
	private int productId;

	private Date date;

	private String description;

	private String productName;

	private double price;

	@OneToOne(targetEntity=Category.class,cascade=CascadeType.ALL)
	@JoinColumn(name="cat_id",referencedColumnName="category_id")
	private Category category;

	private String brandName;

	

}
