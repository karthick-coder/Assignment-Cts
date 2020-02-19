package com.cts.product.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cts.product.dao.EmployeeDao;
import com.cts.product.entity.Employee;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class EmployeeServiceImpl {

	
	
	private final EmployeeDao empDao;
	
	
	@Transactional
	public void saveEmployee(Employee emp) {
		empDao.saveEmployee(emp);
	}


	@Transactional
	public List<Employee> viewEmployee() {
		return empDao.viewEmployee();
	}
	
	
	
}




