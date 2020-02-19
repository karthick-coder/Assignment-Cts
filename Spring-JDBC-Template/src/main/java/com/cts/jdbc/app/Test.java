package com.cts.jdbc.app;

import java.util.List;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;

import com.cts.jdbc.config.AppConfig;
import com.cts.jdbc.dao.ProductDao;
import com.cts.jdbc.model.Product;

public class Test {
	
	public static void main(String[] args) {
		
		AbstractApplicationContext ac=new AnnotationConfigApplicationContext(AppConfig.class);

		ProductDao productDao=ac.getBean(ProductDao.class);
		
		Product product= new Product();
		product.setProductId(1);
		product.setProductName("Cristiano");
		product.setPrice(5000);
		productDao.saveProduct(product);
		productDao.findById(1);
		productDao.deleteById(1);
		productDao.editData("CR7", 7);
		productDao.findAll();
		List<Product> product1=productDao.findByPriceRange(100,20000);
		product1.forEach(prod->{
			System.out.println(prod);
		});
		List<Product> product2=productDao.findBetweenId(1, 15);
		product2.forEach(prod->{
			System.out.println(prod);
		});
	}
	
}
