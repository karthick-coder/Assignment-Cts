package com.employee.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.employee.model.Employee;
import com.employee.repository.Employeerepository;
import lombok.AllArgsConstructor;

@AllArgsConstructor
@Service
public class EmployeeService {
	
	private final Employeerepository employeerepository;

	public Employee save(Employee employee) {
		List<Employee> employees =employeerepository.findAll();
		List<String> ename = employees.stream().map(Employee::getFirstName).collect(Collectors.toList());
	    employees.stream().forEach(employe->employe.setFirstName("chnagd"));
	    Optional.ofNullable(ename).filter(name -> name.equals("chnagd"));
		return employeerepository.save(employee);
	}
	

}
