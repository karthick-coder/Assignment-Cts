package com.cts.product.entity;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;
@Data
public class Product {

	private int prodId;
	private String prodName;
	private double price;
	private LocalDateTime date;
	private List<String> listProds;

}
