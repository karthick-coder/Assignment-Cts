package com.cts.eservice.dao;

import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.cts.eservice.entity.Communication;
import com.cts.eservice.entity.Customer;
import com.cts.eservice.entity.Orders;
import com.cts.eservice.entity.Product;
import com.cts.eservice.entity.Review;

@Repository
public class EcartDaoImpl implements EcartDao {

	@Autowired
	private SessionFactory sessionFactory;

	public Product saveProduct(Product prod) {
		sessionFactory.getCurrentSession().save(prod);
		System.out.println("Saved");
		return prod;
	}

	@SuppressWarnings("unchecked")
	public List<Product> listProduct() {
		List<Product> productList = sessionFactory.getCurrentSession().createQuery("from Product").list();
		System.out.println(productList.size());
		productList.forEach(product-> {
			System.out.println(product);
		});
		return productList;
	}

	@SuppressWarnings("unchecked")
	public List<Orders> getOrderByUserId(int userId) {

		Query<?> query = sessionFactory.getCurrentSession().createQuery("from Orders where user_id = :uId");
		query.setParameter("uId", userId);
		Object queryResult = query.getResultList();
		return (List<Orders>) queryResult;
	}

	@SuppressWarnings("unchecked")
	public List<Orders> listAllOrders() {

		List<Orders> orderList = sessionFactory.getCurrentSession().createQuery("from Orders").list();
		orderList.forEach(order-> {
			System.out.println(order);
		});
		return orderList;

	}

	public Product getProductById(int pId) {

		Query<?> query = sessionFactory.getCurrentSession().createQuery("from Product where product_id = :pId");
		query.setParameter("pId", pId);
		Object queryResult = query.uniqueResult();
		return (Product) queryResult;

	}

	@SuppressWarnings("unchecked")
	public List<Product> getProductByName(String name) {

		Query<?> query = sessionFactory.getCurrentSession().createQuery("from Product where productName = :pName");
		query.setParameter("pName", name);
		Object queryResult = query.getResultList();
		return (List<Product>) queryResult;

	}
	
	public Communication getaddressById(String addressId) {
		Query<?> query = sessionFactory.getCurrentSession().createQuery("from Communication where address_id = :addressId");
		query.setParameter("addressId", addressId);
		Object queryResult = query.uniqueResult();
		return (Communication) queryResult;
	}

	@SuppressWarnings("unchecked")
	public List<Review> listReviewByProductId(int pId) {
		Query<?> query = sessionFactory.getCurrentSession().createQuery("from Review where product_id = :pId");
		query.setParameter("pId", pId);
		Object queryResult = query.getResultList();
		return (List<Review>) queryResult;

	}

	public Review updateReview(int pId, int userId) {
		Query<?> query = sessionFactory.getCurrentSession().createQuery("from Review where product_id = :pId and user_id = :userId");
		query.setParameter("pId", pId);
		query.setParameter("userId", userId);
		Review review = (Review) query.uniqueResult();
		review.setBody("Good Product");
		return review;

	}

	public Orders saveOrder(Orders order) {
		sessionFactory.getCurrentSession().save(order);
		System.out.println("Saved");
		return order;
	}

	public Customer getUserById(int userId) {
		Query<?> query = sessionFactory.getCurrentSession().createQuery("from Customer where user_id = :userId");
		query.setParameter("userId", userId);
		Object queryResult = query.uniqueResult();
		return (Customer) queryResult;
	}

	

}
