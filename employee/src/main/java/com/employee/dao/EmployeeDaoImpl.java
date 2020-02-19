package com.employee.dao;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.employee.model.Employee;
@Component
public class EmployeeDaoImpl implements EmployeeDao{
	
	List<Employee> employees = new ArrayList<Employee>();

	public boolean saveEmployee(Employee emp) {
		employees.add(emp);
		System.out.println(emp.getName() +" is added Successfully");
		return true;
	}

	public Employee findById(Integer empId) {
		// TODO Auto-generated method stub
		employees.forEach(e-> {
			if(e.getId()== empId){
				System.out.println(e.getName());
			}
		});
		return null;
	}

	public List<Employee> findByName(String empName) {
		employees.forEach(e-> {
			if(e.getName().equals(empName) ){
				System.out.println(e.getName());
			}
			});
		return employees;
	}

	public List<Employee> findAll() {
		// TODO Auto-generated method stub
		employees.stream().forEach(e->{
			System.out.println(e.getName()+","+e.getId());
		});
		return employees;
	}

	public boolean deleteById(String empId) {
		// TODO Auto-generated method stub
		return false;
	}

}
