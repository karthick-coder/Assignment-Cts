package com.employee.controller;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.AbstractApplicationContext;

import com.employee.model.Employee;
import com.employee.service.EmployeeService;

@Configuration
@ComponentScan("com")
public class EmployeeController {
	
	
	public static void main(String[] args) {
		AbstractApplicationContext ac =new AnnotationConfigApplicationContext(EmployeeController.class);
		
		EmployeeService emp = ac.getBean(EmployeeService.class);
		Employee employee = new Employee();
		employee.setId(1);
		employee.setName("Ronaldo");
		employee.setSalary(100000);
		emp.saveEmployee(employee);
		emp.findById(1);
		emp.findByName(employee.getName());
		emp.getAllEmployee();
		
		
//		List<LocalDate> dates = new ArrayList<>();
//		dates.add(LocalDate.now());
//		dates.add(LocalDate.now());
//		dates.add(LocalDate.now());
//		dates.add(LocalDate.now());
//		dates.add(LocalDate.now());
//		System.out.println(dates.size());
//		
//		List<LocalDate> date1 = new ArrayList<>();
//		IntStream.range(0, dates.size())
//		   .filter(i -> i % 2 != 0).mapToObj(i-> date1.add(dates.get(i)));
//   	         
//   	System.out.println(date1.size());
//		
//	    	
//	    	String[] names = {"Sam", "Pamela", "Dave", "Pascal", "Erik"};
//	    List<String> name = IntStream.range(0, names.length)
//	    	         .filter(i -> i % 2 != 0)
//	    	         .mapToObj(i -> names[i])
//	    	         .collect(Collectors.toList());
//	    	System.out.println(name);
		
	}

}
