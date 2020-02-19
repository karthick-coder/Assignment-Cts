package com.cts.product.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

import com.cts.product.entity.Employee;
import com.cts.product.service.EmployeeServiceImpl;

import lombok.AllArgsConstructor;

@Controller
@AllArgsConstructor
public class ProductController {

	
	
	private final EmployeeServiceImpl es;
	
	@RequestMapping("/")
	public String welcome() {
		return "index";
	}
	
	@RequestMapping("loadForm")
	public String loadForm() {
		return "employeeform";
	}
	
	@RequestMapping("saveEmployee")
	public void saveEmployee(@ModelAttribute Employee emp) {
		
	es.saveEmployee(emp);
	}
	
	@RequestMapping("viewEmployee")
	public String viewEmployee(Model model)
	{
		model.addAttribute("employee", new Employee());
		model.addAttribute("viewEmployee", es.viewEmployee());
		return "showDbView";
		
	}
		
		
	
	
	
	
	
	
}
