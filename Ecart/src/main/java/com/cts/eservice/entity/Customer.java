package com.cts.eservice.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

import lombok.Data;
@Data
@Entity
public class Customer {
	
	@Id
	@Column(name="user_id")
	private int userId;
	
	private String userName;
	
	private String emailId;
	
	private long mobileNum;

	
	
	

}
