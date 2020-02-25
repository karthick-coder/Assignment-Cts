package com.cts.eservice.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cts.eservice.entity.Communication;
import com.cts.eservice.entity.Customer;
import com.cts.eservice.entity.Orders;
import com.cts.eservice.entity.Product;
import com.cts.eservice.entity.Review;
import com.cts.eservice.service.EcartService;

import lombok.AllArgsConstructor;
@AllArgsConstructor
@RestController
@RequestMapping("api/")
public class EcartController {

	private final EcartService service;

	@GetMapping("getProduct")
	public ResponseEntity<List<Product>> listProducts() {
		return Optional.ofNullable(service.listProduct())
				.map(response-> new ResponseEntity<List<Product>>(response,HttpStatus.OK))
						.orElse(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
	}

	@GetMapping("productById/{pId}")
	public ResponseEntity<Product> getProductById(@PathVariable("pId") int pId) {
		return Optional.ofNullable(service.getProductById(pId))
				.map(response-> new ResponseEntity<>(response,HttpStatus.OK))
				.orElse(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
	}

	@GetMapping("productByName/{pName}")
	public ResponseEntity<List<Product>> getProductByName(@PathVariable("pName") String pName) {
		return Optional.ofNullable(service.getProductByName(pName))
				.map(response-> new ResponseEntity<>(response,HttpStatus.OK))
				.orElse(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
	}


	@GetMapping("order/{userId}")
	public ResponseEntity<List<Orders>> getOrderByUserId(@PathVariable("userId") int userId) {
		return Optional.ofNullable(service.getOrderByUserId(userId))
				.map(response-> new ResponseEntity<>(response,HttpStatus.OK))
				.orElse(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
	}

	@GetMapping("orders")
	public ResponseEntity<List<Orders>> listOrders() {

		return Optional.ofNullable(service.listAllOrders())
				.map(response-> new ResponseEntity<>(response,HttpStatus.OK))
				.orElse(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
	}
	
	@GetMapping("review/{pId}")
	public ResponseEntity<List<Review>> listReviewByProductId(@PathVariable("pId") int pId)
	{
		return Optional.ofNullable(service.listReviewByProductId(pId))
				.map(response-> new ResponseEntity<>(response,HttpStatus.OK))
				.orElse(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
	}
	
	@PostMapping("product")
	public ResponseEntity<Product> saveProduct(@RequestBody Product product) {

		return Optional.ofNullable(service.saveProduct(product))
				.map(response-> new ResponseEntity<>(response,HttpStatus.OK))
				.orElse(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
	}
	
	
	@PostMapping("order/{userId}")
	public ResponseEntity<Product> saveOrder(@RequestBody Orders order) {
		
		System.out.println("orderdetails:="+order.toString());
		Product product= service.getProductById(order.getProduct().getProductId());
		Customer customer=service.getUserById(order.getCustomer().getUserId());
		Communication communication=service.getaddressById(order.getCommunication().getAddressId());
		order.setCustomer(customer);
		order.setProduct(product);
		order.setCommunication(communication);

		
		return Optional.ofNullable(service.saveProduct(product))
				.map(response-> new ResponseEntity<>(response,HttpStatus.OK))
				.orElse(new ResponseEntity<>(HttpStatus.BAD_REQUEST));

	}
	
	@PutMapping("{pId}/reviews")
	public Review updateReviewById(@PathVariable("pId") int pId,@RequestParam("userId") int userId)
	{
		return service.updateReview(pId,userId);
	}

}
