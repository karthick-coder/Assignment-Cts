package com.cts.product.dao;

import java.util.List;

import com.cts.product.entity.Employee;

public interface EmployeeDao {

	void saveEmployee(Employee emp);

	List<Employee> viewEmployee();

}