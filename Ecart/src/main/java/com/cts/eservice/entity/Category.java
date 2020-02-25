package com.cts.eservice.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

import lombok.Data;
@Data
@Entity
public class Category {

	@Id
	@Column(name = "category_id")
	private int categoryId;

	private String categoryType;

	

}
