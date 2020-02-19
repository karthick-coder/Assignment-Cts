package com.cts.product.entity;

import java.time.LocalDate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import lombok.Data;
@Data
@Entity
public class Employee {
	
	@Id
	@Column(name = "empId")
	private int empId;
	@Column(name = "empName")
	private String empName;
	@Column(name = "salary")
	private double salary;
	@Column(name = "dob")
	private LocalDate dob;
	

	
	
	
	

}
