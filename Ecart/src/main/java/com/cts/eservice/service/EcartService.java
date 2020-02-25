package com.cts.eservice.service;

import java.util.List;

import com.cts.eservice.entity.Communication;
import com.cts.eservice.entity.Customer;
import com.cts.eservice.entity.Orders;
import com.cts.eservice.entity.Product;
import com.cts.eservice.entity.Review;

public interface EcartService {

	Product saveProduct(Product prod);

	Product getProductById(int pId);

	List<Product> getProductByName(String name);

	List<Product> listProduct();

	List<Orders> getOrderByUserId(int userId);

	List<Orders> listAllOrders();

	List<Review> listReviewByProductId(int pId);

	Review updateReview(int pId, int userId);

	Orders saveOrder(Orders order);

	Customer getUserById(int userId);

	Communication getaddressById(String string);

}