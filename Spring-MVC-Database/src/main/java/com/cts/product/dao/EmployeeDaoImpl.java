package com.cts.product.dao;

import java.util.List;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.internal.SessionFactoryRegistry;
import org.hibernate.query.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.cts.product.entity.Employee;

@Repository
public class EmployeeDaoImpl implements EmployeeDao {

	@Autowired
	private SessionFactory sf;
	
	public void saveEmployee(Employee emp) {
		sf.getCurrentSession().save(emp);
		System.out.println("Saved");
	}

	public List<Employee> viewEmployee() {
		Session ses=sf.getCurrentSession();
		Query<Employee> qry=ses.createQuery("from Employee");
		List<Employee> emp=qry.getResultList();
		
		return emp;
		
		 
	}
}
