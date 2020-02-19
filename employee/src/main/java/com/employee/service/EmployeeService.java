package com.employee.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.employee.dao.EmployeeDao;
import com.employee.model.Employee;
@Service
public class EmployeeService {
	
	@Autowired
	private EmployeeDao employeeDao;
	
	public List<Employee> getAllEmployee(){
		
		return employeeDao.findAll();
		
	}
	
	public Employee findById(Integer empid){
		
		return employeeDao.findById(empid);	
	}
	
	
	public boolean saveEmployee(Employee emp){
		System.out.println("started");
		
		return employeeDao.saveEmployee(emp);
		
	}
	
    public List<Employee> findByName(String empName){
		
		return employeeDao.findByName(empName);
		
	}
    
    public boolean saveEmployee(String empId){
		
		return employeeDao.deleteById(empId);
		
	}

}
