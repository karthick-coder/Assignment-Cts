package com.employee.dao;

import java.util.List;

import com.employee.model.Employee;

public interface EmployeeDao {
	
	boolean saveEmployee(Employee emp);
	
	Employee findById(Integer empId);
	
	List<Employee> findByName(String empName);
	
	List<Employee> findAll();
	
	boolean deleteById(String empId);

}
